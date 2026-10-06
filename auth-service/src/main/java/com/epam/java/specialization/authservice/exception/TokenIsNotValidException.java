package com.epam.java.specialization.authservice.exception;

import org.springframework.http.HttpStatus;

public class TokenIsNotValidException extends ApiException {
    public TokenIsNotValidException(String message) {
        super(HttpStatus.UNAUTHORIZED, "TOKEN_IS_NOT_VALID", message);
    }
}
