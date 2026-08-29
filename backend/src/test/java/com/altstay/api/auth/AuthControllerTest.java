package com.altstay.api.auth;

import com.altstay.api.auth.dto.AuthUserResponse;
import com.altstay.api.auth.dto.LoginRequest;
import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The controller is @ConditionalOnProperty on spring.datasource.url, so the slice must
// declare it. The value is never dialled: no DataSource is auto-configured in a @WebMvcTest.
@WebMvcTest(controllers = AuthController.class, properties = "spring.datasource.url=jdbc:postgresql://slice-test/none")
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("Valid login request returns 200 OK with AuthUserResponse")
    void validLoginReturns200AndUserResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AuthUserResponse response = new AuthUserResponse(
                userId,
                tenantId,
                "sunset-surf",
                "manager@sunset.com",
                "Manager Alice",
                Set.of(AuthRole.MANAGER)
        );

        when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(response);

        LoginRequest request = new LoginRequest("sunset-surf", "manager@sunset.com", "secret123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.tenantSlug").value("sunset-surf"))
                .andExpect(jsonPath("$.email").value("manager@sunset.com"))
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"));
    }

    @Test
    @DisplayName("Login with invalid credentials returns 401 Unauthorized ProblemDetail")
    void invalidLoginReturns401ProblemDetail() throws Exception {
        when(authService.login(any(LoginRequest.class), any(), any()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        LoginRequest request = new LoginRequest("sunset-surf", "wrong@sunset.com", "badpass");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/unauthorized"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Blank login fields return 400 Bad Request ProblemDetail")
    void blankLoginFieldsReturn400ProblemDetail() throws Exception {
        LoginRequest request = new LoginRequest("", "", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Logout returns 204 No Content")
    void logoutReturns204() throws Exception {
        doNothing().when(authService).logout(any(), any());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me when unauthenticated returns 401 Unauthorized")
    void unauthenticatedMeReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me when authenticated returns 200 with user profile")
    void authenticatedMeReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AuthUserResponse response = new AuthUserResponse(
                userId,
                tenantId,
                "sunset-surf",
                "owner@sunset.com",
                "Owner Bob",
                Set.of(AuthRole.OWNER)
        );

        when(authService.getCurrentUser(any())).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("owner@sunset.com").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@sunset.com"))
                .andExpect(jsonPath("$.tenantSlug").value("sunset-surf"))
                .andExpect(jsonPath("$.roles[0]").value("OWNER"));
    }
}
