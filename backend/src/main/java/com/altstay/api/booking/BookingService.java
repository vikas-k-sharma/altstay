package com.altstay.api.booking;

import com.altstay.api.inventory.RoomType;
import com.altstay.api.inventory.RoomTypeRepository;
import com.altstay.api.inventory.RoomTypeSpace;
import com.altstay.api.inventory.RoomTypeSpaceRepository;
import com.altstay.api.inventory.Space;
import com.altstay.api.inventory.SpaceRepository;
import com.altstay.api.inventory.Unit;
import com.altstay.api.inventory.UnitRepository;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.rate.QuoteCalculator;
import com.altstay.api.rate.RateCalendar;
import com.altstay.api.rate.RateCalendarRepository;
import com.altstay.api.rate.RatePlan;
import com.altstay.api.rate.RatePlanRepository;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.TenantScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@TenantScoped
@Transactional
@Slf4j
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.datasource.url")
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingLineRepository bookingLineRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final AllocationRepository allocationRepository;
    private final GuestRepository guestRepository;
    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final SpaceRepository spaceRepository;
    private final UnitRepository unitRepository;
    private final RoomTypeSpaceRepository roomTypeSpaceRepository;
    private final RatePlanRepository ratePlanRepository;
    private final RateCalendarRepository rateCalendarRepository;

    private static final String REFERENCE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record GuestDto(
            UUID id,
            String fullName,
            String email,
            String phone,
            String countryCode,
            LocalDate dateOfBirth,
            String notes
    ) {}

    public record CreateBookingLineRequest(
            UUID roomTypeId,
            UUID spaceId,
            LocalDate checkIn,
            LocalDate checkOut,
            int unitCount,
            Long amountMinor
    ) {}

    public record CreateBookingRequest(
            UUID propertyId,
            String propertySlug,
            GuestDto guest,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer adults,
            Integer children,
            String source,
            List<CreateBookingLineRequest> lines,
            String idempotencyKey,
            String notes
    ) {}

    public record ModifyBookingRequest(
            LocalDate checkIn,
            LocalDate checkOut,
            Integer adults,
            Integer children,
            List<CreateBookingLineRequest> lines,
            String notes
    ) {}

    public record TransitionRequest(
            String to,
            String reason
    ) {}

    public record BookingLineResponse(
            UUID id,
            UUID roomTypeId,
            String roomTypeCode,
            UUID spaceId,
            LocalDate checkIn,
            LocalDate checkOut,
            int unitCount,
            long amountMinor
    ) {}

    public record AllocationResponse(
            UUID id,
            UUID unitId,
            String unitLabel,
            UUID bookingLineId,
            LocalDate checkIn,
            LocalDate checkOut,
            OffsetDateTime releasedAt
    ) {}

    public record BookingStatusHistoryResponse(
            UUID id,
            String fromStatus,
            String toStatus,
            UUID changedBy,
            String reason,
            OffsetDateTime changedAt
    ) {}

    public record BookingResponse(
            UUID id,
            String reference,
            UUID propertyId,
            UUID guestId,
            GuestDto guest,
            String status,
            String source,
            LocalDate checkIn,
            LocalDate checkOut,
            int adults,
            int children,
            String currencyCode,
            long subtotalMinor,
            long taxMinor,
            long totalMinor,
            long amountPaidMinor,
            String paymentState,
            String idempotencyKey,
            String notes,
            List<BookingLineResponse> lines,
            List<AllocationResponse> allocations,
            List<BookingStatusHistoryResponse> statusHistory,
            boolean earlyCheckIn
    ) {}

    /** One property-local day at the front desk (§9). */
    public record FrontDeskResponse(
            UUID propertyId,
            String propertySlug,
            LocalDate date,
            List<BookingResponse> arrivals,
            List<BookingResponse> departures,
            List<BookingResponse> inHouse
    ) {}

    public BookingResponse createBooking(CreateBookingRequest req, UUID actingUserId) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));

        // 1. Check idempotency key if provided
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(req.idempotencyKey().trim());
            if (existing.isPresent()) {
                log.info("Returning existing booking for idempotency key: reference={}", existing.get().getReference());
                return getBooking(existing.get().getReference());
            }
        }

        // 2. Resolve property
        Property property;
        if (req.propertyId() != null) {
            property = propertyRepository.findById(req.propertyId())
                    .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + req.propertyId()));
        } else if (req.propertySlug() != null) {
            property = propertyRepository.findBySlug(req.propertySlug())
                    .orElseThrow(() -> new IllegalArgumentException("Property not found with slug: " + req.propertySlug()));
        } else {
            throw new IllegalArgumentException("Either propertyId or propertySlug must be provided");
        }

        if (req.checkIn() == null || req.checkOut() == null || !req.checkOut().isAfter(req.checkIn())) {
            throw new IllegalArgumentException("checkOut must be strictly after checkIn");
        }

        // 3. Resolve or create guest (PII: do not log names, emails, phones)
        Guest guest = resolveOrCreateGuest(tenantId, req.guest());

        // 4. Generate unique reference
        String reference = generateUniqueReference(tenantId);

        // 5. Price every line once, through QuoteCalculator: nights x rate x units, with the rate
        //    calendar consulted per night and the room type's base rate as the fallback.
        List<PricedLine> pricedLines = priceLines(req.lines(), req.checkIn(), req.checkOut());
        long subtotalMinor = pricedLines.stream().mapToLong(PricedLine::amountMinor).sum();
        long taxMinor = QuoteCalculator.taxOn(subtotalMinor, property.getTaxRateBps());
        long totalMinor = subtotalMinor + taxMinor;

        // 6. Create booking header
        Booking booking = new Booking(
                tenantId,
                property.getId(),
                reference,
                guest.getId(),
                BookingStatus.BOOKED.name(),
                req.source() != null ? req.source() : "DIRECT",
                req.checkIn(),
                req.checkOut(),
                property.getCurrencyCode(),
                subtotalMinor,
                taxMinor,
                totalMinor
        );
        booking.setAdults(req.adults() != null ? req.adults() : 1);
        booking.setChildren(req.children() != null ? req.children() : 0);
        booking.setIdempotencyKey(req.idempotencyKey());
        booking.setNotes(req.notes());
        booking.setCreatedBy(actingUserId);
        booking = bookingRepository.save(booking);

        // 7. Create booking lines and allocate units
        List<BookingLine> lines = new ArrayList<>();
        List<Allocation> allocations = new ArrayList<>();

        for (PricedLine priced : pricedLines) {
            BookingLine line = new BookingLine(
                    tenantId,
                    booking.getId(),
                    priced.roomType().getId(),
                    priced.spaceId(),
                    priced.checkIn(),
                    priced.checkOut(),
                    priced.unitCount(),
                    priced.amountMinor()
            );
            line = bookingLineRepository.save(line);
            lines.add(line);

            // Allocate beds
            allocations.addAll(allocateUnitsForLine(
                    tenantId, line, priced.roomType(), priced.spaceId(), priced.checkIn(), priced.checkOut()));
        }

        // 8. Record initial status history
        BookingStatusHistory history = new BookingStatusHistory(
                tenantId,
                booking.getId(),
                null,
                BookingStatus.BOOKED.name(),
                actingUserId,
                "Initial booking creation"
        );
        bookingStatusHistoryRepository.save(history);

        log.info("Booking created: reference={}, propertyId={}, totalMinor={}", reference, property.getId(), totalMinor);
        return toBookingResponse(booking, guest, lines, allocations, List.of(history), false);
    }

    public BookingResponse getBooking(String reference) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));
        Booking booking = bookingRepository.findByReference(reference)
                .orElseThrow(() -> new BookingNotFoundException(reference));
        requireCurrentTenantOwns(booking.getTenantId(), reference);

        Guest guest = guestRepository.findById(booking.getGuestId()).orElse(null);
        List<BookingLine> lines = bookingLineRepository.findByBookingIdAndSupersededAtIsNull(booking.getId());
        List<UUID> lineIds = lines.stream().map(BookingLine::getId).toList();
        List<Allocation> allocations = lineIds.isEmpty() ? List.of() :
                lineIds.stream().flatMap(lid -> allocationRepository.findByBookingLineId(lid).stream()).toList();
        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());

        return toBookingResponse(booking, guest, lines, allocations, history, false);
    }

    /**
     * Lists bookings, filtered the way §9 asks: by property, status, stay dates, guest, or
     * reference. Every filter is optional and they compose.
     *
     * <p>The date filter is an <em>overlap</em> test, not an equality one. A front desk asking
     * "who is here this week" means every stay that touches the week, not only those that start in
     * it — a guest who arrived last Thursday for ten nights is very much here.
     */
    public List<BookingResponse> listBookings(
            UUID propertyId, String status, LocalDate from, LocalDate to, UUID guestId, String reference) {

        if (reference != null && !reference.isBlank()) {
            // A reference is unique per tenant, so this is a lookup wearing a filter's clothes.
            return bookingRepository.findByReference(reference.trim())
                    .filter(b -> CurrentTenantHolder.get().map(t -> t.equals(b.getTenantId())).orElse(false))
                    .map(b -> List.of(getBooking(b.getReference())))
                    .orElseGet(List::of);
        }

        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> propertyId == null || propertyId.equals(b.getPropertyId()))
                .filter(b -> status == null || status.isBlank() || status.equalsIgnoreCase(b.getStatus()))
                .filter(b -> guestId == null || guestId.equals(b.getGuestId()))
                .filter(b -> overlapsRange(b, from, to))
                .toList();

        return bookings.stream().map(b -> {
            Guest guest = guestRepository.findById(b.getGuestId()).orElse(null);
            List<BookingLine> lines = bookingLineRepository.findByBookingIdAndSupersededAtIsNull(b.getId());
            List<BookingStatusHistory> history = bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(b.getId());
            return toBookingResponse(b, guest, lines, List.of(), history, false);
        }).toList();
    }

    /** Half-open overlap: a stay touches [from, to) if it starts before `to` and ends after `from`. */
    private static boolean overlapsRange(Booking booking, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (to != null && !booking.getCheckIn().isBefore(to)) {
            return false;
        }
        return from == null || booking.getCheckOut().isAfter(from);
    }

    /**
     * Arrivals, departures and who is in the house on one property-local date (§9's front-desk
     * row) — the query a front desk runs first thing every morning.
     */
    public FrontDeskResponse getFrontDesk(String propertySlug, LocalDate date) {
        Property property = propertyRepository.findBySlug(propertySlug)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertySlug));

        // The date defaults to today IN THE PROPERTY'S ZONE, never the server's. §2: every
        // business-day boundary in a PMS is property-local, and computing this in UTC is how the
        // arrivals list comes up empty at 6am.
        LocalDate on = date != null ? date : LocalDate.now(ZoneId.of(property.getTimezone()));

        List<Booking> ofProperty = bookingRepository.findByPropertyId(property.getId());

        List<BookingResponse> arrivals = new ArrayList<>();
        List<BookingResponse> departures = new ArrayList<>();
        List<BookingResponse> inHouse = new ArrayList<>();

        for (Booking booking : ofProperty) {
            BookingStatus status = BookingStatus.valueOf(booking.getStatus());
            if (status == BookingStatus.CANCELLED) {
                continue;
            }
            BookingResponse response = summaryOf(booking);

            if (booking.getCheckIn().equals(on) && (status == BookingStatus.BOOKED || status == BookingStatus.NO_SHOW)) {
                arrivals.add(response);
            }
            if (booking.getCheckOut().equals(on) && status == BookingStatus.CHECKED_IN) {
                departures.add(response);
            }
            // In house means checked in and not yet departed: [check_in, check_out), the same
            // half-open convention the beds are allocated on.
            if (status == BookingStatus.CHECKED_IN
                    && !on.isBefore(booking.getCheckIn())
                    && on.isBefore(booking.getCheckOut())) {
                inHouse.add(response);
            }
        }

        return new FrontDeskResponse(property.getId(), property.getSlug(), on, arrivals, departures, inHouse);
    }

    private BookingResponse summaryOf(Booking booking) {
        Guest guest = guestRepository.findById(booking.getGuestId()).orElse(null);
        List<BookingLine> lines = bookingLineRepository.findByBookingIdAndSupersededAtIsNull(booking.getId());
        return toBookingResponse(booking, guest, lines, List.of(), List.of(), false);
    }

    public BookingResponse transitionBooking(String reference, TransitionRequest req, UUID actingUserId) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));
        Booking booking = bookingRepository.findByReference(reference)
                .orElseThrow(() -> new BookingNotFoundException(reference));
        requireCurrentTenantOwns(booking.getTenantId(), reference);

        BookingStatus currentStatus = BookingStatus.valueOf(booking.getStatus());
        BookingStatus targetStatus = BookingStatus.valueOf(req.to());

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new InvalidBookingTransitionException(reference, currentStatus, targetStatus);
        }

        Property property = propertyRepository.findById(booking.getPropertyId()).orElse(null);
        ZoneId propertyZone = property != null ? ZoneId.of(property.getTimezone()) : ZoneId.systemDefault();
        LocalDate propertyToday = LocalDate.now(propertyZone);

        boolean isEarlyCheckIn = false;
        List<BookingLine> lines = bookingLineRepository.findByBookingIdAndSupersededAtIsNull(booking.getId());
        List<UUID> lineIds = lines.stream().map(BookingLine::getId).toList();
        List<Allocation> allocations = lineIds.isEmpty() ? List.of() :
                lineIds.stream().flatMap(lid -> allocationRepository.findByBookingLineId(lid).stream()).toList();

        if (targetStatus == BookingStatus.CHECKED_IN) {
            if (propertyToday.isBefore(booking.getCheckIn())) {
                isEarlyCheckIn = true;
            }
            booking.setStatus(targetStatus.name());
        } else if (targetStatus == BookingStatus.CHECKED_OUT) {
            booking.setStatus(targetStatus.name());
            // Checking out before the booked departure date shortens the allocations to end today,
            // in this same transaction, so the bed becomes sellable tonight. §5.1 calls this "the
            // single most commonly missed behaviour in a hostel PMS".
            if (propertyToday.isBefore(booking.getCheckOut()) && !propertyToday.isBefore(booking.getCheckIn())) {
                for (Allocation alloc : allocations) {
                    if (alloc.getReleasedAt() != null) {
                        continue;
                    }
                    if (!propertyToday.isAfter(alloc.getCheckIn())) {
                        // Leaving on the same day they arrived. There is no shorter range to
                        // shorten to: a zero-night allocation would violate check_out > check_in,
                        // and an empty range overlaps nothing and so would consume no inventory
                        // even if it were allowed (§4). Release the row instead — the guest held
                        // the bed, then didn't. Same-day arrival and departure is routine.
                        alloc.setReleasedAt(OffsetDateTime.now());
                        allocationRepository.save(alloc);
                    } else if (alloc.getCheckOut().isAfter(propertyToday)) {
                        alloc.setCheckOut(propertyToday);
                        allocationRepository.save(alloc);
                    }
                }
                for (BookingLine line : lines) {
                    if (line.getCheckOut().isAfter(propertyToday) && propertyToday.isAfter(line.getCheckIn())) {
                        line.setCheckOut(propertyToday);
                        bookingLineRepository.save(line);
                    }
                }
                // Same reason: booking.check_out carries check_out > check_in too, so a same-day
                // departure leaves the sold dates as they were and only the held beds change.
                if (propertyToday.isAfter(booking.getCheckIn())) {
                    booking.setCheckOut(propertyToday);
                }
            }
        } else if (targetStatus == BookingStatus.CANCELLED || targetStatus == BookingStatus.NO_SHOW) {
            booking.setStatus(targetStatus.name());
            for (Allocation alloc : allocations) {
                if (alloc.getReleasedAt() == null) {
                    alloc.setReleasedAt(OffsetDateTime.now());
                    allocationRepository.save(alloc);
                }
            }
            if (targetStatus == BookingStatus.CANCELLED) {
                booking.setCancelledAt(OffsetDateTime.now());
                booking.setCancellationReason(req.reason());
            }
        }

        booking = bookingRepository.save(booking);

        BookingStatusHistory history = new BookingStatusHistory(
                tenantId,
                booking.getId(),
                currentStatus.name(),
                targetStatus.name(),
                actingUserId,
                req.reason()
        );
        bookingStatusHistoryRepository.save(history);

        List<BookingStatusHistory> allHistory = bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());
        Guest guest = guestRepository.findById(booking.getGuestId()).orElse(null);

        log.info("Booking transitioned: reference={}, from={}, to={}", reference, currentStatus, targetStatus);
        return toBookingResponse(booking, guest, lines, allocations, allHistory, isEarlyCheckIn);
    }

    public BookingResponse modifyBooking(String reference, ModifyBookingRequest req, UUID actingUserId) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));
        Booking booking = bookingRepository.findByReference(reference)
                .orElseThrow(() -> new BookingNotFoundException(reference));
        requireCurrentTenantOwns(booking.getTenantId(), reference);

        if (!booking.getStatus().equals(BookingStatus.BOOKED.name())) {
            throw new InvalidBookingTransitionException(reference, BookingStatus.valueOf(booking.getStatus()), BookingStatus.BOOKED);
        }

        Property property = propertyRepository.findById(booking.getPropertyId())
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        // Release the old allocations and SUPERSEDE the old lines, both inside this transaction.
        //
        // Not delete: allocation.booking_line_id cascades, so deleting the lines would take every
        // allocation with it and erase which bed this guest actually held — the exact history §4's
        // partial exclusion constraint exists to keep. Superseded lines stay, their allocations stay
        // released, and only lines with superseded_at is null count as the current stay.
        OffsetDateTime now = OffsetDateTime.now();
        List<BookingLine> oldLines = bookingLineRepository.findByBookingIdAndSupersededAtIsNull(booking.getId());
        for (BookingLine line : oldLines) {
            for (Allocation alloc : allocationRepository.findByBookingLineId(line.getId())) {
                if (alloc.getReleasedAt() == null) {
                    alloc.setReleasedAt(now);
                    allocationRepository.save(alloc);
                }
            }
            line.setSupersededAt(now);
            bookingLineRepository.save(line);
        }

        // Update dates
        LocalDate newCheckIn = req.checkIn() != null ? req.checkIn() : booking.getCheckIn();
        LocalDate newCheckOut = req.checkOut() != null ? req.checkOut() : booking.getCheckOut();
        if (!newCheckOut.isAfter(newCheckIn)) {
            throw new IllegalArgumentException("checkOut must be strictly after checkIn");
        }
        booking.setCheckIn(newCheckIn);
        booking.setCheckOut(newCheckOut);
        if (req.adults() != null) booking.setAdults(req.adults());
        if (req.children() != null) booking.setChildren(req.children());
        if (req.notes() != null) booking.setNotes(req.notes());

        // Create new lines and allocations
        long subtotalMinor = 0L;
        List<BookingLine> newLines = new ArrayList<>();
        List<Allocation> newAllocations = new ArrayList<>();

        for (PricedLine priced : priceLines(req.lines(), newCheckIn, newCheckOut)) {
            subtotalMinor += priced.amountMinor();

            BookingLine line = new BookingLine(
                    tenantId,
                    booking.getId(),
                    priced.roomType().getId(),
                    priced.spaceId(),
                    priced.checkIn(),
                    priced.checkOut(),
                    priced.unitCount(),
                    priced.amountMinor()
            );
            line = bookingLineRepository.save(line);
            newLines.add(line);

            newAllocations.addAll(allocateUnitsForLine(
                    tenantId, line, priced.roomType(), priced.spaceId(), priced.checkIn(), priced.checkOut()));
        }

        long taxMinor = QuoteCalculator.taxOn(subtotalMinor, property.getTaxRateBps());
        long totalMinor = subtotalMinor + taxMinor;
        booking.setSubtotalMinor(subtotalMinor);
        booking.setTaxMinor(taxMinor);
        booking.setTotalMinor(totalMinor);
        booking = bookingRepository.save(booking);

        BookingStatusHistory history = new BookingStatusHistory(
                tenantId,
                booking.getId(),
                BookingStatus.BOOKED.name(),
                BookingStatus.BOOKED.name(),
                actingUserId,
                "Booking modification"
        );
        bookingStatusHistoryRepository.save(history);

        Guest guest = guestRepository.findById(booking.getGuestId()).orElse(null);
        List<BookingStatusHistory> allHistory = bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());

        log.info("Booking modified in single transaction: reference={}, newTotal={}", reference, totalMinor);
        return toBookingResponse(booking, guest, newLines, newAllocations, allHistory, false);
    }

    /**
     * A requested line with its room type resolved and its price settled.
     *
     * @param amountMinor what the line costs. Either the caller's explicit override — a front desk
     *                    honouring a negotiated rate — or, far more usually, the computed
     *                    nights x rate x units.
     */
    private record PricedLine(
            RoomType roomType,
            UUID spaceId,
            LocalDate checkIn,
            LocalDate checkOut,
            int unitCount,
            long amountMinor
    ) {}

    /**
     * Prices every requested line through {@link QuoteCalculator}, consulting each room type's
     * default rate plan calendar per night and falling back to the room type's base rate where the
     * calendar is silent.
     *
     * <p>A line may carry its own {@code amountMinor}, which wins — that is how a front desk
     * records a negotiated price. When it does not, the amount is computed rather than guessed.
     * Getting this wrong is not a rounding error: pricing without a night count charges a
     * five-night stay as one night.
     */
    private List<PricedLine> priceLines(
            List<CreateBookingLineRequest> lineRequests,
            LocalDate bookingCheckIn,
            LocalDate bookingCheckOut
    ) {
        if (lineRequests == null || lineRequests.isEmpty()) {
            throw new IllegalArgumentException("A booking needs at least one line");
        }

        List<PricedLine> priced = new ArrayList<>();
        for (CreateBookingLineRequest lineReq : lineRequests) {
            RoomType roomType = roomTypeRepository.findById(lineReq.roomTypeId())
                    .orElseThrow(() -> new UnknownRoomTypeException(lineReq.roomTypeId()));

            LocalDate checkIn = lineReq.checkIn() != null ? lineReq.checkIn() : bookingCheckIn;
            LocalDate checkOut = lineReq.checkOut() != null ? lineReq.checkOut() : bookingCheckOut;
            if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
                throw new IllegalArgumentException("checkOut must be strictly after checkIn on every booking line");
            }
            if (lineReq.unitCount() < 1) {
                throw new IllegalArgumentException("unitCount must be at least 1 on every booking line");
            }

            long amountMinor = lineReq.amountMinor() != null
                    ? lineReq.amountMinor()
                    : QuoteCalculator.lineSubtotal(
                            checkIn,
                            checkOut,
                            lineReq.unitCount(),
                            roomType.getBaseRateMinor(),
                            calendarRatesFor(roomType.getId(), checkIn, checkOut));

            priced.add(new PricedLine(
                    roomType, lineReq.spaceId(), checkIn, checkOut, lineReq.unitCount(), amountMinor));
        }
        return priced;
    }

    /**
     * The per-date rate overrides for a room type's default rate plan across {@code [from, to)}.
     *
     * <p>Empty when the room type has no default plan, in which case the base rate applies to every
     * night. {@code rate_plan_one_default_per_room_type} makes "which plan applies when the caller
     * names none" a database fact rather than a convention.
     */
    private Map<LocalDate, Long> calendarRatesFor(UUID roomTypeId, LocalDate from, LocalDate to) {
        Optional<RatePlan> defaultPlan = ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(roomTypeId);
        if (defaultPlan.isEmpty()) {
            return Map.of();
        }
        Map<LocalDate, Long> rates = new HashMap<>();
        // to.minusDays(1): the checkout day is not a night and is not charged.
        for (RateCalendar entry : rateCalendarRepository.findByRatePlanIdAndStayDateBetween(
                defaultPlan.get().getId(), from, to.minusDays(1))) {
            rates.put(entry.getStayDate(), entry.getAmountMinor());
        }
        return rates;
    }

    private List<Allocation> allocateUnitsForLine(
            UUID tenantId,
            BookingLine line,
            RoomType roomType,
            UUID spaceIdPreference,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        List<RoomTypeSpace> mappings = roomTypeSpaceRepository.findByRoomTypeId(roomType.getId());
        List<UUID> spaceIds = mappings.stream().map(RoomTypeSpace::getSpaceId).toList();

        if (spaceIds.isEmpty()) {
            throw new NoAvailabilityException("No physical spaces mapped to room type: " + roomType.getName());
        }

        List<Allocation> result = new ArrayList<>();

        if ("PER_UNIT".equalsIgnoreCase(roomType.getSaleMode())) {
            // Dorm beds: allocate 'unitCount' available units across mapped spaces
            List<Unit> units = unitRepository.findBySpaceIdInAndIsActiveTrue(spaceIds);
            List<UUID> unitIds = units.stream().map(Unit::getId).toList();

            List<Allocation> conflicting = allocationRepository.findActiveOverlappingAllocations(unitIds, checkIn, checkOut);
            Set<UUID> occupiedUnitIds = conflicting.stream().map(Allocation::getUnitId).collect(Collectors.toSet());

            List<Unit> availableUnits = units.stream()
                    .filter(u -> !occupiedUnitIds.contains(u.getId()))
                    .limit(line.getUnitCount())
                    .toList();

            if (availableUnits.size() < line.getUnitCount()) {
                throw new NoAvailabilityException("Insufficient available beds for " + roomType.getName());
            }

            for (Unit unit : availableUnits) {
                result.add(persistAllocation(new Allocation(tenantId, unit.getId(), line.getId(), checkIn, checkOut)));
            }
        } else {
            // WHOLE private room: allocate ALL active units in an available space
            List<UUID> targetSpaceIds = spaceIdPreference != null ? List.of(spaceIdPreference) : spaceIds;
            UUID selectedSpaceId = null;
            List<Unit> selectedSpaceUnits = null;

            for (UUID sId : targetSpaceIds) {
                List<Unit> sUnits = unitRepository.findBySpaceIdInAndIsActiveTrue(List.of(sId));
                if (sUnits.isEmpty()) continue;

                List<UUID> sUnitIds = sUnits.stream().map(Unit::getId).toList();
                List<Allocation> conflicting = allocationRepository.findActiveOverlappingAllocations(sUnitIds, checkIn, checkOut);

                if (conflicting.isEmpty()) {
                    selectedSpaceId = sId;
                    selectedSpaceUnits = sUnits;
                    break;
                }
            }

            if (selectedSpaceId == null || selectedSpaceUnits == null) {
                throw new NoAvailabilityException("No available whole space for " + roomType.getName());
            }

            line.setSpaceId(selectedSpaceId);
            bookingLineRepository.save(line);

            // A WHOLE sale takes every active unit in the space (§4.1). That is what makes one
            // exclusion constraint cover both sale modes: any dorm-bed booking for these dates now
            // collides on the same index, in either order of arrival.
            for (Unit unit : selectedSpaceUnits) {
                result.add(persistAllocation(new Allocation(tenantId, unit.getId(), line.getId(), checkIn, checkOut)));
            }
        }

        return result;
    }

    /**
     * Writes one allocation, flushing immediately so that {@code allocation_no_overlap} fires
     * <em>here</em> rather than at commit.
     *
     * <p>This is the point of the whole design and it is worth being explicit about. The
     * availability check a few lines above is an optimisation and a source of good error messages;
     * it is <b>not</b> the correctness boundary. Two requests can both read "one bed free" and both
     * proceed — roadmap §5.1: "the tree was correct in each process and the guest is still standing
     * in a lobby with a confirmation for a bed that doesn't exist." Only the database can make
     * check-and-write atomic across processes.
     *
     * <p>Without the flush, the violation would surface when the transaction commits, i.e. after
     * this service has returned, and be reported as an unhandled error. With it, the loser of the
     * race gets a 409 that a human can act on rather than a 500 with a Postgres constraint name in
     * it.
     */
    private Allocation persistAllocation(Allocation allocation) {
        try {
            return allocationRepository.saveAndFlush(allocation);
        } catch (DataIntegrityViolationException e) {
            throw new BookingConflictException(
                    "That bed was taken for these dates while this booking was being made. "
                            + "Re-check availability and try again.");
        }
    }

    /**
     * Application-level tenancy check, layered on top of RLS rather than trusting it alone.
     *
     * <p>RLS is the boundary and it is not going anywhere — but it will happily <b>cover for a
     * missing application check</b>, and that is a trap this repository has already fallen into
     * once: deleting the tenant predicate from {@code AppUserRepository}'s query left its test
     * suite passing 5/5, because Postgres filtered the row the query no longer did. A guard that
     * exists only in the database cannot be tested with the database mocked out, which means it
     * cannot be tested at all in the offline suite.
     *
     * <p>So every row this service loads by id or by reference is checked here too. In production
     * the check never fires, because RLS already filtered the row. In a unit test with a mocked
     * repository it is the only thing standing between one tenant and another's booking, and
     * {@code BookingServiceTest} asserts exactly that.
     */
    private void requireCurrentTenantOwns(UUID rowTenantId, String what) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));
        if (rowTenantId == null || !rowTenantId.equals(tenantId)) {
            throw new BookingNotFoundException(what);
        }
    }

    private Guest resolveOrCreateGuest(UUID tenantId, GuestDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Guest information is required");
        }
        if (dto.id() != null) {
            return guestRepository.findById(dto.id())
                    .orElseThrow(() -> new GuestNotFoundException("Guest not found: " + dto.id()));
        }
        // Deliberately NOT matched on email. Two travellers sharing one address - a couple, a
        // family, a tour operator booking for a group - are two guests, and silently folding them
        // into one record attaches the second person's stay history, and their PII, to the first.
        // Recognising a returning guest is a "merge guests" feature with a human in the loop, not
        // a side effect of taking a booking. Pass an explicit id to reuse an existing record.
        Guest guest = new Guest(tenantId, dto.fullName(), dto.email(), dto.phone());
        guest.setCountryCode(dto.countryCode());
        guest.setDateOfBirth(dto.dateOfBirth());
        guest.setNotes(dto.notes());
        return guestRepository.save(guest);
    }

    /**
     * A guest-facing reference: {@code ALT-} plus six characters from an alphabet with no
     * {@code 0/O} and no {@code 1/I/L}, because it gets read aloud over a phone.
     *
     * <p>The probe below narrows the window; it does not close it. Two transactions can both find
     * the same candidate free, and only one insert will survive {@code unique (tenant_id,
     * reference)}. Retrying <em>here</em> would not help either: once a constraint fires, the
     * PostgreSQL transaction is aborted and every subsequent statement in it fails, so a new
     * reference could not be inserted anyway. The loser gets a 409 and the caller retries the
     * request — which is why {@code DataIntegrityViolationException} maps to a conflict rather
     * than a 500. With 31^6 candidates the case is rare enough that this is the right trade.
     */
    private String generateUniqueReference(UUID tenantId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("ALT-");
            for (int i = 0; i < 6; i++) {
                sb.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (bookingRepository.findByReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique booking reference after multiple attempts");
    }

    private BookingResponse toBookingResponse(
            Booking booking,
            Guest guest,
            List<BookingLine> lines,
            List<Allocation> allocations,
            List<BookingStatusHistory> history,
            boolean earlyCheckIn
    ) {
        GuestDto guestDto = guest != null ? new GuestDto(
                guest.getId(),
                guest.getFullName(),
                guest.getEmail(),
                guest.getPhone(),
                guest.getCountryCode(),
                guest.getDateOfBirth(),
                guest.getNotes()
        ) : null;

        // Batch the lookups. Building this response used to issue one query per line and one per
        // allocation, which on a list endpoint multiplied by every booking on the page.
        Map<UUID, RoomType> roomTypesById = new HashMap<>();
        for (RoomType rt : roomTypeRepository.findAllById(
                lines.stream().map(BookingLine::getRoomTypeId).distinct().toList())) {
            roomTypesById.put(rt.getId(), rt);
        }
        Map<UUID, Unit> unitsById = new HashMap<>();
        for (Unit u : unitRepository.findAllById(
                allocations.stream().map(Allocation::getUnitId).distinct().toList())) {
            unitsById.put(u.getId(), u);
        }

        List<BookingLineResponse> lineDtos = lines.stream().map(l -> {
            RoomType rt = roomTypesById.get(l.getRoomTypeId());
            return new BookingLineResponse(
                    l.getId(),
                    l.getRoomTypeId(),
                    rt != null ? rt.getCode() : null,
                    l.getSpaceId(),
                    l.getCheckIn(),
                    l.getCheckOut(),
                    l.getUnitCount(),
                    l.getAmountMinor()
            );
        }).toList();

        List<AllocationResponse> allocDtos = allocations.stream().map(a -> {
            Unit u = unitsById.get(a.getUnitId());
            return new AllocationResponse(
                    a.getId(),
                    a.getUnitId(),
                    u != null ? u.getLabel() : null,
                    a.getBookingLineId(),
                    a.getCheckIn(),
                    a.getCheckOut(),
                    a.getReleasedAt()
            );
        }).toList();

        List<BookingStatusHistoryResponse> historyDtos = history.stream().map(h -> new BookingStatusHistoryResponse(
                h.getId(),
                h.getFromStatus(),
                h.getToStatus(),
                h.getChangedBy(),
                h.getReason(),
                h.getChangedAt()
        )).toList();

        return new BookingResponse(
                booking.getId(),
                booking.getReference(),
                booking.getPropertyId(),
                booking.getGuestId(),
                guestDto,
                booking.getStatus(),
                booking.getSource(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getAdults(),
                booking.getChildren(),
                booking.getCurrencyCode(),
                booking.getSubtotalMinor(),
                booking.getTaxMinor(),
                booking.getTotalMinor(),
                booking.getAmountPaidMinor(),
                booking.getPaymentState(),
                booking.getIdempotencyKey(),
                booking.getNotes(),
                lineDtos,
                allocDtos,
                historyDtos,
                earlyCheckIn
        );
    }
}
