package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.dto.AdminUpdateUserRequest;
import com.epam.java.specialization.authservice.dto.UserPage;
import com.epam.java.specialization.authservice.dto.UserResponse;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.UserStatus;
import com.epam.java.specialization.authservice.service.UserService;
import com.epam.java.specialization.authservice.web.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only user management. Role checks are not enforced here yet (the gateway is meant to do that).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
//@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserPage> listUsers(
            @RequestParam(required = false) @Size(min = 1, max = 100) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(userService.listUsers(q, role, status, page, size));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(@CurrentUserId Long callerId,
                                                   @PathVariable @Positive Long userId,
                                                   @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(callerId, userId, request));
    }
}
