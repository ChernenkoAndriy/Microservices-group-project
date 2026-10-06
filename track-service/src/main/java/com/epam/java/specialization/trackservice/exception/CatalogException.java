package com.epam.java.specialization.trackservice.exception;

import org.springframework.http.HttpStatus;

public class CatalogException extends ApiException {

    private CatalogException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static CatalogException badRequest(String code, String message) {
        return new CatalogException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static CatalogException unauthorized(String message) {
        return new CatalogException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static CatalogException forbidden(String message) {
        return new CatalogException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static CatalogException notFound(String code, String message) {
        return new CatalogException(HttpStatus.NOT_FOUND, code, message);
    }

    public static CatalogException unprocessable(String code, String message) {
        return new CatalogException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static CatalogException serviceUnavailable(String code, String message) {
        return new CatalogException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
