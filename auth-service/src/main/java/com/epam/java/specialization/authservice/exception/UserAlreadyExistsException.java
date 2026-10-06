package com.epam.java.specialization.authservice.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApiException {

    private UserAlreadyExistsException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public static UserAlreadyExistsException emailTaken(String email) {
        return new UserAlreadyExistsException("EMAIL_ALREADY_REGISTERED", "Email is already registered: " + email);
    }

    public static UserAlreadyExistsException usernameTaken(String username) {
        return new UserAlreadyExistsException("USERNAME_ALREADY_TAKEN", "Username is already taken: " + username);
    }
}
