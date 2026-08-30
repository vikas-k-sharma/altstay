package com.altstay.api.booking;

import java.util.UUID;

/**
 * A booking line named a room type that does not exist in this tenant.
 *
 * <p>Distinct from a generic {@code IllegalArgumentException} so that phase-5 §9's
 * {@code unknown-room-type} problem type can be returned as a 404 rather than falling through to
 * the catch-all handler and surfacing as a 500.
 */
public class UnknownRoomTypeException extends RuntimeException {

    private final UUID roomTypeId;

    public UnknownRoomTypeException(UUID roomTypeId) {
        super("Room type not found: " + roomTypeId);
        this.roomTypeId = roomTypeId;
    }

    public UUID getRoomTypeId() {
        return roomTypeId;
    }
}
