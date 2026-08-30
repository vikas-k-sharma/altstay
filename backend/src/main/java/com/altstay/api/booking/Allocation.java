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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "allocation")
@Getter
@Setter
@NoArgsConstructor
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "booking_line_id", nullable = false)
    private UUID bookingLineId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Allocation(UUID tenantId, UUID unitId, UUID bookingLineId, LocalDate checkIn, LocalDate checkOut) {
        this.tenantId = tenantId;
        this.unitId = unitId;
        this.bookingLineId = bookingLineId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }
}
