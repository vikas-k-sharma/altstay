package com.altstay.api.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Test-only writer for {@link CurrentTenantHolder}.
 *
 * <p>Lives in {@code src/test} and in the tenancy package so it can reach the holder's
 * package-private writers. Production code has exactly one writer — {@link TenantContextFilter},
 * driven by an authenticated principal — and this helper exists so that keeping it that way does
 * not require putting a test affordance into the shipped jar.
 */
public final class TenantContextTestSupport {

    private TenantContextTestSupport() {
    }

    /** Runs {@code action} with {@code tenantId} bound, restoring any previous binding after. */
    public static <T> T runAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CurrentTenantHolder.get().orElse(null);
        try {
            CurrentTenantHolder.set(tenantId);
            return action.get();
        } finally {
            if (previous != null) {
                CurrentTenantHolder.set(previous);
            } else {
                CurrentTenantHolder.clear();
            }
        }
    }

    public static void runAs(UUID tenantId, Runnable action) {
        runAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
