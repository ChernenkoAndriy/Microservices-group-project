package com.epam.java.specialization.authservice.exception;

import org.springframework.http.HttpStatus;

public class SelfModificationForbiddenException extends ApiException {
    public SelfModificationForbiddenException() {
        super(HttpStatus.CONFLICT, "SELF_MODIFICATION_FORBIDDEN",
                "An administrator cannot block or demote their own account");
    }
}
