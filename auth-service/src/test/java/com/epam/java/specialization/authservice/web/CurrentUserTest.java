package com.epam.java.specialization.authservice.web;

import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.exception.UnauthenticatedException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserTest {

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private CurrentUser currentUser;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsCurrentRequest() {
        request.addHeader("X-User-Id", "42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(currentUser.id()).isEqualTo(42L);
    }

    @Test
    void prefersUserIdHeader() {
        request.addHeader("X-User-Id", " 42 ");
        request.addHeader("Authorization", "Bearer token");

        assertThat(currentUser.resolve(request)).isEqualTo(42L);
        verifyNoInteractions(jwtService);
    }

    @Test
    void rejectsNonNumericUserIdHeader() {
        request.addHeader("X-User-Id", "abc");

        assertThatThrownBy(() -> currentUser.resolve(request)).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void fallsBackToBearerToken() {
        request.addHeader("Authorization", "Bearer token");
        when(jwtService.extractUserId("token")).thenReturn(7L);

        assertThat(currentUser.resolve(request)).isEqualTo(7L);
    }

    @Test
    void propagatesInvalidToken() {
        request.addHeader("Authorization", "Bearer junk");
        when(jwtService.extractUserId("junk")).thenThrow(new TokenIsNotValidException("bad"));

        assertThatThrownBy(() -> currentUser.resolve(request)).isInstanceOf(TokenIsNotValidException.class);
    }

    @Test
    void rejectsRequestWithoutCredentials() {
        assertThatThrownBy(() -> currentUser.resolve(request))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("missing");
        verifyNoInteractions(jwtService);
    }
}
