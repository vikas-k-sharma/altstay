package com.altstay.api.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BookingStatusMachineTest {

    @ParameterizedTest(name = "{0} -> {1} should be valid={2}")
    @CsvSource({
            // Valid transitions from BOOKED
            "BOOKED, CHECKED_IN, true",
            "BOOKED, CANCELLED, true",
            "BOOKED, NO_SHOW, true",

            // Invalid transitions from BOOKED
            "BOOKED, CHECKED_OUT, false",
            "BOOKED, BOOKED, false",

            // Valid transitions from CHECKED_IN
            "CHECKED_IN, CHECKED_OUT, true",
            "CHECKED_IN, CANCELLED, true",

            // Invalid transitions from CHECKED_IN
            "CHECKED_IN, BOOKED, false",
            "CHECKED_IN, NO_SHOW, false",
            "CHECKED_IN, CHECKED_IN, false",

            // Terminal status: CHECKED_OUT
            "CHECKED_OUT, BOOKED, false",
            "CHECKED_OUT, CHECKED_IN, false",
            "CHECKED_OUT, CANCELLED, false",
            "CHECKED_OUT, NO_SHOW, false",
            "CHECKED_OUT, CHECKED_OUT, false",

            // Terminal status: CANCELLED
            "CANCELLED, BOOKED, false",
            "CANCELLED, CHECKED_IN, false",
            "CANCELLED, CHECKED_OUT, false",
            "CANCELLED, NO_SHOW, false",
            "CANCELLED, CANCELLED, false",

            // Terminal status: NO_SHOW
            "NO_SHOW, BOOKED, false",
            "NO_SHOW, CHECKED_IN, false",
            "NO_SHOW, CHECKED_OUT, false",
            "NO_SHOW, CANCELLED, false",
            "NO_SHOW, NO_SHOW, false"
    })
    @DisplayName("Exhaustive state machine validation across all status combinations")
    void exhaustiveStatusTransitionMatrix(BookingStatus from, BookingStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Null target transition always returns false")
    void nullTargetReturnsFalse() {
        for (BookingStatus status : BookingStatus.values()) {
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }
}
