package com.epam.java.specialization.trackservice.client;

import com.epam.java.specialization.trackservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.trackservice.client.auth.dto.UserRoleDto;
import com.epam.java.specialization.trackservice.client.auth.dto.UserStatusDto;
import com.epam.java.specialization.trackservice.client.auth.dto.UserSummaryDto;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:directory;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "resilience4j.bulkhead.instances.auth-service.max-concurrent-calls=1",
        "resilience4j.bulkhead.instances.auth-service.max-wait-duration=0",
})
class ArtistDirectoryTest {

    @MockitoBean
    private InternalUsersApi usersApi;
    @Autowired
    private ArtistDirectory directory;
    @Autowired
    private BulkheadRegistry bulkheads;

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
        Bulkhead bulkhead = bulkheads.bulkhead(AuthClientConfig.AUTH_SERVICE);
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
    }

    @Test
    void emptyLookupDoesNotCallAuthService() {
        assertThat(directory.findProfiles(List.of()).available()).isTrue();
        verifyNoInteractions(usersApi);
    }

    private static UserSummaryDto artist(Long id) {
        return new UserSummaryDto(id, "artist" + id + "@example.com", "Artist " + id, UserRoleDto.ARTIST,
                UserStatusDto.ACTIVE, OffsetDateTime.parse("2026-10-01T10:00:00Z"));
    }
}
