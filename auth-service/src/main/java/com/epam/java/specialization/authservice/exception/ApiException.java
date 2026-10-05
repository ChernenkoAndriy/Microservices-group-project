package com.epam.java.specialization.authservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for exceptions that map to a ProblemDetail response with a stable {@code code}.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
