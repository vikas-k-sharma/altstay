package com.altstay.api.tenancy;

import com.altstay.api.auth.TenantUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that reads the authenticated {@link TenantUserDetails} from Spring Security's
 * {@link SecurityContextHolder} and binds the tenant ID into {@link CurrentTenantHolder}
 * for the lifetime of the request.
 *
 * <p>This filter is the single authoritative writer for {@link CurrentTenantHolder} in production request flows.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof TenantUserDetails principal) {
                CurrentTenantHolder.set(principal.getTenantId());
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentTenantHolder.clear();
        }
    }
}
