package com.altstay.api.conversation;

import com.altstay.api.auth.TenantUserDetails;
import com.altstay.api.chat.ChatService;
import com.altstay.api.chat.dto.ChatRequest;
import com.altstay.api.chat.dto.ChatResponse;
import com.altstay.api.property.Property;
import com.altstay.api.property.PropertyService;
import com.altstay.api.tenancy.TenantContextTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.config.import=optional:file:./.env.properties",
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=${ALTSTAY_DB_URL}",
        "spring.datasource.username=${ALTSTAY_DB_USER}",
        "spring.datasource.password=${ALTSTAY_DB_PASSWORD}",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ConversationPersistenceIT {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SLUG_A = "conv-tenant-a";
    private static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-222222222222");

    private static String url;
    private static String user;
    private static String password;

    @Autowired
    private ChatService chatService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private ConversationTurnRepository turnRepository;

    @Autowired
    private java.util.Optional<ConversationPersistenceService> persistenceService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.ai.chat.client.ChatClient chatClient;

    @org.mockito.Mock
    private org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec requestSpec;

    @org.mockito.Mock
    private org.springframework.ai.chat.client.ChatClient.CallResponseSpec callResponseSpec;

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

        assertThat(url).as("ALTSTAY_DB_URL must be set").isNotBlank();

        try (Connection c = open()) {
            seedTenant(c, TENANT_A, SLUG_A, USER_A, "alice@conv-a.com");
            c.commit();
        }
    }

    @BeforeEach
    void clearTenantDataAndMockModel() throws SQLException {
        org.mockito.MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();

        org.mockito.Mockito.when(chatClient.prompt(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(requestSpec);
        org.mockito.Mockito.when(requestSpec.call()).thenReturn(callResponseSpec);

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("Check-in is 2 PM. Breakfast is 8-10 AM.");
        org.springframework.ai.chat.model.Generation generation =
                new org.springframework.ai.chat.model.Generation(assistantMessage);
        org.springframework.ai.chat.metadata.Usage usage =
                new org.springframework.ai.chat.metadata.DefaultUsage(150, 50);
        org.springframework.ai.chat.metadata.ChatResponseMetadata metadata =
                org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
                        .model("gemini-2.5-flash-lite")
                        .usage(usage)
                        .build();
        org.springframework.ai.chat.model.ChatResponse aiChatResponse =
                new org.springframework.ai.chat.model.ChatResponse(List.of(generation), metadata);
        org.mockito.Mockito.when(callResponseSpec.chatResponse()).thenReturn(aiChatResponse);

        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("delete from conversation where tenant_id = ?")) {
                ps.setObject(1, TENANT_A);
                ps.execute();
            }
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
                "insert into tenant (id, name, slug) values (?, ?, ?) on conflict (slug) do nothing")) {
            ps.setObject(1, tenant);
            ps.setString(2, "Conv Test " + slug);
            ps.setString(3, slug);
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "insert into app_user (id, tenant_id, email, password_hash, full_name, is_active) values (?, ?, ?, ?, ?, ?) on conflict do nothing")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenant);
            ps.setString(3, email);
            ps.setString(4, "{noop}secret");
            ps.setString(5, "User " + slug);
            ps.setBoolean(6, true);
            ps.execute();
        }
    }

    private void authenticateAs(UUID tenantId, String slug, UUID userId) {
        TenantUserDetails principal = new TenantUserDetails(
                userId, tenantId, slug, "user@" + slug + ".com", "hash", "Authenticated User", true, Set.of("FRONT_DESK")
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Authenticated property-scoped chat call persists turns and enables token margin aggregation")
    void authenticatedChatCall_persistsConversationAndTokenMargin() throws SQLException {
        authenticateAs(TENANT_A, SLUG_A, USER_A);
        Property property = TenantContextTestSupport.runAs(TENANT_A, () ->
                propertyService.createProperty("Conv Beach Resort", "conv-" + UUID.randomUUID().toString().substring(0, 8), "Asia/Kolkata", "INR")
        );

        ChatRequest request = new ChatRequest(
                "Conv Beach Resort",
                "Check-in is 2 PM. Breakfast is 8-10 AM.",
                List.of(),
                "What time is check in and breakfast?",
                property.getId(),
                null
        );

        assertThat(persistenceService).as("ConversationPersistenceService must be present").isPresent();

        ChatResponse response = TenantContextTestSupport.runAs(TENANT_A, () ->
                chatService.answer(request)
        );

        assertThat(response.reply()).isNotBlank();
        assertThat(response.usage().totalTokens()).isGreaterThan(0);

        // Verify directly in Postgres via raw SQL aggregation (Roadmap §9 metric 5 query)
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "select tenant_id, sum(total_tokens) as total_tokens, count(*) as turn_count "
                            + "from conversation_turn where tenant_id = ? group by tenant_id")) {
                ps.setObject(1, TENANT_A);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("total_tokens")).isGreaterThan(0);
                    assertThat(rs.getInt("turn_count")).isEqualTo(2); // 1 USER turn + 1 ASSISTANT turn
                }
            }
            c.rollback();
        }

        // Verify tenant B cannot see tenant A's conversation turns (PostgreSQL RLS verification)
        UUID tenantB = UUID.randomUUID();
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, tenantB.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "select count(*) from conversation_turn where tenant_id = ?")) {
                ps.setObject(1, TENANT_A);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("Anonymous chat call writes ZERO rows to conversation and conversation_turn")
    void anonymousChatCall_writesNoDatabaseRows() throws SQLException {
        SecurityContextHolder.clearContext();

        int initialTurnCount;
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("select count(*) from conversation_turn")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    initialTurnCount = rs.getInt(1);
                }
            }
            c.rollback();
        }

        ChatRequest request = new ChatRequest(
                "Anonymous Guest Stay",
                "WiFi password is beachvibes.",
                List.of(),
                "What is the wifi password?"
        );

        ChatResponse response = chatService.answer(request);
        assertThat(response.reply()).isNotBlank();

        int finalTurnCount;
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT_A.toString());
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("select count(*) from conversation_turn")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    finalTurnCount = rs.getInt(1);
                }
            }
            c.rollback();
        }

        assertThat(finalTurnCount).isEqualTo(initialTurnCount);
    }
}
