package com.altstay.api.knowledgebase;

import com.altstay.api.auth.AuthRole;
import com.altstay.api.auth.TenantUserDetails;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyService;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
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
        "spring.flyway.enabled=false"
})
class KnowledgeBaseIsolationIT {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private KnowledgeBaseVersionRepository knowledgeBaseVersionRepository;

    @Autowired
    private PropertyService propertyService;

    private static String url;
    private static String user;
    private static String password;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    private static final String SLUG_A = "kb-iso-a-" + TENANT_A.toString().substring(0, 8);
    private static final String SLUG_B = "kb-iso-b-" + TENANT_B.toString().substring(0, 8);

    private static Property propA;
    private static Property propB;

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

        try (Connection c = open()) {
            seedTenant(c, TENANT_A, SLUG_A, USER_A, "owner@" + SLUG_A + ".com");
            seedTenant(c, TENANT_B, SLUG_B, USER_B, "owner@" + SLUG_B + ".com");
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

    private static void seedTenant(Connection c, UUID tenant, String slug, UUID userId, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "insert into tenant (id, name, slug) values (?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "KB Isolation Test " + slug);
            ps.setString(3, slug);
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "insert into app_user (id, tenant_id, email, password_hash, full_name, is_active) values (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenant);
            ps.setString(3, email);
            ps.setString(4, "{noop}secret");
            ps.setString(5, "User " + slug);
            ps.setBoolean(6, true);
            ps.execute();
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        SecurityContextHolder.clearContext();
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

    private void authenticateAs(UUID tenantId, String slug, UUID userId) {
        TenantUserDetails principal = new TenantUserDetails(
                userId,
                tenantId,
                slug,
                "owner@" + slug + ".com",
                "hash",
                "Owner " + slug,
                true,
                Set.of(AuthRole.OWNER)
        );
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("1. char_count violations are rejected by PostgreSQL and the assertion names the constraint")
    void rawSql_charCountViolationIsRefusedByDatabaseConstraint() throws SQLException {
        UUID pId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }

            // Create a property and knowledge base in DB directly
            try (PreparedStatement ps = c.prepareStatement("insert into property (id, tenant_id, name, slug, timezone, currency_code) values (?, ?, ?, ?, 'Asia/Kolkata', 'INR')")) {
                ps.setObject(1, pId);
                ps.setObject(2, TENANT_A);
                ps.setString(3, "Raw Test Property");
                ps.setString(4, "raw-prop-" + pId.toString().substring(0, 8));
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("insert into knowledge_base (id, tenant_id, property_id) values (?, ?, ?)")) {
                ps.setObject(1, kbId);
                ps.setObject(2, TENANT_A);
                ps.setObject(3, pId);
                ps.execute();
            }
            c.commit();
        }

        // Raw SQL insert with char_count = 0 (violates check (char_count between 1 and 20000))
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "insert into knowledge_base_version (tenant_id, knowledge_base_id, version_no, content, content_sha256, char_count, authored_by) "
                                + "values (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, TENANT_A);
                    ps.setObject(2, kbId);
                    ps.setInt(3, 1);
                    ps.setString(4, "");
                    ps.setString(5, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
                    ps.setInt(6, 0);
                    ps.setObject(7, USER_A);
                    ps.execute();
                }
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("knowledge_base_version_char_count_check");
            c.rollback();
        }

        // Raw SQL insert with char_count = 20001
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "insert into knowledge_base_version (tenant_id, knowledge_base_id, version_no, content, content_sha256, char_count, authored_by) "
                                + "values (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, TENANT_A);
                    ps.setObject(2, kbId);
                    ps.setInt(3, 1);
                    ps.setString(4, "a".repeat(20001));
                    ps.setString(5, "sha");
                    ps.setInt(6, 20001);
                    ps.setObject(7, USER_A);
                    ps.execute();
                }
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("knowledge_base_version_char_count_check");
            c.rollback();
        }
    }

    @Test
    @DisplayName("2. duplicate (knowledge_base_id, version_no) is rejected by unique constraint")
    void rawSql_duplicateVersionNoIsRefusedByUniqueConstraint() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }

            UUID pId = UUID.randomUUID();
            UUID kbId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement("insert into property (id, tenant_id, name, slug, timezone, currency_code) values (?, ?, ?, ?, 'Asia/Kolkata', 'INR')")) {
                ps.setObject(1, pId);
                ps.setObject(2, TENANT_A);
                ps.setString(3, "Raw Uniq Property");
                ps.setString(4, "raw-uniq-" + pId.toString().substring(0, 8));
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("insert into knowledge_base (id, tenant_id, property_id) values (?, ?, ?)")) {
                ps.setObject(1, kbId);
                ps.setObject(2, TENANT_A);
                ps.setObject(3, pId);
                ps.execute();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "insert into knowledge_base_version (tenant_id, knowledge_base_id, version_no, content, content_sha256, char_count, authored_by) "
                            + "values (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, kbId);
                ps.setInt(3, 1);
                ps.setString(4, "Version 1");
                ps.setString(5, "sha1");
                ps.setInt(6, 9);
                ps.setObject(7, USER_A);
                ps.execute();
            }

            // Duplicate version_no = 1
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "insert into knowledge_base_version (tenant_id, knowledge_base_id, version_no, content, content_sha256, char_count, authored_by) "
                                + "values (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, TENANT_A);
                    ps.setObject(2, kbId);
                    ps.setInt(3, 1);
                    ps.setString(4, "Duplicate Version 1");
                    ps.setString(5, "sha2");
                    ps.setInt(6, 19);
                    ps.setObject(7, USER_A);
                    ps.execute();
                }
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("knowledge_base_version_knowledge_base_id_version_no_key");

            c.rollback();
        }
    }

    @Test
    @DisplayName("3. Tenant A cannot read Tenant B's knowledge base through repository queries written with NO where tenant_id")
    void tenantIsolation_cannotReadAcrossTenantBoundaryThroughRepository() {
        authenticateAs(TENANT_A, SLUG_A, USER_A);
        Property pA = TenantContextTestSupport.runAs(TENANT_A, () ->
                propertyService.createProperty("Property A", "prop-a-" + UUID.randomUUID().toString().substring(0, 8), "Asia/Kolkata", "INR")
        );
        KnowledgeBaseVersion vA = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.save(pA.getId(), "Tenant A house rules content")
        );

        authenticateAs(TENANT_B, SLUG_B, USER_B);
        Property pB = TenantContextTestSupport.runAs(TENANT_B, () ->
                propertyService.createProperty("Property B", "prop-b-" + UUID.randomUUID().toString().substring(0, 8), "Asia/Kolkata", "INR")
        );

        // Tenant B attempts to read Tenant A's KB via getCurrent(pA.getId()) -> returns empty
        Optional<KnowledgeBaseVersion> crossReadCurrent = TenantContextTestSupport.runAs(TENANT_B, () ->
                knowledgeBaseService.getCurrent(pA.getId())
        );
        assertThat(crossReadCurrent).as("Tenant B cannot get current KB of Tenant A").isEmpty();

        // Tenant B attempts to read Tenant A's KB history -> returns empty list
        List<KnowledgeBaseVersion> crossReadHistory = TenantContextTestSupport.runAs(TENANT_B, () ->
                knowledgeBaseService.history(pA.getId(), 50)
        );
        assertThat(crossReadHistory).as("Tenant B cannot read history of Tenant A").isEmpty();

        // Tenant B queries repository directly (no WHERE clause) -> sees only 0 rows for pA
        Optional<KnowledgeBase> directKb = TenantContextTestSupport.runAs(TENANT_B, () ->
                knowledgeBaseRepository.findByPropertyId(pA.getId())
        );
        assertThat(directKb).as("Tenant B direct repository lookup for Tenant A's KB returns empty under RLS").isEmpty();
    }

    @Test
    @DisplayName("4. Save produces new version, repoints current_version_id, and preserves readable history")
    void save_endToEnd_createsVersionAndPreservesHistory() {
        authenticateAs(TENANT_A, SLUG_A, USER_A);
        Property prop = TenantContextTestSupport.runAs(TENANT_A, () ->
                propertyService.createProperty("Sunset Resort", "sunset-" + UUID.randomUUID().toString().substring(0, 8), "Asia/Kolkata", "INR")
        );

        // Save Version 1
        KnowledgeBaseVersion v1 = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.save(prop.getId(), "Quiet hours: 10 PM to 7 AM.")
        );
        assertThat(v1.getVersionNo()).isEqualTo(1);
        assertThat(v1.getAuthoredBy()).isEqualTo(USER_A);

        Optional<KnowledgeBaseVersion> currentAfterV1 = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.getCurrent(prop.getId())
        );
        assertThat(currentAfterV1).isPresent();
        assertThat(currentAfterV1.get().getVersionNo()).isEqualTo(1);
        assertThat(currentAfterV1.get().getContent()).isEqualTo("Quiet hours: 10 PM to 7 AM.");

        // Save Version 2
        KnowledgeBaseVersion v2 = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.save(prop.getId(), "Quiet hours: 11 PM to 7 AM. Pool closes at 9 PM.")
        );
        assertThat(v2.getVersionNo()).isEqualTo(2);
        assertThat(v2.getId()).isNotEqualTo(v1.getId());

        Optional<KnowledgeBaseVersion> currentAfterV2 = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.getCurrent(prop.getId())
        );
        assertThat(currentAfterV2).isPresent();
        assertThat(currentAfterV2.get().getVersionNo()).isEqualTo(2);
        assertThat(currentAfterV2.get().getContent()).isEqualTo("Quiet hours: 11 PM to 7 AM. Pool closes at 9 PM.");

        // History lists both versions in descending order
        List<KnowledgeBaseVersion> history = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.history(prop.getId(), 50)
        );
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getVersionNo()).isEqualTo(2);
        assertThat(history.get(1).getVersionNo()).isEqualTo(1);
        assertThat(history.get(1).getContent()).isEqualTo("Quiet hours: 10 PM to 7 AM.");
    }

    @Test
    @DisplayName("5. Save with unchanged content produces NO new version and returns existing one")
    void save_withUnchangedContent_isNoOp() {
        authenticateAs(TENANT_A, SLUG_A, USER_A);
        Property prop = TenantContextTestSupport.runAs(TENANT_A, () ->
                propertyService.createProperty("Palm Villa", "palm-" + UUID.randomUUID().toString().substring(0, 8), "Asia/Kolkata", "INR")
        );

        String content = "Welcome to Palm Villa. WiFi: palm123";
        KnowledgeBaseVersion v1 = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.save(prop.getId(), content)
        );

        // Save same content again
        KnowledgeBaseVersion v2 = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.save(prop.getId(), content)
        );

        assertThat(v2.getId()).isEqualTo(v1.getId());
        assertThat(v2.getVersionNo()).isEqualTo(v1.getVersionNo());

        List<KnowledgeBaseVersion> history = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.history(prop.getId(), 50)
        );
        assertThat(history).hasSize(1);
    }

    @Test
    @DisplayName("6. Concurrent saves: one wins, the other retries cleanly — never a duplicate version_no or 500")
    void concurrentSaves_retryCleanlyWithoutDuplicateVersionNo() throws ExecutionException, InterruptedException {
        authenticateAs(TENANT_A, SLUG_A, USER_A);
        Property prop = TenantContextTestSupport.runAs(TENANT_A, () ->
                propertyService.createProperty("Concurrent Lodge", "concur-" + UUID.randomUUID().toString().substring(0, 8), "Asia/Kolkata", "INR")
        );

        // Pre-create version 1
        TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.save(prop.getId(), "Initial rules")
        );

        // Execute 2 concurrent saves with different content on separate threads
        CompletableFuture<KnowledgeBaseVersion> future1 = CompletableFuture.supplyAsync(() -> {
            authenticateAs(TENANT_A, SLUG_A, USER_A);
            return TenantContextTestSupport.runAs(TENANT_A, () ->
                    knowledgeBaseService.save(prop.getId(), "Concurrent update by worker 1")
            );
        });

        CompletableFuture<KnowledgeBaseVersion> future2 = CompletableFuture.supplyAsync(() -> {
            authenticateAs(TENANT_A, SLUG_A, USER_A);
            return TenantContextTestSupport.runAs(TENANT_A, () ->
                    knowledgeBaseService.save(prop.getId(), "Concurrent update by worker 2")
            );
        });

        KnowledgeBaseVersion res1 = future1.get();
        KnowledgeBaseVersion res2 = future2.get();

        assertThat(res1).isNotNull();
        assertThat(res2).isNotNull();
        assertThat(res1.getVersionNo()).isNotEqualTo(res2.getVersionNo());
        assertThat(Set.of(res1.getVersionNo(), res2.getVersionNo())).containsExactlyInAnyOrder(2, 3);

        List<KnowledgeBaseVersion> history = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.history(prop.getId(), 50)
        );
        assertThat(history).hasSize(3);
    }

    @Test
    @DisplayName("7. authored_by is the authenticated user on every version written through the API")
    void authoredBy_isWrittenFromPrincipalInDatabase() throws SQLException {
        authenticateAs(TENANT_A, SLUG_A, USER_A);
        Property prop = TenantContextTestSupport.runAs(TENANT_A, () ->
                propertyService.createProperty("Author Check Lodge", "author-" + UUID.randomUUID().toString().substring(0, 8), "Asia/Kolkata", "INR")
        );

        KnowledgeBaseVersion version = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.save(prop.getId(), "Content with author check")
        );

        assertThat(version.getAuthoredBy()).isEqualTo(USER_A);

        Optional<KnowledgeBaseVersion> loaded = TenantContextTestSupport.runAs(TENANT_A, () ->
                knowledgeBaseService.getCurrent(prop.getId())
        );
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getAuthoredBy()).isEqualTo(USER_A);

        // Also assert directly in PostgreSQL via JDBC
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("select authored_by from knowledge_base_version where id = ?")) {
                ps.setObject(1, version.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat((UUID) rs.getObject("authored_by")).isEqualTo(USER_A);
                }
            }
            c.rollback();
        }
    }
}
