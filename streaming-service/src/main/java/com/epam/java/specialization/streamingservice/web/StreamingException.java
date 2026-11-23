package com.epam.java.specialization.streamingservice.web;

import org.springframework.http.HttpStatus;

/** An error with a stable {@code code} for the problem body (api-contracts/README.md, "Errors"). */
public class StreamingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public StreamingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static StreamingException invalidToken(String message) {
        return new StreamingException(HttpStatus.UNAUTHORIZED, "INVALID_STREAM_TOKEN", message);
    }

    public static StreamingException tokenMismatch(Long trackId) {
        return new StreamingException(HttpStatus.FORBIDDEN, "STREAM_TOKEN_MISMATCH",
                "The token was not issued for track " + trackId);
    }

    public static StreamingException notPlayable(Long trackId) {
        return new StreamingException(HttpStatus.CONFLICT, "TRACK_NOT_PLAYABLE",
                "Track " + trackId + " has no playable audio yet");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
