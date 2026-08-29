package com.altstay.api.common;

import com.altstay.api.tenancy.MissingTenantException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("MissingTenantException returns 401 Unauthorized ProblemDetail with missing-tenant type")
    void handleMissingTenantExceptionReturns401ProblemDetail() {
        MissingTenantException exception = new MissingTenantException("No authenticated tenant context bound");

        ResponseEntity<ProblemDetail> response = handler.handleMissingTenantException(exception, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(401);
        assertThat(body.getTitle()).isEqualTo("Missing Tenant Context");
        assertThat(body.getType().toString()).isEqualTo("https://api.altstay.com/errors/missing-tenant");
        assertThat(body.getDetail())
                .as("The internal message names application internals and must not reach the client")
                .doesNotContain("No authenticated tenant context bound");
    }

    @Test
    @DisplayName("AuthenticationException detail is uniform and never echoes the internal reason")
    void authenticationFailureDetailIsUniformAcrossReasons() {
        ResponseEntity<ProblemDetail> badCredentials = handler.handleAuthenticationException(
                new BadCredentialsException("Invalid credentials"), null);
        ResponseEntity<ProblemDetail> disabled = handler.handleAuthenticationException(
                new DisabledException("User account is inactive"), null);

        assertThat(badCredentials.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ProblemDetail badCredentialsBody = badCredentials.getBody();
        ProblemDetail disabledBody = disabled.getBody();
        assertThat(badCredentialsBody).isNotNull();
        assertThat(disabledBody).isNotNull();

        assertThat(disabledBody.getDetail())
                .as("A distinguishable body tells an attacker which emails are registered")
                .isEqualTo(badCredentialsBody.getDetail());
        assertThat(disabledBody.getDetail()).doesNotContain("inactive");
    }
}
