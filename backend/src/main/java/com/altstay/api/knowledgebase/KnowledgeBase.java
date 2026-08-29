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
 * Root entity for a property's knowledge base.
 *
 * <p>Mirrors {@code V2__property_and_knowledge_base.sql}. Every property has at most one
 * knowledge base per tenant. {@code current_version_id} repoints at the latest
 * {@link KnowledgeBaseVersion} on each save.
 */
@Entity
@Table(name = "knowledge_base")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public KnowledgeBase(UUID tenantId, UUID propertyId) {
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.updatedAt = OffsetDateTime.now();
    }
}
