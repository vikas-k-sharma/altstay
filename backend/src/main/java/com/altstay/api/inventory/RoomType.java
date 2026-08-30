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
@Table(name = "room_type")
@Getter
@Setter
@NoArgsConstructor
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sale_mode", nullable = false)
    private String saleMode;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "max_occupancy", nullable = false)
    private Integer maxOccupancy;

    @Column(name = "base_rate_minor", nullable = false)
    private Long baseRateMinor;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public RoomType(UUID tenantId, UUID propertyId, String code, String name, String saleMode, String kind, int maxOccupancy, long baseRateMinor) {
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.code = code;
        this.name = name;
        this.saleMode = saleMode;
        this.kind = kind;
        this.maxOccupancy = maxOccupancy;
        this.baseRateMinor = baseRateMinor;
        this.isActive = true;
    }
}
