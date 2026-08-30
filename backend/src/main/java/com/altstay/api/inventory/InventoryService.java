package com.altstay.api.inventory;

import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.MissingTenantException;
import com.altstay.api.tenancy.TenantScoped;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@TenantScoped
@ConditionalOnProperty(name = "spring.datasource.url")
public class InventoryService {

    private static final Set<String> VALID_SALE_MODES = Set.of("PER_UNIT", "WHOLE");
    private static final Set<String> VALID_KINDS = Set.of("DORM", "PRIVATE");
    private static final Set<String> VALID_UNIT_KINDS = Set.of("SINGLE", "BUNK_TOP", "BUNK_BOTTOM", "DOUBLE");

    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final SpaceRepository spaceRepository;
    private final UnitRepository unitRepository;
    private final RoomTypeSpaceRepository roomTypeSpaceRepository;

    public InventoryService(
            PropertyRepository propertyRepository,
            RoomTypeRepository roomTypeRepository,
            SpaceRepository spaceRepository,
            UnitRepository unitRepository,
            RoomTypeSpaceRepository roomTypeSpaceRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.spaceRepository = spaceRepository;
        this.unitRepository = unitRepository;
        this.roomTypeSpaceRepository = roomTypeSpaceRepository;
    }

    // --- Room Types ---

