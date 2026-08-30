package com.altstay.api.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByReference(String reference);
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    List<Booking> findByPropertyId(UUID propertyId);
    List<Booking> findByPropertyIdAndStatus(UUID propertyId, String status);
}
