package com.altstay.api.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "property")
@Getter
@Setter
@NoArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city")
    private String city;

    @Column(name = "state_region")
    private String stateRegion;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime = LocalTime.of(14, 0);

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime = LocalTime.of(11, 0);

    @Column(name = "tax_rate_bps", nullable = false)
    private Integer taxRateBps = 0;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * There is deliberately no constructor without a timezone and currency. Both are {@code not
     * null} in the schema and neither has a default anywhere in this system (§2) — a convenience
     * overload that filled them in was how a property could be created with an invented timezone.
     */
    public Property(UUID tenantId, String name, String slug, String timezone, String currencyCode) {
        this.tenantId = tenantId;
        this.name = name;
        this.slug = slug;
        this.timezone = timezone;
        this.currencyCode = currencyCode;
        this.status = "ACTIVE";
        this.checkInTime = LocalTime.of(14, 0);
        this.checkOutTime = LocalTime.of(11, 0);
        this.taxRateBps = 0;
    }
}
