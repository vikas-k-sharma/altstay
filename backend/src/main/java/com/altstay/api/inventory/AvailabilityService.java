package com.altstay.api.inventory;

import com.altstay.api.booking.Allocation;
import com.altstay.api.booking.AllocationRepository;
import com.altstay.api.inventory.AvailabilityCalculator.AllocationInfo;
import com.altstay.api.inventory.AvailabilityCalculator.AvailabilityResult;
import com.altstay.api.inventory.AvailabilityCalculator.RoomTypeInfo;
import com.altstay.api.inventory.AvailabilityCalculator.SpaceInfo;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.rate.RateCalendar;
import com.altstay.api.rate.RateCalendarRepository;
import com.altstay.api.rate.RatePlan;
import com.altstay.api.rate.RatePlanRepository;
import com.altstay.api.tenancy.TenantScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@TenantScoped
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.datasource.url")
public class AvailabilityService {

    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final SpaceRepository spaceRepository;
    private final UnitRepository unitRepository;
    private final RoomTypeSpaceRepository roomTypeSpaceRepository;
    private final AllocationRepository allocationRepository;
    private final RatePlanRepository ratePlanRepository;
    private final RateCalendarRepository rateCalendarRepository;

    public record DayAvailabilityDto(
            LocalDate date,
            int availableUnits,
            int totalUnits,
            int availableSpaces,
            int totalSpaces,
            long rateMinor
    ) {}

    /**
     * @param bookableWholeSpaces spaces free across the <em>entire</em> requested range — what a
     *                            {@code WHOLE} sale actually needs (§7). Distinct from the per-day
     *                            {@code availableSpaces} counts, which a calendar renders.
     */
    public record RoomTypeAvailabilityDto(
            UUID roomTypeId,
            String code,
            String saleMode,
            int bookableWholeSpaces,
            List<DayAvailabilityDto> days
    ) {}

    public record PropertyAvailabilityResponse(
            LocalDate from,
            LocalDate to,
            String currency,
            List<RoomTypeAvailabilityDto> roomTypes
    ) {}

    public PropertyAvailabilityResponse getAvailability(
            String propertySlug,
            LocalDate from,
            LocalDate to,
            UUID roomTypeId
    ) {
        Property property = propertyRepository.findBySlug(propertySlug)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertySlug));

        List<RoomType> roomTypes;
        if (roomTypeId != null) {
            RoomType rt = roomTypeRepository.findById(roomTypeId)
                    .orElseThrow(() -> new IllegalArgumentException("Room type not found: " + roomTypeId));
            roomTypes = List.of(rt);
        } else {
            roomTypes = roomTypeRepository.findByPropertyId(property.getId()).stream()
                    .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                    .toList();
        }

        List<UUID> rtIds = roomTypes.stream().map(RoomType::getId).toList();
        List<RoomTypeSpace> mappings = roomTypeSpaceRepository.findByRoomTypeIdIn(rtIds);
        Map<UUID, List<UUID>> spaceIdsByRt = new HashMap<>();
        Set<UUID> allSpaceIds = new HashSet<>();
        for (RoomTypeSpace m : mappings) {
            spaceIdsByRt.computeIfAbsent(m.getRoomTypeId(), k -> new ArrayList<>()).add(m.getSpaceId());
            allSpaceIds.add(m.getSpaceId());
        }

        List<Unit> allUnits = unitRepository.findBySpaceIdInAndIsActiveTrue(allSpaceIds);
        Map<UUID, Set<UUID>> unitIdsBySpace = new HashMap<>();
        List<UUID> allUnitIds = new ArrayList<>();
        for (Unit u : allUnits) {
            unitIdsBySpace.computeIfAbsent(u.getSpaceId(), k -> new HashSet<>()).add(u.getId());
            allUnitIds.add(u.getId());
        }

        List<SpaceInfo> spaceInfos = new ArrayList<>();
        for (UUID sId : allSpaceIds) {
            Set<UUID> uIds = unitIdsBySpace.getOrDefault(sId, Set.of());
            spaceInfos.add(new SpaceInfo(sId, uIds.size(), uIds));
        }

        List<RoomTypeInfo> rtInfos = roomTypes.stream().map(rt -> new RoomTypeInfo(
                rt.getId(),
                rt.getCode(),
                rt.getSaleMode(),
                rt.getBaseRateMinor(),
                spaceIdsByRt.getOrDefault(rt.getId(), List.of())
        )).toList();

        List<Allocation> allocations = allUnitIds.isEmpty() ? List.of() :
                allocationRepository.findActiveOverlappingAllocations(allUnitIds, from, to);
        List<AllocationInfo> allocInfos = allocations.stream()
                .map(a -> new AllocationInfo(a.getUnitId(), a.getCheckIn(), a.getCheckOut()))
                .toList();

        // Rate calendars
        Map<UUID, Map<LocalDate, Long>> calendarRatesByRoomType = new HashMap<>();
        for (RoomType rt : roomTypes) {
            Optional<RatePlan> defaultPlan = ratePlanRepository.findByRoomTypeIdAndIsDefaultTrue(rt.getId());
            if (defaultPlan.isPresent()) {
                List<RateCalendar> entries = rateCalendarRepository.findByRatePlanIdAndStayDateBetween(
                        defaultPlan.get().getId(), from, to.minusDays(1));
                Map<LocalDate, Long> map = entries.stream()
                        .collect(Collectors.toMap(RateCalendar::getStayDate, RateCalendar::getAmountMinor));
                calendarRatesByRoomType.put(rt.getId(), map);
            }
        }

        AvailabilityResult result = AvailabilityCalculator.calculate(
                from, to, rtInfos, spaceInfos, allocInfos, calendarRatesByRoomType
        );

        List<RoomTypeAvailabilityDto> rtDtos = result.roomTypes().stream().map(rta -> new RoomTypeAvailabilityDto(
                rta.roomTypeId(),
                rta.code(),
                rta.saleMode(),
                rta.bookableWholeSpaces(),
                rta.days().stream().map(d -> new DayAvailabilityDto(
                        d.date(),
                        d.availableUnits(),
                        d.totalUnits(),
                        d.availableSpaces(),
                        d.totalSpaces(),
                        d.rateMinor()
                )).toList()
        )).toList();

        return new PropertyAvailabilityResponse(
                from,
                to,
                property.getCurrencyCode(),
                rtDtos
        );
    }
}
