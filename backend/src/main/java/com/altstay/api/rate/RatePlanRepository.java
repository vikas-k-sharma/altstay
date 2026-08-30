package com.altstay.api.rate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RatePlanRepository extends JpaRepository<RatePlan, UUID> {
    List<RatePlan> findByPropertyId(UUID propertyId);
    List<RatePlan> findByRoomTypeId(UUID roomTypeId);
    Optional<RatePlan> findByRoomTypeIdAndCode(UUID roomTypeId, String code);
    Optional<RatePlan> findByRoomTypeIdAndIsDefaultTrue(UUID roomTypeId);
}
