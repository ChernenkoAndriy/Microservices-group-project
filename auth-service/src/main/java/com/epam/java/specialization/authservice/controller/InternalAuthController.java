package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.dto.UserResponse;
import com.epam.java.specialization.authservice.service.AuthService;
import com.epam.java.specialization.authservice.service.UserService;
import com.epam.java.specialization.authservice.web.BearerTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service endpoints. The gateway must not route {@code /internal/**} to clients.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * Expects the token in the {@code Authorization: Bearer <token>} header.
     */
    @GetMapping("/auth/token-is-valid")
    public ResponseEntity<Boolean> isTokenValid(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.ok(authService.isTokenValid(BearerTokens.extract(authorization)));
    }

    /**
     * Expects the token in the {@code Authorization: Bearer <token>} header.
     * Responds 401 (TOKEN_IS_NOT_VALID) or 404 (ENTITY_DOES_NOT_EXIST) on failure.
     */
    @GetMapping("/auth/get-user-id-from-token")
    public ResponseEntity<Long> getUserIdFromToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.ok(authService.getUserIdFromToken(BearerTokens.extract(authorization)));
    }

    @GetMapping("/users/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Auth service is working");
    }
}
