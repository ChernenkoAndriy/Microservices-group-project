package com.epam.java.specialization.authservice.config;

import com.epam.java.specialization.authservice.api.dto.LoginRequestDto;
import com.epam.java.specialization.authservice.api.dto.RefreshTokenRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigTest {

    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        JsonMapper.Builder builder = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        new JacksonConfig().tolerantLoginRequest().customize(builder);
        jsonMapper = builder.build();
    }

    @Test
    void loginRequestIgnoresUnknownProperties() {
        LoginRequestDto request = jsonMapper.readValue(
                "{\"email\":\"a@b.com\",\"password\":\"secret\",\"rememberMe\":true}", LoginRequestDto.class);

        assertThat(request.getEmail()).isEqualTo("a@b.com");
        assertThat(request.getPassword()).isEqualTo("secret");
    }

    @Test
    void otherRequestsStillRejectUnknownProperties() {
        assertThatThrownBy(() -> jsonMapper.readValue(
                "{\"refreshToken\":\"t\",\"extra\":1}", RefreshTokenRequestDto.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
