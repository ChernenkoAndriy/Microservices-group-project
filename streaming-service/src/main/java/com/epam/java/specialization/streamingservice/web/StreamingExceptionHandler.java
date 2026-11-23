package com.epam.java.specialization.streamingservice.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns {@link StreamingException} into an RFC 9457 problem body with the {@code code} extension. */
@RestControllerAdvice
public class StreamingExceptionHandler {

    @ExceptionHandler(StreamingException.class)
    public ResponseEntity<ProblemDetail> handle(StreamingException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        problem.setProperty("code", e.getCode());
        return ResponseEntity.status(e.getStatus()).body(problem);
    }

    @ExceptionHandler(ByteRange.Unsatisfiable.class)
    public ResponseEntity<ProblemDetail> handle(ByteRange.Unsatisfiable e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                e.getMessage());
        problem.setProperty("code", "RANGE_NOT_SATISFIABLE");
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + e.getSize())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
