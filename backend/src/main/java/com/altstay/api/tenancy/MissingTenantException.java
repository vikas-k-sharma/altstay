package com.altstay.api.tenancy;

/**
 * Thrown when a tenant-scoped operation is invoked without an authenticated tenant context.
 */
public class MissingTenantException extends RuntimeException {

    public MissingTenantException(String message) {
        super(message);
    }
}
