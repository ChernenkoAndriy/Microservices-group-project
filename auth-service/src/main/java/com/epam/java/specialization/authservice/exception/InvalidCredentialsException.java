package com.epam.java.specialization.authservice.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Wrong email or password");
    }
}
