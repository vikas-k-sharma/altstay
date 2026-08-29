package com.altstay.api.knowledgebase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable version of a property's knowledge base content.
 *
 * <p>Mirrors {@code V2__property_and_knowledge_base.sql}. Every save of modified content allocates
 * a monotonically increasing {@code version_no} within the parent {@code knowledge_base_id}.
 * {@code char_count} is constrained between 1 and 20000 by the database schema.
 */
@Entity
@Table(name = "knowledge_base_version")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeBaseVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "knowledge_base_id", nullable = false)
    private UUID knowledgeBaseId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "content_sha256", nullable = false)
    private String contentSha256;

    @Column(name = "char_count", nullable = false)
    private Integer charCount;

    @Column(name = "authored_by")
    private UUID authoredBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public KnowledgeBaseVersion(UUID tenantId, UUID knowledgeBaseId, Integer versionNo,
                                String content, String contentSha256, Integer charCount,
                                UUID authoredBy) {
        this.tenantId = tenantId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.versionNo = versionNo;
        this.content = content;
        this.contentSha256 = contentSha256;
        this.charCount = charCount;
        this.authoredBy = authoredBy;
    }
}
