package com.altstay.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "user_role")
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    public UserRole(UUID userId, UUID tenantId, String role) {
        this.id = new UserRoleId(userId, role);
        this.tenantId = tenantId;
    }

    public UUID getUserId() {
        return id != null ? id.getUserId() : null;
    }

    public String getRole() {
        return id != null ? id.getRole() : null;
    }
}
