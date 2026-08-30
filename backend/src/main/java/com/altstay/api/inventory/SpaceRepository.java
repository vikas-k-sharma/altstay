package com.altstay.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpaceRepository extends JpaRepository<Space, UUID> {
    List<Space> findByPropertyId(UUID propertyId);
    Optional<Space> findByPropertyIdAndId(UUID propertyId, UUID id);
    Optional<Space> findByPropertyIdAndName(UUID propertyId, String name);
}
