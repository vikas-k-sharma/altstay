package com.altstay.api.knowledgebase.dto;

import com.altstay.api.knowledgebase.KnowledgeBaseVersion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KnowledgeBaseVersionResponse(
        UUID id,
        UUID tenantId,
        UUID knowledgeBaseId,
        int versionNo,
        String content,
        String contentSha256,
        int charCount,
        UUID authoredBy,
        OffsetDateTime createdAt
) {
    public static KnowledgeBaseVersionResponse from(KnowledgeBaseVersion version) {
        return new KnowledgeBaseVersionResponse(
                version.getId(),
                version.getTenantId(),
                version.getKnowledgeBaseId(),
                version.getVersionNo(),
                version.getContent(),
                version.getContentSha256(),
                version.getCharCount(),
                version.getAuthoredBy(),
                version.getCreatedAt()
        );
    }
}
