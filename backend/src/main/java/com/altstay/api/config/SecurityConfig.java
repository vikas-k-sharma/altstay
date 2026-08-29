package com.altstay.api.config;

import com.altstay.api.ratelimit.RateLimitConfig;
import com.altstay.api.ratelimit.RateLimitFilter;
import com.altstay.api.tenancy.TenantContextFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(RateLimitProperties.class)
@Import(RateLimitConfig.class)
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(TenantContextFilter tenantContextFilter, RateLimitFilter rateLimitFilter) {
        this.tenantContextFilter = tenantContextFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Phase 4 §3.4: CSRF is disabled for the API only. The browser never calls Spring
                // directly - every browser-originated request goes to the Next.js BFF, which calls
                // this API server-to-server, so CSRF is the BFF's problem for those paths. That is a
                // load-bearing invariant, not a convention: if a browser ever gains a direct route
                // to Spring, this decision is void. Everything outside /api/v1/** keeps CSRF on.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**"))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/chat").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/properties/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterAfter(tenantContextFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(rateLimitFilter, TenantContextFilter.class);

        return http.build();
    }

    /**
     * Phase 4 §3.3's session cookie contract, declared in code rather than in
     * {@code application.yaml}.
     *
     * <p>This is not a style preference. {@code src/test/resources/application.yaml} replaces the
     * main {@code application.yaml} wholesale on the test classpath, so anything configured only in
     * the main file is invisible to every test in this repo - which is exactly how "an httpOnly
     * session cookie" stayed a sentence in a plan rather than an asserted property. A bean is on the
     * classpath for tests and production alike, so {@code AuthLoginIT} can assert it on the wire.
     */
    @Bean
    public CookieSameSiteSupplier sessionCookieSameSiteSupplier() {
        return CookieSameSiteSupplier.ofStrict().whenHasName("JSESSIONID");
    }

    /**
     * Suppresses Boot's automatic registration of {@link TenantContextFilter} in the servlet
     * container's own filter chain.
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Suppresses Boot's automatic registration of {@link RateLimitFilter} in the servlet
     * container's own filter chain.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
