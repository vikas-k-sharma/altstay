package com.altstay.api.booking;

import com.altstay.api.booking.BookingService.CreateBookingLineRequest;
import com.altstay.api.booking.BookingService.CreateBookingRequest;
import com.altstay.api.booking.BookingService.GuestDto;
import com.altstay.api.booking.BookingService.ModifyBookingRequest;
import com.altstay.api.booking.BookingService.TransitionRequest;
import com.altstay.api.inventory.RoomType;
import com.altstay.api.inventory.RoomTypeRepository;
import com.altstay.api.inventory.RoomTypeSpace;
import com.altstay.api.inventory.RoomTypeSpaceId;
import com.altstay.api.inventory.RoomTypeSpaceRepository;
import com.altstay.api.inventory.Space;
import com.altstay.api.inventory.SpaceRepository;
import com.altstay.api.inventory.Unit;
import com.altstay.api.inventory.UnitRepository;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingLineRepository bookingLineRepository;
    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private GuestRepository guestRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private RoomTypeRepository roomTypeRepository;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private RoomTypeSpaceRepository roomTypeSpaceRepository;
    @Mock
    private com.altstay.api.rate.RatePlanRepository ratePlanRepository;
    @Mock
    private com.altstay.api.rate.RateCalendarRepository rateCalendarRepository;

    @InjectMocks
    private BookingService bookingService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID ROOM_TYPE_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private Property property;
    private RoomType dormRoomType;
    private Space space;
    private Unit unit;
    private Guest guest;

    @BeforeEach
    void setUp() {
        property = new Property(TENANT_ID, "Beach Hostel", "beach-hostel", "Asia/Kolkata", "INR");
        property.setId(PROPERTY_ID);
        property.setTaxRateBps(1200); // 12%

        dormRoomType = new RoomType(TENANT_ID, PROPERTY_ID, "DORM6", "6 Bed Dorm", "PER_UNIT", "DORM", 6, 50000L);
        dormRoomType.setId(ROOM_TYPE_ID);

        space = new Space(TENANT_ID, PROPERTY_ID, "101", "1");
        space.setId(SPACE_ID);

        unit = new Unit(TENANT_ID, SPACE_ID, "101-A", "SINGLE");
        unit.setId(UNIT_ID);

        guest = new Guest(TENANT_ID, "Alice Smith", "alice@example.com", "+919876543210");
        guest.setId(GUEST_ID);
    }

    @Test
    @DisplayName("Create booking allocates bed and calculates tax correctly")
    void createBookingSuccess() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            when(propertyRepository.findBySlug("beach-hostel")).thenReturn(Optional.of(property));
            stubGuestCreation();
            when(bookingRepository.findByReference(any())).thenReturn(Optional.empty());
            when(roomTypeRepository.findById(ROOM_TYPE_ID)).thenReturn(Optional.of(dormRoomType));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                b.setId(UUID.randomUUID());
                return b;
            });
            when(bookingLineRepository.save(any(BookingLine.class))).thenAnswer(inv -> {
                BookingLine l = inv.getArgument(0);
                l.setId(UUID.randomUUID());
                return l;
            });
            when(roomTypeSpaceRepository.findByRoomTypeId(ROOM_TYPE_ID)).thenReturn(
                    List.of(new RoomTypeSpace(TENANT_ID, ROOM_TYPE_ID, SPACE_ID)));
            when(unitRepository.findBySpaceIdInAndIsActiveTrue(List.of(SPACE_ID))).thenReturn(List.of(unit));
            when(allocationRepository.findActiveOverlappingAllocations(anyCollection(), any(), any())).thenReturn(List.of());
            when(allocationRepository.saveAndFlush(any(Allocation.class))).thenAnswer(inv -> {
                Allocation a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

            var req = new CreateBookingRequest(
                    null,
                    "beach-hostel",
                    new GuestDto(null, "Alice Smith", "alice@example.com", "+919876543210", "IN", null, null),
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 3),
                    1,
                    0,
                    "DIRECT",
                    List.of(new CreateBookingLineRequest(ROOM_TYPE_ID, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 1, 100000L)),
                    null,
                    "Quiet room please"
            );

            var res = bookingService.createBooking(req, USER_ID);

            assertThat(res).isNotNull();
            assertThat(res.reference()).startsWith("ALT-");
            assertThat(res.status()).isEqualTo("BOOKED");
            assertThat(res.subtotalMinor()).isEqualTo(100000L);
            assertThat(res.taxMinor()).isEqualTo(12000L); // 12% of 100,000
            assertThat(res.totalMinor()).isEqualTo(112000L);
            assertThat(res.allocations()).hasSize(1);
        });
    }

    @Test
    @DisplayName("Idempotency key replay returns existing booking without inserting new row")
    void idempotencyReplayReturnsExistingBooking() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            Booking existingBooking = new Booking(
                    TENANT_ID, PROPERTY_ID, "ALT-EXIST1", GUEST_ID, "BOOKED", "DIRECT",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), "INR", 100000L, 12000L, 112000L);
            existingBooking.setId(UUID.randomUUID());
            existingBooking.setIdempotencyKey("idemp-key-123");

            when(bookingRepository.findByIdempotencyKey("idemp-key-123")).thenReturn(Optional.of(existingBooking));
            when(bookingRepository.findByReference("ALT-EXIST1")).thenReturn(Optional.of(existingBooking));
            when(guestRepository.findById(GUEST_ID)).thenReturn(Optional.of(guest));

            var req = new CreateBookingRequest(
                    null,
                    "beach-hostel",
                    new GuestDto(null, "Alice Smith", "alice@example.com", null, null, null, null),
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 3),
                    1, 0, "DIRECT", List.of(), "idemp-key-123", null
            );

            var res = bookingService.createBooking(req, USER_ID);

            assertThat(res.reference()).isEqualTo("ALT-EXIST1");
            verify(bookingRepository, never()).save(any(Booking.class));
        });
    }

    @Test
    @DisplayName("Illegal transition throws InvalidBookingTransitionException (409 problem type)")
    void illegalTransitionThrowsException() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            Booking booking = new Booking(
                    TENANT_ID, PROPERTY_ID, "ALT-TRANS1", GUEST_ID, "CHECKED_OUT", "DIRECT",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), "INR", 100000L, 12000L, 112000L);
            booking.setId(UUID.randomUUID());

            when(bookingRepository.findByReference("ALT-TRANS1")).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.transitionBooking(
                    "ALT-TRANS1",
                    new TransitionRequest("CHECKED_IN", "Reopen"),
                    USER_ID
            ))
                    .isInstanceOf(InvalidBookingTransitionException.class)
                    .hasMessageContaining("Cannot transition booking ALT-TRANS1 from CHECKED_OUT to CHECKED_IN");
        });
    }

    @Test
    @DisplayName("Cancellation releases all active allocations")
    void cancelReleasesAllocations() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            Booking booking = new Booking(
                    TENANT_ID, PROPERTY_ID, "ALT-CANC1", GUEST_ID, "BOOKED", "DIRECT",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), "INR", 100000L, 12000L, 112000L);
            UUID bookingId = UUID.randomUUID();
            booking.setId(bookingId);

            BookingLine line = new BookingLine(TENANT_ID, bookingId, ROOM_TYPE_ID, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 1, 100000L);
            UUID lineId = UUID.randomUUID();
            line.setId(lineId);

            Allocation alloc = new Allocation(TENANT_ID, UNIT_ID, lineId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

            when(bookingRepository.findByReference("ALT-CANC1")).thenReturn(Optional.of(booking));
            when(bookingLineRepository.findByBookingIdAndSupersededAtIsNull(bookingId)).thenReturn(List.of(line));
            when(allocationRepository.findByBookingLineId(lineId)).thenReturn(List.of(alloc));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            var res = bookingService.transitionBooking(
                    "ALT-CANC1",
                    new TransitionRequest("CANCELLED", "Flight cancelled"),
                    USER_ID
            );

            assertThat(res.status()).isEqualTo("CANCELLED");
            assertThat(alloc.getReleasedAt()).isNotNull();
            verify(allocationRepository).save(alloc);
        });
    }

    @Test
    @DisplayName("Early checkout shortens active allocations to today")
    void earlyCheckOutShortensAllocations() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            LocalDate today = LocalDate.now();
            LocalDate futureCheckOut = today.plusDays(3);
            LocalDate pastCheckIn = today.minusDays(1);

            Booking booking = new Booking(
                    TENANT_ID, PROPERTY_ID, "ALT-EARLY1", GUEST_ID, "CHECKED_IN", "DIRECT",
                    pastCheckIn, futureCheckOut, "INR", 100000L, 12000L, 112000L);
            UUID bookingId = UUID.randomUUID();
            booking.setId(bookingId);

            BookingLine line = new BookingLine(TENANT_ID, bookingId, ROOM_TYPE_ID, null, pastCheckIn, futureCheckOut, 1, 100000L);
            UUID lineId = UUID.randomUUID();
            line.setId(lineId);

            Allocation alloc = new Allocation(TENANT_ID, UNIT_ID, lineId, pastCheckIn, futureCheckOut);

            when(bookingRepository.findByReference("ALT-EARLY1")).thenReturn(Optional.of(booking));
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
            when(bookingLineRepository.findByBookingIdAndSupersededAtIsNull(bookingId)).thenReturn(List.of(line));
            when(allocationRepository.findByBookingLineId(lineId)).thenReturn(List.of(alloc));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            var res = bookingService.transitionBooking(
                    "ALT-EARLY1",
                    new TransitionRequest("CHECKED_OUT", "Departing early"),
                    USER_ID
            );

            assertThat(res.status()).isEqualTo("CHECKED_OUT");
            assertThat(booking.getCheckOut()).isEqualTo(today);
            assertThat(alloc.getCheckOut()).isEqualTo(today);
            verify(allocationRepository).save(alloc);
            verify(bookingLineRepository).save(line);
        });
    }

    @Test
    @DisplayName("Modify booking re-allocates in single transaction")
    void modifyBookingReallocates() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            LocalDate checkIn = LocalDate.of(2026, 9, 1);
            LocalDate checkOut = LocalDate.of(2026, 9, 3);
            LocalDate newCheckIn = LocalDate.of(2026, 9, 2);
            LocalDate newCheckOut = LocalDate.of(2026, 9, 5);

            Booking booking = new Booking(
                    TENANT_ID, PROPERTY_ID, "ALT-MOD1", GUEST_ID, "BOOKED", "DIRECT",
                    checkIn, checkOut, "INR", 100000L, 12000L, 112000L);
            UUID bookingId = UUID.randomUUID();
            booking.setId(bookingId);

            BookingLine oldLine = new BookingLine(TENANT_ID, bookingId, ROOM_TYPE_ID, null, checkIn, checkOut, 1, 100000L);
            UUID oldLineId = UUID.randomUUID();
            oldLine.setId(oldLineId);

            Allocation oldAlloc = new Allocation(TENANT_ID, UNIT_ID, oldLineId, checkIn, checkOut);

            when(bookingRepository.findByReference("ALT-MOD1")).thenReturn(Optional.of(booking));
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
            when(bookingLineRepository.findByBookingIdAndSupersededAtIsNull(bookingId)).thenReturn(List.of(oldLine));
            when(allocationRepository.findByBookingLineId(oldLineId)).thenReturn(List.of(oldAlloc));
            when(roomTypeRepository.findById(ROOM_TYPE_ID)).thenReturn(Optional.of(dormRoomType));
            when(bookingLineRepository.save(any(BookingLine.class))).thenAnswer(inv -> {
                BookingLine l = inv.getArgument(0);
                l.setId(UUID.randomUUID());
                return l;
            });
            when(roomTypeSpaceRepository.findByRoomTypeId(ROOM_TYPE_ID)).thenReturn(
                    List.of(new RoomTypeSpace(TENANT_ID, ROOM_TYPE_ID, SPACE_ID)));
            when(unitRepository.findBySpaceIdInAndIsActiveTrue(List.of(SPACE_ID))).thenReturn(List.of(unit));
            when(allocationRepository.findActiveOverlappingAllocations(anyCollection(), any(), any())).thenReturn(List.of());
            when(allocationRepository.saveAndFlush(any(Allocation.class))).thenAnswer(inv -> {
                Allocation a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            var modifyReq = new ModifyBookingRequest(
                    newCheckIn,
                    newCheckOut,
                    1,
                    0,
                    List.of(new CreateBookingLineRequest(ROOM_TYPE_ID, null, newCheckIn, newCheckOut, 1, 150000L)),
                    "Changed dates"
            );

            var res = bookingService.modifyBooking("ALT-MOD1", modifyReq, USER_ID);

            assertThat(res.checkIn()).isEqualTo(newCheckIn);
            assertThat(res.checkOut()).isEqualTo(newCheckOut);
            assertThat(res.subtotalMinor()).isEqualTo(150000L);
            assertThat(res.taxMinor()).isEqualTo(18000L); // 12% of 150,000
            assertThat(res.totalMinor()).isEqualTo(168000L);
            assertThat(oldAlloc.getReleasedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Pricing without an explicit amount is nights x rate x units, not a single night")
    void multiNightBookingIsPricedPerNight() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            when(propertyRepository.findBySlug("beach-hostel")).thenReturn(Optional.of(property));
            stubGuestCreation();
            when(bookingRepository.findByReference(any())).thenReturn(Optional.empty());
            when(roomTypeRepository.findById(ROOM_TYPE_ID)).thenReturn(Optional.of(dormRoomType));
            when(ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(ROOM_TYPE_ID)).thenReturn(Optional.empty());
            stubPersistence();

            // 4 nights, 2 beds, base rate 50 000 minor, no calendar override.
            var req = new CreateBookingRequest(
                    null, "beach-hostel",
                    new GuestDto(null, "Alice Smith", "alice@example.com", null, null, null, null),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                    2, 0, "DIRECT",
                    List.of(new CreateBookingLineRequest(ROOM_TYPE_ID, null, null, null, 2, null)),
                    null, null);

            var res = bookingService.createBooking(req, USER_ID);

            assertThat(res.subtotalMinor())
                    .as("4 nights x 2 beds x 50000 - pricing that ignores the night count charges 100000")
                    .isEqualTo(400_000L);
            assertThat(res.taxMinor()).isEqualTo(48_000L);   // 12% of 400000, rounded once on the total
            assertThat(res.totalMinor()).isEqualTo(448_000L);
        });
    }

    @Test
    @DisplayName("A rate calendar entry overrides the base rate for that night only")
    void rateCalendarOverridesBaseRateForOneNight() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            when(propertyRepository.findBySlug("beach-hostel")).thenReturn(Optional.of(property));
            stubGuestCreation();
            when(bookingRepository.findByReference(any())).thenReturn(Optional.empty());
            when(roomTypeRepository.findById(ROOM_TYPE_ID)).thenReturn(Optional.of(dormRoomType));

            var ratePlan = new com.altstay.api.rate.RatePlan(
                    TENANT_ID, PROPERTY_ID, ROOM_TYPE_ID, "STD", "Standard", true);
            UUID ratePlanId = UUID.randomUUID();
            ratePlan.setId(ratePlanId);
            when(ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(ROOM_TYPE_ID))
                    .thenReturn(Optional.of(ratePlan));
            when(rateCalendarRepository.findByRatePlanIdAndStayDateBetween(
                    ratePlanId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)))
                    .thenReturn(List.of(newRateCalendarEntry(ratePlanId, LocalDate.of(2026, 9, 2), 90_000L)));
            stubPersistence();

            var req = new CreateBookingRequest(
                    null, "beach-hostel",
                    new GuestDto(null, "Alice Smith", "alice@example.com", null, null, null, null),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                    1, 0, "DIRECT",
                    List.of(new CreateBookingLineRequest(ROOM_TYPE_ID, null, null, null, 1, null)),
                    null, null);

            var res = bookingService.createBooking(req, USER_ID);

            // 1 Sep at the base 50 000, 2 Sep overridden to 90 000.
            assertThat(res.subtotalMinor()).isEqualTo(140_000L);
        });
    }

    @Test
    @DisplayName("An unknown room type raises its own exception, so it can be a 404 rather than a 500")
    void unknownRoomTypeIsItsOwnException() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            UUID missing = UUID.randomUUID();
            when(propertyRepository.findBySlug("beach-hostel")).thenReturn(Optional.of(property));
            stubGuestCreation();
            when(bookingRepository.findByReference(any())).thenReturn(Optional.empty());
            when(roomTypeRepository.findById(missing)).thenReturn(Optional.empty());

            var req = new CreateBookingRequest(
                    null, "beach-hostel",
                    new GuestDto(null, "Alice Smith", "alice@example.com", null, null, null, null),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                    1, 0, "DIRECT",
                    List.of(new CreateBookingLineRequest(missing, null, null, null, 1, null)),
                    null, null);

            assertThatThrownBy(() -> bookingService.createBooking(req, USER_ID))
                    .isInstanceOf(UnknownRoomTypeException.class);
        });
    }

    @Test
    @DisplayName("The exclusion constraint firing becomes a BookingConflictException, not a raw data-integrity error")
    void lostRaceBecomesABookingConflict() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            when(propertyRepository.findBySlug("beach-hostel")).thenReturn(Optional.of(property));
            stubGuestCreation();
            when(bookingRepository.findByReference(any())).thenReturn(Optional.empty());
            when(roomTypeRepository.findById(ROOM_TYPE_ID)).thenReturn(Optional.of(dormRoomType));
            when(ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(ROOM_TYPE_ID)).thenReturn(Optional.empty());
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                if (b.getId() == null) {
                    b.setId(UUID.randomUUID());
                }
                return b;
            });
            when(bookingLineRepository.save(any(BookingLine.class))).thenAnswer(inv -> {
                BookingLine l = inv.getArgument(0);
                if (l.getId() == null) {
                    l.setId(UUID.randomUUID());
                }
                return l;
            });
            when(roomTypeSpaceRepository.findByRoomTypeId(ROOM_TYPE_ID)).thenReturn(
                    List.of(new RoomTypeSpace(TENANT_ID, ROOM_TYPE_ID, SPACE_ID)));
            when(unitRepository.findBySpaceIdInAndIsActiveTrue(anyCollection())).thenReturn(List.of(unit));
            when(allocationRepository.findActiveOverlappingAllocations(anyList(), any(), any()))
                    .thenReturn(List.of());
            // The bed looked free a microsecond ago; another transaction committed first. The
            // pre-check cannot prevent this and is not supposed to - the constraint is the boundary.
            when(allocationRepository.saveAndFlush(any(Allocation.class))).thenThrow(
                    new org.springframework.dao.DataIntegrityViolationException(
                            "conflicting key value violates exclusion constraint allocation_no_overlap"));

            var req = new CreateBookingRequest(
                    null, "beach-hostel",
                    new GuestDto(null, "Alice Smith", "alice@example.com", null, null, null, null),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                    1, 0, "DIRECT",
                    List.of(new CreateBookingLineRequest(ROOM_TYPE_ID, null, null, null, 1, null)),
                    null, null);

            assertThatThrownBy(() -> bookingService.createBooking(req, USER_ID))
                    .isInstanceOf(BookingConflictException.class)
                    .hasMessageContaining("Re-check availability");
        });
    }

    @Test
    @DisplayName("Same-day check-in and check-out releases the bed rather than writing a zero-night allocation")
    void sameDayCheckOutReleasesInsteadOfShortening() {
        UUID bookingId = UUID.randomUUID();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));

        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            Booking booking = new Booking(TENANT_ID, PROPERTY_ID, "ALT-SAMEDY", GUEST_ID,
                    "CHECKED_IN", "WALK_IN", today, today.plusDays(2), "INR", 50000L, 6000L, 56000L);
            booking.setId(bookingId);

            BookingLine line = new BookingLine(TENANT_ID, bookingId, ROOM_TYPE_ID, null,
                    today, today.plusDays(2), 1, 50000L);
            line.setId(UUID.randomUUID());

            Allocation alloc = new Allocation(TENANT_ID, UNIT_ID, line.getId(), today, today.plusDays(2));
            alloc.setId(UUID.randomUUID());

            when(bookingRepository.findByReference("ALT-SAMEDY")).thenReturn(Optional.of(booking));
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
            when(bookingLineRepository.findByBookingIdAndSupersededAtIsNull(bookingId)).thenReturn(List.of(line));
            when(allocationRepository.findByBookingLineId(line.getId())).thenReturn(List.of(alloc));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            bookingService.transitionBooking("ALT-SAMEDY", new TransitionRequest("CHECKED_OUT", "left early"), USER_ID);

            assertThat(alloc.getReleasedAt())
                    .as("a zero-night allocation is forbidden by check_out > check_in, so the row is released")
                    .isNotNull();
            assertThat(alloc.getCheckOut())
                    .as("the held range is left intact rather than collapsed into an invalid one")
                    .isEqualTo(today.plusDays(2));
            assertThat(booking.getCheckOut())
                    .as("booking.check_out carries the same constraint and is left alone")
                    .isEqualTo(today.plusDays(2));
        });
    }

    @Test
    @DisplayName("Modifying a booking supersedes its old lines rather than deleting them, so allocation history survives")
    void modifyPreservesHistoryInsteadOfDeletingIt() {
        UUID bookingId = UUID.randomUUID();

        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            Booking booking = new Booking(TENANT_ID, PROPERTY_ID, "ALT-HIST01", GUEST_ID,
                    "BOOKED", "DIRECT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                    "INR", 50000L, 6000L, 56000L);
            booking.setId(bookingId);

            BookingLine oldLine = new BookingLine(TENANT_ID, bookingId, ROOM_TYPE_ID, null,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 1, 50000L);
            oldLine.setId(UUID.randomUUID());
            Allocation oldAlloc = new Allocation(TENANT_ID, UNIT_ID, oldLine.getId(),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));
            oldAlloc.setId(UUID.randomUUID());

            when(bookingRepository.findByReference("ALT-HIST01")).thenReturn(Optional.of(booking));
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
            when(bookingLineRepository.findByBookingIdAndSupersededAtIsNull(bookingId)).thenReturn(List.of(oldLine));
            when(allocationRepository.findByBookingLineId(oldLine.getId())).thenReturn(List.of(oldAlloc));
            when(roomTypeRepository.findById(ROOM_TYPE_ID)).thenReturn(Optional.of(dormRoomType));
            when(ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(ROOM_TYPE_ID)).thenReturn(Optional.empty());
            when(roomTypeSpaceRepository.findByRoomTypeId(ROOM_TYPE_ID)).thenReturn(
                    List.of(new RoomTypeSpace(TENANT_ID, ROOM_TYPE_ID, SPACE_ID)));
            when(unitRepository.findBySpaceIdInAndIsActiveTrue(anyCollection())).thenReturn(List.of(unit));
            when(allocationRepository.findActiveOverlappingAllocations(anyList(), any(), any()))
                    .thenReturn(List.of());
            when(allocationRepository.saveAndFlush(any(Allocation.class))).thenAnswer(inv -> inv.getArgument(0));
            when(bookingLineRepository.save(any(BookingLine.class))).thenAnswer(inv -> {
                BookingLine l = inv.getArgument(0);
                if (l.getId() == null) {
                    l.setId(UUID.randomUUID());
                }
                return l;
            });
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            bookingService.modifyBooking("ALT-HIST01", new ModifyBookingRequest(
                    LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 8), null, null,
                    List.of(new CreateBookingLineRequest(ROOM_TYPE_ID, null, null, null, 1, null)),
                    null), USER_ID);

            verify(bookingLineRepository, never()).deleteAll(anyList());
            assertThat(oldLine.getSupersededAt())
                    .as("the old line is kept and marked, so its allocations are not cascade-deleted")
                    .isNotNull();
            assertThat(oldAlloc.getReleasedAt())
                    .as("the bed it held is released, and the row saying which bed it was survives")
                    .isNotNull();
        });
    }

    @Test
    @DisplayName("A booking belonging to another tenant is refused by the application, not only by RLS")
    void crossTenantBookingIsRefusedByTheApplicationNotOnlyByRls() {
        UUID otherTenant = UUID.randomUUID();

        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            // The repository is MOCKED, so RLS is not in the picture at all. That is the point:
            // against a real database the policy would filter this row and the test could not tell
            // a working application-level guard from a missing one.
            Booking foreign = new Booking(otherTenant, PROPERTY_ID, "ALT-OTHER1", GUEST_ID,
                    "BOOKED", "DIRECT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                    "INR", 50000L, 6000L, 56000L);
            foreign.setId(UUID.randomUUID());
            when(bookingRepository.findByReference("ALT-OTHER1")).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> bookingService.getBooking("ALT-OTHER1"))
                    .isInstanceOf(BookingNotFoundException.class);
        });
    }

    /** The save-and-hand-back-an-id stubbing the happy paths share. */
    private void stubPersistence() {
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
        when(bookingLineRepository.save(any(BookingLine.class))).thenAnswer(inv -> {
            BookingLine l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(UUID.randomUUID());
            }
            return l;
        });
        when(roomTypeSpaceRepository.findByRoomTypeId(ROOM_TYPE_ID)).thenReturn(
                List.of(new RoomTypeSpace(TENANT_ID, ROOM_TYPE_ID, SPACE_ID)));
        when(unitRepository.findBySpaceIdInAndIsActiveTrue(anyCollection()))
                .thenReturn(List.of(unit, secondUnit()));
        when(allocationRepository.findActiveOverlappingAllocations(anyList(), any(), any()))
                .thenReturn(List.of());
        when(allocationRepository.saveAndFlush(any(Allocation.class))).thenAnswer(inv -> {
            Allocation a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
    }

    private com.altstay.api.rate.RateCalendar newRateCalendarEntry(UUID ratePlanId, LocalDate date, long amountMinor) {
        var entry = new com.altstay.api.rate.RateCalendar();
        entry.setTenantId(TENANT_ID);
        entry.setRatePlanId(ratePlanId);
        entry.setStayDate(date);
        entry.setAmountMinor(amountMinor);
        return entry;
    }

    /**
     * Guests are no longer looked up by email — two people can share an address, and folding them
     * into one record attaches one guest's history to another. The service creates a record.
     */
    private void stubGuestCreation() {
        when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> {
            Guest g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId(GUEST_ID);
            }
            return g;
        });
    }

    private Unit secondUnit() {
        Unit second = new Unit(TENANT_ID, SPACE_ID, "101-B", "SINGLE");
        second.setId(UUID.randomUUID());
        return second;
    }
}
