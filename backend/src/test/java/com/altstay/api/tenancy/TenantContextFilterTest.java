package com.altstay.api.tenancy;

import com.altstay.api.auth.AuthRole;
import com.altstay.api.auth.TenantUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CurrentTenantHolder.clear();
    }

    @Test
    @DisplayName("TenantContextFilter binds authenticated principal's tenantId into CurrentTenantHolder and clears on completion")
    void bindsAuthenticatedTenantAndClearsOnCompletion() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantUserDetails principal = new TenantUserDetails(
                userId,
                tenantId,
                "sunset-surf",
                "manager@sunset.com",
                "hash",
                "Manager",
                true,
                Set.of(AuthRole.MANAGER)
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);

        AtomicReference<UUID> boundDuringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> boundDuringRequest.set(CurrentTenantHolder.get().orElse(null));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(boundDuringRequest.get()).isEqualTo(tenantId);
        assertThat(CurrentTenantHolder.get()).isEmpty();
    }

    @Test
    @DisplayName("TenantContextFilter leaves CurrentTenantHolder empty for unauthenticated requests")
    void leavesCurrentTenantHolderEmptyForUnauthenticatedRequests() throws ServletException, IOException {
        SecurityContextHolder.clearContext();

        AtomicReference<Boolean> wasEmptyDuringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> wasEmptyDuringRequest.set(CurrentTenantHolder.get().isEmpty());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(wasEmptyDuringRequest.get()).isTrue();
        assertThat(CurrentTenantHolder.get()).isEmpty();
    }
}
