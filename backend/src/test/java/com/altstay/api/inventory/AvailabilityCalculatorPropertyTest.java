package com.altstay.api.inventory;

import com.altstay.api.inventory.AvailabilityCalculator.AllocationInfo;
import com.altstay.api.inventory.AvailabilityCalculator.AvailabilityResult;
import com.altstay.api.inventory.AvailabilityCalculator.DayAvailability;
import com.altstay.api.inventory.AvailabilityCalculator.RoomTypeAvailability;
import com.altstay.api.inventory.AvailabilityCalculator.RoomTypeInfo;
import com.altstay.api.inventory.AvailabilityCalculator.SpaceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Randomized comparison of {@link AvailabilityCalculator}'s sweep line against a brute-force
 * oracle, over a seeded corpus of ≥200 cases (phase-5 §12.2).
 *
 * <p><b>The oracle is deliberately written a different way round from the implementation, and that
 * is the whole point of it.</b> An earlier version of this test computed its "oracle" with the same
 * per-day occupied-set loop the calculator itself used, so it compared the implementation against a
 * copy of itself and could not fail for any algorithmic reason — 250 green iterations that proved
 * nothing. The oracle below instead <b>materializes</b> occupancy: it expands every allocation
 * night by night into a {@code date -> occupied unit ids} map, then answers each question by
 * counting individual units. The implementation carries running per-space <b>deltas</b> along a
 * sorted event list and never expands anything. Two structurally different routes to the same
 * number is what makes an agreement between them evidence.
 *
 * <p>The seed is fixed and printed on failure so any failure is reproducible.
 */
class AvailabilityCalculatorPropertyTest {

    private static final long SEED = 20260830L;
    private static final int ITERATIONS = 250;

    @Test
    @DisplayName("Sweep line matches an independently-written brute-force oracle over 250 seeded random cases")
    void sweepLineMatchesBruteForceOracle() {
        Random random = new Random(SEED);

        for (int iter = 0; iter < ITERATIONS; iter++) {
            Scenario s = randomScenario(random);
            String where = "seed=" + SEED + " iteration=" + iter;

            AvailabilityResult actual = AvailabilityCalculator.calculate(
                    s.from, s.to, s.roomTypes, s.spaces, s.allocations, Map.of());

            Map<LocalDate, Set<UUID>> occupiedByDate = expandAllocationsNightByNight(s);

            assertThat(actual.roomTypes()).as(where).hasSize(s.roomTypes.size());

            for (RoomTypeAvailability rta : actual.roomTypes()) {
                RoomTypeInfo rt = s.roomTypes.stream()
                        .filter(r -> r.id().equals(rta.roomTypeId()))
                        .findFirst()
                        .orElseThrow();

                assertThat(rta.days()).as(where).hasSize(s.nights());

                for (DayAvailability day : rta.days()) {
                    Set<UUID> occupied = occupiedByDate.getOrDefault(day.date(), Set.of());

                    assertThat(day.availableUnits())
                            .as("%s date=%s availableUnits", where, day.date())
                            .isEqualTo(oracleFreeUnits(s, rt, occupied));
                    assertThat(day.totalUnits())
                            .as("%s date=%s totalUnits", where, day.date())
                            .isEqualTo(oracleTotalUnits(s, rt));
                    assertThat(day.availableSpaces())
                            .as("%s date=%s availableSpaces", where, day.date())
                            .isEqualTo(oracleFreeSpaces(s, rt, occupied));
                    assertThat(day.totalSpaces())
                            .as("%s date=%s totalSpaces", where, day.date())
                            .isEqualTo(rt.mappedSpaceIds().size());
                    assertThat(day.availableUnits())
                            .as("%s date=%s availableUnits within bounds", where, day.date())
                            .isBetween(0, day.totalUnits());
                }

                assertThat(rta.bookableWholeSpaces())
                        .as("%s bookableWholeSpaces (intersection over the whole range)", where)
                        .isEqualTo(oracleSpacesFreeThroughout(s, rt, occupiedByDate));
            }
        }
    }

    // ---------------------------------------------------------------- the oracle

    /**
     * Brute force, by materialization: walk each allocation one night at a time and record the unit
     * as occupied on that date. No events, no running counts, no clamping — the opposite approach
     * to the implementation's.
     */
    private static Map<LocalDate, Set<UUID>> expandAllocationsNightByNight(Scenario s) {
        Map<LocalDate, Set<UUID>> occupied = new HashMap<>();
        for (AllocationInfo alloc : s.allocations) {
            for (LocalDate d = alloc.checkIn(); d.isBefore(alloc.checkOut()); d = d.plusDays(1)) {
                occupied.computeIfAbsent(d, k -> new HashSet<>()).add(alloc.unitId());
            }
        }
        return occupied;
    }

    /** Counts individual free units one at a time rather than subtracting an aggregate. */
    private static int oracleFreeUnits(Scenario s, RoomTypeInfo rt, Set<UUID> occupiedUnits) {
        int free = 0;
        for (UUID spaceId : rt.mappedSpaceIds()) {
            SpaceInfo space = s.spaceById.get(spaceId);
            if (space == null) {
                continue;
            }
            for (UUID unitId : space.unitIds()) {
                if (!occupiedUnits.contains(unitId)) {
                    free++;
                }
            }
        }
        return free;
    }

