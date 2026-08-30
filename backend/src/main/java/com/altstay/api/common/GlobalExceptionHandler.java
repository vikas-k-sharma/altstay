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

    @ExceptionHandler(com.altstay.api.booking.InvalidBookingTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidBookingTransitionException(
            com.altstay.api.booking.InvalidBookingTransitionException ex,
            WebRequest request) {

        log.warn("Invalid booking transition: reference={}, from={}, to={}", ex.getReference(), ex.getFromStatus(), ex.getToStatus());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "invalid-booking-transition"));
        problemDetail.setTitle("Invalid Booking Transition");
        problemDetail.setProperty("reference", ex.getReference());
        problemDetail.setProperty("fromStatus", ex.getFromStatus() != null ? ex.getFromStatus().name() : null);
        problemDetail.setProperty("toStatus", ex.getToStatus().name());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.booking.BookingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBookingNotFoundException(
            com.altstay.api.booking.BookingNotFoundException ex,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "not-found"));
        problemDetail.setTitle("Not Found");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.booking.NoAvailabilityException.class)
    public ResponseEntity<ProblemDetail> handleNoAvailabilityException(
            com.altstay.api.booking.NoAvailabilityException ex,
            WebRequest request) {

        log.warn("No availability: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "no-availability"));
        problemDetail.setTitle("No Availability");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.booking.BookingConflictException.class)
    public ResponseEntity<ProblemDetail> handleBookingConflictException(
            com.altstay.api.booking.BookingConflictException ex,
            WebRequest request) {

        log.warn("Booking conflict: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "booking-conflict"));
        problemDetail.setTitle("Booking Conflict");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.booking.GuestNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGuestNotFoundException(
            com.altstay.api.booking.GuestNotFoundException ex,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "not-found"));
        problemDetail.setTitle("Not Found");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.booking.UnknownRoomTypeException.class)
    public ResponseEntity<ProblemDetail> handleUnknownRoomTypeException(
            com.altstay.api.booking.UnknownRoomTypeException ex,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "unknown-room-type"));
        problemDetail.setTitle("Unknown Room Type");
        problemDetail.setProperty("roomTypeId", ex.getRoomTypeId() != null ? ex.getRoomTypeId().toString() : null);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(com.altstay.api.booking.RateCurrencyMismatchException.class)
    public ResponseEntity<ProblemDetail> handleRateCurrencyMismatchException(
            com.altstay.api.booking.RateCurrencyMismatchException ex,
            WebRequest request) {

        log.warn("Rate currency mismatch: expected={}, actual={}", ex.getExpectedCurrency(), ex.getActualCurrency());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "rate-currency-mismatch"));
        problemDetail.setTitle("Rate Currency Mismatch");
        problemDetail.setProperty("expectedCurrency", ex.getExpectedCurrency());
        problemDetail.setProperty("actualCurrency", ex.getActualCurrency());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    /**
     * Last line of defence for a database constraint that reached the wire untranslated.
     *
     * <p>{@code BookingService} turns an {@code allocation_no_overlap} violation into a
     * {@link com.altstay.api.booking.BookingConflictException} at the point it happens, which is
     * where the good error message comes from. This handler exists because the default for anything
     * it misses is the catch-all below: a <b>500</b> carrying a Postgres constraint name, which is
     * both the wrong status for a lost race and a schema leak.
     *
     * <p>The detail is deliberately generic and the log line carries only the constraint name —
     * never the failing row, which for {@code guest} would be personal data (roadmap §6).
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex,
            WebRequest request) {

        String constraint = constraintNameOf(ex);
        log.warn("Database constraint violated: constraint={}", constraint);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "That change conflicts with something already recorded. Re-check the current state and try again."
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "booking-conflict"));
        problemDetail.setTitle("Conflict");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    /**
     * Pulls out only the constraint name, never the message.
     *
     * <p>The message must not be logged whole: PostgreSQL appends {@code "Detail: Failing row
     * contains (...)"}, so for {@code guest} it carries the guest's name, email and phone. A
     * regex over the quoted constraint token gets what is useful for diagnosis and leaves the row
     * behind.
     *
     * <p>Deliberately no reference to {@code PSQLException}: the driver is a <b>runtime</b>
     * dependency and is not on the compile classpath, so touching its types here would not build.
     */
    private static String constraintNameOf(org.springframework.dao.DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause.getMessage();
        if (message != null) {
            java.util.regex.Matcher matcher = CONSTRAINT_NAME.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (cause instanceof java.sql.SQLException sqlException) {
            return "sqlstate:" + sqlException.getSQLState();
        }
        return cause.getClass().getSimpleName();
    }

    private static final java.util.regex.Pattern CONSTRAINT_NAME =
            java.util.regex.Pattern.compile("constraint \"([^\"]+)\"");

    /**
     * Invalid input that reached a service rather than being caught by bean validation at the
     * boundary — an inverted date range, a missing property reference, a zero unit count.
     *
     * <p>Without this the catch-all below reports a caller's mistake as a 500, which tells the
     * front desk nothing and pages somebody for a bad request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(BASE_ERROR_URI + "invalid-request"));
        problemDetail.setTitle("Invalid Request");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
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
