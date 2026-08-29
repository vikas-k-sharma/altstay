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
