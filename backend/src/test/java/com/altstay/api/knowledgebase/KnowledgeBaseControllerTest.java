package com.altstay.api.knowledgebase;

import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.config.SecurityConfig;
import com.altstay.api.knowledgebase.dto.KnowledgeBaseVersionResponse;
import com.altstay.api.knowledgebase.dto.SaveKnowledgeBaseRequest;
import com.altstay.api.tenancy.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KnowledgeBaseController.class, properties = "spring.datasource.url=jdbc:postgresql://slice-test/none")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TenantContextFilter.class})
class KnowledgeBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeBaseService knowledgeBaseService;

    private final UUID propertyId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID kbId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/properties/{propertyId}/knowledge-base unauthenticated returns 401 Unauthorized")
    void unauthenticatedKnowledgeBaseReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/properties/{propertyId}/knowledge-base", propertyId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/properties/{propertyId}/knowledge-base when authenticated returns 200 OK with version")
    void getCurrent_whenAuthenticated_returns200() throws Exception {
        KnowledgeBaseVersion version = new KnowledgeBaseVersion(
                tenantId, kbId, 1, "House rules", "sha256", 11, userId
        );
        version.setId(versionId);
        when(knowledgeBaseService.getCurrent(propertyId)).thenReturn(Optional.of(version));

        mockMvc.perform(get("/api/v1/properties/{propertyId}/knowledge-base", propertyId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.content").value("House rules"))
                .andExpect(jsonPath("$.contentSha256").value("sha256"))
                .andExpect(jsonPath("$.charCount").value(11));
    }

    @Test
    @DisplayName("GET /api/v1/properties/{propertyId}/knowledge-base when not found returns 404 Not Found")
    void getCurrent_whenNotFound_returns404() throws Exception {
        when(knowledgeBaseService.getCurrent(propertyId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/properties/{propertyId}/knowledge-base", propertyId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/properties/{propertyId}/knowledge-base when authenticated returns 200 OK with version")
    void save_whenAuthenticated_returns200() throws Exception {
        KnowledgeBaseVersion version = new KnowledgeBaseVersion(
                tenantId, kbId, 2, "Updated rules", "newsha", 13, userId
        );
        version.setId(versionId);
        when(knowledgeBaseService.save(eq(propertyId), eq("Updated rules"))).thenReturn(version);

        mockMvc.perform(post("/api/v1/properties/{propertyId}/knowledge-base", propertyId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Updated rules\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.content").value("Updated rules"));
    }

    @Test
    @DisplayName("POST /api/v1/properties/{propertyId}/knowledge-base when content is blank returns 400 Bad Request")
    void save_whenContentIsBlank_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/properties/{propertyId}/knowledge-base", propertyId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/validation-error"))
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    @DisplayName("POST /api/v1/properties/{propertyId}/knowledge-base when content exceeds 20000 chars returns 400 Bad Request")
    void save_whenContentExceeds20000Chars_returns400() throws Exception {
        String largeContent = "a".repeat(20001);
        mockMvc.perform(post("/api/v1/properties/{propertyId}/knowledge-base", propertyId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + largeContent + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/validation-error"))
                .andExpect(jsonPath("$.errors.content").value("content must not exceed 20000 characters"));
    }

    @Test
    @DisplayName("POST /api/v1/properties/{propertyId}/knowledge-base on persistent conflict returns 409 Conflict ProblemDetail")
    void save_onConflict_returns409() throws Exception {
        when(knowledgeBaseService.save(eq(propertyId), anyString()))
                .thenThrow(new KnowledgeBaseConflictException("Someone else saved first"));

        mockMvc.perform(post("/api/v1/properties/{propertyId}/knowledge-base", propertyId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Concurrent save\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/knowledge-base-conflict"))
                .andExpect(jsonPath("$.title").value("Knowledge Base Conflict"))
                .andExpect(jsonPath("$.detail").value("Someone else saved first"));
    }

    @Test
    @DisplayName("GET /api/v1/properties/{propertyId}/knowledge-base/history returns 200 OK with history list")
    void history_whenAuthenticated_returns200() throws Exception {
        KnowledgeBaseVersion v1 = new KnowledgeBaseVersion(tenantId, kbId, 1, "V1", "sha1", 2, userId);
        v1.setId(UUID.randomUUID());
        KnowledgeBaseVersion v2 = new KnowledgeBaseVersion(tenantId, kbId, 2, "V2", "sha2", 2, userId);
        v2.setId(UUID.randomUUID());

        when(knowledgeBaseService.history(eq(propertyId), anyInt())).thenReturn(List.of(v2, v1));

        mockMvc.perform(get("/api/v1/properties/{propertyId}/knowledge-base/history", propertyId)
                        .with(user("staff@sunset.com").roles("FRONT_DESK"))
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[1].versionNo").value(1));
    }
}
