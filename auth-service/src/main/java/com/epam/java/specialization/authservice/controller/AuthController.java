package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.api.AuthenticationApi;
import com.epam.java.specialization.authservice.api.dto.LoginRequestDto;
import com.epam.java.specialization.authservice.api.dto.LogoutRequestDto;
import com.epam.java.specialization.authservice.api.dto.RefreshTokenRequestDto;
import com.epam.java.specialization.authservice.api.dto.RegisterRequestDto;
import com.epam.java.specialization.authservice.api.dto.TokenResponseDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.idempotency.IdempotencyService;
import com.epam.java.specialization.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthenticationApi {

    private final AuthService authService;
    private final IdempotencyService idempotencyService;

    @Override
    public ResponseEntity<UserProfileDto> register(RegisterRequestDto request, String idempotencyKey) {
        return idempotencyService.execute("register", idempotencyKey, fingerprint(request), UserProfileDto.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request)));
    }

    @Override
    public ResponseEntity<TokenResponseDto> login(LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Override
    public ResponseEntity<TokenResponseDto> refreshTokens(RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @Override
    public ResponseEntity<Void> logout(LogoutRequestDto request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private static String fingerprint(RegisterRequestDto request) {
        return request.getEmail().toLowerCase(Locale.ROOT) + "|" + request.getDisplayName() + "|" + request.getRole();
    }
}
