package com.altstay.api.inventory;

import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.config.SecurityConfig;
import com.altstay.api.inventory.InventoryService.CreateSpaceRequest;
import com.altstay.api.inventory.InventoryService.CreateUnitRequest;
import com.altstay.api.inventory.InventoryService.SpaceDto;
import com.altstay.api.inventory.InventoryService.UnitDto;
import com.altstay.api.inventory.InventoryService.UpdateSpaceRequest;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SpaceController.class, properties = "spring.datasource.url=jdbc:postgresql://slice-test/none")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TenantContextFilter.class})
class SpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/properties/{slug}/spaces unauthenticated returns 401 Unauthorized")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/properties/sunset-lodge/spaces"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/properties/{slug}/spaces as FRONT_DESK returns 200 OK")
    void frontDeskCanListSpaces() throws Exception {
        UnitDto unit = new UnitDto(UUID.randomUUID(), tenantId, spaceId, "101-A", "SINGLE", true);
        SpaceDto space = new SpaceDto(spaceId, tenantId, propertyId, "101", "1", true, 1, List.of(unit));
        when(inventoryService.listSpaces("sunset-lodge")).thenReturn(List.of(space));

        mockMvc.perform(get("/api/v1/properties/sunset-lodge/spaces")
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("101"))
                .andExpect(jsonPath("$[0].capacity").value(1))
                .andExpect(jsonPath("$[0].units[0].label").value("101-A"));
    }

    @Test
    @DisplayName("POST /api/v1/properties/{slug}/spaces as FRONT_DESK returns 403 Forbidden")
    void frontDeskCannotCreateSpace() throws Exception {
        mockMvc.perform(post("/api/v1/properties/sunset-lodge/spaces")
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"102\",\"units\":[{\"label\":\"102-A\",\"unitKind\":\"SINGLE\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/properties/{slug}/spaces as MANAGER returns 201 Created")
    void managerCanCreateSpace() throws Exception {
        UnitDto unit = new UnitDto(UUID.randomUUID(), tenantId, spaceId, "102-A", "SINGLE", true);
        SpaceDto space = new SpaceDto(spaceId, tenantId, propertyId, "102", "1", true, 1, List.of(unit));
        when(inventoryService.createSpace(eq("sunset-lodge"), any(CreateSpaceRequest.class))).thenReturn(space);

        mockMvc.perform(post("/api/v1/properties/sunset-lodge/spaces")
                        .with(user("mgr@sunset.com").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"102\",\"units\":[{\"label\":\"102-A\",\"unitKind\":\"SINGLE\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("102"))
                .andExpect(jsonPath("$.capacity").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/properties/{slug}/spaces/{id} as FRONT_DESK returns 403 Forbidden")
    void frontDeskCannotUpdateSpace() throws Exception {
        mockMvc.perform(put("/api/v1/properties/sunset-lodge/spaces/" + spaceId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"102-renamed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/properties/{slug}/spaces/{id} as OWNER returns 200 OK")
    void ownerCanUpdateSpace() throws Exception {
        UnitDto unit = new UnitDto(UUID.randomUUID(), tenantId, spaceId, "102-A", "SINGLE", true);
        SpaceDto space = new SpaceDto(spaceId, tenantId, propertyId, "102-renamed", "1", true, 1, List.of(unit));
        when(inventoryService.updateSpace(eq("sunset-lodge"), eq(spaceId), any(UpdateSpaceRequest.class))).thenReturn(space);

        mockMvc.perform(put("/api/v1/properties/sunset-lodge/spaces/" + spaceId)
                        .with(user("owner@sunset.com").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"102-renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("102-renamed"));
    }
}
