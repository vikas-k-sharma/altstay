package com.altstay.api.ratelimit;

import com.altstay.api.auth.TenantUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * Applies {@link RateLimiter} to the chat endpoint, returning 429 with {@code Retry-After}.
 *
 * <p>Registered inside the Spring Security chain (see {@code SecurityConfig}) rather than the
 * servlet container's, so an authenticated request has its principal available and buckets on its
 * tenant rather than on the client-supplied session header.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CHAT_PATH = "/api/v1/chat";
    private static final String HEADER_SESSION = "x-altstay-session";
    private static final String PROBLEM_TYPE = "https://api.altstay.com/errors/rate-limited";

    /**
     * Private to this filter, not the application's shared instance. The one shape written here is
     * a {@link ProblemDetail}, whose serialization must not shift with whatever Jackson
     * customization the rest of the application acquires later.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !CHAT_PATH.equals(path) || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        UUID tenantId = extractTenantId();
        String sessionId = request.getHeader(HEADER_SESSION);

        ConsumptionResult result = rateLimiter.tryConsume(tenantId, sessionId);
        if (!result.allowed()) {
            log.warn("Rate limit exceeded: tenantId={}, retryAfterSeconds={}",
                    tenantId, result.retryAfterSeconds());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "One moment — catching up."
            );
            problemDetail.setType(URI.create(PROBLEM_TYPE));
            problemDetail.setTitle("Too Many Requests");
            problemDetail.setInstance(URI.create(request.getRequestURI()));

            OBJECT_MAPPER.writeValue(response.getOutputStream(), problemDetail);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * The {@code x-altstay-session} header is deliberately not consulted here. It is a bucket key
     * and nothing else - it is not identity, it is not a tenant, and it never reaches
     * {@code CurrentTenantHolder}.
     */
    private UUID extractTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantUserDetails userDetails) {
            return userDetails.getTenantId();
        }
        return null;
    }
}
