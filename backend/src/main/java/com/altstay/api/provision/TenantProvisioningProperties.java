package com.altstay.api.provision;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Arguments for one provisioning run (§10).
 *
 * <p><b>No defaults on anything that is a business fact.</b> {@code timezone} and
 * {@code currencyCode} are required for the reason §2 gives: a defaulted timezone is a wrong
 * answer that looks like a right one, and provisioning is precisely where a property's timezone is
 * decided. An earlier version defaulted them to {@code Asia/Kolkata} / {@code INR}, which meant a
 * run that simply forgot to say produced a property silently pinned to the wrong day boundary.
 *
 * <p><b>There is no {@code ownerPassword} property.</b> The password is read from the
 * {@code ALTSTAY_PROVISION_OWNER_PASSWORD} environment variable only. Binding it here would let it
 * be passed as {@code --altstay.provision.owner-password=...}, which puts a credential in the
 * process command line — visible to every other process on the machine — and in Spring's own
 * environment, where {@code /actuator/env} could surface it.
 */
@ConfigurationProperties(prefix = "altstay.provision")
@Validated
public record TenantProvisioningProperties(
        @NotBlank String tenantSlug,
        @NotBlank String tenantName,
        @NotBlank String ownerEmail,
        @NotBlank String propertyName,
        String propertySlug,
        @NotBlank(message = "altstay.provision.timezone is required and has no default: give an IANA zone id")
        String timezone,
        @NotBlank(message = "altstay.provision.currency-code is required and has no default: give an ISO 4217 code")
        String currencyCode,
        @NotNull @Min(0) @Max(10000) @DefaultValue("0") Integer taxRateBps
) {}
