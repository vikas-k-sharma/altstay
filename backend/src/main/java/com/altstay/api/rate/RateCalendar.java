package com.altstay.api.rate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rate_calendar")
@IdClass(RateCalendarId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RateCalendar {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "rate_plan_id", nullable = false)
    private UUID ratePlanId;

    @Id
    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
}
