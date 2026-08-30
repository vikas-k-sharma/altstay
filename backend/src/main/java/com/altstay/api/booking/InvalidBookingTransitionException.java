package com.altstay.api.booking;

import lombok.Getter;

@Getter
public class InvalidBookingTransitionException extends RuntimeException {
    private final String reference;
    private final BookingStatus fromStatus;
    private final BookingStatus toStatus;

    public InvalidBookingTransitionException(String reference, BookingStatus fromStatus, BookingStatus toStatus) {
        super(String.format("Cannot transition booking %s from %s to %s", reference, fromStatus, toStatus));
        this.reference = reference;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
}
