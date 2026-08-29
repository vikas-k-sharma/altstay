package com.altstay.api.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String BASE_ERROR_URI = "https://api.altstay.com/errors/";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request payload failed validation"
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "validation-error"));
        problemDetail.setTitle("Validation Failure");

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        problemDetail.setProperty("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(ModelUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleModelUnavailableException(
            ModelUnavailableException ex,
            WebRequest request) {

        log.error("AI model unavailable error handled: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The upstream AI model is currently unavailable. Please try again later."
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "model-unavailable"));
        problemDetail.setTitle("Model Unavailable");

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problemDetail);
    }

    @ExceptionHandler(ModelRateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleModelRateLimitedException(
            ModelRateLimitedException ex,
            WebRequest request) {

        log.warn("AI model rate limited / quota exhausted: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The upstream AI model is rate limited or quota exhausted. Please try again later."
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "model-rate-limited"));
        problemDetail.setTitle("Model Rate Limited");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problemDetail);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            org.springframework.security.core.AuthenticationException ex,
            WebRequest request) {

        // The detail is a constant on purpose. Echoing the exception message distinguishes
        // "wrong password" from "account is inactive" from "no such user", which turns the login
        // endpoint into an oracle for which emails are registered against a workspace.
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "unauthorized"));
        problemDetail.setTitle("Unauthorized");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.tenancy.MissingTenantException.class)
    public ResponseEntity<ProblemDetail> handleMissingTenantException(
            com.altstay.api.tenancy.MissingTenantException ex,
            WebRequest request) {

        log.warn("Missing tenant context: {}", ex.getMessage());

        // Logged above for the operator; not echoed to the client, which has no use for the name
        // of an internal aspect and should not be told one exists.
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Authentication is required for this resource"
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "missing-tenant"));
        problemDetail.setTitle("Missing Tenant Context");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    @ExceptionHandler({
            org.springframework.security.authorization.AuthorizationDeniedException.class,
            org.springframework.security.access.AccessDeniedException.class
    })
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(
            Exception ex,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Access is denied: insufficient role privileges"
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "forbidden"));
        problemDetail.setTitle("Forbidden");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.knowledgebase.KnowledgeBaseConflictException.class)
    public ResponseEntity<ProblemDetail> handleKnowledgeBaseConflictException(
            com.altstay.api.knowledgebase.KnowledgeBaseConflictException ex,
            WebRequest request) {

        log.warn("Knowledge base version conflict: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage() != null ? ex.getMessage() : "Someone else saved first"
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "knowledge-base-conflict"));
        problemDetail.setTitle("Knowledge Base Conflict");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex,
            WebRequest request) {

        log.error("Unhandled internal error: ", ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred."
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "internal-error"));
        problemDetail.setTitle("Internal Server Error");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
