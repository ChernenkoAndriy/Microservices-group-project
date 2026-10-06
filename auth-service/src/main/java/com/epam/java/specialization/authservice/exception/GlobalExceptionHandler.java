package com.epam.java.specialization.authservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
                .flatMap(result -> result.getResolvableErrors().stream()
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
