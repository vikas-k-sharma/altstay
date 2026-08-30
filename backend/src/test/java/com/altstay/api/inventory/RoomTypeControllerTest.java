package com.altstay.api.inventory;

import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.config.SecurityConfig;
import com.altstay.api.inventory.InventoryService.CreateRoomTypeRequest;
import com.altstay.api.inventory.InventoryService.RoomTypeDto;
import com.altstay.api.inventory.InventoryService.UpdateRoomTypeRequest;
import com.altstay.api.tenancy.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoomTypeController.class, properties = "spring.datasource.url=jdbc:postgresql://slice-test/none")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TenantContextFilter.class})
class RoomTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    private final UUID rtId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/properties/{slug}/room-types unauthenticated returns 401 Unauthorized")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/properties/sunset-lodge/room-types"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/properties/{slug}/room-types as FRONT_DESK returns 200 OK")
    void frontDeskCanListRoomTypes() throws Exception {
        RoomTypeDto dto = new RoomTypeDto(rtId, tenantId, propertyId, "DORM6MIX", "6-bed dorm", "PER_UNIT", "DORM", 6, 60000L, null, true, List.of());
        when(inventoryService.listRoomTypes("sunset-lodge")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/properties/sunset-lodge/room-types")
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("DORM6MIX"))
                .andExpect(jsonPath("$[0].saleMode").value("PER_UNIT"));
    }

    @Test
    @DisplayName("POST /api/v1/properties/{slug}/room-types as FRONT_DESK returns 403 Forbidden")
    void frontDeskCannotCreateRoomType() throws Exception {
        mockMvc.perform(post("/api/v1/properties/sunset-lodge/room-types")
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DORM6\",\"name\":\"Dorm 6\",\"saleMode\":\"PER_UNIT\",\"kind\":\"DORM\",\"maxOccupancy\":6,\"baseRateMinor\":50000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/properties/{slug}/room-types as MANAGER returns 201 Created")
    void managerCanCreateRoomType() throws Exception {
        RoomTypeDto dto = new RoomTypeDto(rtId, tenantId, propertyId, "DORM6", "Dorm 6", "PER_UNIT", "DORM", 6, 50000L, null, true, List.of());
        when(inventoryService.createRoomType(eq("sunset-lodge"), any(CreateRoomTypeRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/properties/sunset-lodge/room-types")
                        .with(user("mgr@sunset.com").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DORM6\",\"name\":\"Dorm 6\",\"saleMode\":\"PER_UNIT\",\"kind\":\"DORM\",\"maxOccupancy\":6,\"baseRateMinor\":50000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("DORM6"));
    }

    @Test
    @DisplayName("PUT /api/v1/properties/{slug}/room-types/{id} as FRONT_DESK returns 403 Forbidden")
    void frontDeskCannotUpdateRoomType() throws Exception {
        mockMvc.perform(put("/api/v1/properties/sunset-lodge/room-types/" + rtId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Dorm\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/properties/{slug}/room-types/{id} as OWNER returns 200 OK")
    void ownerCanUpdateRoomType() throws Exception {
        RoomTypeDto dto = new RoomTypeDto(rtId, tenantId, propertyId, "DORM6", "Updated Dorm", "PER_UNIT", "DORM", 6, 50000L, null, true, List.of());
        when(inventoryService.updateRoomType(eq("sunset-lodge"), eq(rtId), any(UpdateRoomTypeRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/properties/sunset-lodge/room-types/" + rtId)
                        .with(user("owner@sunset.com").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Dorm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Dorm"));
    }

    @Test
    @DisplayName("POST /api/v1/room-types/{id}/spaces/{spaceId} as FRONT_DESK returns 403 Forbidden")
    void frontDeskCannotAssociateSpace() throws Exception {
        UUID spaceId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/room-types/" + rtId + "/spaces/" + spaceId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/room-types/{id}/spaces/{spaceId} as MANAGER returns 201 Created")
    void managerCanAssociateSpace() throws Exception {
        UUID spaceId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/room-types/" + rtId + "/spaces/" + spaceId)
                        .with(user("mgr@sunset.com").roles("MANAGER")))
                .andExpect(status().isCreated());

        verify(inventoryService).associateSpace(rtId, spaceId);
    }

    @Test
    @DisplayName("DELETE /api/v1/room-types/{id}/spaces/{spaceId} as FRONT_DESK returns 403 Forbidden")
    void frontDeskCannotDissociateSpace() throws Exception {
        UUID spaceId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/room-types/" + rtId + "/spaces/" + spaceId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/room-types/{id}/spaces/{spaceId} as OWNER returns 204 No Content")
    void ownerCanDissociateSpace() throws Exception {
        UUID spaceId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/room-types/" + rtId + "/spaces/" + spaceId)
                        .with(user("owner@sunset.com").roles("OWNER")))
                .andExpect(status().isNoContent());

        verify(inventoryService).dissociateSpace(rtId, spaceId);
    }
}
