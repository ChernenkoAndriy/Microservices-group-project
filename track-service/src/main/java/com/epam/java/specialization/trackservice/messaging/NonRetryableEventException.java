package com.epam.java.specialization.trackservice.messaging;

/** An event that can never be applied, however often it is retried; it goes straight to the dead letter topic. */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
