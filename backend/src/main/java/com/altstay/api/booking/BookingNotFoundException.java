package com.altstay.api.booking;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String reference) {
        super("Booking not found: " + reference);
    }
}
