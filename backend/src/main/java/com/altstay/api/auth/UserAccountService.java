package com.altstay.api.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class UserAccountService {

    /**
     * The single refusal message. Every failure mode - unknown user, wrong password, inactive
     * account, a row belonging to another tenant - must be indistinguishable to the caller, or the
     * endpoint becomes an oracle for which emails are registered against which workspace.
     */
    private static final String REFUSED = "Invalid credentials";

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    public UserAccountService(AppUserRepository appUserRepository,
                              UserRoleRepository userRoleRepository,
                              DataSource dataSource,
                              PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates a user under the bound tenant RLS context.
     *
     * @param tenantId    The resolved tenant ID (must match the active RLS context)
     * @param tenantSlug  The public workspace slug
     * @param email       The user's email address
     * @param rawPassword The plaintext password to verify
     * @return Fully populated TenantUserDetails
     */
    @Transactional(readOnly = true)
    public TenantUserDetails authenticate(UUID tenantId, String tenantSlug, String email, String rawPassword) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            try (PreparedStatement ps = conn.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }

            AppUser user = appUserRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                    .orElseThrow(() -> new BadCredentialsException(REFUSED));

            // Defence in depth, and deliberately not redundant. RLS filters this row in the
            // database, and the repository query filters it again - but a mutation proved that
            // removing the query's tenant predicate left every test passing, because RLS silently
            // covered for it. This check is the application's own assertion, and it is the one
            // UserAccountServiceTest can exercise with the database mocked out.
            if (!tenantId.equals(user.getTenantId())) {
                throw new BadCredentialsException(REFUSED);
            }

            if (!user.isActive()) {
                throw new BadCredentialsException(REFUSED);
            }

            if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw new BadCredentialsException(REFUSED);
            }

            Set<String> roles = userRoleRepository.findByIdUserId(user.getId()).stream()
                    .map(UserRole::getRole)
                    .collect(Collectors.toUnmodifiableSet());

            return new TenantUserDetails(
                    user.getId(),
                    tenantId,
                    tenantSlug,
                    user.getEmail(),
                    user.getPasswordHash(),
                    user.getFullName(),
                    user.isActive(),
                    roles
            );
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to bind tenant context during authentication", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }
}
