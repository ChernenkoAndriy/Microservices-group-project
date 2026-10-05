package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.dto.LoginRequest;
import com.epam.java.specialization.authservice.dto.RegisterRequest;
import com.epam.java.specialization.authservice.dto.TokenResponse;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Expects the token in the {@code Authorization: Bearer <token>} header.
     */
    @GetMapping("/token-is-valid")
    public ResponseEntity<Boolean> isTokenValid(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.ok(authService.isTokenValid(extractToken(authorization)));
    }

    /**
     * Expects the token in the {@code Authorization: Bearer <token>} header.
     * Responds 401 (TOKEN_IS_NOT_VALID) or 404 (ENTITY_DOES_NOT_EXIST) on failure.
     */
    @GetMapping("/get-user-id-from-token")
    public ResponseEntity<Long> getUserIdFromToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.ok(authService.getUserIdFromToken(extractToken(authorization)));
    }

    @GetMapping("/users/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(authService.getUserByEmail(email));
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Auth service is working");
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return authorization;
    }
}