    @Transactional(readOnly = true)
    public List<RoomTypeDto> listRoomTypes(String propertySlug) {
        Property property = getPropertyOrThrow(propertySlug);
        List<RoomType> roomTypes = roomTypeRepository.findByPropertyId(property.getId());
        List<UUID> rtIds = roomTypes.stream().map(RoomType::getId).toList();
        Map<UUID, List<UUID>> spaceIdsByRt = roomTypeSpaceRepository.findByRoomTypeIdIn(rtIds)
                .stream()
                .collect(Collectors.groupingBy(RoomTypeSpace::getRoomTypeId,
                        Collectors.mapping(RoomTypeSpace::getSpaceId, Collectors.toList())));

        return roomTypes.stream()
                .map(rt -> RoomTypeDto.from(rt, spaceIdsByRt.getOrDefault(rt.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomTypeDto getRoomType(String propertySlug, UUID roomTypeId) {
        Property property = getPropertyOrThrow(propertySlug);
        RoomType rt = roomTypeRepository.findByPropertyIdAndId(property.getId(), roomTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Room type not found: " + roomTypeId));
        List<UUID> spaceIds = roomTypeSpaceRepository.findByRoomTypeId(rt.getId())
                .stream()
                .map(RoomTypeSpace::getSpaceId)
                .toList();
        return RoomTypeDto.from(rt, spaceIds);
    }

    @Transactional
    public RoomTypeDto createRoomType(String propertySlug, CreateRoomTypeRequest request) {
        UUID tenantId = requireTenant();
        Property property = getPropertyOrThrow(propertySlug);

        validateSaleMode(request.saleMode());
        validateKind(request.kind());
        if (request.maxOccupancy() <= 0) {
            throw new IllegalArgumentException("maxOccupancy must be greater than 0");
        }
        if (request.baseRateMinor() < 0) {
            throw new IllegalArgumentException("baseRateMinor must be non-negative");
        }

        RoomType roomType = new RoomType(
                tenantId,
                property.getId(),
                request.code().toUpperCase().trim(),
                request.name().trim(),
                request.saleMode().toUpperCase().trim(),
                request.kind().toUpperCase().trim(),
                request.maxOccupancy(),
                request.baseRateMinor()
        );
        if (request.description() != null) {
            roomType.setDescription(request.description());
        }
        if (request.isActive() != null) {
            roomType.setIsActive(request.isActive());
        }

        RoomType saved = roomTypeRepository.save(roomType);
        return RoomTypeDto.from(saved, List.of());
    }

    @Transactional
    public RoomTypeDto updateRoomType(String propertySlug, UUID roomTypeId, UpdateRoomTypeRequest request) {
        requireTenant();
        Property property = getPropertyOrThrow(propertySlug);

        RoomType rt = roomTypeRepository.findByPropertyIdAndId(property.getId(), roomTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Room type not found: " + roomTypeId));

        if (request.name() != null && !request.name().isBlank()) {
            rt.setName(request.name().trim());
        }
        if (request.saleMode() != null && !request.saleMode().isBlank()) {
            validateSaleMode(request.saleMode());
            rt.setSaleMode(request.saleMode().toUpperCase().trim());
        }
        if (request.kind() != null && !request.kind().isBlank()) {
            validateKind(request.kind());
            rt.setKind(request.kind().toUpperCase().trim());
        }
        if (request.maxOccupancy() != null) {
            if (request.maxOccupancy() <= 0) {
                throw new IllegalArgumentException("maxOccupancy must be greater than 0");
            }
            rt.setMaxOccupancy(request.maxOccupancy());
        }
        if (request.baseRateMinor() != null) {
            if (request.baseRateMinor() < 0) {
                throw new IllegalArgumentException("baseRateMinor must be non-negative");
            }
            rt.setBaseRateMinor(request.baseRateMinor());
        }
        if (request.description() != null) {
            rt.setDescription(request.description());
        }
        if (request.isActive() != null) {
            rt.setIsActive(request.isActive());
        }

        RoomType saved = roomTypeRepository.save(rt);
        List<UUID> spaceIds = roomTypeSpaceRepository.findByRoomTypeId(saved.getId())
                .stream()
                .map(RoomTypeSpace::getSpaceId)
                .toList();
        return RoomTypeDto.from(saved, spaceIds);
    }

    // --- Hybrid mapping: room_type_space ---

    @Transactional
    public void associateSpace(UUID roomTypeId, UUID spaceId) {
        UUID tenantId = requireTenant();
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Room type not found: " + roomTypeId));
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));

        if (!roomType.getPropertyId().equals(space.getPropertyId())) {
            throw new IllegalArgumentException("Room type and space must belong to the same property");
        }

        if (roomTypeSpaceRepository.findByRoomTypeIdAndSpaceId(roomTypeId, spaceId).isEmpty()) {
            roomTypeSpaceRepository.save(new RoomTypeSpace(tenantId, roomTypeId, spaceId));
        }
    }

    @Transactional
    public void dissociateSpace(UUID roomTypeId, UUID spaceId) {
        requireTenant();
        roomTypeSpaceRepository.deleteByRoomTypeIdAndSpaceId(roomTypeId, spaceId);
    }

    // --- Spaces & Units ---

    @Transactional(readOnly = true)
    public List<SpaceDto> listSpaces(String propertySlug) {
        Property property = getPropertyOrThrow(propertySlug);
        List<Space> spaces = spaceRepository.findByPropertyId(property.getId());
        List<UUID> spaceIds = spaces.stream().map(Space::getId).toList();

        Map<UUID, List<Unit>> unitsBySpace = unitRepository.findBySpaceIdIn(spaceIds)
                .stream()
                .collect(Collectors.groupingBy(Unit::getSpaceId));

        return spaces.stream()
                .map(s -> {
                    List<Unit> units = unitsBySpace.getOrDefault(s.getId(), List.of());
                    int capacity = (int) units.stream().filter(Unit::getIsActive).count();
                    return SpaceDto.from(s, capacity, units);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public SpaceDto getSpace(String propertySlug, UUID spaceId) {
        Property property = getPropertyOrThrow(propertySlug);
        Space space = spaceRepository.findByPropertyIdAndId(property.getId(), spaceId)
                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
        List<Unit> units = unitRepository.findBySpaceId(space.getId());
        int capacity = (int) units.stream().filter(Unit::getIsActive).count();
        return SpaceDto.from(space, capacity, units);
    }

    @Transactional
    public SpaceDto createSpace(String propertySlug, CreateSpaceRequest request) {
        UUID tenantId = requireTenant();
        Property property = getPropertyOrThrow(propertySlug);

        if (request.units() == null || request.units().isEmpty()) {
            throw new IllegalArgumentException("A space must have at least one unit");
        }

        Space space = new Space(tenantId, property.getId(), request.name().trim(), request.floor());
        if (request.isActive() != null) {
            space.setIsActive(request.isActive());
        }
        Space savedSpace = spaceRepository.save(space);

        List<Unit> savedUnits = new ArrayList<>();
        for (CreateUnitRequest u : request.units()) {
            validateUnitKind(u.unitKind());
            Unit unit = new Unit(
                    tenantId,
                    savedSpace.getId(),
                    u.label().trim(),
                    u.unitKind().toUpperCase().trim()
            );
            if (u.isActive() != null) {
                unit.setIsActive(u.isActive());
            }
            savedUnits.add(unitRepository.save(unit));
        }

        int capacity = (int) savedUnits.stream().filter(Unit::getIsActive).count();
        return SpaceDto.from(savedSpace, capacity, savedUnits);
    }

    @Transactional
    public SpaceDto updateSpace(String propertySlug, UUID spaceId, UpdateSpaceRequest request) {
        UUID tenantId = requireTenant();
        Property property = getPropertyOrThrow(propertySlug);

        Space space = spaceRepository.findByPropertyIdAndId(property.getId(), spaceId)
                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));

        if (request.name() != null && !request.name().isBlank()) {
            space.setName(request.name().trim());
        }
        if (request.floor() != null) {
            space.setFloor(request.floor());
        }
        if (request.isActive() != null) {
            space.setIsActive(request.isActive());
        }
        Space savedSpace = spaceRepository.save(space);

        if (request.units() != null) {
            if (request.units().isEmpty()) {
                throw new IllegalArgumentException("A space must have at least one unit");
            }
            unitRepository.deleteBySpaceId(savedSpace.getId());
            List<Unit> newUnits = new ArrayList<>();
            for (CreateUnitRequest u : request.units()) {
                validateUnitKind(u.unitKind());
                Unit unit = new Unit(
                        tenantId,
                        savedSpace.getId(),
                        u.label().trim(),
                        u.unitKind().toUpperCase().trim()
                );
                if (u.isActive() != null) {
                    unit.setIsActive(u.isActive());
                }
                newUnits.add(unitRepository.save(unit));
            }
            int capacity = (int) newUnits.stream().filter(Unit::getIsActive).count();
            return SpaceDto.from(savedSpace, capacity, newUnits);
        }

        List<Unit> currentUnits = unitRepository.findBySpaceId(savedSpace.getId());
        int capacity = (int) currentUnits.stream().filter(Unit::getIsActive).count();
        return SpaceDto.from(savedSpace, capacity, currentUnits);
    }

    // --- Helpers & Validations ---

    private Property getPropertyOrThrow(String slug) {
        return propertyRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with slug: " + slug));
    }

    private UUID requireTenant() {
        return CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException("No authenticated tenant context"));
    }

    private void validateSaleMode(String saleMode) {
        if (saleMode == null || !VALID_SALE_MODES.contains(saleMode.toUpperCase().trim())) {
            throw new IllegalArgumentException("Invalid sale_mode: " + saleMode + ". Expected PER_UNIT or WHOLE.");
        }
    }

    private void validateKind(String kind) {
        if (kind == null || !VALID_KINDS.contains(kind.toUpperCase().trim())) {
            throw new IllegalArgumentException("Invalid kind: " + kind + ". Expected DORM or PRIVATE.");
        }
    }

    private void validateUnitKind(String unitKind) {
        if (unitKind == null || !VALID_UNIT_KINDS.contains(unitKind.toUpperCase().trim())) {
            throw new IllegalArgumentException("Invalid unit_kind: " + unitKind + ". Expected SINGLE, BUNK_TOP, BUNK_BOTTOM, or DOUBLE.");
        }
    }

    // --- DTOs ---

    public record RoomTypeDto(
            UUID id,
            UUID tenantId,
            UUID propertyId,
            String code,
            String name,
            String saleMode,
            String kind,
            Integer maxOccupancy,
            Long baseRateMinor,
            String description,
            Boolean isActive,
            List<UUID> spaceIds
    ) {
        public static RoomTypeDto from(RoomType rt, List<UUID> spaceIds) {
            return new RoomTypeDto(
                    rt.getId(),
                    rt.getTenantId(),
                    rt.getPropertyId(),
                    rt.getCode(),
                    rt.getName(),
                    rt.getSaleMode(),
                    rt.getKind(),
                    rt.getMaxOccupancy(),
                    rt.getBaseRateMinor(),
                    rt.getDescription(),
                    rt.getIsActive(),
                    spaceIds
            );
        }
    }

    public record CreateRoomTypeRequest(
            String code,
            String name,
            String saleMode,
            String kind,
            Integer maxOccupancy,
            Long baseRateMinor,
            String description,
            Boolean isActive
    ) {}

    public record UpdateRoomTypeRequest(
            String name,
            String saleMode,
            String kind,
            Integer maxOccupancy,
            Long baseRateMinor,
            String description,
            Boolean isActive
    ) {}

    public record UnitDto(
            UUID id,
            UUID tenantId,
            UUID spaceId,
            String label,
            String unitKind,
            Boolean isActive
    ) {
        public static UnitDto from(Unit u) {
            return new UnitDto(
                    u.getId(),
                    u.getTenantId(),
                    u.getSpaceId(),
                    u.getLabel(),
                    u.getUnitKind(),
                    u.getIsActive()
            );
        }
    }

    public record SpaceDto(
            UUID id,
            UUID tenantId,
            UUID propertyId,
            String name,
            String floor,
            Boolean isActive,
            Integer capacity,
            List<UnitDto> units
    ) {
        public static SpaceDto from(Space s, int capacity, List<Unit> units) {
            return new SpaceDto(
                    s.getId(),
                    s.getTenantId(),
                    s.getPropertyId(),
                    s.getName(),
                    s.getFloor(),
                    s.getIsActive(),
                    capacity,
                    units.stream().map(UnitDto::from).toList()
            );
        }
    }

    public record CreateUnitRequest(
            String label,
            String unitKind,
            Boolean isActive
    ) {}

    public record CreateSpaceRequest(
            String name,
            String floor,
            Boolean isActive,
            List<CreateUnitRequest> units
    ) {}

    public record UpdateSpaceRequest(
            String name,
            String floor,
            Boolean isActive,
            List<CreateUnitRequest> units
    ) {}
}
