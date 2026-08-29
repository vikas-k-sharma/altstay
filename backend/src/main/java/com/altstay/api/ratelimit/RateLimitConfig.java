package com.altstay.api.ratelimit;

import com.altstay.api.config.RateLimitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the limiter beans explicitly rather than by component scan.
 *
 * <p>{@code SecurityConfig} imports this, so every {@code @WebMvcTest} slice that already imports
 * {@code SecurityConfig} to exercise the real filter chain gets the real limiter with it. The
 * alternative - leaving them as scanned {@code @Component}s and having {@code SecurityConfig} accept
 * their absence through an {@code ObjectProvider} - is the optional-dependency-with-a-null-branch
 * pattern that phase-4-foundations.md §3.7 finding 6 calls test scaffolding in production code.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter rateLimiter(RateLimitProperties properties) {
        return new RateLimiter(properties);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimiter rateLimiter) {
        return new RateLimitFilter(rateLimiter);
    }
}
