package com.epam.java.specialization.authservice.exception;

import org.springframework.http.HttpStatus;

public class EntityDoesNotExistException extends ApiException {
    public EntityDoesNotExistException(String message) {
        super(HttpStatus.NOT_FOUND, "ENTITY_DOES_NOT_EXIST", message);
    }
}
