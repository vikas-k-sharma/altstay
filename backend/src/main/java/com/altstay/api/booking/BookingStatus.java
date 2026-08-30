package com.altstay.api.booking;

/**
 * The booking lifecycle, in one place (§5.1).
 *
 * <pre>
 *                 +--------------&gt; CANCELLED
 *                 |
 * BOOKED ---------+--------------&gt; NO_SHOW
 *    |            |
 *    +--&gt; CHECKED_IN --------------&gt; CHECKED_OUT
 * </pre>
 *
 * <p>{@code CANCELLED}, {@code NO_SHOW} and {@code CHECKED_OUT} are terminal. {@code NO_SHOW} is
 * here because a front desk needs it nightly and because it releases inventory differently from a
 * cancellation <em>in reporting</em>, even though both release it.
 *
 * <p><b>One deviation from the diagram, deliberate:</b> {@code CHECKED_IN -> CANCELLED} is allowed.
 * A guest who has checked in and then has their stay voided - a payment that never cleared, a
 * booking made in error, someone asked to leave - is a real event, and refusing it leaves the front
 * desk with no legal move and a parallel notebook, which roadmap R3 names as a kill criterion. The
 * inventory effect is identical to any other cancellation: every allocation is released.
 *
 * <p>This machine is the single source of truth for what is legal, checked in the service and
 * backed by the {@code check} constraint on {@code booking.status}. An illegal transition is a
 * <b>409</b> with problem type {@code .../invalid-booking-transition} - never a 500, never a
 * silent no-op.
 */
public enum BookingStatus {
    BOOKED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
    NO_SHOW;

    public boolean canTransitionTo(BookingStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case BOOKED -> target == CHECKED_IN || target == CANCELLED || target == NO_SHOW;
            case CHECKED_IN -> target == CHECKED_OUT || target == CANCELLED;
            case CHECKED_OUT, CANCELLED, NO_SHOW -> false;
        };
    }
}
