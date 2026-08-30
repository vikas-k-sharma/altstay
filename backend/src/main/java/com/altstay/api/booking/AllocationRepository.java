package com.altstay.api.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    List<Allocation> findByBookingLineId(UUID bookingLineId);

    List<Allocation> findByUnitId(UUID unitId);

    @Query("select a from Allocation a where a.unitId in :unitIds and a.releasedAt is null and a.checkIn < :to and a.checkOut > :from")
    List<Allocation> findActiveOverlappingAllocations(
            @Param("unitIds") Collection<UUID> unitIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
