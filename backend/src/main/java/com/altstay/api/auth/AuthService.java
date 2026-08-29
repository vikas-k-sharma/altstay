package com.altstay.api.auth;

import com.altstay.api.auth.dto.AuthUserResponse;
import com.altstay.api.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantDirectoryRepository tenantDirectoryRepository;
    private final UserAccountService userAccountService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthService(TenantDirectoryRepository tenantDirectoryRepository,
                       UserAccountService userAccountService) {
        this.tenantDirectoryRepository = tenantDirectoryRepository;
        this.userAccountService = userAccountService;
    }

    /**
     * Authenticates a user against a tenant workspace slug.
     *
     * 1. Resolves tenantId from tenant_directory (unprotected lookup, no PII).
     * 2. Delegates to UserAccountService, which binds app.tenant_id on the transaction's
     *    connection so the user and role queries execute under RLS. CurrentTenantHolder is NOT
     *    used here: it is written only by TenantContextFilter, from an already-authenticated
     *    principal, and login is by definition the request that has no principal yet.
     * 3. Verifies credentials and loads authorities.
     * 4. Stores authenticated SecurityContext in HttpSession (httpOnly cookie).
     */
    @Transactional(readOnly = true)
    public AuthUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        TenantDirectory directory = tenantDirectoryRepository.findBySlug(request.tenantSlug())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        UUID tenantId = directory.getTenantId();

        TenantUserDetails principal = userAccountService.authenticate(
                tenantId, directory.getSlug(), request.email(), request.password());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        log.info("Authentication successful: tenantSlug={}, userId={}", directory.getSlug(), principal.getUserId());

        return new AuthUserResponse(
                principal.getUserId(),
                principal.getTenantId(),
                principal.getTenantSlug(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRoles()
        );
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public Optional<AuthUserResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantUserDetails principal)) {
            return Optional.empty();
        }
        return Optional.of(new AuthUserResponse(
                principal.getUserId(),
                principal.getTenantId(),
                principal.getTenantSlug(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRoles()
        ));
    }
}
