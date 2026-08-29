package com.altstay.api.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class DelegatingPasswordEncoderTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    @DisplayName("DelegatingPasswordEncoder prefixes hash with default algorithm ID {bcrypt}")
    void delegatingPasswordEncoderUsesBcryptPrefix() {
        String raw = "hostel-super-secret-password-123";
        String encoded = encoder.encode(raw);

        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    @DisplayName("DelegatingPasswordEncoder verifies bcrypt hash without prefix or with prefix")
    void delegatingPasswordEncoderMatchesPrefixedHash() {
        String raw = "sunsetSurfPass2026!";
        String encoded = encoder.encode(raw);

        assertThat(encoder.matches(raw, encoded)).isTrue();
    }
}
