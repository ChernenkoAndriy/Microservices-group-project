package com.epam.java.specialization.trackservice.client;

import com.epam.java.specialization.trackservice.client.auth.dto.UserRoleDto;
import com.epam.java.specialization.trackservice.client.auth.dto.UserSummaryDto;

import java.util.Map;
import java.util.Optional;

public record ArtistProfiles(Map<Long, UserSummaryDto> users, boolean available) {

    public static final String UNKNOWN_ARTIST = "Unknown artist";

    public static ArtistProfiles unavailable() {
        return new ArtistProfiles(Map.of(), false);
    }

    public Optional<UserSummaryDto> find(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    public String nameOf(Long artistId) {
        return find(artistId).map(UserSummaryDto::getDisplayName).orElse(UNKNOWN_ARTIST);
    }

    public boolean isArtist(Long userId) {
        return find(userId).map(user -> user.getRole() == UserRoleDto.ARTIST).orElse(false);
    }
}
