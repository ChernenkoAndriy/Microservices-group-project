package com.epam.java.specialization.authservice.web;

import com.epam.java.specialization.authservice.exception.UnauthenticatedException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUserId} parameters.
 * <p>
 * The gateway validates the JWT and forwards the caller's id in {@code X-User-Id}; that header wins when present.
 * Otherwise the id is read from the {@code Authorization: Bearer} token, so the service can also be called directly.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final JwtService jwtService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class) && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userIdHeader = webRequest.getHeader(USER_ID_HEADER);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return Long.valueOf(userIdHeader.trim());
            } catch (NumberFormatException e) {
                throw new UnauthenticatedException(USER_ID_HEADER + " header is not a valid user id");
            }
        }

        String token = BearerTokens.extract(webRequest.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            throw new UnauthenticatedException("The access token is missing");
        }
        return jwtService.extractUserId(token);
    }
}
