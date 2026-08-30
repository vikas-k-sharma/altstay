package com.altstay.api.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import com.altstay.api.booking.Allocation;
import com.altstay.api.booking.AllocationRepository;
import com.altstay.api.booking.Booking;
import com.altstay.api.booking.BookingConflictException;
import com.altstay.api.booking.BookingLine;
import com.altstay.api.booking.BookingLineRepository;
import com.altstay.api.booking.BookingRepository;
import com.altstay.api.booking.BookingService;
import com.altstay.api.booking.BookingService.CreateBookingLineRequest;
import com.altstay.api.booking.BookingService.CreateBookingRequest;
import com.altstay.api.booking.BookingService.GuestDto;
import com.altstay.api.booking.BookingStatusHistory;
import com.altstay.api.booking.BookingStatusHistoryRepository;
import com.altstay.api.booking.Guest;
import com.altstay.api.booking.GuestRepository;
import com.altstay.api.common.GlobalExceptionHandler;
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
import com.altstay.api.rate.RateCalendarRepository;
import com.altstay.api.rate.RatePlanRepository;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Guest PII must never reach a log line — the sibling to {@link LoggingPrivacyTest} that phase-5
 * §5 requires, extended from chat content to the {@code guest} table.
 *
 * <p>{@code guest} is the first table in this system holding personal data, so roadmap §6's DPDP
 * rules attach the moment it exists: never logged, never in an error payload, never in a prompt.
 *
 * <p>The failure path is the one that actually matters and is the reason this test is not just the
 * happy path with an assertion bolted on. PostgreSQL constraint violations carry
 * {@code "Detail: Failing row contains (...)"} — the offending row, inline, in the exception
 * message. Any handler that logs such a throwable whole publishes the guest's name, email and phone
 * to the log, and it does so only on the day something goes wrong, which is the worst day to
 * discover it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestPrivacyLoggingTest {

    private static final String GUEST_NAME = "Priyanka Raghunathan";
    private static final String GUEST_EMAIL = "priyanka.raghunathan@example.com";
    private static final String GUEST_PHONE = "+919812345678";

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID ROOM_TYPE_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingLineRepository bookingLineRepository;
    @Mock private BookingStatusHistoryRepository bookingStatusHistoryRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private SpaceRepository spaceRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private RoomTypeSpaceRepository roomTypeSpaceRepository;
    @Mock private RatePlanRepository ratePlanRepository;
    @Mock private RateCalendarRepository rateCalendarRepository;

    private BookingService bookingService;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        bookingService = new BookingService(
                bookingRepository, bookingLineRepository, bookingStatusHistoryRepository,
                allocationRepository, guestRepository, propertyRepository, roomTypeRepository,
                spaceRepository, unitRepository, roomTypeSpaceRepository,
                ratePlanRepository, rateCalendarRepository);

        Property property = new Property(TENANT_ID, "Beach Hostel", "beach-hostel", "Asia/Kolkata", "INR");
        property.setId(PROPERTY_ID);
        property.setTaxRateBps(1200);

        RoomType roomType = new RoomType(TENANT_ID, PROPERTY_ID, "DORM6", "6 Bed Dorm", "PER_UNIT", "DORM", 6, 50000L);
        roomType.setId(ROOM_TYPE_ID);

        Space space = new Space(TENANT_ID, PROPERTY_ID, "101", "1");
        space.setId(SPACE_ID);

        Unit unit = new Unit(TENANT_ID, SPACE_ID, "101-A", "SINGLE");
        unit.setId(UNIT_ID);

        when(propertyRepository.findBySlug("beach-hostel")).thenReturn(Optional.of(property));
        when(bookingRepository.findByReference(any())).thenReturn(Optional.empty());
        when(roomTypeRepository.findById(ROOM_TYPE_ID)).thenReturn(Optional.of(roomType));
        when(ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(ROOM_TYPE_ID)).thenReturn(Optional.empty());
        when(guestRepository.findByEmailIgnoreCase(GUEST_EMAIL)).thenReturn(Optional.empty());
        when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> {
            Guest g = inv.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
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
        when(bookingStatusHistoryRepository.save(any(BookingStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(roomTypeSpaceRepository.findByRoomTypeId(ROOM_TYPE_ID))
                .thenReturn(List.of(new RoomTypeSpace(TENANT_ID, ROOM_TYPE_ID, SPACE_ID)));
        when(unitRepository.findBySpaceIdInAndIsActiveTrue(anyCollection())).thenReturn(List.of(unit));
        when(unitRepository.findById(UNIT_ID)).thenReturn(Optional.of(unit));
        when(allocationRepository.findActiveOverlappingAllocations(anyList(), any(), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
        }
    }

    private CreateBookingRequest bookingRequest() {
        return new CreateBookingRequest(
                null, "beach-hostel",
                new GuestDto(null, GUEST_NAME, GUEST_EMAIL, GUEST_PHONE, "IN", LocalDate.of(1994, 3, 2), "Allergic to peanuts"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                1, 0, "DIRECT",
                List.of(new CreateBookingLineRequest(ROOM_TYPE_ID, null, null, null, 1, null)),
                null, "Late arrival expected");
    }

    @Test
    @DisplayName("Creating a booking logs no guest name, email or phone")
    void bookingCreationLogsNoGuestPii() {
        when(allocationRepository.saveAndFlush(any(Allocation.class))).thenAnswer(inv -> {
            Allocation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        TenantContextTestSupport.runAs(TENANT_ID, () -> bookingService.createBooking(bookingRequest(), null));

        assertThat(listAppender.list).as("the booking path must log something, or this proves nothing").isNotEmpty();
        assertNoGuestPii();
    }

    @Test
    @DisplayName("A constraint violation carrying the failing row still logs no guest PII")
    void constraintViolationLogsNoGuestPii() {
        // Exactly the shape libpq produces: the whole offending row inlined into the message.
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "ERROR: conflicting key value violates exclusion constraint \"allocation_no_overlap\"\n"
                        + "  Detail: Failing row contains (" + GUEST_NAME + ", " + GUEST_EMAIL + ", " + GUEST_PHONE + ").");
        when(allocationRepository.saveAndFlush(any(Allocation.class))).thenThrow(violation);

        assertThatThrownBy(() -> TenantContextTestSupport.runAs(
                TENANT_ID, () -> bookingService.createBooking(bookingRequest(), null)))
                .isInstanceOf(BookingConflictException.class);

        assertNoGuestPii();
    }

    @Test
    @DisplayName("The data-integrity handler logs the constraint name only, never the failing row")
    void dataIntegrityHandlerLogsConstraintNameOnly() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"guest_tenant_email_idx\"\n"
                        + "  Detail: Failing row contains (" + GUEST_NAME + ", " + GUEST_EMAIL + ", " + GUEST_PHONE + ").");

        var response = handler.handleDataIntegrityViolation(violation, null);

        assertThat(response.getStatusCode().value())
                .as("a conflict is a 409, not a 500 with a Postgres constraint name in it")
                .isEqualTo(409);
        assertThat(String.valueOf(response.getBody().getDetail()))
                .as("the response body must not carry the failing row either")
                .doesNotContain(GUEST_NAME, GUEST_EMAIL, GUEST_PHONE);
        assertNoGuestPii();
    }

    private void assertNoGuestPii() {
        for (ILoggingEvent event : List.copyOf(listAppender.list)) {
            String rendered = renderFully(event);
            assertThat(rendered).as("no log event may contain a guest's name").doesNotContain(GUEST_NAME);
            assertThat(rendered).as("no log event may contain a guest's email").doesNotContain(GUEST_EMAIL);
            assertThat(rendered).as("no log event may contain a guest's phone").doesNotContain(GUEST_PHONE);
        }
    }

    /** The formatted message alone is not enough: arguments and throwables carry content too. */
    private String renderFully(ILoggingEvent event) {
        StringBuilder rendered = new StringBuilder(event.getFormattedMessage());
        if (event.getArgumentArray() != null) {
            for (Object argument : event.getArgumentArray()) {
                rendered.append(' ').append(argument);
            }
        }
        IThrowableProxy throwable = event.getThrowableProxy();
        while (throwable != null) {
            rendered.append(' ').append(throwable.getClassName()).append(' ').append(throwable.getMessage());
            for (StackTraceElementProxy frame : throwable.getStackTraceElementProxyArray()) {
                rendered.append(' ').append(frame.getSTEAsString());
            }
            throwable = throwable.getCause();
        }
        return rendered.toString();
    }
}
