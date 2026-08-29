package com.altstay.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the application-layer half of tenant-scoped authentication.
 *
 * <p>These exist because a mutation showed the database was carrying the whole guarantee on its
 * own: stripping the {@code tenant_id} predicate out of {@link AppUserRepository}'s query left
 * {@code AuthLoginIT} passing 5/5, because PostgreSQL RLS filtered the row the query no longer
 * did. Defence in depth that no test can distinguish from its absence is not defence in depth.
 *
 * <p>The repository is mocked here precisely so RLS cannot mask the application check.
 */
class UserAccountServiceTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "CorrectPassword123!";

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AppUserRepository appUserRepository;
    private UserRoleRepository userRoleRepository;
    private UserAccountService service;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository = mock(AppUserRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        when(userRoleRepository.findByIdUserId(any())).thenReturn(List.of());

        service = new UserAccountService(appUserRepository, userRoleRepository, dataSource, encoder);
    }

    @Test
    @DisplayName("A user row belonging to another tenant is refused even when the password is correct")
    void userFromAnotherTenantIsRefusedEvenWithCorrectPassword() {
        AppUser foreignUser = new AppUser(
                TENANT_B, EMAIL, encoder.encode(RAW_PASSWORD), "Alice Owner", true);

        when(appUserRepository.findByTenantIdAndEmailIgnoreCase(any(), anyString()))
                .thenReturn(Optional.of(foreignUser));

        assertThatThrownBy(() -> service.authenticate(TENANT_A, "tenant-a-slug", EMAIL, RAW_PASSWORD))
                .as("A row carrying a different tenant_id must never authenticate, whatever the database returned")
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("An inactive account is refused with the same message as a wrong password")
    void inactiveAccountIsIndistinguishableFromWrongPassword() {
        AppUser inactive = new AppUser(
                TENANT_A, EMAIL, encoder.encode(RAW_PASSWORD), "Inactive Eve", false);
        AppUser active = new AppUser(
                TENANT_A, EMAIL, encoder.encode(RAW_PASSWORD), "Alice Owner", true);

        when(appUserRepository.findByTenantIdAndEmailIgnoreCase(any(), anyString()))
                .thenReturn(Optional.of(inactive));
        String inactiveMessage = messageFrom(() ->
                service.authenticate(TENANT_A, "tenant-a-slug", EMAIL, RAW_PASSWORD));

        when(appUserRepository.findByTenantIdAndEmailIgnoreCase(any(), anyString()))
                .thenReturn(Optional.of(active));
        String wrongPasswordMessage = messageFrom(() ->
                service.authenticate(TENANT_A, "tenant-a-slug", EMAIL, "WrongPassword999!"));

        org.assertj.core.api.Assertions.assertThat(inactiveMessage)
                .as("Distinguishing 'inactive' from 'wrong password' tells an attacker the account exists")
                .isEqualTo(wrongPasswordMessage);
    }

    private static String messageFrom(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected authentication to be refused");
        } catch (BadCredentialsException e) {
            return e.getMessage();
        }
    }
}
