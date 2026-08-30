package com.altstay.api.provision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.datasource.url")
public class TenantProvisioningService {

    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;

    public record ProvisioningResult(
            UUID tenantId,
            String tenantSlug,
            UUID ownerUserId,
            String ownerEmail,
            UUID propertyId,
            String propertySlug
    ) {}

    public ProvisioningResult provisionTenant(TenantProvisioningProperties props, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Owner password must not be blank");
        }

        UUID tenantId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        String propSlug = (props.propertySlug() != null && !props.propertySlug().isBlank())
                ? props.propertySlug()
                : props.tenantSlug() + "-main";
        // Both are @NotBlank on the properties record and are validated as an IANA zone id / ISO
        // 4217 code here. Neither is defaulted anywhere in this system (§2).
        String timezone = props.timezone();
        if (!java.time.ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new IllegalArgumentException("Unknown IANA timezone: " + timezone);
        }
        String currency = props.currencyCode().toUpperCase();
        try {
            java.util.Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown ISO 4217 currency code: " + currency);
        }
        int taxRateBps = props.taxRateBps();

        String passwordHash = passwordEncoder.encode(rawPassword);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            // 1. Bind app.tenant_id to new tenant UUID
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }

            // 2. Insert tenant (trigger populates tenant_directory)
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into tenant (id, name, slug) values (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setString(2, props.tenantName());
                ps.setString(3, props.tenantSlug());
                ps.execute();
            }

            // 3. Insert owner app_user
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into app_user (id, tenant_id, email, password_hash, is_active) values (?, ?, ?, ?, true)")) {
                ps.setObject(1, ownerUserId);
                ps.setObject(2, tenantId);
                ps.setString(3, props.ownerEmail());
                ps.setString(4, passwordHash);
                ps.execute();
            }

            // 4. Insert user_role OWNER
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into user_role (tenant_id, user_id, role) values (?, ?, 'OWNER')")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ownerUserId);
                ps.execute();
            }

            // 5. Insert initial property
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into property (id, tenant_id, name, slug, timezone, currency_code, tax_rate_bps) values (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, propertyId);
                ps.setObject(2, tenantId);
                ps.setString(3, props.propertyName());
                ps.setString(4, propSlug);
                ps.setString(5, timezone);
                ps.setString(6, currency);
                ps.setInt(7, taxRateBps);
                ps.execute();
            }

            c.commit();

            log.info("Tenant provisioned successfully: tenantId={}, slug={}, ownerEmail={}, propertyId={}, propertySlug={}",
                    tenantId, props.tenantSlug(), props.ownerEmail(), propertyId, propSlug);

            return new ProvisioningResult(tenantId, props.tenantSlug(), ownerUserId, props.ownerEmail(), propertyId, propSlug);
        } catch (SQLException e) {
            log.error("Failed to provision tenant with slug: {}", props.tenantSlug(), e);
            throw new RuntimeException("Provisioning failed: " + e.getMessage(), e);
        }
    }
}
