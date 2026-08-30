package com.altstay.api.provision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=optional:file:./.env.properties",
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=${ALTSTAY_DB_URL}",
        "spring.datasource.username=${ALTSTAY_DB_USER}",
        "spring.datasource.password=${ALTSTAY_DB_PASSWORD}",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
class TenantProvisioningIT {

    @Autowired
    private TenantProvisioningService provisioningService;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("Provision new tenant and property, then successfully log in via HTTP as created owner")
    void provisionNewTenantAndLoginAsOwner() throws Exception {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        String tenantSlug = "prov-" + randomSuffix;
        String ownerEmail = "owner-" + randomSuffix + "@example.com";
        String password = "SecretPassword123!";
        String propertySlug = "prop-" + randomSuffix;

        var props = new TenantProvisioningProperties(
                tenantSlug,
                "Provisioned Resort " + randomSuffix,
                ownerEmail,
                "Goa Beach Property",
                propertySlug,
                "Asia/Kolkata",
                "INR",
                1800
        );

        // 1. Execute provisioning
        var result = provisioningService.provisionTenant(props, password);
        assertThat(result.tenantSlug()).isEqualTo(tenantSlug);
        assertThat(result.ownerEmail()).isEqualTo(ownerEmail);

        // 2. Perform HTTP login with provisioned credentials
        var loginPayload = Map.of(
                "tenantSlug", tenantSlug,
                "email", ownerEmail,
                "password", password
        );

        var mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(result.ownerUserId().toString()))
                .andExpect(jsonPath("$.tenantId").value(result.tenantId().toString()))
                .andExpect(jsonPath("$.tenantSlug").value(tenantSlug))
                .andExpect(jsonPath("$.email").value(ownerEmail))
                .andExpect(jsonPath("$.roles[0]").value("OWNER"))
                .andReturn();

        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) mvcResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // 3. Access authenticated endpoint with owner session
        mockMvc.perform(get("/api/v1/properties/" + propertySlug + "/rate-plans").session(session))
                .andExpect(status().isOk());
    }
}
