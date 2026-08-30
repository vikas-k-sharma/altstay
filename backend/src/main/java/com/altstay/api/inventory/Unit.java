package com.altstay.api.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "unit")
@Getter
@Setter
@NoArgsConstructor
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "unit_kind", nullable = false)
    private String unitKind;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Unit(UUID tenantId, UUID spaceId, String label, String unitKind) {
        this.tenantId = tenantId;
        this.spaceId = spaceId;
        this.label = label;
        this.unitKind = unitKind;
        this.isActive = true;
    }
}
