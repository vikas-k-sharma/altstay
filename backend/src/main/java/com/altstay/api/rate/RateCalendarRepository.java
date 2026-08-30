package com.altstay.api.rate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RateCalendarRepository extends JpaRepository<RateCalendar, RateCalendarId> {
    List<RateCalendar> findByRatePlanIdAndStayDateBetween(UUID ratePlanId, LocalDate from, LocalDate to);
    List<RateCalendar> findByRatePlanIdInAndStayDateBetween(Collection<UUID> ratePlanIds, LocalDate from, LocalDate to);
}
