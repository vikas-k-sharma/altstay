package com.altstay.api.tenancy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Tenancy and transaction interceptor configuration.
 *
 * <p>Sets transaction interceptor order to 100 so that {@link TenantBindingAspect}
 * (at {@code Ordered.LOWEST_PRECEDENCE}) runs strictly inside the active transaction boundary.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = 100)
@ConditionalOnProperty(name = "spring.datasource.url")
public class TenancyConfig {
}
