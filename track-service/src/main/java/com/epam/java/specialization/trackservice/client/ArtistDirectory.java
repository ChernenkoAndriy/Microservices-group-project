package com.epam.java.specialization.trackservice.client;

import com.epam.java.specialization.trackservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.trackservice.client.auth.dto.UserSummaryDto;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistDirectory {

    private final InternalUsersApi usersApi;
    private final CircuitBreaker authCircuitBreaker;
    private final Retry authRetry;
    private final Bulkhead authBulkhead;

    public ArtistProfiles findProfiles(Collection<Long> artistIds) {
        if (artistIds.isEmpty()) {
            return new ArtistProfiles(Map.of(), true);
        }
        Set<Long> ids = new LinkedHashSet<>(artistIds);
        Supplier<ArtistProfiles> call = () -> toProfiles(usersApi.getUsersByIds(ids).getBody());

        Supplier<ArtistProfiles> guarded = Retry.decorateSupplier(authRetry,
                CircuitBreaker.decorateSupplier(authCircuitBreaker,
                        Bulkhead.decorateSupplier(authBulkhead, call)));
        try {
            return guarded.get();
        } catch (RuntimeException e) {
            return fallback(e);
        }
    }

    public ArtistProfiles findProfile(Long artistId) {
        return findProfiles(List.of(artistId));
    }

    private ArtistProfiles fallback(Throwable e) {
        log.warn("auth-service lookup failed ({}), using the fallback: artists are shown as '{}'",
                e.toString(), ArtistProfiles.UNKNOWN_ARTIST);
        return ArtistProfiles.unavailable();
    }

    private static ArtistProfiles toProfiles(List<UserSummaryDto> users) {
        Map<Long, UserSummaryDto> byId = users == null ? Map.of()
                : users.stream().collect(Collectors.toMap(UserSummaryDto::getId, Function.identity(), (a, b) -> a));
        return new ArtistProfiles(byId, true);
    }
}
