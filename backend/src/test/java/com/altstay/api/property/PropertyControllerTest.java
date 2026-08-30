package com.altstay.api.property;

import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.config.SecurityConfig;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PropertyController.class, properties = "spring.datasource.url=jdbc:postgresql://slice-test/none")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TenantContextFilter.class})
class PropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropertyService propertyService;

    @Test
    @DisplayName("GET /api/v1/properties when unauthenticated returns 401 Unauthorized")
    void unauthenticatedPropertiesReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/properties when authenticated as FRONT_DESK returns 200 OK")
    void authenticatedFrontDeskCanListProperties() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Property prop = new Property(tenantId, "Sunset Lodge", "sunset-lodge", "Asia/Kolkata", "INR");
        prop.setId(UUID.randomUUID());
        when(propertyService.listProperties()).thenReturn(List.of(prop));
        when(propertyService.getPropertyAmenities(prop.getId())).thenReturn(List.of("WIFI", "AC"));

        mockMvc.perform(get("/api/v1/properties")
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sunset Lodge"))
                .andExpect(jsonPath("$[0].slug").value("sunset-lodge"))
                .andExpect(jsonPath("$[0].timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$[0].currencyCode").value("INR"))
                .andExpect(jsonPath("$[0].amenities[0]").value("WIFI"));
    }

    @Test
    @DisplayName("GET /api/v1/properties/{slug} when authenticated returns 200 OK")
    void authenticatedUserCanGetPropertyBySlug() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Property prop = new Property(tenantId, "Sunset Lodge", "sunset-lodge", "Asia/Kolkata", "INR");
        prop.setId(UUID.randomUUID());
        when(propertyService.getPropertyBySlug("sunset-lodge")).thenReturn(Optional.of(prop));
        when(propertyService.getPropertyAmenities(prop.getId())).thenReturn(List.of("WIFI"));

        mockMvc.perform(get("/api/v1/properties/sunset-lodge")
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sunset Lodge"))
                .andExpect(jsonPath("$.slug").value("sunset-lodge"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
    }

    @Test
    @DisplayName("POST /api/v1/properties when user has role FRONT_DESK returns 403 Forbidden")
    void frontDeskRoleIsRefusedOnOwnerEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/properties")
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Villa\",\"slug\":\"new-villa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/properties when user has role MANAGER returns 403 Forbidden")
    void managerRoleIsRefusedOnCreateProperty() throws Exception {
        mockMvc.perform(post("/api/v1/properties")
                        .with(user("mgr@sunset.com").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Villa\",\"slug\":\"new-villa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/properties when user has role OWNER returns 201 Created")
    void ownerRoleSucceedsOnOwnerEndpoint() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Property created = new Property(tenantId, "New Villa", "new-villa", "Asia/Kolkata", "INR");
        created.setId(UUID.randomUUID());
        when(propertyService.createProperty(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);
        when(propertyService.getPropertyAmenities(created.getId())).thenReturn(List.of("WIFI"));

        mockMvc.perform(post("/api/v1/properties")
                        .with(user("owner@sunset.com").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Villa\",\"slug\":\"new-villa\",\"timezone\":\"Asia/Kolkata\",\"currencyCode\":\"INR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Villa"))
                .andExpect(jsonPath("$.slug").value("new-villa"));
    }

    @Test
    @DisplayName("PUT /api/v1/properties/{slug} when user has role FRONT_DESK returns 403 Forbidden")
    void frontDeskRoleIsRefusedOnUpdateProperty() throws Exception {
        mockMvc.perform(put("/api/v1/properties/sunset-lodge")
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Villa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/properties/{slug} when user has role MANAGER returns 403 Forbidden")
    void managerRoleIsRefusedOnUpdateProperty() throws Exception {
        mockMvc.perform(put("/api/v1/properties/sunset-lodge")
                        .with(user("mgr@sunset.com").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Villa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/properties/{slug} when user has role OWNER returns 200 OK")
    void ownerRoleSucceedsOnUpdateProperty() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Property updated = new Property(tenantId, "Updated Villa", "sunset-lodge", "Asia/Kolkata", "INR");
        updated.setId(UUID.randomUUID());
        when(propertyService.updateProperty(eq("sunset-lodge"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updated);
        when(propertyService.getPropertyAmenities(updated.getId())).thenReturn(List.of("WIFI", "AIR_CONDITIONING"));

        mockMvc.perform(put("/api/v1/properties/sunset-lodge")
                        .with(user("owner@sunset.com").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Villa\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Villa"))
                .andExpect(jsonPath("$.slug").value("sunset-lodge"));
    }
}
