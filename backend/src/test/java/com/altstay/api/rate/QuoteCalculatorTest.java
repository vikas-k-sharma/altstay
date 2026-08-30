package com.altstay.api.rate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteCalculatorTest {

    @Test
    @DisplayName("Single night without calendar override uses room type base rate and rounds tax once on total")
    void singleNightBaseRate() {
        var result = QuoteCalculator.calculateQuote(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                1,
                50000L, // 500.00 INR
                Map.of(),
                1200 // 12%
        );

        assertThat(result.subtotalMinor()).isEqualTo(50000L);
        assertThat(result.taxMinor()).isEqualTo(6000L); // 12% of 50000
        assertThat(result.totalMinor()).isEqualTo(56000L);
        assertThat(result.nightlyRates()).hasSize(1);
        assertThat(result.nightlyRates().get(0).rateMinor()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("Multi-night with rate calendar overrides calculates correct sum and tax")
    void multiNightWithOverrides() {
        var rates = Map.of(
                LocalDate.of(2026, 9, 1), 60000L,
                LocalDate.of(2026, 9, 2), 75000L
        );

        var result = QuoteCalculator.calculateQuote(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 4), // 3 nights: Sep 1 (60000), Sep 2 (75000), Sep 3 (fallback base 50000)
                2, // 2 units
                50000L,
                rates,
                1800 // 18%
        );

        // Subtotal = (60000 + 75000 + 50000) * 2 = 185000 * 2 = 370000
        assertThat(result.subtotalMinor()).isEqualTo(370000L);
        // Tax = (370000 * 1800 + 5000) / 10000 = (666000000 + 5000) / 10000 = 66600
        assertThat(result.taxMinor()).isEqualTo(66600L);
        assertThat(result.totalMinor()).isEqualTo(436600L);
        assertThat(result.nightlyRates()).hasSize(3);
    }

    @Test
    @DisplayName("Half-up tax rounding rounds once on total")
    void taxRoundingHalfUp() {
        // Subtotal = 1001, taxRate = 500 bps (5%)
        // 1001 * 500 = 500500. (500500 + 5000) / 10000 = 505500 / 10000 = 50
        var result = QuoteCalculator.calculateQuote(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                1,
                1001L,
                Map.of(),
                500
        );

        assertThat(result.subtotalMinor()).isEqualTo(1001L);
        assertThat(result.taxMinor()).isEqualTo(50L);
        assertThat(result.totalMinor()).isEqualTo(1051L);
    }

    @Test
    @DisplayName("Zero tax rate results in zero tax")
    void zeroTaxRate() {
        var result = QuoteCalculator.calculateQuote(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3),
                1,
                80000L,
                Map.of(),
                0
        );

        assertThat(result.subtotalMinor()).isEqualTo(160000L);
        assertThat(result.taxMinor()).isEqualTo(0L);
        assertThat(result.totalMinor()).isEqualTo(160000L);
    }

    @Test
    @DisplayName("Invalid date range throws IllegalArgumentException")
    void invalidDateRangeThrows() {
        assertThatThrownBy(() -> QuoteCalculator.calculateQuote(
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 1),
                1, 50000L, Map.of(), 1200
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
