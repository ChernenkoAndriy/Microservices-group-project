package com.epam.java.specialization.authservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the auth service, exported at {@code /actuator/prometheus}.
 * <ul>
 *   <li>{@code spotty_auth_logins_total} (Counter): login attempts, tagged with
 *       {@code outcome} = {@code success}, {@code invalid_credentials} or {@code blocked}.</li>
 * </ul>
 */
@Component
public class AuthMetrics {

    public static final String LOGINS = "spotty.auth.logins";

    private final Counter loginSuccess;
    private final Counter loginInvalidCredentials;
    private final Counter loginBlocked;

    public AuthMetrics(MeterRegistry registry) {
        this.loginSuccess = loginCounter(registry, "success");
        this.loginInvalidCredentials = loginCounter(registry, "invalid_credentials");
        this.loginBlocked = loginCounter(registry, "blocked");
    }

    private static Counter loginCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(LOGINS)
                .description("Login attempts by outcome")
                .tag("outcome", outcome)
                .register(registry);
    }

    public void loginSucceeded() {
        loginSuccess.increment();
    }

    public void loginRejectedInvalidCredentials() {
        loginInvalidCredentials.increment();
    }

    public void loginRejectedBlocked() {
        loginBlocked.increment();
    }
}
