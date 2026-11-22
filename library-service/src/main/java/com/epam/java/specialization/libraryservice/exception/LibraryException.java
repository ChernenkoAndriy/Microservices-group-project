package com.epam.java.specialization.libraryservice.exception;

import org.springframework.http.HttpStatus;

public class LibraryException extends ApiException {

    private LibraryException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static LibraryException badRequest(String code, String message) {
        return new LibraryException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static LibraryException unauthorized(String message) {
        return new LibraryException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static LibraryException notFound(String code, String message) {
        return new LibraryException(HttpStatus.NOT_FOUND, code, message);
    }

    public static LibraryException conflict(String code, String message) {
        return new LibraryException(HttpStatus.CONFLICT, code, message);
    }

    public static LibraryException unprocessable(String code, String message) {
        return new LibraryException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static LibraryException serviceUnavailable(String code, String message) {
        return new LibraryException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
