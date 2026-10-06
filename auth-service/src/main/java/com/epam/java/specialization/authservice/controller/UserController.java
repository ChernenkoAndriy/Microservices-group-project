package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.dto.UpdateProfileRequest;
import com.epam.java.specialization.authservice.dto.UserResponse;
import com.epam.java.specialization.authservice.service.UserService;
import com.epam.java.specialization.authservice.web.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@CurrentUserId Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@CurrentUserId Long userId,
                                                        @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }
}
