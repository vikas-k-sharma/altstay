package com.altstay.api.rate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure pricing calculator.
 *
 * <p>Follows phase-5 §6: integer arithmetic, rounding tax ONCE on total using half-up integer rounding
 * {@code (subtotal * taxRateBps + 5000) / 10000}, never per night.
 */
public final class QuoteCalculator {

    private QuoteCalculator() {}

    public record NightlyRate(LocalDate date, long rateMinor) {}

    public record QuoteResult(
            long subtotalMinor,
            long taxMinor,
            long totalMinor,
            List<NightlyRate> nightlyRates
    ) {}

    public static QuoteResult calculateQuote(
            LocalDate checkIn,
            LocalDate checkOut,
            int unitCount,
            long baseRateMinor,
            Map<LocalDate, Long> calendarRates,
            int taxRateBps
    ) {
        requireValidRange(checkIn, checkOut);
        if (unitCount <= 0) {
            throw new IllegalArgumentException("unitCount must be greater than 0");
        }

        List<NightlyRate> nights = nightlyRates(checkIn, checkOut, baseRateMinor, calendarRates);
        long subtotal = subtotalOf(nights, unitCount);
        long tax = taxOn(subtotal, taxRateBps);

        return new QuoteResult(subtotal, tax, subtotal + tax, nights);
    }

    /**
     * The per-night rate for every night of a stay: the rate calendar's entry for that date, or the
     * room type's base rate where the calendar says nothing.
     *
     * <p>Half-open {@code [checkIn, checkOut)} — the checkout day is not a night and is not
     * charged, matching how the bed is allocated.
     */
    public static List<NightlyRate> nightlyRates(
            LocalDate checkIn,
            LocalDate checkOut,
            long baseRateMinor,
            Map<LocalDate, Long> calendarRates
    ) {
        requireValidRange(checkIn, checkOut);
        List<NightlyRate> nights = new ArrayList<>();
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            long nightly = (calendarRates != null && calendarRates.containsKey(date))
                    ? calendarRates.get(date)
                    : baseRateMinor;
            nights.add(new NightlyRate(date, nightly));
        }
        return nights;
    }

    /** Sums nightly rates across a stay for {@code unitCount} beds or rooms. Integers throughout. */
    public static long subtotalOf(List<NightlyRate> nights, int unitCount) {
        if (unitCount <= 0) {
            throw new IllegalArgumentException("unitCount must be greater than 0");
        }
        long subtotal = 0L;
        for (NightlyRate night : nights) {
            subtotal += night.rateMinor() * unitCount;
        }
        return subtotal;
    }

    /**
     * Convenience for a whole line: nights × rate × units.
     *
     * <p>This is the method the booking path uses. It exists so that "what does this stay cost"
     * has exactly one answer in the codebase — an earlier version of {@code BookingService}
     * computed {@code baseRate × unitCount} with no night count at all and no calendar lookup,
     * which charged a five-night stay as one night.
     */
    public static long lineSubtotal(
            LocalDate checkIn,
            LocalDate checkOut,
            int unitCount,
            long baseRateMinor,
            Map<LocalDate, Long> calendarRates
    ) {
        return subtotalOf(nightlyRates(checkIn, checkOut, baseRateMinor, calendarRates), unitCount);
    }

    /**
     * Tax, in integer arithmetic, rounded half-up <b>once</b> on the whole subtotal.
     *
     * <p>Never per night, and never in floating point: rounding each night and summing produces a
     * total that disagrees with the arithmetic a guest does on the invoice (§6).
     */
    public static long taxOn(long subtotalMinor, int taxRateBps) {
        if (subtotalMinor < 0) {
            throw new IllegalArgumentException("subtotalMinor must be non-negative");
        }
        if (taxRateBps < 0 || taxRateBps > 10000) {
            throw new IllegalArgumentException("taxRateBps must be between 0 and 10000");
        }
        return (subtotalMinor * (long) taxRateBps + 5000L) / 10000L;
    }

    private static void requireValidRange(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be strictly after checkIn");
        }
    }
}
