package com.epam.java.specialization.authservice.mapper;

import com.epam.java.specialization.authservice.api.dto.PageMetadataDto;
import com.epam.java.specialization.authservice.api.dto.RegistrationRoleDto;
import com.epam.java.specialization.authservice.api.dto.UserPageDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserProfileDto toProfile(User user) {
        UserProfileDto profile = new UserProfileDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                toDto(user.getRole()),
                toDto(user.getStatus() != null ? user.getStatus() : UserStatus.ACTIVE),
                toOffsetDateTime(user.getCreatedAt()),
                toOffsetDateTime(user.getUpdatedAt()));
        profile.setAvatarUrl(user.getAvatarUrl());
        return profile;
    }

    public static UserPageDto toPage(Page<User> page) {
        return new UserPageDto(
                page.map(UserMapper::toProfile).getContent(),
                new PageMetadataDto(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    public static Role toModel(UserRoleDto role) {
        return role == null ? null : Role.valueOf(role.name());
    }

    public static Role toModel(RegistrationRoleDto role) {
        return role == null ? null : Role.valueOf(role.name());
    }

    public static UserStatus toModel(UserStatusDto status) {
        return status == null ? null : UserStatus.valueOf(status.name());
    }

    private static UserRoleDto toDto(Role role) {
        return role == null ? null : UserRoleDto.valueOf(role.name());
    }

    private static UserStatusDto toDto(UserStatus status) {
        return UserStatusDto.valueOf(status.name());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
