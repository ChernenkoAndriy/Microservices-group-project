package com.epam.java.specialization.trackservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException e) {
        log.warn("{}: {}", e.getCode(), e.getMessage());
        return problem(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage(), error.getCode()))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException e) {
        List<Map<String, String>> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result instanceof ParameterErrors bodyErrors
                        ? bodyErrors.getFieldErrors().stream()
                                .map(error -> fieldError(error.getField(), error.getDefaultMessage(), error.getCode()))
                        : result.getResolvableErrors().stream()
                                .map(error -> fieldError(
                                        result.getMethodParameter().getParameterName(),
                                        error.getDefaultMessage(),
                                        error.getCodes() != null && error.getCodes().length > 0
                                                ? error.getCodes()[error.getCodes().length - 1] : null)))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationProblem(List.of(fieldError(e.getName(), "has an invalid value", "TypeMismatch")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is malformed or contains unknown or invalid properties");
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class,
            ServletRequestBindingException.class,
            ErrorResponseException.class
    })
    public ResponseEntity<ProblemDetail> handleSpringMvcException(Exception e) {
        ErrorResponse error = (ErrorResponse) e;
        ProblemDetail problem = error.getBody();
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        problem.setProperty("code", status.name());
        log.warn("{}: {}", status, e.getMessage());
        return ResponseEntity.status(status).headers(error.getHeaders()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private ProblemDetail validationProblem(List<Map<String, String>> errors) {
        log.warn("Validation failed: {}", errors);
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Request validation failed.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private Map<String, String> fieldError(String field, String message, String code) {
        return code == null
                ? Map.of("field", String.valueOf(field), "message", String.valueOf(message))
                : Map.of("field", String.valueOf(field), "message", String.valueOf(message), "code", code);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
