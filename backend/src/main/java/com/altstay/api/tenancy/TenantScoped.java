package com.altstay.api.tenancy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as tenant-scoped. Operations on tenant-scoped components
 * require an active transaction and an authenticated tenant context, binding
 * {@code app.tenant_id} to the transaction's database connection.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface TenantScoped {
}
