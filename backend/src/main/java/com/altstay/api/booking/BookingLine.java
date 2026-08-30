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
@Table(name = "booking_line")
@Getter
@Setter
@NoArgsConstructor
public class BookingLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "room_type_id", nullable = false)
    private UUID roomTypeId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "unit_count", nullable = false)
    private Integer unitCount = 1;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    /** Non-null once a modification has replaced this line. History, not current state. See V11. */
    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public BookingLine(
            UUID tenantId,
            UUID bookingId,
            UUID roomTypeId,
            UUID spaceId,
            LocalDate checkIn,
            LocalDate checkOut,
            int unitCount,
            long amountMinor
    ) {
        this.tenantId = tenantId;
        this.bookingId = bookingId;
        this.roomTypeId = roomTypeId;
        this.spaceId = spaceId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.unitCount = unitCount;
        this.amountMinor = amountMinor;
    }
}
