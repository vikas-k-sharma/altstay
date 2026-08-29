package com.altstay.api.knowledgebase;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeBaseVersionRepository extends JpaRepository<KnowledgeBaseVersion, UUID> {

    // Deliberately NO 'where tenant_id = ...' clauses.
    // PostgreSQL Row-Level Security enforces tenant boundaries at the database level.
    List<KnowledgeBaseVersion> findByKnowledgeBaseIdOrderByVersionNoDesc(UUID knowledgeBaseId, Pageable pageable);

    @Query("select coalesce(max(v.versionNo), 0) from KnowledgeBaseVersion v where v.knowledgeBaseId = :knowledgeBaseId")
    int findMaxVersionNoByKnowledgeBaseId(@Param("knowledgeBaseId") UUID knowledgeBaseId);

    Optional<KnowledgeBaseVersion> findByKnowledgeBaseIdAndVersionNo(UUID knowledgeBaseId, int versionNo);
}
