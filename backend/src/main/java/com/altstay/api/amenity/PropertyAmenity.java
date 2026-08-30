package com.altstay.api.amenity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "property_amenity")
@IdClass(PropertyAmenityId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAmenity {

    @Id
    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Id
    @Column(name = "amenity_code", nullable = false)
    private String amenityCode;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
}
