package com.epam.java.specialization.authservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityDoesNotExistException.class)
    public ProblemDetail handleEntityDoesNotExist(EntityDoesNotExistException e) {
        log.warn("Entity not found: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, "ENTITY_DOES_NOT_EXIST", e.getMessage());
    }

    @ExceptionHandler(TokenIsNotValidException.class)
    public ProblemDetail handleTokenIsNotValid(TokenIsNotValidException e) {
        log.warn("Invalid token: {}", e.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "TOKEN_IS_NOT_VALID", e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        log.warn("Login rejected: {}", e.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", e.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException e) {
        log.warn("Registration rejected: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", details);
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", details);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
