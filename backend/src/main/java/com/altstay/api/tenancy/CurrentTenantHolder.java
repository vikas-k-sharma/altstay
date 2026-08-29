package com.altstay.api.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the current authenticated tenant context for the executing thread.
 *
 * <p>Writers are strictly package-private to {@code com.altstay.api.tenancy}: only
 * {@link TenantContextFilter} can bind a tenant to a thread, and it does so from an authenticated
 * principal. Application code outside this package cannot bind an arbitrary tenant, which is what
 * makes product-roadmap §4.1's "the tenant id must never be a client-supplied value" a property of
 * the code rather than of everyone remembering.
 *
 * <p>Tests bind a tenant through {@code TenantContextTestSupport}, which lives in this package
 * under {@code src/test} so that the helper is not shipped in production code.
 */
public final class CurrentTenantHolder {

    private static final ThreadLocal<UUID> TENANT_HOLDER = new ThreadLocal<>();

    private CurrentTenantHolder() {
    }

    static void set(UUID tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(TENANT_HOLDER.get());
    }

    static void clear() {
        TENANT_HOLDER.remove();
    }
}
