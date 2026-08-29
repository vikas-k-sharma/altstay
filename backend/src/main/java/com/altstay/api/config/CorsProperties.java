package com.altstay.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties("altstay.cors")
@Validated
public record CorsProperties(
        List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of("http://localhost:3000") : List.copyOf(allowedOrigins);
    }
}
