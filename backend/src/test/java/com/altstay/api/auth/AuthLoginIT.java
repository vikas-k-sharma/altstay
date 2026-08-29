package com.altstay.api.auth;

import com.altstay.api.auth.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live integration test asserting authentication and tenancy rules per Phase 4 §3.2 & §3.3.
 *
 * <p>Crucial assertions:
 * <ul>
 *   <li>User of Tenant A with correct password is strictly REFUSED (401) against Tenant B's slug.</li>
 *   <li>Unknown tenant slugs return 401 without revealing slug existence.</li>
 *   <li>Wrong password returns 401.</li>
 *   <li>Inactive user returns 401.</li>
 *   <li>Valid login returns 200, sets authenticated session, and authorizes subsequent requests to {@code /api/v1/auth/me}.</li>
 *   <li>Logout invalidates the session and subsequent authenticated requests return 401.</li>
 * </ul>
 *
 * <p>Gated on {@code ALTSTAY_DB_TESTS=true}, mirroring {@link TenantIsolationIT}.
 */
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:./.env.properties",
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=${ALTSTAY_DB_URL}",
        "spring.datasource.username=${ALTSTAY_DB_USER}",
        "spring.datasource.password=${ALTSTAY_DB_PASSWORD}",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false"
})
class AuthLoginIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private static String url;
    private static String user;
    private static String password;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private static final String SLUG_A = "auth-a-" + TENANT_A.toString().substring(0, 8);
    private static final String SLUG_B = "auth-b-" + TENANT_B.toString().substring(0, 8);

    private static final UUID USER_A_ID = UUID.randomUUID();
    private static final UUID USER_B_ID = UUID.randomUUID();
    private static final UUID INACTIVE_USER_ID = UUID.randomUUID();
    private static final UUID SHARED_IN_A_ID = UUID.randomUUID();
    private static final UUID SHARED_IN_B_ID = UUID.randomUUID();

    private static final String USER_A_EMAIL = "alice@" + SLUG_A + ".com";
    private static final String USER_B_EMAIL = "bob@" + SLUG_B + ".com";
    private static final String INACTIVE_USER_EMAIL = "inactive@" + SLUG_A + ".com";

    /**
     * Section 3.2's actual motivation: V1 keys users on unique(tenant_id, lower(email)) because
     * "the same person may hold an account at two properties". One address, two tenants, two
     * passwords. Alice-against-tenant-B only proves the "no such user" path.
     */
    private static final String SHARED_EMAIL = "carol@shared-example.com";

    private static final String RAW_PASSWORD = "CorrectPassword123!";
    private static final String WRONG_PASSWORD = "WrongPassword999!";
    private static final String SHARED_PASSWORD_IN_A = "CarolAtTenantA-1!";
    private static final String SHARED_PASSWORD_IN_B = "CarolAtTenantB-2!";

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

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

        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String passwordHash = encoder.encode(RAW_PASSWORD);

        try (Connection c = open()) {
            // Seed Tenant A and User Alice (OWNER)
            seedTenant(c, TENANT_A, SLUG_A);
            seedUser(c, TENANT_A, USER_A_ID, USER_A_EMAIL, passwordHash, "Alice Owner", true, AuthRole.OWNER);
            seedUser(c, TENANT_A, INACTIVE_USER_ID, INACTIVE_USER_EMAIL, passwordHash, "Inactive Eve", false, AuthRole.FRONT_DESK);

            // Seed Tenant B and User Bob (MANAGER)
            seedTenant(c, TENANT_B, SLUG_B);
            seedUser(c, TENANT_B, USER_B_ID, USER_B_EMAIL, passwordHash, "Bob Manager", true, AuthRole.MANAGER);

            // Carol holds an account at BOTH properties under one email, with different passwords.
            seedUser(c, TENANT_A, SHARED_IN_A_ID, SHARED_EMAIL, encoder.encode(SHARED_PASSWORD_IN_A),
                    "Carol at A", true, AuthRole.MANAGER);
            seedUser(c, TENANT_B, SHARED_IN_B_ID, SHARED_EMAIL, encoder.encode(SHARED_PASSWORD_IN_B),
                    "Carol at B", true, AuthRole.FRONT_DESK);

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
            ps.setString(2, "Auth Test " + slug);
            ps.setString(3, slug);
            ps.execute();
        }
    }

    private static void seedUser(Connection c, UUID tenant, UUID userId, String email,
                                 String passwordHash, String fullName, boolean isActive, String role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "insert into app_user (id, tenant_id, email, password_hash, full_name, is_active) values (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenant);
            ps.setString(3, email);
            ps.setString(4, passwordHash);
            ps.setString(5, fullName);
            ps.setBoolean(6, isActive);
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "insert into user_role (user_id, tenant_id, role) values (?, ?, ?)")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenant);
            ps.setString(3, role);
            ps.execute();
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
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
    @DisplayName("Happy path: User logs in against their tenant slug with correct password")
    void happyPathLoginSucceedsAndEstablishesSession() throws Exception {
        LoginRequest request = new LoginRequest(SLUG_A, USER_A_EMAIL, RAW_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_A_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()))
                .andExpect(jsonPath("$.tenantSlug").value(SLUG_A))
                .andExpect(jsonPath("$.email").value(USER_A_EMAIL))
                .andExpect(jsonPath("$.fullName").value("Alice Owner"))
                .andExpect(jsonPath("$.roles", hasItem("OWNER")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Subsequent call to /api/v1/auth/me using the authenticated session succeeds
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_A_ID.toString()))
                .andExpect(jsonPath("$.tenantSlug").value(SLUG_A))
                .andExpect(jsonPath("$.email").value(USER_A_EMAIL));

        // Logout invalidates session
        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isNoContent());

        // Subsequent call to /me after logout returns 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Crucial anti-escalation test: User of Tenant A with correct password is REFUSED against Tenant B slug")
    void crossTenantLoginRefusedEvenWithCorrectPassword() throws Exception {
        // Alice from Tenant A submits her valid email & password against Tenant B's slug
        LoginRequest request = new LoginRequest(SLUG_B, USER_A_EMAIL, RAW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("Same email at two tenants: each password works only against its own slug")
    void sharedEmailResolvesToTheAccountOfTheRequestedTenantOnly() throws Exception {
        // Carol's tenant-A password against tenant A: the tenant-A account, with tenant A's role.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(SLUG_A, SHARED_EMAIL, SHARED_PASSWORD_IN_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(SHARED_IN_A_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()))
                .andExpect(jsonPath("$.roles", hasItem("MANAGER")));

        // The same email and the same password against tenant B: refused. Email is not an identity.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(SLUG_B, SHARED_EMAIL, SHARED_PASSWORD_IN_A))))
                .andExpect(status().isUnauthorized());

        // And Carol's tenant-B password resolves to the tenant-B account, not tenant A's.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(SLUG_B, SHARED_EMAIL, SHARED_PASSWORD_IN_B))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(SHARED_IN_B_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_B.toString()))
                .andExpect(jsonPath("$.roles", hasItem("FRONT_DESK")));
    }

    @Test
    @DisplayName("An inactive account is indistinguishable from a wrong password and an unknown user")
    void inactiveAccountIsNotAnEnumerationOracle() throws Exception {
        String inactiveBody = loginBody(new LoginRequest(SLUG_A, INACTIVE_USER_EMAIL, RAW_PASSWORD));
        String wrongPasswordBody = loginBody(new LoginRequest(SLUG_A, USER_A_EMAIL, WRONG_PASSWORD));
        String unknownUserBody = loginBody(new LoginRequest(SLUG_A, "nobody@" + SLUG_A + ".com", RAW_PASSWORD));

        assertThat(inactiveBody)
                .as("A body that says 'inactive' confirms the address is registered at this workspace")
                .isEqualTo(wrongPasswordBody)
                .isEqualTo(unknownUserBody);
    }

    private String loginBody(LoginRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Section 3.3: the session cookie reaches the wire HttpOnly and SameSite=Strict")
    void sessionCookieIsHttpOnlyOnTheWire() throws Exception {
        // Deliberately a real socket, not MockMvc: MockMvc never serialises a Set-Cookie header, so
        // it cannot see the attributes this asserts. Section 3.3's decision is about the wire.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        new LoginRequest(SLUG_A, USER_A_EMAIL, RAW_PASSWORD))))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        String sessionCookie = response.headers().allValues("set-cookie").stream()
                .filter(c -> c.startsWith("JSESSIONID"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No JSESSIONID in " + response.headers().allValues("set-cookie")));

        assertThat(sessionCookie)
                .as("Section 3.3 decided an httpOnly cookie; it must not be readable from browser JS")
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Strict");
    }

    @Test
    @DisplayName("Wrong password is refused with 401 Unauthorized")
    void wrongPasswordReturns401() throws Exception {
        LoginRequest request = new LoginRequest(SLUG_A, USER_A_EMAIL, WRONG_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unknown tenant slug is refused with 401 Unauthorized without disclosing slug existence")
    void unknownTenantSlugReturns401() throws Exception {
        LoginRequest request = new LoginRequest("non-existent-tenant-slug", USER_A_EMAIL, RAW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Inactive user account is refused with 401 Unauthorized")
    void inactiveUserReturns401() throws Exception {
        LoginRequest request = new LoginRequest(SLUG_A, INACTIVE_USER_EMAIL, RAW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
