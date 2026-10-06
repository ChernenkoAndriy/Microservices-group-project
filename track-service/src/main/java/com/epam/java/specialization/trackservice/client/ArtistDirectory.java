package com.epam.java.specialization.trackservice.client;

import com.epam.java.specialization.trackservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.trackservice.client.auth.dto.UserSummaryDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.java.specialization.trackservice.client.AuthClientConfig.AUTH_SERVICE;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistDirectory {

    private final InternalUsersApi usersApi;

    @Retry(name = AUTH_SERVICE, fallbackMethod = "fallback")
    @CircuitBreaker(name = AUTH_SERVICE)
    @Bulkhead(name = AUTH_SERVICE)
    public ArtistProfiles findProfiles(Collection<Long> artistIds) {
        if (artistIds.isEmpty()) {
            return new ArtistProfiles(Map.of(), true);
        }
        return fetch(new LinkedHashSet<>(artistIds));
    }

    @Retry(name = AUTH_SERVICE, fallbackMethod = "fallback")
    @CircuitBreaker(name = AUTH_SERVICE)
    @Bulkhead(name = AUTH_SERVICE)
    public ArtistProfiles findProfile(Long artistId) {
        return fetch(Set.of(artistId));
    }

    private ArtistProfiles fallback(Collection<Long> artistIds, Throwable e) {
        return unavailable(e);
    }

    private ArtistProfiles fallback(Long artistId, Throwable e) {
        return unavailable(e);
    }

    private ArtistProfiles unavailable(Throwable e) {
        log.warn("auth-service lookup failed ({}), using the fallback: artists are shown as '{}'",
                e.toString(), ArtistProfiles.UNKNOWN_ARTIST);
        return ArtistProfiles.unavailable();
    }

    private ArtistProfiles fetch(Set<Long> ids) {
        return toProfiles(usersApi.getUsersByIds(ids).getBody());
    }

    private static ArtistProfiles toProfiles(List<UserSummaryDto> users) {
        Map<Long, UserSummaryDto> byId = users == null ? Map.of()
                : users.stream().collect(Collectors.toMap(UserSummaryDto::getId, Function.identity(), (a, b) -> a));
        return new ArtistProfiles(byId, true);
    }
}
