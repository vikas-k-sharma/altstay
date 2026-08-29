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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The controller is @ConditionalOnProperty on spring.datasource.url, so the slice must
// declare it. The value is never dialled: no DataSource is auto-configured in a @WebMvcTest.
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
        Property prop = new Property(tenantId, "Sunset Lodge", "sunset-lodge");
        when(propertyService.listProperties()).thenReturn(List.of(prop));

        mockMvc.perform(get("/api/v1/properties")
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sunset Lodge"))
                .andExpect(jsonPath("$[0].slug").value("sunset-lodge"));
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
    @DisplayName("POST /api/v1/properties when user has role OWNER returns 201 Created")
    void ownerRoleSucceedsOnOwnerEndpoint() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Property created = new Property(tenantId, "New Villa", "new-villa");
        when(propertyService.createProperty(anyString(), anyString())).thenReturn(created);

        mockMvc.perform(post("/api/v1/properties")
                        .with(user("owner@sunset.com").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Villa\",\"slug\":\"new-villa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Villa"))
                .andExpect(jsonPath("$.slug").value("new-villa"));
    }
}
