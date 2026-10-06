package com.epam.java.specialization.authservice.exception;

import org.springframework.http.HttpStatus;

public class UnauthenticatedException extends ApiException {
    public UnauthenticatedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
