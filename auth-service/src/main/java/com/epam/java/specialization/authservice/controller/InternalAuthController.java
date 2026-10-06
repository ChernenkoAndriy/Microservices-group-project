package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.internal.api.InternalTokensApi;
import com.epam.java.specialization.authservice.internal.api.InternalUsersApi;
import com.epam.java.specialization.authservice.internal.api.dto.UserSummaryDto;
import com.epam.java.specialization.authservice.service.AuthService;
import com.epam.java.specialization.authservice.service.UserService;
import com.epam.java.specialization.authservice.web.BearerTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class InternalAuthController implements InternalTokensApi, InternalUsersApi {

    private final AuthService authService;
    private final UserService userService;

    @Override
    public ResponseEntity<Boolean> isTokenValid(String authorization) {
        return ResponseEntity.ok(authService.isTokenValid(BearerTokens.extract(authorization)));
    }

    @Override
    public ResponseEntity<Long> getUserIdFromToken(String authorization) {
        return ResponseEntity.ok(authService.getUserIdFromToken(BearerTokens.extract(authorization)));
    }

    @Override
    public ResponseEntity<List<UserSummaryDto>> getUsersByIds(Set<Long> ids) {
        return ResponseEntity.ok(userService.getUsersByIds(ids));
    }

    @Override
    public ResponseEntity<UserSummaryDto> getUserByEmail(String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }
}
