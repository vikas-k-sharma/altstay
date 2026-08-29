package com.altstay.api.tenancy;

import com.altstay.api.auth.AuthRole;
import com.altstay.api.auth.TenantUserDetails;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test asserting application-level tenant binding via Spring AOP aspect,
 * {@link TenantContextFilter}, and PostgreSQL Row-Level Security.
 *
 * <p>Verifies the 5 requirements of Phase 4 §2.4:
 * <ol>
 *   <li>A service method invoked as Tenant A cannot read Tenant B's rows through a JPA repository with no WHERE clause.</li>
 *   <li>A tenant-scoped call with no principal / context throws {@link MissingTenantException} and returns no rows.</li>
 *   <li>Real HTTP request with client-supplied tenant ID (header/route) cannot escalate access to Tenant B (anti-escalation).</li>
 *   <li>Sequential requests as different tenants on the same pool connection do not leak or bleed state.</li>
 *   <li>The aspect runs strictly inside the {@code @Transactional} boundary.</li>
 * </ol>
 *
 * <p>Gated on {@code ALTSTAY_DB_TESTS=true}, mirroring {@link TenantIsolationIT}.
 */
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.config.import=optional:file:./.env.properties",
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=${ALTSTAY_DB_URL}",
        "spring.datasource.username=${ALTSTAY_DB_USER}",
        "spring.datasource.password=${ALTSTAY_DB_PASSWORD}",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false"
})
@Import(TenantBindingIT.NonTransactionalTestConfig.class)
class TenantBindingIT {

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Autowired
    private NonTransactionalService nonTransactionalService;

    private static String url;
    private static String user;
    private static String password;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();

    private static final String SLUG_A = "bind-a-" + TENANT_A.toString().substring(0, 8);
    private static final String SLUG_B = "bind-b-" + TENANT_B.toString().substring(0, 8);

    private static TenantUserDetails tenantAUser;

    @BeforeAll
    static void loadCredentialsAndSeed() throws IOException, SQLException {
        Properties p = new Properties();
        Path local = Path.of(".env.properties");
        if (Files.exists(local)) {
            try (var in = Files.newInputStream(local)) {
                p.load(in);
            }
        }
        url = resolve(p, "ALTSTAY_DB_URL");
        user = resolve(p, "ALTSTAY_DB_USER");
        password = resolve(p, "ALTSTAY_DB_PASSWORD");

        assertThat(url).as("ALTSTAY_DB_URL must be set to run DB tests").isNotBlank();

        tenantAUser = new TenantUserDetails(
                USER_A,
                TENANT_A,
                SLUG_A,
                "owner@" + SLUG_A + ".com",
                "hash",
                "Owner A",
                true,
                Set.of(AuthRole.OWNER)
        );

        try (Connection c = open()) {
            seedTenant(c, TENANT_A, SLUG_A);
            seedTenant(c, TENANT_B, SLUG_B);
            c.commit();
        }
    }

    private static String resolve(Properties file, String key) {
        String fromEnv = System.getenv(key);
        return fromEnv != null && !fromEnv.isBlank() ? fromEnv : file.getProperty(key, "");
    }

    private static Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url, user, password);
        c.setAutoCommit(false);
        return c;
    }

    private static void seedTenant(Connection c, UUID tenant, String slug) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "insert into tenant (id, name, slug) values (?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "Binding Test " + slug);
            ps.setString(3, slug);
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "insert into property (tenant_id, name, slug) values (?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "Property " + slug);
            ps.setString(3, slug);
            ps.execute();
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        CurrentTenantHolder.clear();
        try (Connection c = open()) {
            for (UUID t : List.of(TENANT_A, TENANT_B)) {
                try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, t.toString());
                    ps.execute();
                }
                try (PreparedStatement ps = c.prepareStatement("delete from tenant where id = ?")) {
                    ps.setObject(1, t);
                    ps.execute();
                }
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("1. A service method invoked as Tenant A cannot read Tenant B's rows through a JPA repository with no WHERE clause")
    void boundTenantSeesOnlyItsOwnRowsThroughRepository() {
        List<Property> properties = TenantContextTestSupport.runAs(TENANT_A, () -> propertyService.listProperties());

        assertThat(properties)
                .as("Tenant A must see exactly its own property via JPA repository")
                .hasSize(1);
        assertThat(properties.get(0).getSlug()).isEqualTo(SLUG_A);
        assertThat(properties.get(0).getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("2. A tenant-scoped call with no principal throws MissingTenantException and returns no rows")
    void unboundCallThrowsMissingTenantException() {
        CurrentTenantHolder.clear();

        assertThatThrownBy(() -> propertyService.listProperties())
                .as("A tenant-scoped operation with no principal must fail fast and loudly")
                .isInstanceOf(MissingTenantException.class)
                .hasMessageContaining("no authenticated tenant context");
    }

    @Test
    @DisplayName("3. HTTP boundary anti-escalation: client headers, route params, or query params cannot access Tenant B")
    void httpBoundaryRefusesTenantEscalation() throws Exception {
        // Authenticated as Tenant A, attempting to pass Tenant B in header and query param
        mockMvc.perform(get("/api/v1/properties")
                        .with(user(tenantAUser))
                        .header("X-Tenant-Id", TENANT_B.toString())
                        .param("tenantId", TENANT_B.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value(SLUG_A))
                .andExpect(jsonPath("$[0].tenantId").value(TENANT_A.toString()));

        // Attempting to directly fetch Tenant B's property slug as Tenant A returns 404
        mockMvc.perform(get("/api/v1/properties/{slug}", SLUG_B)
                        .with(user(tenantAUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("4. Sequential requests as different principals on a pooled connection do not bleed state")
    void sequentialCallsAsDifferentTenantsDoNotBleed() {
        List<Property> propsA1 = TenantContextTestSupport.runAs(TENANT_A, () -> propertyService.listProperties());
        assertThat(propsA1).hasSize(1);
        assertThat(propsA1.get(0).getSlug()).isEqualTo(SLUG_A);

        List<Property> propsB = TenantContextTestSupport.runAs(TENANT_B, () -> propertyService.listProperties());
        assertThat(propsB).hasSize(1);
        assertThat(propsB.get(0).getSlug()).isEqualTo(SLUG_B);

        List<Property> propsA2 = TenantContextTestSupport.runAs(TENANT_A, () -> propertyService.listProperties());
        assertThat(propsA2).hasSize(1);
        assertThat(propsA2.get(0).getSlug()).isEqualTo(SLUG_A);
    }

    @Test
    @DisplayName("5. The aspect is proven to run inside the @Transactional boundary")
    void aspectRequiresActiveTransaction() {
        assertThatThrownBy(() ->
                TenantContextTestSupport.runAs(TENANT_A, () -> nonTransactionalService.doSomethingWithoutTransaction())
        )
                .as("A tenant-scoped operation without an active transaction must be rejected by the aspect")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be executed within an active transaction");
    }

    @TestConfiguration
    static class NonTransactionalTestConfig {
        @Bean
        public NonTransactionalService nonTransactionalService() {
            return new NonTransactionalService();
        }
    }

    @TenantScoped
    static class NonTransactionalService {
        public String doSomethingWithoutTransaction() {
            return "ok";
        }
    }
}
