package com.epam.java.specialization.authservice.config;

import com.epam.java.specialization.authservice.api.dto.LoginRequestDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Unknown properties are rejected everywhere ({@code fail-on-unknown-properties}), except in the login request:
     * it is a tolerant reader, so clients that send extra fields can still log in.
     */
    @Bean
    public JsonMapperBuilderCustomizer tolerantLoginRequest() {
        return builder -> builder.addMixIn(LoginRequestDto.class, IgnoreUnknownProperties.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    interface IgnoreUnknownProperties {
    }
}
