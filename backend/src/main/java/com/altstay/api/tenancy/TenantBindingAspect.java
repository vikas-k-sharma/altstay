package com.altstay.api.tenancy;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * AOP Aspect that binds the authenticated tenant ID to the active database connection
 * using PostgreSQL's {@code set_config('app.tenant_id', ?, true)}.
 *
 * <p>Ordering is critical: this aspect runs with {@link Ordered#LOWEST_PRECEDENCE}, placing it
 * <em>inside</em> the {@code @Transactional} interceptor (which is configured at order 100).
 * This ensures {@link DataSourceUtils#getConnection(DataSource)} obtains the exact connection
 * bound to the active transaction, making the setting transaction-local.
 *
 * <p>Fails fast and loudly if:
 * <ul>
 *   <li>The operation is invoked without an active transaction.</li>
 *   <li>The operation is invoked without an authenticated tenant context.</li>
 * </ul>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "spring.datasource.url")
public class TenantBindingAspect {

    private final DataSource dataSource;

    public TenantBindingAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("@annotation(com.altstay.api.tenancy.TenantScoped) || @within(com.altstay.api.tenancy.TenantScoped)")
    public Object bindTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Tenant-scoped operation must be executed within an active transaction");
        }

        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new MissingTenantException(
                        "Tenant-scoped operation invoked with no authenticated tenant context"));

        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            try (PreparedStatement ps = conn.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to bind tenant ID to database connection: " + tenantId, e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }

        return joinPoint.proceed();
    }
}
