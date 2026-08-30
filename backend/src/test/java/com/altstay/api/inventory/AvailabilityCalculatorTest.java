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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary cases phase-5 §12.2 names individually, because "half of all PMS bugs live there".
 *
 * <p>These are hand-written rather than generated on purpose: a randomized corpus establishes that
 * the sweep line agrees with a brute-force oracle, but it does not say which cases anyone cared
 * about. These do.
 */
class AvailabilityCalculatorTest {

    private static final UUID SPACE_101 = UUID.randomUUID();
    private static final UUID BED_A = UUID.randomUUID();
    private static final UUID BED_B = UUID.randomUUID();
    private static final UUID DORM_ID = UUID.randomUUID();
    private static final UUID PRIVATE_ID = UUID.randomUUID();

    private static final SpaceInfo SPACE = new SpaceInfo(SPACE_101, 2, Set.of(BED_A, BED_B));

    private static final RoomTypeInfo DORM =
            new RoomTypeInfo(DORM_ID, "DORM2", "PER_UNIT", 50_000L, List.of(SPACE_101));
    private static final RoomTypeInfo PRIVATE =
            new RoomTypeInfo(PRIVATE_ID, "PRIV2", "WHOLE", 180_000L, List.of(SPACE_101));

    private static AvailabilityResult calculate(
            LocalDate from, LocalDate to, List<AllocationInfo> allocations) {
        return AvailabilityCalculator.calculate(
                from, to, List.of(DORM, PRIVATE), List.of(SPACE), allocations, Map.of());
    }

