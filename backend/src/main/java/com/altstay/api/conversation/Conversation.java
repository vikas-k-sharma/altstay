package com.altstay.api.conversation;

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

@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt = OffsetDateTime.now();

    public Conversation(UUID tenantId, UUID propertyId) {
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.startedAt = OffsetDateTime.now();
        this.lastActivityAt = OffsetDateTime.now();
    }
}
