package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.api.ProfileApi;
import com.epam.java.specialization.authservice.api.dto.UpdateProfileRequestDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.service.UserService;
import com.epam.java.specialization.authservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements ProfileApi {

    private final UserService userService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<UserProfileDto> getMyProfile() {
        return ResponseEntity.ok(userService.getProfile(currentUser.id()));
    }

    @Override
    public ResponseEntity<UserProfileDto> updateMyProfile(UpdateProfileRequestDto request) {
        return ResponseEntity.ok(userService.updateProfile(currentUser.id(), request));
    }
}
