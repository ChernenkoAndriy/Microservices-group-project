package com.epam.java.specialization.apigateway.resilience;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Answers for a route whose circuit breaker gave up on the downstream service (see {@code CircuitBreaker} in
 * application.yaml), so the client gets a problem body instead of a hanging request or a raw stack trace.
 */
@RestController
public class FallbackController {

    public static final String PATH = "/fallback";

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping(PATH)
    public ResponseEntity<ProblemDetail> fallback(HttpServletRequest request) {
        Throwable error = (Throwable) request.getAttribute(MvcUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        Object route = request.getAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR);
        log.warn("Route {} failed: {}", route, error == null ? "unknown error" : error.toString());

        ProblemDetail problem;
        if (error instanceof CallNotPermittedException) {
            problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "The service is failing and temporarily switched off, try again later");
        } else if (error instanceof BulkheadFullException) {
            problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_OVERLOADED",
                    "The service is handling too many requests, try again later");
        } else if (isTimeout(error)) {
            problem = problem(HttpStatus.GATEWAY_TIMEOUT, "SERVICE_TIMEOUT",
                    "The service did not answer in time");
        } else {
            problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "The service is unavailable, try again later");
        }
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
