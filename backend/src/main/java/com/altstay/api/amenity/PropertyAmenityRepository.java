package com.altstay.api.amenity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, PropertyAmenityId> {
    List<PropertyAmenity> findByPropertyId(UUID propertyId);
    void deleteByPropertyId(UUID propertyId);
}