    private static DayAvailability day(AvailabilityResult result, UUID roomTypeId, LocalDate date) {
        return result.roomTypes().stream()
                .filter(rt -> rt.roomTypeId().equals(roomTypeId))
                .flatMap(rt -> rt.days().stream())
                .filter(d -> d.date().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no day " + date));
    }

    private static RoomTypeAvailability roomType(AvailabilityResult result, UUID roomTypeId) {
        return result.roomTypes().stream()
                .filter(rt -> rt.roomTypeId().equals(roomTypeId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("The checkout day is bookable: a stay ending on the day another begins frees the bed that morning")
    void checkoutDayIsBookableByTheNextGuest() {
        LocalDate arrive = LocalDate.of(2026, 9, 10);
        LocalDate depart = LocalDate.of(2026, 9, 12);

        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14),
                List.of(new AllocationInfo(BED_A, arrive, depart)));

        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 9)).availableUnits()).isEqualTo(2);
        assertThat(day(result, DORM_ID, arrive).availableUnits()).isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 11)).availableUnits()).isEqualTo(1);
        // The checkout date itself: the departing guest is gone, the bed is sellable again.
        assertThat(day(result, DORM_ID, depart).availableUnits())
                .as("checkout day must be bookable — [check_in, check_out) is half-open")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("A one-night stay occupies exactly one night")
    void oneNightStayOccupiesExactlyOneNight() {
        LocalDate night = LocalDate.of(2026, 9, 10);

        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 12),
                List.of(new AllocationInfo(BED_A, night, night.plusDays(1))));

        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 9)).availableUnits()).isEqualTo(2);
        assertThat(day(result, DORM_ID, night).availableUnits()).isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 11)).availableUnits()).isEqualTo(2);
    }

    @Test
    @DisplayName("A stay spanning a month boundary occupies every night across it")
    void stayAcrossMonthBoundaryOccupiesEveryNight() {
        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4),
                List.of(new AllocationInfo(BED_A, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2))));

        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 29)).availableUnits()).isEqualTo(2);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 30)).availableUnits()).isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 10, 1)).availableUnits()).isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 10, 2)).availableUnits()).isEqualTo(2);
    }

    @Test
    @DisplayName("One dorm bed sold makes the whole-room product for that space unavailable that night")
    void oneBedSoldRemovesTheWholeRoomProduct() {
        LocalDate night = LocalDate.of(2026, 9, 10);

        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 12),
                List.of(new AllocationInfo(BED_A, night, night.plusDays(1))));

        assertThat(day(result, PRIVATE_ID, LocalDate.of(2026, 9, 9)).availableSpaces()).isEqualTo(1);
        assertThat(day(result, PRIVATE_ID, night).availableSpaces())
                .as("a single occupied bed must remove the whole-space product for that night")
                .isZero();
        assertThat(day(result, PRIVATE_ID, LocalDate.of(2026, 9, 11)).availableSpaces()).isEqualTo(1);
    }

    @Test
    @DisplayName("A whole-space sale (every unit allocated) removes every dorm bed in that space")
    void wholeSpaceSaleRemovesEveryBed() {
        LocalDate night = LocalDate.of(2026, 9, 10);

        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 12),
                List.of(
                        new AllocationInfo(BED_A, night, night.plusDays(1)),
                        new AllocationInfo(BED_B, night, night.plusDays(1))));

        assertThat(day(result, DORM_ID, night).availableUnits()).isZero();
        assertThat(day(result, PRIVATE_ID, night).availableSpaces()).isZero();
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 11)).availableUnits()).isEqualTo(2);
    }

    @Test
    @DisplayName("bookableWholeSpaces is the intersection across the range, not the per-day maximum")
    void bookableWholeSpacesIsAnIntersectionNotAPerDayCount() {
        LocalDate from = LocalDate.of(2026, 9, 9);
        LocalDate to = LocalDate.of(2026, 9, 12);

        // Occupied on the middle night only. Every other night the space is completely free, so a
        // per-day view shows availability on 2 of 3 nights — but the room cannot be sold for the
        // range.
        AvailabilityResult result = calculate(from, to,
                List.of(new AllocationInfo(BED_A, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11))));

        assertThat(day(result, PRIVATE_ID, LocalDate.of(2026, 9, 9)).availableSpaces()).isEqualTo(1);
        assertThat(day(result, PRIVATE_ID, LocalDate.of(2026, 9, 11)).availableSpaces()).isEqualTo(1);
        assertThat(roomType(result, PRIVATE_ID).bookableWholeSpaces())
                .as("free on two of three nights is not bookable for the range")
                .isZero();
    }

    @Test
    @DisplayName("An allocation that straddles the window edges is clamped, not dropped")
    void allocationStraddlingTheWindowIsClamped() {
        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13),
                List.of(new AllocationInfo(BED_A, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))));

        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 10)).availableUnits()).isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 12)).availableUnits()).isEqualTo(1);
        assertThat(roomType(result, PRIVATE_ID).bookableWholeSpaces()).isZero();
    }

    @Test
    @DisplayName("Allocations entirely outside the window do not affect it")
    void allocationsOutsideTheWindowAreIgnored() {
        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13),
                List.of(
                        new AllocationInfo(BED_A, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)),
                        new AllocationInfo(BED_B, LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 20))));

        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 10)).availableUnits()).isEqualTo(2);
        assertThat(roomType(result, PRIVATE_ID).bookableWholeSpaces()).isEqualTo(1);
    }

    @Test
    @DisplayName("Back-to-back stays on one bed leave it continuously occupied with no phantom free night")
    void backToBackStaysLeaveNoPhantomFreeNight() {
        AvailabilityResult result = calculate(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14),
                List.of(
                        new AllocationInfo(BED_A, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)),
                        new AllocationInfo(BED_A, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13))));

        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 10)).availableUnits()).isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 11)).availableUnits())
                .as("handover day: one guest leaves and another arrives, the bed is not free")
                .isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 12)).availableUnits()).isEqualTo(1);
        assertThat(day(result, DORM_ID, LocalDate.of(2026, 9, 13)).availableUnits()).isEqualTo(2);
    }

    @Test
    @DisplayName("An empty or inverted range is rejected")
    void invalidRangeIsRejected() {
        assertThatThrownBy(() -> calculate(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculate(
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 10), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
