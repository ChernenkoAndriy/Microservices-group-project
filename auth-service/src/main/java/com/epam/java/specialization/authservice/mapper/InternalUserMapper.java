package com.epam.java.specialization.authservice.mapper;

import com.epam.java.specialization.authservice.internal.api.dto.UserSummaryDto;
import com.epam.java.specialization.authservice.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = DateTimeMapping.class)
public interface InternalUserMapper {

    @Mapping(target = "displayName", source = "username")
    @Mapping(target = "status", source = "status", defaultValue = "ACTIVE")
    UserSummaryDto toSummary(User user);
}
