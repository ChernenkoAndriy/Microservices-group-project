package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.internal.api.InternalUsersApi;
import com.epam.java.specialization.authservice.internal.api.dto.UserSummaryDto;
import com.epam.java.specialization.authservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class InternalAuthController implements InternalUsersApi {

    private final UserService userService;

    @Override
    public ResponseEntity<List<UserSummaryDto>> getUsersByIds(Set<Long> ids) {
        return ResponseEntity.ok(userService.getUsersByIds(ids));
    }
}
