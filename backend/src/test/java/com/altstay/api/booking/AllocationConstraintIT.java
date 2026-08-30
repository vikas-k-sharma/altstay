package com.altstay.api.booking;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the allocation_no_overlap GiST exclusion constraint at the database boundary.
 */
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
class AllocationConstraintIT {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID ROOM_TYPE_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID UNIT_1 = UUID.randomUUID();
    private static final UUID UNIT_2 = UUID.randomUUID();
    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID BOOKING_LINE_ID = UUID.randomUUID();

    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void seed() throws IOException, SQLException {
        Properties p = new Properties();
        Path local = Path.of(".env.properties");
        if (Files.exists(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                p.load(in);
            }
        }
        url = resolve(p, "ALTSTAY_DB_URL");
        user = resolve(p, "ALTSTAY_DB_USER");
        password = resolve(p, "ALTSTAY_DB_PASSWORD");

        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }

            // Tenant
            try (PreparedStatement ps = c.prepareStatement("insert into tenant (id, name, slug) values (?, ?, ?)")) {
                ps.setObject(1, TENANT_ID);
                ps.setString(2, "Alloc Test Tenant");
                ps.setString(3, "alloc-t-" + TENANT_ID.toString().substring(0, 8));
                ps.execute();
            }

            // Property
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into property (id, tenant_id, name, slug, timezone, currency_code) values (?, ?, ?, ?, 'Asia/Kolkata', 'INR')")) {
                ps.setObject(1, PROPERTY_ID);
                ps.setObject(2, TENANT_ID);
                ps.setString(3, "Alloc Property");
                ps.setString(4, "alloc-p-" + PROPERTY_ID.toString().substring(0, 8));
                ps.execute();
            }

            // Room Type
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into room_type (id, tenant_id, property_id, code, name, sale_mode, kind, max_occupancy, base_rate_minor) values (?, ?, ?, 'DORM6', 'Dorm 6', 'PER_UNIT', 'DORM', 6, 50000)")) {
                ps.setObject(1, ROOM_TYPE_ID);
                ps.setObject(2, TENANT_ID);
                ps.setObject(3, PROPERTY_ID);
                ps.execute();
            }

            // Space
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into space (id, tenant_id, property_id, name, floor) values (?, ?, ?, '101', '1')")) {
                ps.setObject(1, SPACE_ID);
                ps.setObject(2, TENANT_ID);
                ps.setObject(3, PROPERTY_ID);
                ps.execute();
            }

            // Units
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into unit (id, tenant_id, space_id, label, unit_kind) values (?, ?, ?, ?, 'SINGLE')")) {
                ps.setObject(1, UNIT_1);
                ps.setObject(2, TENANT_ID);
                ps.setObject(3, SPACE_ID);
                ps.setString(4, "101-A");
                ps.execute();

                ps.setObject(1, UNIT_2);
                ps.setObject(2, TENANT_ID);
                ps.setObject(3, SPACE_ID);
                ps.setString(4, "101-B");
                ps.execute();
            }

            // Guest
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into guest (id, tenant_id, full_name, email) values (?, ?, 'John Doe', 'john@alloc.test')")) {
                ps.setObject(1, GUEST_ID);
                ps.setObject(2, TENANT_ID);
                ps.execute();
            }

            // Booking
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into booking (id, tenant_id, property_id, reference, guest_id, status, source, check_in, check_out, currency_code, subtotal_minor, tax_minor, total_minor) "
                            + "values (?, ?, ?, 'ALT-ALLOC1', ?, 'BOOKED', 'DIRECT', '2026-09-01', '2026-09-10', 'INR', 50000, 0, 50000)")) {
                ps.setObject(1, BOOKING_ID);
                ps.setObject(2, TENANT_ID);
                ps.setObject(3, PROPERTY_ID);
                ps.setObject(4, GUEST_ID);
                ps.execute();
            }

            // Booking Line
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into booking_line (id, tenant_id, booking_id, room_type_id, check_in, check_out, unit_count, amount_minor) "
                            + "values (?, ?, ?, ?, '2026-09-01', '2026-09-10', 1, 50000)")) {
                ps.setObject(1, BOOKING_LINE_ID);
                ps.setObject(2, TENANT_ID);
                ps.setObject(3, BOOKING_ID);
                ps.setObject(4, ROOM_TYPE_ID);
                ps.execute();
            }

            c.commit();
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("delete from tenant where id = ?")) {
                ps.setObject(1, TENANT_ID);
                ps.execute();
            }
            c.commit();
        }
    }

    private static Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url, user, password);
        c.setAutoCommit(false);
        return c;
    }

    private static String resolve(Properties file, String key) {
        String fromEnv = System.getenv(key);
        return fromEnv != null && !fromEnv.isBlank() ? fromEnv : file.getProperty(key, "");
    }

    @Test
    @DisplayName("1. Overlapping allocations for the same unit violate allocation_no_overlap GiST exclusion constraint")
    void overlappingAllocationsOnSameUnitViolateConstraint() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }

            // First allocation: Sept 1 to Sept 5
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null);

            // Conflicting allocation: Sept 3 to Sept 7 on UNIT_1
            assertThatThrownBy(() -> {
                insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 7), null);
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("allocation_no_overlap");

            c.rollback();
        }
    }

    @Test
    @DisplayName("2. Contiguous half-open intervals [check_in, check_out) on same unit do NOT conflict (checkout day boundary)")
    void contiguousAllocationsDoNotConflict() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }

            // Guest 1: Sept 1 to Sept 3
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), null);

            // Guest 2: Sept 3 to Sept 5 (starts on Guest 1 checkout day)
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 5), null);

            // Both must succeed without error
            c.rollback();
        }
    }

    @Test
    @DisplayName("3. Released allocations (released_at is not null) do NOT conflict with new overlapping allocations")
    void releasedAllocationAllowsNewOverlappingAllocation() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }

            // Guest 1 cancelled / released: Sept 1 to Sept 10
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10), Timestamp.from(Instant.now()));

            // Guest 2 books overlapping dates: Sept 2 to Sept 6 on same unit
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 6), null);

            // Must succeed because released_at is not null
            c.rollback();
        }
    }

    @Test
    @DisplayName("4. Overlapping date ranges on different units do NOT conflict")
    void overlappingDatesOnDifferentUnitsDoNotConflict() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }

            // Unit 1: Sept 1 to Sept 5
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null);

            // Unit 2: Sept 1 to Sept 5
            insertAllocation(c, UUID.randomUUID(), UNIT_2, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null);

            // Different units must both succeed
            c.rollback();
        }
    }

    // ------------------------------------------------------------------------------------------
    // The hybrid case. §4.1's claim is that ONE constraint covers both sale modes, because a WHOLE
    // sale allocates every unit in the space — so the whole-room product and the dorm beds collide
    // on the same index, in either direction, with no application logic that has to remember both.
    // These two tests are that claim. Without them, "a whole-space booking and a single-bed booking
    // cannot both hold the same night" is an assertion in a design document and nothing more.
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("5. Whole-space first, then a single bed: the bed booking is refused")
    void wholeSpaceThenSingleBedConflicts() throws SQLException {
        try (Connection c = open()) {
            bindTenant(c);

            // A private-room sale takes EVERY unit in space 101 for Sept 1-5.
            allocateWholeSpace(c, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

            // A guest now tries to buy one dorm bed in that same room for an overlapping night.
            assertThatThrownBy(() ->
                    insertAllocation(c, UUID.randomUUID(), UNIT_2,
                            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 6), null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("allocation_no_overlap");

            c.rollback();
        }
    }

    @Test
    @DisplayName("6. Single bed first, then the whole space: the whole-room booking is refused")
    void singleBedThenWholeSpaceConflicts() throws SQLException {
        try (Connection c = open()) {
            bindTenant(c);

            // One dorm bed sold for one night in the middle of the range.
            insertAllocation(c, UUID.randomUUID(), UNIT_2,
                    LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4), null);

            // The whole-room product must now be unsellable for any range covering that night —
            // and it fails on UNIT_2 specifically, because a WHOLE sale must take every bed.
            assertThatThrownBy(() ->
                    allocateWholeSpace(c, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("allocation_no_overlap");

            c.rollback();
        }
    }

    @Test
    @DisplayName("7. A whole-space sale abutting a bed sale does not conflict — the checkout-day boundary holds for both modes")
    void wholeSpaceAbuttingASingleBedDoesNotConflict() throws SQLException {
        try (Connection c = open()) {
            bindTenant(c);

            // A bed occupied up to (not including) Sept 5.
            insertAllocation(c, UUID.randomUUID(), UNIT_2,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null);

            // The whole room from Sept 5 onward: the departing guest is gone that morning.
            allocateWholeSpace(c, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 8));

            c.rollback();
        }
    }

    @Test
    @DisplayName("8. A one-night stay conflicts with itself and with nothing else")
    void oneNightStayIsExactlyOneNight() throws SQLException {
        try (Connection c = open()) {
            bindTenant(c);

            insertAllocation(c, UUID.randomUUID(), UNIT_1,
                    LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11), null);

            // The night before and the night after are both still sellable.
            insertAllocation(c, UUID.randomUUID(), UNIT_1,
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), null);
            insertAllocation(c, UUID.randomUUID(), UNIT_1,
                    LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12), null);

            // The night itself is not.
            assertThatThrownBy(() ->
                    insertAllocation(c, UUID.randomUUID(), UNIT_1,
                            LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11), null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("allocation_no_overlap");

            c.rollback();
        }
    }

    @Test
    @DisplayName("9. A stay spanning a month boundary is one continuous range, not two")
    void stayAcrossAMonthBoundaryIsContinuous() throws SQLException {
        try (Connection c = open()) {
            bindTenant(c);

            insertAllocation(c, UUID.randomUUID(), UNIT_1,
                    LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 3), null);

            // 30 Sep -> 1 Oct is inside the range; crossing the month must not create a gap.
            assertThatThrownBy(() ->
                    insertAllocation(c, UUID.randomUUID(), UNIT_1,
                            LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2), null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("allocation_no_overlap");

            c.rollback();
        }
    }

    @Test
    @DisplayName("10. A zero-night allocation is refused rather than silently consuming no inventory")
    void zeroNightAllocationIsRefused() throws SQLException {
        try (Connection c = open()) {
            bindTenant(c);

            // An empty range overlaps nothing, so it would pass the exclusion constraint and hold
            // no bed at all — a booking that looks placed and reserves nothing (§4).
            assertThatThrownBy(() ->
                    insertAllocation(c, UUID.randomUUID(), UNIT_1,
                            LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), null))
                    .isInstanceOf(SQLException.class);

            c.rollback();
        }
    }

    /**
     * The constraint is <b>load-bearing</b>, demonstrated rather than asserted.
     *
     * <p>Every other test here shows that overlapping inserts are refused. None of them shows
     * <em>what</em> refuses them — an application-level pre-check somewhere in the stack would
     * produce exactly the same green. This one removes {@code allocation_no_overlap} and watches
     * the identical pair of inserts succeed, which is the only way to know the index is doing the
     * work. A guard that has never been observed absent has never been observed at all.
     *
     * <p><b>The drop is inside a transaction that is always rolled back.</b> PostgreSQL has
     * transactional DDL, so the constraint is restored by the rollback and the real schema is never
     * altered — the alternative, dropping it for real and putting it back afterwards, leaves the
     * database unprotected if the run dies in between, and turns this evidence into a manual ritual
     * nobody repeats. Here it re-runs on every DB-gated build.
     */
    @Test
    @DisplayName("11. With allocation_no_overlap dropped, the same overlapping inserts BOTH succeed — the constraint is what stops them")
    void withoutTheConstraintTheSameOverlapIsAccepted() throws SQLException {
        try (Connection c = open()) {
            bindTenant(c);

            // Sanity: with the constraint in place, the second insert is refused.
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null);
            assertThatThrownBy(() ->
                    insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 7), null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("allocation_no_overlap");
            c.rollback();
        }

        try (Connection c = open()) {
            bindTenant(c);
            try (Statement s = c.createStatement()) {
                s.execute("alter table allocation drop constraint allocation_no_overlap");
            }

            // The very same pair of rows the previous block could not write.
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null);
            insertAllocation(c, UUID.randomUUID(), UNIT_1, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 7), null);

            int overlapping;
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "select count(*) from allocation where unit_id = '" + UNIT_1 + "'"
                                 + " and released_at is null"
                                 + " and daterange(check_in, check_out, '[)') && daterange('2026-09-03', '2026-09-05', '[)')")) {
                rs.next();
                overlapping = rs.getInt(1);
            }

            assertThat(overlapping)
                    .as("two guests hold the same bed on 3-4 Sept once the constraint is gone — "
                            + "this is the incident the constraint prevents")
                    .isEqualTo(2);

            // Rolled back, so the constraint was never really removed.
            c.rollback();
        }

        // And it is still there afterwards.
        try (Connection c = open()) {
            bindTenant(c);
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "select count(*) from pg_constraint where conname = 'allocation_no_overlap'")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("the rollback must have restored the constraint")
                        .isEqualTo(1);
            }
            c.rollback();
        }
    }

    private void bindTenant(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, TENANT_ID.toString());
            ps.execute();
        }
    }

    /** What a WHOLE sale does: take every active unit in the space for the range (§4.1). */
    private void allocateWholeSpace(Connection c, LocalDate checkIn, LocalDate checkOut) throws SQLException {
        for (UUID unitId : List.of(UNIT_1, UNIT_2)) {
            insertAllocation(c, UUID.randomUUID(), unitId, checkIn, checkOut, null);
        }
    }

    private void insertAllocation(Connection c, UUID allocId, UUID unitId, LocalDate checkIn, LocalDate checkOut, Timestamp releasedAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into allocation (id, tenant_id, unit_id, booking_line_id, check_in, check_out, released_at) values (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, allocId);
            ps.setObject(2, TENANT_ID);
            ps.setObject(3, unitId);
            ps.setObject(4, BOOKING_LINE_ID);
            ps.setObject(5, checkIn);
            ps.setObject(6, checkOut);
            ps.setTimestamp(7, releasedAt);
            ps.execute();
        }
    }
}
