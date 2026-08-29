package com.altstay.api.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@ConditionalOnProperty(name = "spring.datasource.url")
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return Health.up().build();
            }
            log.warn("Database health check returned invalid connection");
            return Health.down().withDetail("status", "Database unavailable").build();
        } catch (Exception ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return Health.down().withDetail("status", "Database unavailable").build();
        }
    }
}
