package com.epam.java.specialization.authservice.idempotency;

import com.epam.java.specialization.authservice.exception.ApiException;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends ApiException {

    public IdempotencyKeyReusedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                "The Idempotency-Key was already used with a different request");
    }
}
