package com.altstay.api.amenity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, String> {
    List<Amenity> findAllByOrderByCategoryAscCodeAsc();
}
