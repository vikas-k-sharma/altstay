package com.altstay.api.booking;

import com.altstay.api.booking.BookingService.CreateBookingLineRequest;
import com.altstay.api.booking.BookingService.CreateBookingRequest;
import com.altstay.api.booking.BookingService.GuestDto;
import com.altstay.api.booking.BookingService.TransitionRequest;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The booking lifecycle end to end against the real database (§12.1): create, check in, check out
 * early, and watch the freed night become sellable again — then cancel and watch the bed come back.
 *
 * <p>This is the operational behaviour a property actually runs on, and none of it is provable from
 * unit tests: early check-out shortening an allocation, a cancellation releasing one, and the
 * inventory reopening as a result are all statements about rows and constraints.
 *
 * <p>Dates are computed relative to <b>today in the property's timezone</b>, because that is what
 * the service's transitions use. A fixed calendar date would make this test start failing on a
 * particular day for reasons that have nothing to do with the code.
 */
@SpringBootTest(properties = {
        "spring.config.import=optional:file:./.env.properties",
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=${ALTSTAY_DB_URL}",
        "spring.datasource.username=${ALTSTAY_DB_USER}",
        "spring.datasource.password=${ALTSTAY_DB_PASSWORD}",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
class BookingLifecycleIT {

    private static final String ZONE = "Asia/Kolkata";
    /** Per-test, so the two tests cannot collide on the tenant primary key. */
    private final UUID tenantId = UUID.randomUUID();

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID propertyId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID roomTypeId = UUID.randomUUID();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(ZONE));
    }

    @org.junit.jupiter.api.BeforeEach
    void seed() throws SQLException {
        try (var conn = jdbcTemplate.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }
            try (var ps = conn.prepareStatement("insert into tenant (id, name, slug) values (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setString(2, "Lifecycle Tenant " + suffix);
                ps.setString(3, "life-" + suffix);
                ps.execute();
            }
            try (var ps = conn.prepareStatement(
                    "insert into property (id, tenant_id, name, slug, timezone, currency_code, tax_rate_bps)"
                            + " values (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, propertyId);
                ps.setObject(2, tenantId);
                ps.setString(3, "Lifecycle Hostel");
                ps.setString(4, "life-prop-" + suffix);
                ps.setString(5, ZONE);
                ps.setString(6, "INR");
                ps.setInt(7, 1200);
                ps.execute();
            }
            try (var ps = conn.prepareStatement(
                    "insert into space (id, tenant_id, property_id, name) values (?, ?, ?, ?)")) {
                ps.setObject(1, spaceId);
                ps.setObject(2, tenantId);
                ps.setObject(3, propertyId);
                ps.setString(4, "Dorm 1");
                ps.execute();
            }
            // ONE bed, deliberately. With a single unit, "the freed night became sellable again" is
            // unambiguous: there is no other bed the second booking could have quietly landed on.
            try (var ps = conn.prepareStatement(
                    "insert into unit (id, tenant_id, space_id, label, unit_kind) values (?, ?, ?, ?, ?)")) {
                ps.setObject(1, unitId);
                ps.setObject(2, tenantId);
                ps.setObject(3, spaceId);
                ps.setString(4, "D1-A");
                ps.setString(5, "SINGLE");
                ps.execute();
            }
            try (var ps = conn.prepareStatement(
                    "insert into room_type (id, tenant_id, property_id, code, name, sale_mode, kind,"
                            + " max_occupancy, base_rate_minor) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, roomTypeId);
                ps.setObject(2, tenantId);
                ps.setObject(3, propertyId);
                ps.setString(4, "DORM1");
                ps.setString(5, "1 Bed Dorm");
                ps.setString(6, "PER_UNIT");
                ps.setString(7, "DORM");
                ps.setInt(8, 1);
                ps.setLong(9, 50_000L);
                ps.execute();
            }
            try (var ps = conn.prepareStatement(
                    "insert into room_type_space (tenant_id, room_type_id, space_id) values (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, roomTypeId);
                ps.setObject(3, spaceId);
                ps.execute();
            }
            conn.commit();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from tenant where id = ?", tenantId);
    }

    private CreateBookingRequest booking(String guestName, LocalDate checkIn, LocalDate checkOut) {
        return new CreateBookingRequest(
                propertyId, null,
                new GuestDto(null, guestName, null, null, null, null, null),
                checkIn, checkOut, 1, 0, "WALK_IN",
                List.of(new CreateBookingLineRequest(roomTypeId, null, checkIn, checkOut, 1, null)),
                null, null);
    }

    @Test
    @DisplayName("Create, check in, check out early, and the freed night is immediately sellable again")
    void fullLifecycleFreesInventoryAtEveryStep() {
        LocalDate today = today();
        LocalDate arrived = today.minusDays(2);
        LocalDate bookedDeparture = today.plusDays(2);

        TenantContextTestSupport.runAs(tenantId, () -> {
            // 1. A four-night stay, priced per night rather than per booking.
            var stay = bookingService.createBooking(booking("Long Stay Guest", arrived, bookedDeparture), null);
            assertThat(stay.status()).isEqualTo("BOOKED");
            assertThat(stay.allocations()).hasSize(1);
            assertThat(stay.subtotalMinor())
                    .as("4 nights x 1 bed x 50000")
                    .isEqualTo(200_000L);

            // 2. The only bed is taken, so tonight cannot be sold to anyone else.
            assertThatThrownBy(() ->
                    bookingService.createBooking(booking("Hopeful Walk-in", today, today.plusDays(1)), null))
                    .isInstanceOfAny(NoAvailabilityException.class, BookingConflictException.class);

            // 3. Check in, then check out two nights early.
            bookingService.transitionBooking(stay.reference(), new TransitionRequest("CHECKED_IN", null), null);
            var departed = bookingService.transitionBooking(
                    stay.reference(), new TransitionRequest("CHECKED_OUT", "left early"), null);

            assertThat(departed.status()).isEqualTo("CHECKED_OUT");
            assertThat(departed.allocations())
                    .as("the held bed is shortened to end today, in the same transaction")
                    .allSatisfy(a -> assertThat(a.checkOut()).isEqualTo(today));

            // 4. Tonight is now sellable — the behaviour §5.1 calls the most commonly missed in a
            //    hostel PMS. The same request that failed at step 2 now succeeds.
            var walkIn = bookingService.createBooking(booking("Hopeful Walk-in", today, today.plusDays(1)), null);
            assertThat(walkIn.status()).isEqualTo("BOOKED");
            assertThat(walkIn.allocations()).hasSize(1);
            assertThat(walkIn.allocations().get(0).unitId())
                    .as("it is the same physical bed the first guest vacated")
                    .isEqualTo(unitId);

            // 5. Cancelling releases the bed and keeps the row that says which bed it was.
            var cancelled = bookingService.transitionBooking(
                    walkIn.reference(), new TransitionRequest("CANCELLED", "changed plans"), null);
            assertThat(cancelled.status()).isEqualTo("CANCELLED");
            assertThat(cancelled.allocations())
                    .as("released, not deleted — a PMS is a system of record")
                    .isNotEmpty()
                    .allSatisfy(a -> assertThat(a.releasedAt()).isNotNull());

            // 6. And the bed is back on sale.
            var third = bookingService.createBooking(booking("Third Guest", today, today.plusDays(1)), null);
            assertThat(third.allocations()).hasSize(1);
            assertThat(third.allocations().get(0).unitId()).isEqualTo(unitId);
        });
    }

    @Test
    @DisplayName("A no-show releases the bed too, and is recorded distinctly from a cancellation")
    void noShowReleasesInventoryAndIsRecordedDistinctly() {
        LocalDate tomorrow = today().plusDays(1);

        TenantContextTestSupport.runAs(tenantId, () -> {
            var expected = bookingService.createBooking(booking("Never Arrived", tomorrow, tomorrow.plusDays(1)), null);

            var noShow = bookingService.transitionBooking(
                    expected.reference(), new TransitionRequest("NO_SHOW", "did not arrive"), null);

            assertThat(noShow.status()).isEqualTo("NO_SHOW");
            assertThat(noShow.allocations()).allSatisfy(a -> assertThat(a.releasedAt()).isNotNull());
            assertThat(noShow.statusHistory())
                    .as("every transition is recorded with who and why")
                    .anySatisfy(h -> {
                        assertThat(h.toStatus()).isEqualTo("NO_SHOW");
                        assertThat(h.fromStatus()).isEqualTo("BOOKED");
                    });

            // The night is sellable again, exactly as after a cancellation — the difference between
            // the two is in reporting, not in inventory.
            var replacement = bookingService.createBooking(booking("Walk-in", tomorrow, tomorrow.plusDays(1)), null);
            assertThat(replacement.allocations()).hasSize(1);

            // Terminal means terminal.
            assertThatThrownBy(() -> bookingService.transitionBooking(
                    noShow.reference(), new TransitionRequest("CHECKED_IN", null), null))
                    .isInstanceOf(InvalidBookingTransitionException.class);
        });
    }
}
