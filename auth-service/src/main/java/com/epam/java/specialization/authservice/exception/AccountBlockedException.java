package com.epam.java.specialization.authservice.exception;

import org.springframework.http.HttpStatus;

public class AccountBlockedException extends ApiException {
    public AccountBlockedException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", "The account is blocked");
    }
}
