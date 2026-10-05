package com.epam.java.specialization.authservice.web;

import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.exception.UnauthenticatedException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserIdArgumentResolverTest {

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private CurrentUserIdArgumentResolver resolver;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @SuppressWarnings("unused")
    private static void handler(@CurrentUserId Long annotated, Long notAnnotated, @CurrentUserId String wrongType) {
    }

    @Test
    void supportsOnlyAnnotatedLongParameters() throws NoSuchMethodException {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isFalse();
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }

    @Test
    void prefersUserIdHeader() throws NoSuchMethodException {
        request.addHeader("X-User-Id", " 42 ");
        request.addHeader("Authorization", "Bearer token");

        assertThat(resolve()).isEqualTo(42L);
        verifyNoInteractions(jwtService);
    }

    @Test
    void rejectsNonNumericUserIdHeader() {
        request.addHeader("X-User-Id", "abc");

        assertThatThrownBy(this::resolve).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void fallsBackToBearerToken() throws NoSuchMethodException {
        request.addHeader("Authorization", "Bearer token");
        when(jwtService.extractUserId("token")).thenReturn(7L);

        assertThat(resolve()).isEqualTo(7L);
    }

    @Test
    void propagatesInvalidToken() {
        request.addHeader("Authorization", "Bearer junk");
        when(jwtService.extractUserId("junk")).thenThrow(new TokenIsNotValidException("bad"));

        assertThatThrownBy(this::resolve).isInstanceOf(TokenIsNotValidException.class);
    }

    @Test
    void rejectsRequestWithoutCredentials() {
        assertThatThrownBy(this::resolve)
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("missing");
        verifyNoInteractions(jwtService);
    }

    private Long resolve() throws NoSuchMethodException {
        return resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        return new MethodParameter(CurrentUserIdArgumentResolverTest.class
                .getDeclaredMethod("handler", Long.class, Long.class, String.class), index);
    }
}
