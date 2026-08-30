package com.altstay.api.booking;

import com.altstay.api.booking.BookingService.CreateBookingLineRequest;
import com.altstay.api.booking.BookingService.CreateBookingRequest;
import com.altstay.api.booking.BookingService.GuestDto;
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
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=optional:file:./.env.properties",
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=${ALTSTAY_DB_URL}",
        "spring.datasource.username=${ALTSTAY_DB_USER}",
        "spring.datasource.password=${ALTSTAY_DB_PASSWORD}",
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
class BookingConcurrencyIT {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private RoomTypeSpaceRepository roomTypeSpaceRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Unwraps whatever the executor and the transaction proxy wrapped the real failure in. */
    private static Throwable rootCauseOf(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            if (current instanceof BookingConflictException || current instanceof NoAvailabilityException) {
                return current;
            }
            current = current.getCause();
        }
        return current;
    }

    @Test
    @DisplayName("8 concurrent threads competing for 1 last bed: exactly 1 succeeds, 7 fail, 1 allocation in DB")
    void eightConcurrentThreadsCompetingForOneBed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String slug = "conc-t-" + UUID.randomUUID().toString().substring(0, 8);
        UUID propertyId = UUID.randomUUID();
        String propSlug = "prop-" + UUID.randomUUID().toString().substring(0, 8);
        UUID spaceId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();

        // 1. Seed tenant and property directly with bound session
        try (var conn = jdbcTemplate.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }

            try (var ps = conn.prepareStatement("insert into tenant (id, name, slug) values (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setString(2, "Concurrency Tenant " + slug);
                ps.setString(3, slug);
                ps.execute();
            }

            try (var ps = conn.prepareStatement(
                    "insert into property (id, tenant_id, name, slug, timezone, currency_code, tax_rate_bps) values (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, propertyId);
                ps.setObject(2, tenantId);
                ps.setString(3, "Conc Property");
                ps.setString(4, propSlug);
                ps.setString(5, "Asia/Kolkata");
                ps.setString(6, "INR");
                ps.setInt(7, 1200);
                ps.execute();
            }

            try (var ps = conn.prepareStatement(
                    "insert into space (id, tenant_id, property_id, name, floor) values (?, ?, ?, ?, ?)")) {
                ps.setObject(1, spaceId);
                ps.setObject(2, tenantId);
                ps.setObject(3, propertyId);
                ps.setString(4, "Space-C1");
                ps.setString(5, "1");
                ps.execute();
            }

            try (var ps = conn.prepareStatement(
                    "insert into unit (id, tenant_id, space_id, label, unit_kind, is_active) values (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, unitId);
                ps.setObject(2, tenantId);
                ps.setObject(3, spaceId);
                ps.setString(4, "Bed-C1");
                ps.setString(5, "SINGLE");
                ps.setBoolean(6, true);
                ps.execute();
            }

            try (var ps = conn.prepareStatement(
                    "insert into room_type (id, tenant_id, property_id, code, name, sale_mode, kind, max_occupancy, base_rate_minor, is_active) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, roomTypeId);
                ps.setObject(2, tenantId);
                ps.setObject(3, propertyId);
                ps.setString(4, "DORM1");
                ps.setString(5, "1 Bed Dorm");
                ps.setString(6, "PER_UNIT");
                ps.setString(7, "DORM");
                ps.setInt(8, 1);
                ps.setLong(9, 50000L);
                ps.setBoolean(10, true);
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

        // 2. Launch 8 threads simultaneously
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        LocalDate checkIn = LocalDate.of(2026, 10, 1);
        LocalDate checkOut = LocalDate.of(2026, 10, 5);

        for (int i = 0; i < threadCount; i++) {
            final int guestNum = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // wait for simultaneous release
                    TenantContextTestSupport.runAs(tenantId, () -> {
                        var req = new CreateBookingRequest(
                                propertyId,
                                null,
                                new GuestDto(null, "Guest " + guestNum, "guest" + guestNum + "@example.com", null, null, null, null),
                                checkIn,
                                checkOut,
                                1,
                                0,
                                "DIRECT",
                                List.of(new CreateBookingLineRequest(roomTypeId, null, checkIn, checkOut, 1, 200000L)),
                                null,
                                "Concurrent test"
                        );
                        bookingService.createBooking(req, null);
                    });
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                    exceptions.add(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // Fire all 8 threads!
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get())
                .as("Exactly 1 concurrent booking must succeed on the single available bed")
                .isEqualTo(1);
        assertThat(failureCount.get())
                .as("The remaining 7 concurrent bookings must fail")
                .isEqualTo(7);

        // "Seven CLEAN 409s", not merely seven failures. §9: a booking that loses the race must
        // return a conflict a human can act on, never a constraint-violation stack — and the raw
        // Postgres error IS the default, so this assertion is the difference between a front desk
        // seeing "that bed was just taken" and seeing "unexpected internal error".
        assertThat(exceptions)
                .as("every loser must fail as a booking conflict")
                .hasSize(7)
                .allSatisfy(t -> assertThat(rootCauseOf(t))
                        .isInstanceOfAny(BookingConflictException.class, NoAvailabilityException.class));

        // 3. Verify exactly 1 active allocation in the database for that unit
        TenantContextTestSupport.runAs(tenantId, () -> {
            List<Allocation> activeAllocations = allocationRepository.findActiveOverlappingAllocations(List.of(unitId), checkIn, checkOut);
            assertThat(activeAllocations).hasSize(1);
        });
    }
}
