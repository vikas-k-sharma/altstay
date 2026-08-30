package com.altstay.api.booking;

/**
 * The property's currency and the currency a booking was priced in disagree.
 *
 * <p>Phase-5 §6: "A booking whose property currency differs from the rate plan's expectation is a
 * 409, not a coerced conversion. There is no FX in this system and there should not be one."
 */
public class RateCurrencyMismatchException extends RuntimeException {

    private final String expectedCurrency;
    private final String actualCurrency;

    public RateCurrencyMismatchException(String expectedCurrency, String actualCurrency) {
        super("Currency mismatch: property transacts in " + expectedCurrency
                + " but the booking is priced in " + actualCurrency
                + ". This system does not convert currencies.");
        this.expectedCurrency = expectedCurrency;
        this.actualCurrency = actualCurrency;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public String getActualCurrency() {
        return actualCurrency;
    }
}
