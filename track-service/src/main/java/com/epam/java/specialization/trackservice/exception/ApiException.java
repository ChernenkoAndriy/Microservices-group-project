package com.epam.java.specialization.trackservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.net.URI;

@Getter
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final URI type;

    protected ApiException(HttpStatus status, String code, String message) {
        this(status, code, toTypeUri(code), message);
    }

    protected ApiException(HttpStatus status, String code, URI type, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.type = type != null ? type : toTypeUri(code);
    }

    public static URI toTypeUri(String code) {
        if (code == null || code.isBlank()) {
            return URI.create("about:blank");
        }
        return URI.create("/errors/" + code.toLowerCase().replace('_', '-'));
    }
}