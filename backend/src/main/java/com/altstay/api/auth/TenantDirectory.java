package com.altstay.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tenant_directory")
@Getter
@Setter
@NoArgsConstructor
public class TenantDirectory {

    @Id
    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    public TenantDirectory(String slug, UUID tenantId) {
        this.slug = slug;
        this.tenantId = tenantId;
    }
}
