package com.altstay.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    List<RoomType> findByPropertyId(UUID propertyId);
    Optional<RoomType> findByPropertyIdAndCode(UUID propertyId, String code);
    Optional<RoomType> findByPropertyIdAndId(UUID propertyId, UUID id);
}
