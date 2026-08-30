package com.altstay.api.booking;

public class NoAvailabilityException extends RuntimeException {
    public NoAvailabilityException(String message) {
        super(message);
    }
}
