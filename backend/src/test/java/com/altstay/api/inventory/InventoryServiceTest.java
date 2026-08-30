package com.altstay.api.inventory;

import com.altstay.api.inventory.InventoryService.CreateRoomTypeRequest;
import com.altstay.api.inventory.InventoryService.CreateSpaceRequest;
import com.altstay.api.inventory.InventoryService.CreateUnitRequest;
import com.altstay.api.inventory.InventoryService.RoomTypeDto;
import com.altstay.api.inventory.InventoryService.SpaceDto;
import com.altstay.api.inventory.InventoryService.UpdateRoomTypeRequest;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyRepository;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private RoomTypeRepository roomTypeRepository;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private RoomTypeSpaceRepository roomTypeSpaceRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private Property property;

    @BeforeEach
    void setUp() {
        property = new Property(tenantId, "Sunset Lodge", "sunset-lodge", "Asia/Kolkata", "INR");
        property.setId(propertyId);
    }

    @Test
    @DisplayName("createSpace without units is refused (every space must have at least one unit)")
    void createSpaceWithoutUnitsIsRefused() {
        TenantContextTestSupport.runAs(tenantId, () -> {
            when(propertyRepository.findBySlug("sunset-lodge")).thenReturn(Optional.of(property));

            CreateSpaceRequest reqEmpty = new CreateSpaceRequest("Room 101", "1", true, List.of());
            assertThatThrownBy(() -> inventoryService.createSpace("sunset-lodge", reqEmpty))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one unit");

            CreateSpaceRequest reqNull = new CreateSpaceRequest("Room 101", "1", true, null);
            assertThatThrownBy(() -> inventoryService.createSpace("sunset-lodge", reqNull))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one unit");
        });
    }

    @Test
    @DisplayName("createSpace with units succeeds and derives active capacity")
    void createSpaceWithUnitsSucceeds() {
        TenantContextTestSupport.runAs(tenantId, () -> {
            when(propertyRepository.findBySlug("sunset-lodge")).thenReturn(Optional.of(property));
            UUID spaceId = UUID.randomUUID();
            when(spaceRepository.save(any(Space.class))).thenAnswer(inv -> {
                Space s = inv.getArgument(0);
                s.setId(spaceId);
                return s;
            });

            Unit u1 = new Unit(tenantId, spaceId, "101-A", "BUNK_TOP");
            u1.setId(UUID.randomUUID());
            u1.setIsActive(true);

            Unit u2 = new Unit(tenantId, spaceId, "101-B", "BUNK_BOTTOM");
            u2.setId(UUID.randomUUID());
            u2.setIsActive(false);

            when(unitRepository.save(any(Unit.class))).thenReturn(u1, u2);

            CreateSpaceRequest req = new CreateSpaceRequest("Dorm 101", "1", true, List.of(
                    new CreateUnitRequest("101-A", "BUNK_TOP", true),
                    new CreateUnitRequest("101-B", "BUNK_BOTTOM", false)
            ));

            SpaceDto dto = inventoryService.createSpace("sunset-lodge", req);
            assertThat(dto.name()).isEqualTo("Dorm 101");
            assertThat(dto.capacity()).isEqualTo(1); // Only active unit counted
            assertThat(dto.units()).hasSize(2);
        });
    }

    @Test
    @DisplayName("createRoomType validates sale_mode, kind, occupancy and rate")
    void createRoomTypeValidations() {
        TenantContextTestSupport.runAs(tenantId, () -> {
            when(propertyRepository.findBySlug("sunset-lodge")).thenReturn(Optional.of(property));

            assertThatThrownBy(() -> inventoryService.createRoomType("sunset-lodge",
                    new CreateRoomTypeRequest("DORM", "Dorm", "INVALID_MODE", "DORM", 6, 50000L, null, true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sale_mode");

            assertThatThrownBy(() -> inventoryService.createRoomType("sunset-lodge",
                    new CreateRoomTypeRequest("DORM", "Dorm", "PER_UNIT", "INVALID_KIND", 6, 50000L, null, true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("kind");

            assertThatThrownBy(() -> inventoryService.createRoomType("sunset-lodge",
                    new CreateRoomTypeRequest("DORM", "Dorm", "PER_UNIT", "DORM", 0, 50000L, null, true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxOccupancy");

            assertThatThrownBy(() -> inventoryService.createRoomType("sunset-lodge",
                    new CreateRoomTypeRequest("DORM", "Dorm", "PER_UNIT", "DORM", 6, -100L, null, true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseRateMinor");
        });
    }

    @Test
    @DisplayName("associateSpace refuses join between room type and space of different properties")
    void associateSpaceRefusesCrossPropertyJoin() {
        TenantContextTestSupport.runAs(tenantId, () -> {
            UUID rtId = UUID.randomUUID();
            UUID spaceId = UUID.randomUUID();
            UUID otherPropId = UUID.randomUUID();

            RoomType rt = new RoomType(tenantId, propertyId, "DORM", "Dorm", "PER_UNIT", "DORM", 6, 50000L);
            rt.setId(rtId);
            Space space = new Space(tenantId, otherPropId, "101", "1");
            space.setId(spaceId);

            when(roomTypeRepository.findById(rtId)).thenReturn(Optional.of(rt));
            when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));

            assertThatThrownBy(() -> inventoryService.associateSpace(rtId, spaceId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same property");
        });
    }

    @Test
    @DisplayName("associateSpace saves mapping when properties match")
    void associateSpaceSavesMapping() {
        TenantContextTestSupport.runAs(tenantId, () -> {
            UUID rtId = UUID.randomUUID();
            UUID spaceId = UUID.randomUUID();

            RoomType rt = new RoomType(tenantId, propertyId, "DORM", "Dorm", "PER_UNIT", "DORM", 6, 50000L);
            rt.setId(rtId);
            Space space = new Space(tenantId, propertyId, "101", "1");
            space.setId(spaceId);

            when(roomTypeRepository.findById(rtId)).thenReturn(Optional.of(rt));
            when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
            when(roomTypeSpaceRepository.findByRoomTypeIdAndSpaceId(rtId, spaceId)).thenReturn(Optional.empty());

            inventoryService.associateSpace(rtId, spaceId);

            verify(roomTypeSpaceRepository).save(any(RoomTypeSpace.class));
        });
    }
}
