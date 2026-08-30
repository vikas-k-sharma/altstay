package com.altstay.api.rate;

import com.altstay.api.inventory.RoomType;
import com.altstay.api.inventory.RoomTypeRepository;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.rate.QuoteCalculator.QuoteResult;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.TenantScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@TenantScoped
@Transactional
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.datasource.url")
public class RateService {

    private final RatePlanRepository ratePlanRepository;
    private final RateCalendarRepository rateCalendarRepository;
    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;

    public record RatePlanDto(
            UUID id,
            UUID tenantId,
            UUID propertyId,
            UUID roomTypeId,
            String code,
            String name,
            boolean isDefault,
            boolean isActive
    ) {}

    public record CreateRatePlanRequest(
            UUID roomTypeId,
            String code,
            String name,
            boolean isDefault
    ) {}

    public record SetRateCalendarRequest(
            LocalDate from,
            LocalDate to,
            long amountMinor
    ) {}

    public record RateCalendarDto(
            LocalDate stayDate,
            long amountMinor
    ) {}

    public record QuoteRequest(
            UUID propertyId,
            String propertySlug,
            UUID roomTypeId,
            UUID ratePlanId,
            LocalDate checkIn,
            LocalDate checkOut,
            int unitCount
    ) {}

    public record QuoteResponse(
            long subtotalMinor,
            long taxMinor,
            long totalMinor,
            String currencyCode,
            List<QuoteCalculator.NightlyRate> nightlyRates
    ) {}

    public RatePlanDto createRatePlan(String propertySlug, CreateRatePlanRequest req) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));
        Property property = propertyRepository.findBySlug(propertySlug)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertySlug));
        RoomType rt = roomTypeRepository.findById(req.roomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Room type not found: " + req.roomTypeId()));

        if (!rt.getPropertyId().equals(property.getId())) {
            throw new IllegalArgumentException("Room type does not belong to property: " + propertySlug);
        }

        RatePlan plan = new RatePlan(tenantId, property.getId(), rt.getId(), req.code(), req.name(), req.isDefault());
        plan = ratePlanRepository.save(plan);
        return toDto(plan);
    }

    public List<RatePlanDto> listRatePlans(String propertySlug) {
        Property property = propertyRepository.findBySlug(propertySlug)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertySlug));
        return ratePlanRepository.findByPropertyId(property.getId()).stream().map(this::toDto).toList();
    }

    public List<RateCalendarDto> getCalendar(UUID ratePlanId, LocalDate from, LocalDate to) {
        List<RateCalendar> entries = rateCalendarRepository.findByRatePlanIdAndStayDateBetween(ratePlanId, from, to);
        return entries.stream().map(e -> new RateCalendarDto(e.getStayDate(), e.getAmountMinor())).toList();
    }

    public void setCalendarRange(UUID ratePlanId, SetRateCalendarRequest req) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));
        RatePlan plan = ratePlanRepository.findById(ratePlanId)
                .orElseThrow(() -> new IllegalArgumentException("Rate plan not found: " + ratePlanId));

        if (req.from() == null || req.to() == null || req.to().isBefore(req.from())) {
            throw new IllegalArgumentException("to date must be on or after from date");
        }

        List<RateCalendar> entries = new ArrayList<>();
        for (LocalDate d = req.from(); !d.isAfter(req.to()); d = d.plusDays(1)) {
            entries.add(new RateCalendar(tenantId, plan.getId(), d, req.amountMinor()));
        }
        rateCalendarRepository.saveAll(entries);
    }

    public QuoteResponse getQuote(QuoteRequest req) {
        Property property;
        if (req.propertyId() != null) {
            property = propertyRepository.findById(req.propertyId())
                    .orElseThrow(() -> new IllegalArgumentException("Property not found: " + req.propertyId()));
        } else if (req.propertySlug() != null) {
            property = propertyRepository.findBySlug(req.propertySlug())
                    .orElseThrow(() -> new IllegalArgumentException("Property not found: " + req.propertySlug()));
        } else {
            throw new IllegalArgumentException("Either propertyId or propertySlug must be provided");
        }

        RoomType rt = roomTypeRepository.findById(req.roomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Room type not found: " + req.roomTypeId()));

        RatePlan plan = null;
        if (req.ratePlanId() != null) {
            plan = ratePlanRepository.findById(req.ratePlanId()).orElse(null);
        } else {
            plan = ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(rt.getId()).orElse(null);
        }

        Map<LocalDate, Long> calendarRates = new HashMap<>();
        if (plan != null) {
            List<RateCalendar> entries = rateCalendarRepository.findByRatePlanIdAndStayDateBetween(
                    plan.getId(), req.checkIn(), req.checkOut().minusDays(1));
            for (RateCalendar e : entries) {
                calendarRates.put(e.getStayDate(), e.getAmountMinor());
            }
        }

        QuoteResult quote = QuoteCalculator.calculateQuote(
                req.checkIn(),
                req.checkOut(),
                req.unitCount() > 0 ? req.unitCount() : 1,
                rt.getBaseRateMinor(),
                calendarRates,
                property.getTaxRateBps()
        );

        return new QuoteResponse(
                quote.subtotalMinor(),
                quote.taxMinor(),
                quote.totalMinor(),
                property.getCurrencyCode(),
                quote.nightlyRates()
        );
    }

    private RatePlanDto toDto(RatePlan p) {
        return new RatePlanDto(
                p.getId(),
                p.getTenantId(),
                p.getPropertyId(),
                p.getRoomTypeId(),
                p.getCode(),
                p.getName(),
                p.isDefault(),
                p.isActive()
        );
    }
}
