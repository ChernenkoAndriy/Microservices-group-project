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
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(uses = DateTimeMapping.class)
public interface UserMapper {

    @Mapping(target = "displayName", source = "username")
    @Mapping(target = "status", source = "status", defaultValue = "ACTIVE")
    UserProfileDto toProfile(User user);

    default UserPageDto toPage(Page<User> page) {
        return new UserPageDto(page.map(this::toProfile).getContent(), toPageMetadata(page));
    }

    PageMetadataDto toPageMetadata(Page<?> page);

    Role toModel(UserRoleDto role);

    Role toModel(RegistrationRoleDto role);

    UserStatus toModel(UserStatusDto status);
}
