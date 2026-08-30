package com.altstay.api.tenancy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 §11.2 & §12.1 — Schema Tenancy and Pre-Flight Verifications.
 *
 * <p>Enumerates every user table in the {@code public} schema and asserts that:
 * <ul>
 *   <li>Only explicitly allowlisted reference tables ({@code tenant_directory}, {@code amenity}, {@code flyway_schema_history}) are exempt.</li>
 *   <li>Every other table has {@code relrowsecurity = true} (RLS enabled) AND {@code relforcerowsecurity = true} (RLS forced).</li>
 * </ul>
 *
 * <p>Also verifies pre-flight invariants:
 * <ul>
 *   <li>{@code btree_gist} extension is installed on PostgreSQL.</li>
 *   <li>PostgreSQL accepts a {@code STORED} generated column over the 3-argument {@code daterange(check_in, check_out, '[)')} constructor.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
public class SchemaTenancyIT {

    private static String url;
    private static String user;
    private static String password;

    private static final Set<String> ALLOWLISTED_TABLES = Set.of(
            "tenant_directory",
            "amenity",
            "flyway_schema_history"
    );

    @BeforeAll
    static void setUp() throws IOException {
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

        assertThat(url).as("ALTSTAY_DB_URL must be set").isNotBlank();

        // Ensure all migrations up to current are applied
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
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

    @Test
    @DisplayName("Pre-flight: btree_gist extension is installed and available")
    void btreeGistExtensionIsInstalled() throws SQLException {
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "select name, default_version, installed_version "
                             + "from pg_available_extensions where name = 'btree_gist'")) {
            assertThat(rs.next()).as("btree_gist extension should exist in pg_available_extensions").isTrue();
            String installed = rs.getString("installed_version");
            assertThat(installed)
                    .as("btree_gist extension must be installed on the database")
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("Pre-flight: PostgreSQL accepts STORED generated column over daterange(check_in, check_out, '[)')")
    void postgresqlAcceptsStoredGeneratedDaterange() throws SQLException {
        try (Connection c = open();
             Statement s = c.createStatement()) {
            s.execute("create temp table _preflight_test ("
                    + "id uuid primary key default gen_random_uuid(), "
                    + "check_in date not null, "
                    + "check_out date not null, "
                    + "stay_range daterange generated always as (daterange(check_in, check_out, '[)')) stored"
                    + ")");

            s.execute("insert into _preflight_test (check_in, check_out) "
                    + "values ('2026-09-01', '2026-09-05')");

            try (ResultSet rs = s.executeQuery("select stay_range from _preflight_test")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("stay_range")).isEqualTo("[2026-09-01,2026-09-05)");
            }

            s.execute("drop table _preflight_test");
            c.commit();
        }
    }

    record TableSecurity(String tableName, boolean rlsEnabled, boolean rlsForced) {}

    @Test
    @DisplayName("Every table in public schema is either explicitly allowlisted or has RLS ENABLED and FORCED")
    void everyPublicTableHasRowLevelSecurityEnabledAndForced() throws SQLException {
        List<TableSecurity> tables = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "select c.relname as table_name, c.relrowsecurity, c.relforcerowsecurity "
                             + "from pg_class c "
                             + "join pg_namespace n on n.oid = c.relnamespace "
                             + "where n.nspname = 'public' and c.relkind = 'r' "
                             + "order by c.relname")) {
            while (rs.next()) {
                tables.add(new TableSecurity(
                        rs.getString("table_name"),
                        rs.getBoolean("relrowsecurity"),
                        rs.getBoolean("relforcerowsecurity")
                ));
            }
        }

        assertThat(tables).as("Public schema should contain tables").isNotEmpty();

        List<String> unprotectedTables = new ArrayList<>();
        List<String> unforcedTables = new ArrayList<>();

        for (TableSecurity t : tables) {
            if (ALLOWLISTED_TABLES.contains(t.tableName())) {
                continue;
            }
            if (!t.rlsEnabled()) {
                unprotectedTables.add(t.tableName());
            }
            if (!t.rlsForced()) {
                unforcedTables.add(t.tableName());
            }
        }

        assertThat(unprotectedTables)
                .as("All non-allowlisted tables MUST have Row Level Security ENABLED. Unprotected tables: %s", unprotectedTables)
                .isEmpty();

        assertThat(unforcedTables)
                .as("All non-allowlisted tables MUST have Row Level Security FORCED (FORCE ROW LEVEL SECURITY). Unforced tables: %s", unforcedTables)
                .isEmpty();
    }
}
