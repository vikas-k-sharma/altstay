package com.altstay.api.knowledgebase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {

    // Deliberately NO 'where tenant_id = ...' clauses.
    // PostgreSQL Row-Level Security enforces tenant boundaries at the database level.
    Optional<KnowledgeBase> findByPropertyId(UUID propertyId);
}