    private static int oracleTotalUnits(Scenario s, RoomTypeInfo rt) {
        int total = 0;
        for (UUID spaceId : rt.mappedSpaceIds()) {
            SpaceInfo space = s.spaceById.get(spaceId);
            if (space != null) {
                total += space.unitIds().size();
            }
        }
        return total;
    }

    private static int oracleFreeSpaces(Scenario s, RoomTypeInfo rt, Set<UUID> occupiedUnits) {
        int free = 0;
        for (UUID spaceId : rt.mappedSpaceIds()) {
            SpaceInfo space = s.spaceById.get(spaceId);
            if (space == null || space.unitIds().isEmpty()) {
                continue;
            }
            boolean everyUnitFree = space.unitIds().stream().noneMatch(occupiedUnits::contains);
            if (everyUnitFree) {
                free++;
            }
        }
        return free;
    }

    private static int oracleSpacesFreeThroughout(
            Scenario s, RoomTypeInfo rt, Map<LocalDate, Set<UUID>> occupiedByDate) {
        int free = 0;
        for (UUID spaceId : rt.mappedSpaceIds()) {
            SpaceInfo space = s.spaceById.get(spaceId);
            if (space == null || space.unitIds().isEmpty()) {
                continue;
            }
            boolean freeEveryNight = true;
            for (LocalDate d = s.from; d.isBefore(s.to); d = d.plusDays(1)) {
                Set<UUID> occupied = occupiedByDate.getOrDefault(d, Set.of());
                if (space.unitIds().stream().anyMatch(occupied::contains)) {
                    freeEveryNight = false;
                    break;
                }
            }
            if (freeEveryNight) {
                free++;
            }
        }
        return free;
    }

    // ---------------------------------------------------------------- generation

    private record Scenario(
            LocalDate from,
            LocalDate to,
            List<SpaceInfo> spaces,
            Map<UUID, SpaceInfo> spaceById,
            List<RoomTypeInfo> roomTypes,
            List<AllocationInfo> allocations
    ) {
        int nights() {
            return (int) (to.toEpochDay() - from.toEpochDay());
        }
    }

    private static Scenario randomScenario(Random random) {
        LocalDate from = LocalDate.of(2026, 9, 1).plusDays(random.nextInt(60));
        LocalDate to = from.plusDays(1 + random.nextInt(20));

        int spaceCount = 1 + random.nextInt(4);
        List<SpaceInfo> spaces = new ArrayList<>();
        Map<UUID, SpaceInfo> spaceById = new HashMap<>();
        List<UUID> allUnitIds = new ArrayList<>();

        for (int i = 0; i < spaceCount; i++) {
            UUID spaceId = UUID.randomUUID();
            int unitCount = 1 + random.nextInt(6);
            Set<UUID> unitIds = new HashSet<>();
            for (int u = 0; u < unitCount; u++) {
                UUID unitId = UUID.randomUUID();
                unitIds.add(unitId);
                allUnitIds.add(unitId);
            }
            SpaceInfo space = new SpaceInfo(spaceId, unitCount, unitIds);
            spaces.add(space);
            spaceById.put(spaceId, space);
        }

        // Two room types over the same spaces: the hybrid case, where one physical room is sold
        // both per-bed and whole.
        List<UUID> spaceIds = spaces.stream().map(SpaceInfo::id).toList();
        List<RoomTypeInfo> roomTypes = List.of(
                new RoomTypeInfo(UUID.randomUUID(), "DORM", "PER_UNIT", 50_000L, spaceIds),
                new RoomTypeInfo(UUID.randomUUID(), "PRIV", "WHOLE", 200_000L, spaceIds)
        );

        // Allocations are generated PER UNIT and never overlap on the same unit, because
        // allocation_no_overlap makes overlapping-on-one-unit data unreachable in the real system.
        // Feeding the calculator rows the database would have rejected would only test behaviour
        // that cannot occur. Gaps of zero are allowed on purpose: back-to-back stays where one
        // guest's checkout is the next guest's check-in are the boundary half of all PMS bugs live
        // at. Ranges also run off both ends of the window so clamping is exercised.
        List<AllocationInfo> allocations = new ArrayList<>();
        int windowNights = (int) (to.toEpochDay() - from.toEpochDay());
        for (UUID unitId : allUnitIds) {
            LocalDate cursor = from.minusDays(3);
            LocalDate horizon = to.plusDays(3);
            while (cursor.isBefore(horizon)) {
                if (random.nextInt(3) == 0) {           // roughly a third of the slots stay empty
                    cursor = cursor.plusDays(1 + random.nextInt(3));
                    continue;
                }
                LocalDate checkOut = cursor.plusDays(1 + random.nextInt(Math.max(2, windowNights / 2)));
                allocations.add(new AllocationInfo(unitId, cursor, checkOut));
                cursor = checkOut.plusDays(random.nextInt(3));   // 0 = back-to-back
            }
        }

        return new Scenario(from, to, spaces, spaceById, roomTypes, allocations);
    }
}
