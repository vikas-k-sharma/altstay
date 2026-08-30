package com.altstay.api.inventory;

import com.altstay.api.inventory.InventoryService.CreateRoomTypeRequest;
import com.altstay.api.inventory.InventoryService.CreateSpaceRequest;
import com.altstay.api.inventory.InventoryService.CreateUnitRequest;
import com.altstay.api.property.PropertyService;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
class InventoryIntegrityIT {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PropertyService propertyService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String TENANT_SLUG = "inv-t-" + TENANT_ID.toString().substring(0, 8);
    private static final String SLUG_A = "prop-a-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String SLUG_B = "prop-b-" + UUID.randomUUID().toString().substring(0, 8);

    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void seedTenant() throws IOException, SQLException {
        Properties p = new Properties();
        Path local = Path.of(".env.properties");
        if (Files.exists(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                p.load(in);
            }
        }
        url = resolve(p, "ALTSTAY_DB_URL");
        user = resolve(p, "ALTSTAY_DB_USER");
        password = resolve(p, "ALTSTAY_DB_PASSWORD");

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("insert into tenant (id, name, slug) values (?, ?, ?)")) {
                ps.setObject(1, TENANT_ID);
                ps.setString(2, "Inventory Integrity Test Tenant");
                ps.setString(3, TENANT_SLUG);
                ps.execute();
            }
            c.commit();
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_ID.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("delete from tenant where id = ?")) {
                ps.setObject(1, TENANT_ID);
                ps.execute();
            }
            c.commit();
        }
    }

    private static String resolve(Properties file, String key) {
        String fromEnv = System.getenv(key);
        return fromEnv != null && !fromEnv.isBlank() ? fromEnv : file.getProperty(key, "");
    }

    @Test
    @DisplayName("Every space created must have at least one unit")
    void spaceMustHaveAtLeastOneUnit() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            propertyService.createProperty("Property A", SLUG_A, "Asia/Kolkata", "INR");

            assertThatThrownBy(() -> inventoryService.createSpace(SLUG_A, new CreateSpaceRequest("101", "1", true, List.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one unit");
        });
    }

    @Test
    @DisplayName("Every room_type_space row joins a room type and space in the same property")
    void roomTypeSpaceCrossPropertyJoinIsRefused() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            propertyService.createProperty("Property B", SLUG_B, "Asia/Kolkata", "INR");

            var rtA = inventoryService.createRoomType(SLUG_A, new CreateRoomTypeRequest(
                    "DORM6", "Dorm 6", "PER_UNIT", "DORM", 6, 50000L, null, true));

            var spaceB = inventoryService.createSpace(SLUG_B, new CreateSpaceRequest(
                    "201", "2", true, List.of(new CreateUnitRequest("201-A", "SINGLE", true))));

            assertThatThrownBy(() -> inventoryService.associateSpace(rtA.id(), spaceB.id()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same property");
        });
    }

    @Test
    @DisplayName("Hybrid mapping: one space can be associated with multiple room types of the same property")
    void hybridMappingAllowsMultipleRoomTypesForOneSpace() {
        TenantContextTestSupport.runAs(TENANT_ID, () -> {
            var dormRt = inventoryService.createRoomType(SLUG_A, new CreateRoomTypeRequest(
                    "DORM6MIX", "6 Bed Mixed Dorm", "PER_UNIT", "DORM", 6, 60000L, null, true));

            var privRt = inventoryService.createRoomType(SLUG_A, new CreateRoomTypeRequest(
                    "PRIV6", "6 Bed Private Dorm Buyout", "WHOLE", "DORM", 6, 300000L, null, true));

            var space = inventoryService.createSpace(SLUG_A, new CreateSpaceRequest(
                    "101", "1", true, List.of(
                    new CreateUnitRequest("101-1", "BUNK_BOTTOM", true),
                    new CreateUnitRequest("101-2", "BUNK_TOP", true),
                    new CreateUnitRequest("101-3", "BUNK_BOTTOM", true),
                    new CreateUnitRequest("101-4", "BUNK_TOP", true),
                    new CreateUnitRequest("101-5", "BUNK_BOTTOM", true),
                    new CreateUnitRequest("101-6", "BUNK_TOP", true)
            )));

            inventoryService.associateSpace(dormRt.id(), space.id());
            inventoryService.associateSpace(privRt.id(), space.id());

            var dormRtFetched = inventoryService.getRoomType(SLUG_A, dormRt.id());
            var privRtFetched = inventoryService.getRoomType(SLUG_A, privRt.id());

            assertThat(dormRtFetched.spaceIds()).containsExactly(space.id());
            assertThat(privRtFetched.spaceIds()).containsExactly(space.id());
        });
    }
}
