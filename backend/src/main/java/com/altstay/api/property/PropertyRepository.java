package com.altstay.api.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    // Deliberately NO 'where tenant_id = ...' clauses.
    // PostgreSQL Row-Level Security enforces tenant boundaries at the database level.
    List<Property> findAll();

    Optional<Property> findBySlug(String slug);
}
