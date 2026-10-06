package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.exception.GlobalExceptionHandler;
import com.epam.java.specialization.authservice.jwt.JwtService;
import com.epam.java.specialization.authservice.web.CurrentUserIdArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Builds a standalone MockMvc (no Spring context, no database) wired like the real application:
 * bean validation, {@link GlobalExceptionHandler} and the {@code @CurrentUserId} resolver.
 */
final class ControllerTestSupport {

    private ControllerTestSupport() {
    }

    static MockMvc mockMvc(Object controller, JwtService jwtService) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(jwtService))
                .build();
    }
}
