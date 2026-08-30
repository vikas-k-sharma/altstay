package com.altstay.api.provision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("provision")
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(TenantProvisioningProperties.class)
@ConditionalOnProperty(name = "spring.datasource.url")
public class TenantProvisioningRunner implements ApplicationRunner {

    private final TenantProvisioningProperties properties;
    private final TenantProvisioningService provisioningService;

    /** The one place the owner password is read. Environment only — never a bound property. */
    static final String OWNER_PASSWORD_ENV = "ALTSTAY_PROVISION_OWNER_PASSWORD";

    @Override
    public void run(ApplicationArguments args) {
        // Environment variable only, deliberately. A --altstay.provision.owner-password argument
        // would put the credential in the process command line, where any other process on the
        // machine can read it, and into Spring's Environment, where /actuator/env could surface it.
        String password = System.getenv(OWNER_PASSWORD_ENV);

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Provisioning requires the owner password in the " + OWNER_PASSWORD_ENV
                            + " environment variable. It is deliberately not accepted as a command-line argument.");
        }

        var result = provisioningService.provisionTenant(properties, password);
        System.out.println("================================================================================");
        System.out.println("ALTSTAY TENANT PROVISIONED SUCCESSFULLY");
        System.out.printf("  Tenant ID:     %s%n", result.tenantId());
        System.out.printf("  Tenant Slug:   %s%n", result.tenantSlug());
        System.out.printf("  Owner Email:   %s%n", result.ownerEmail());
        System.out.printf("  Property ID:   %s%n", result.propertyId());
        System.out.printf("  Property Slug: %s%n", result.propertySlug());
        System.out.println("================================================================================");
    }
}
