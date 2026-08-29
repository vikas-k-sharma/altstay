package com.altstay.api.tenancy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves tenant isolation is enforced <em>by PostgreSQL</em>, not by application code remembering
 * to add a {@code WHERE tenant_id} predicate.
 *
 * <p>Deliberately plain JDBC rather than {@code @SpringBootTest}. What is under test is the
 * behaviour of the RLS policies in {@code V4__row_level_security.sql}, and a Spring context adds
 * only the risk that a repository's own filtering masks a policy that does nothing.
 *
 * <p><b>This test is worthless run as the wrong role.</b> Neon's default {@code neondb_owner} has
 * {@code rolbypassrls = true} — verified 2026-08-29 — which skips every policy while leaving the
 * suite green. {@link #roleCannotBypassRowLevelSecurity()} runs first and asserts the connected
 * role is not privileged, so a misconfigured connection fails loudly instead of passing silently.
 *
 * <p>Opt-in, mirroring {@code ALTSTAY_LIVE_TESTS}: {@code mvnw clean verify} must stay green
 * offline with no credentials at all.
 */
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
class TenantIsolationIT {

    private static String url;
    private static String user;
    private static String password;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeAll
    static void loadCredentials() throws IOException {
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
        assertThat(url)
                .as("Use the DIRECT database host. A transaction-mode pooler multiplexes sessions "
                        + "across backends, which is exactly the leak this test exists to catch")
                .doesNotContain("-pooler");
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

    /** Binds the tenant for the current transaction, the way the application will at runtime. */
    private static void bind(Connection c, UUID tenant) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private static List<String> propertySlugs(Connection c) throws SQLException {
        // Deliberately no WHERE clause. If a row from another tenant comes back, the policy is not
        // doing its job — which is the entire point of enforcing this in the database.
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select slug from property")) {
            List<String> out = new java.util.ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        }
    }

    private static void seedTenant(Connection c, UUID tenant, String slug) throws SQLException {
        bind(c, tenant);
        try (PreparedStatement ps = c.prepareStatement(
                "insert into tenant (id, name, slug) values (?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "Isolation test " + slug);
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

    @BeforeAll
    static void seed() throws SQLException {
        try (Connection c = open()) {
            seedTenant(c, TENANT_A, "iso-a-" + TENANT_A.toString().substring(0, 8));
            seedTenant(c, TENANT_B, "iso-b-" + TENANT_B.toString().substring(0, 8));
            c.commit();
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        try (Connection c = open()) {
            for (UUID t : List.of(TENANT_A, TENANT_B)) {
                bind(c, t);
                try (PreparedStatement ps = c.prepareStatement("delete from tenant where id = ?")) {
                    ps.setObject(1, t);
                    ps.execute();
                }
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("the connected role cannot bypass RLS — without this, every other test here is theatre")
    void roleCannotBypassRowLevelSecurity() throws SQLException {
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "select current_user, rolsuper, rolbypassrls "
                             + "from pg_roles where rolname = current_user")) {
            assertThat(rs.next()).isTrue();
            String role = rs.getString("current_user");
            assertThat(rs.getBoolean("rolsuper"))
                    .as("role %s is a superuser and bypasses all RLS policies", role)
                    .isFalse();
            assertThat(rs.getBoolean("rolbypassrls"))
                    .as("role %s holds BYPASSRLS — every policy in V4 is inert. Connect as "
                            + "altstay_app, not neondb_owner", role)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a bound tenant sees only its own rows, through a query with no WHERE clause")
    void boundTenantSeesOnlyItsOwnRows() throws SQLException {
        try (Connection c = open()) {
            bind(c, TENANT_A);
            List<String> slugs = propertySlugs(c);

            assertThat(slugs).hasSize(1);
            assertThat(slugs.get(0)).startsWith("iso-a-");
            c.rollback();
        }
    }

    @Test
    @DisplayName("an unbound connection sees ZERO rows, never all rows — the policy fails closed")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection c = open()) {
            // No bind() call at all.
            assertThat(propertySlugs(c))
                    .as("a policy that fails OPEN looks identical to a working one until the day "
                            + "it matters")
                    .isEmpty();
            c.rollback();
        }
    }

    @Test
    @DisplayName("a bound tenant cannot WRITE a row belonging to another tenant")
    void cannotWriteAcrossTheTenantBoundary() throws SQLException {
        try (Connection c = open()) {
            bind(c, TENANT_A);

            // USING controls what you can read; WITH CHECK controls what you can write. Without
            // the latter, tenant A can insert rows carrying B's tenant_id — writing across a
            // boundary it cannot read across.
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "insert into property (tenant_id, name, slug) values (?, ?, ?)")) {
                    ps.setObject(1, TENANT_B);
                    ps.setString(2, "smuggled");
                    ps.setString(3, "smuggled-" + UUID.randomUUID());
                    ps.execute();
                }
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");

            c.rollback();
        }
    }

    @Test
    @DisplayName("a tenant binding does not survive onto the next transaction on the same connection")
    void bindingDoesNotLeakAcrossTransactionsOnAReusedConnection() throws SQLException {
        // The runtime pool reuses connections. set_config(..., true) is transaction-local, so the
        // binding must be gone after commit/rollback — otherwise the next request on this
        // connection inherits the previous tenant's identity.
        try (Connection c = open()) {
            bind(c, TENANT_A);
            assertThat(propertySlugs(c)).hasSize(1);
            c.rollback();

            assertThat(propertySlugs(c))
                    .as("tenant binding leaked into the next transaction on a reused connection")
                    .isEmpty();
            c.rollback();
        }
    }

    @Test
    @DisplayName("tenant_directory has NO RLS and is maintained automatically from tenant via trigger")
    void tenantDirectoryHasNoRlsAndSyncsFromTenant() throws SQLException {
        try (Connection c = open()) {
            // Unbound connection: property table sees 0 rows due to FORCE RLS, but tenant_directory
            // is readable without any tenant binding (no RLS, deliberately).
            try (PreparedStatement ps = c.prepareStatement(
                    "select tenant_id from tenant_directory where slug = ?")) {
                ps.setString(1, "iso-a-" + TENANT_A.toString().substring(0, 8));
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("tenant_directory must resolve slug for unbound connection").isTrue();
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_A);
                }

                ps.setString(1, "iso-b-" + TENANT_B.toString().substring(0, 8));
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("tenant_directory must resolve slug for unbound connection").isTrue();
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_B);
                }
            }
            c.rollback();
        }
    }
}
