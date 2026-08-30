package com.altstay.api.inventory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure availability calculator — no repository, no clock, no database.
 *
 * <p>Implements phase-5 §7 / roadmap §5.1: <b>a sweep line</b> over allocation start and end
 * events, not a rescan of every allocation for every cell.
 *
 * <p><b>What makes this a sweep line rather than the naive form.</b> The naive shape — and the
 * shape this class used to have — is, for every room type, for every date, scan every allocation
 * to rebuild the occupied set from scratch: {@code O(roomTypes × days × allocations)}. Here each
 * allocation contributes exactly two events, {@code (+1)} on its clamped check-in and {@code (-1)}
 * on its clamped check-out. The events are sorted once and consumed as the day cursor advances, so
 * a running per-space occupancy count is carried from day to day and no allocation is ever looked
 * at twice. The cost is {@code O(A log A)} for the sort plus the size of the output itself,
 * {@code O(days × Σ spaces-per-room-type)}.
 *
 * <p>The day cursor is the outer loop and room types the inner one, deliberately: the sweep must
 * run <b>once</b> for all room types, not once per room type. Room types share spaces (that is the
 * whole point of the hybrid model), so their occupancy is the same occupancy.
 *
 * <p><b>Half-open ranges.</b> A stay is {@code [check_in, check_out)} — the checkout day is
 * bookable by the next guest, matching {@code allocation.stay_range}'s {@code '[)'} bound. The
 * {@code -1} event therefore lands <i>on</i> the checkout date and is applied before that day is
 * emitted. Roadmap §5.1: "boundary handling is exactly where this goes wrong."
 *
 * <p><b>The two sale modes are coupled through one set of counts</b> (§7). Because a {@code WHOLE}
 * booking allocates every unit in its space (§4.1), whole-space availability is simply "this
 * space's occupied count is zero", read off the same running counts that produce per-unit
 * availability. Neither mode needs its own bookkeeping.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {}

    public record RoomTypeInfo(
            UUID id,
            String code,
            String saleMode,
            long baseRateMinor,
            List<UUID> mappedSpaceIds
    ) {}

    public record SpaceInfo(
            UUID id,
            int activeUnitsCount,
            Set<UUID> unitIds
    ) {}

    public record AllocationInfo(
            UUID unitId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {}

    public record DayAvailability(
            LocalDate date,
            int availableUnits,
            int totalUnits,
            int availableSpaces,
            int totalSpaces,
            long rateMinor
    ) {}

    /**
     * @param bookableWholeSpaces spaces free on <b>every</b> day of the requested range — the
     *                            intersection §7 specifies, which is what a {@code WHOLE} sale
     *                            actually needs. It is not the minimum of the per-day
     *                            {@code availableSpaces} counts: a space free on Monday and a
     *                            different one free on Tuesday give a per-day minimum of 1 and an
     *                            intersection of 0, and only the intersection can be sold.
     */
    public record RoomTypeAvailability(
            UUID roomTypeId,
            String code,
            String saleMode,
            int bookableWholeSpaces,
            List<DayAvailability> days
    ) {}

    public record AvailabilityResult(
            LocalDate from,
            LocalDate to,
            List<RoomTypeAvailability> roomTypes
    ) {}

    /** One endpoint of an allocation, carrying the delta it applies to its space's occupancy. */
    private record OccupancyEvent(LocalDate date, UUID spaceId, int delta) {}

    public static AvailabilityResult calculate(
            LocalDate from,
            LocalDate to,
            List<RoomTypeInfo> roomTypes,
            List<SpaceInfo> spaces,
            List<AllocationInfo> activeAllocations,
            Map<UUID, Map<LocalDate, Long>> calendarRatesByRoomType
    ) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("to date must be strictly after from date");
        }

        Map<UUID, UUID> spaceIdByUnitId = new HashMap<>();
        Map<UUID, SpaceInfo> spaceById = new HashMap<>();
        for (SpaceInfo s : spaces) {
            spaceById.put(s.id(), s);
            for (UUID unitId : s.unitIds()) {
                spaceIdByUnitId.put(unitId, s.id());
            }
        }

        List<OccupancyEvent> events = buildClampedEvents(from, to, activeAllocations, spaceIdByUnitId);
        // Sort by date; within a date the -1s must land before the +1s so that a departure and an
        // arrival on the same unit on the same day nets to zero rather than transiently double-
        // counting. Deltas sort naturally: -1 before +1.
        events.sort((a, b) -> {
            int byDate = a.date().compareTo(b.date());
            return byDate != 0 ? byDate : Integer.compare(a.delta(), b.delta());
        });

        Map<UUID, Integer> occupiedBySpace = new HashMap<>();
        Map<UUID, List<DayAvailability>> daysByRoomType = new HashMap<>();
        // Spaces still free on every day seen so far, per room type — the running intersection.
        // Starts optimistic and only ever shrinks, one day at a time.
        Map<UUID, List<UUID>> freeThroughoutByRoomType = new HashMap<>();
        for (RoomTypeInfo rt : roomTypes) {
            daysByRoomType.put(rt.id(), new ArrayList<>());
            freeThroughoutByRoomType.put(rt.id(), new ArrayList<>(rt.mappedSpaceIds()));
        }

        int eventCursor = 0;
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            // Advance the sweep: apply every event that takes effect on or before this day.
            while (eventCursor < events.size() && !events.get(eventCursor).date().isAfter(date)) {
                OccupancyEvent e = events.get(eventCursor++);
                occupiedBySpace.merge(e.spaceId(), e.delta(), Integer::sum);
            }

            for (RoomTypeInfo rt : roomTypes) {
                int availableUnits = 0;
                int totalUnits = 0;
                int availableSpaces = 0;
                int totalSpaces = rt.mappedSpaceIds().size();

                for (UUID spaceId : rt.mappedSpaceIds()) {
                    SpaceInfo space = spaceById.get(spaceId);
                    if (space == null) {
                        continue;
                    }
                    int occupied = occupiedBySpace.getOrDefault(spaceId, 0);
                    totalUnits += space.activeUnitsCount();
                    availableUnits += Math.max(0, space.activeUnitsCount() - occupied);
                    if (occupied == 0 && space.activeUnitsCount() > 0) {
                        availableSpaces++;
                    }
                }

                Map<LocalDate, Long> overrides = calendarRatesByRoomType != null
                        ? calendarRatesByRoomType.getOrDefault(rt.id(), Map.of())
                        : Map.of();

                daysByRoomType.get(rt.id()).add(new DayAvailability(
                        date,
                        availableUnits,
                        totalUnits,
                        availableSpaces,
                        totalSpaces,
                        overrides.getOrDefault(date, rt.baseRateMinor())
                ));

                freeThroughoutByRoomType.get(rt.id()).removeIf(spaceId -> {
                    SpaceInfo space = spaceById.get(spaceId);
                    return space == null
                            || space.activeUnitsCount() == 0
                            || occupiedBySpace.getOrDefault(spaceId, 0) > 0;
                });
            }
        }

        List<RoomTypeAvailability> results = new ArrayList<>();
        for (RoomTypeInfo rt : roomTypes) {
            results.add(new RoomTypeAvailability(
                    rt.id(),
                    rt.code(),
                    rt.saleMode(),
                    freeThroughoutByRoomType.get(rt.id()).size(),
                    daysByRoomType.get(rt.id())
            ));
        }

        return new AvailabilityResult(from, to, results);
    }

    /**
     * Turns each allocation into at most two events, clamped to the requested window.
     *
     * <p>Clamping is what keeps a long-running stay from needing per-day expansion: an allocation
     * that starts before {@code from} contributes its {@code +1} on {@code from} itself, and one
     * that runs past {@code to} contributes no {@code -1} at all inside the window.
     */
    private static List<OccupancyEvent> buildClampedEvents(
            LocalDate from,
            LocalDate to,
            List<AllocationInfo> activeAllocations,
            Map<UUID, UUID> spaceIdByUnitId
    ) {
        List<OccupancyEvent> events = new ArrayList<>();
        if (activeAllocations == null) {
            return events;
        }
        for (AllocationInfo alloc : activeAllocations) {
            UUID spaceId = spaceIdByUnitId.get(alloc.unitId());
            if (spaceId == null) {
                continue;   // a unit outside the spaces under consideration
            }
            // Half-open intersection test: [checkIn, checkOut) ∩ [from, to) is non-empty.
            if (!alloc.checkIn().isBefore(to) || !alloc.checkOut().isAfter(from)) {
                continue;
            }
            LocalDate start = alloc.checkIn().isBefore(from) ? from : alloc.checkIn();
            events.add(new OccupancyEvent(start, spaceId, 1));
            if (alloc.checkOut().isBefore(to)) {
                events.add(new OccupancyEvent(alloc.checkOut(), spaceId, -1));
            }
        }
        return events;
    }
}
