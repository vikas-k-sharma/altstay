package com.altstay.api.booking;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "adults", nullable = false)
    private Integer adults = 1;

    @Column(name = "children", nullable = false)
    private Integer children = 0;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "subtotal_minor", nullable = false)
    private Long subtotalMinor;

    @Column(name = "tax_minor", nullable = false)
    private Long taxMinor = 0L;

    @Column(name = "total_minor", nullable = false)
    private Long totalMinor;

    @Column(name = "amount_paid_minor", nullable = false)
    private Long amountPaidMinor = 0L;

    @Column(name = "payment_state", nullable = false)
    private String paymentState = "UNPAID";

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    public Booking(
            UUID tenantId,
            UUID propertyId,
            String reference,
            UUID guestId,
            String status,
            String source,
            LocalDate checkIn,
            LocalDate checkOut,
            String currencyCode,
            long subtotalMinor,
            long taxMinor,
            long totalMinor
    ) {
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.reference = reference;
        this.guestId = guestId;
        this.status = status;
        this.source = source;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.currencyCode = currencyCode;
        this.subtotalMinor = subtotalMinor;
        this.taxMinor = taxMinor;
        this.totalMinor = totalMinor;
        this.amountPaidMinor = 0L;
        this.paymentState = "UNPAID";
        this.adults = 1;
        this.children = 0;
    }
}
