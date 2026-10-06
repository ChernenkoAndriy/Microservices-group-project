package com.epam.java.specialization.authservice.mapper;

import com.epam.java.specialization.authservice.internal.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.internal.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.internal.api.dto.UserSummaryDto;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;

import java.time.ZoneOffset;

public final class InternalUserMapper {

    private InternalUserMapper() {
    }

    public static UserSummaryDto toSummary(User user) {
        UserSummaryDto summary = new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                UserRoleDto.valueOf(user.getRole().name()),
                UserStatusDto.valueOf((user.getStatus() != null ? user.getStatus() : UserStatus.ACTIVE).name()),
                user.getCreatedAt() != null ? user.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        summary.setAvatarUrl(user.getAvatarUrl());
        return summary;
    }
}
