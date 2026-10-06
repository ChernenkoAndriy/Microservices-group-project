package com.epam.java.specialization.trackservice.client;

import com.epam.java.specialization.trackservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.trackservice.client.auth.dto.UserRoleDto;
import com.epam.java.specialization.trackservice.client.auth.dto.UserStatusDto;
import com.epam.java.specialization.trackservice.client.auth.dto.UserSummaryDto;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtistDirectoryTest {

    private static final AuthClientProperties PROPERTIES = new AuthClientProperties(
            URI.create("http://auth-service"), "token", Duration.ofSeconds(1), Duration.ofSeconds(1),
            new AuthClientProperties.CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(60), 2),
            new AuthClientProperties.RetrySettings(3, Duration.ofMillis(10), 2, 0.5),
            new AuthClientProperties.BulkheadSettings(1, Duration.ZERO));

    private final AuthClientConfig config = new AuthClientConfig();
    private final InternalUsersApi usersApi = mock(InternalUsersApi.class);
    private final Bulkhead bulkhead = config.authBulkhead(PROPERTIES);
    private final ArtistDirectory directory = new ArtistDirectory(
            usersApi, config.authCircuitBreaker(PROPERTIES), config.authRetry(PROPERTIES), bulkhead);

    @Test
    void fullBulkheadFallsBackInsteadOfWaiting() throws Exception {
        CountDownLatch inCall = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(usersApi.getUsersByIds(any())).thenAnswer(invocation -> {
            inCall.countDown();
            release.await(5, TimeUnit.SECONDS);
            return ResponseEntity.ok(List.of(artist(7L)));
        });

        CompletableFuture<ArtistProfiles> slowCall = CompletableFuture.supplyAsync(() -> directory.findProfile(7L));
        assertThat(inCall.await(5, TimeUnit.SECONDS)).isTrue();

        ArtistProfiles rejected = directory.findProfile(7L);
        release.countDown();

        assertThat(rejected.available()).isFalse();
        assertThat(rejected.nameOf(7L)).isEqualTo(ArtistProfiles.UNKNOWN_ARTIST);
        assertThat(slowCall.get(5, TimeUnit.SECONDS).nameOf(7L)).isEqualTo("Artist 7");
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
    }

    @Test
    void emptyLookupDoesNotCallAuthService() {
        assertThat(directory.findProfiles(List.of()).available()).isTrue();
    }

    private static UserSummaryDto artist(Long id) {
        return new UserSummaryDto(id, "artist" + id + "@example.com", "Artist " + id, UserRoleDto.ARTIST,
                UserStatusDto.ACTIVE, OffsetDateTime.parse("2026-10-01T10:00:00Z"));
    }
}
