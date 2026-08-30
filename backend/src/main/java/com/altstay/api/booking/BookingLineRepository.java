package com.altstay.api.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingLineRepository extends JpaRepository<BookingLine, UUID> {
    List<BookingLine> findByBookingId(UUID bookingId);

    /**
     * The booking's <em>current</em> lines. Superseded rows are history (V11) and must not be
     * priced, allocated against, or shown as part of the stay.
     */
    List<BookingLine> findByBookingIdAndSupersededAtIsNull(UUID bookingId);
}
