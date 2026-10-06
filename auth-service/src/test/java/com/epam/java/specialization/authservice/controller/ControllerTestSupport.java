package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.exception.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

final class ControllerTestSupport {

    private ControllerTestSupport() {
    }

    static MockMvc mockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
