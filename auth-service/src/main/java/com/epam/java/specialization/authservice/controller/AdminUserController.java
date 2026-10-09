package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.api.UserAdministrationApi;
import com.epam.java.specialization.authservice.api.dto.AdminUpdateUserRequestDto;
import com.epam.java.specialization.authservice.api.dto.UserPageDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.mapper.UserMapper;
import com.epam.java.specialization.authservice.service.UserService;
import com.epam.java.specialization.authservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminUserController implements UserAdministrationApi {

    private final UserService userService;
    private final CurrentUser currentUser;
    private final UserMapper userMapper;

    @Override
    public ResponseEntity<UserPageDto> listUsers(String q, UserRoleDto role, UserStatusDto status,
                                                 Integer page, Integer size) {
        return ResponseEntity.ok(userService.listUsers(
                q, userMapper.toModel(role), userMapper.toModel(status), page, size));
    }

    @Override
    public ResponseEntity<UserProfileDto> updateUser(Long userId, AdminUpdateUserRequestDto request) {
        return ResponseEntity.ok(userService.updateUser(currentUser.id(), userId, request));
    }
}
