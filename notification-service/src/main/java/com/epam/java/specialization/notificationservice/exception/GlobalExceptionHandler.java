package com.epam.java.specialization.notificationservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns every error of this service into an RFC 9457 ProblemDetail with a machine-readable {@code code},
 * the same shape as the other services return.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException e) {
        List<Map<String, String>> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of("field", String.valueOf(result.getMethodParameter().getParameterName()),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationProblem(List.of(Map.of("field", e.getName(), "message", "has an invalid value")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is malformed or contains unknown or invalid properties");
    }

    /** ResponseStatusException and the other Spring MVC errors keep their status; only the code is added. */
    @ExceptionHandler({
            ErrorResponseException.class,
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ProblemDetail> handleErrorResponse(Exception e) {
        ErrorResponse error = (ErrorResponse) e;
        HttpStatusCode status = error.getStatusCode();
        String code = HttpStatus.valueOf(status.value()).name();
        ProblemDetail problem = error.getBody();
        problem.setType(typeOf(code));
        problem.setProperty("code", code);
        log.warn("{}: {}", code, problem.getDetail());
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

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(typeOf(code));
        problem.setProperty("code", code);
        return problem;
    }

    private static URI typeOf(String code) {
        return URI.create("/errors/" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
