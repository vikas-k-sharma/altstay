package com.altstay.api.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, UUID> {

    List<ConversationTurn> findByConversationIdOrderBySeqAsc(UUID conversationId);

    Optional<ConversationTurn> findByConversationIdAndSeq(UUID conversationId, int seq);

    @Query("select coalesce(max(t.seq), -1) from ConversationTurn t where t.conversationId = :conversationId")
    int findMaxSeqByConversationId(@Param("conversationId") UUID conversationId);

    @Query("select coalesce(sum(t.totalTokens), 0L) from ConversationTurn t where t.tenantId = :tenantId")
    long sumTotalTokensByTenantId(@Param("tenantId") UUID tenantId);
}
