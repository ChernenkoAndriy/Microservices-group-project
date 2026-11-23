package com.epam.java.specialization.streamingservice.service;

import com.epam.java.specialization.streamingservice.web.StreamingException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamTokenServiceTest {

    private static final String SECRET = "test-stream-token-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private final StreamTokenService service = service(NOW);

    @Test
    void issuedTokenVerifiesForItsTrack() {
        StreamTokenService.Issued issued = service.issue(42, 7, StreamTokenService.Type.STREAM);

        StreamTokenService.Claims claims = service.verify(issued.token(), 7);

        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.type()).isEqualTo(StreamTokenService.Type.STREAM);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void offlineTokensLiveADay() {
        StreamTokenService.Issued issued = service.issue(42, 7, StreamTokenService.Type.OFFLINE);

        assertThat(issued.claims().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(service(NOW.plus(Duration.ofHours(23))).verify(issued.token(), 7).type())
                .isEqualTo(StreamTokenService.Type.OFFLINE);
    }

    @Test
    void tokenForAnotherTrackIsAMismatch() {
        String token = service.issue(42, 7, StreamTokenService.Type.STREAM).token();

        assertThatThrownBy(() -> service.verify(token, 8))
                .isInstanceOfSatisfying(StreamingException.class,
                        e -> assertThat(e.getCode()).isEqualTo("STREAM_TOKEN_MISMATCH"));
    }

    @Test
    void expiredTokenIsInvalid() {
        String token = service.issue(42, 7, StreamTokenService.Type.STREAM).token();

        assertThatThrownBy(() -> service(NOW.plus(Duration.ofMinutes(5))).verify(token, 7))
                .isInstanceOfSatisfying(StreamingException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_STREAM_TOKEN"));
    }

    @Test
    void tamperedOrMissingTokensAreInvalid() {
        String token = service.issue(42, 7, StreamTokenService.Type.STREAM).token();
        String otherUser = service.issue(43, 7, StreamTokenService.Type.STREAM).token();
        String forged = otherUser.split("\\.")[0] + "." + token.split("\\.")[1];
        String signedElsewhere = new StreamTokenService("another-secret-0123456789abcdefghijklmnop",
                Duration.ofMinutes(5), Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC))
                .issue(42, 7, StreamTokenService.Type.STREAM).token();

        for (String bad : new String[]{null, "", "abc", "a.b.c", forged, signedElsewhere}) {
            assertThatThrownBy(() -> service.verify(bad, 7))
                    .isInstanceOfSatisfying(StreamingException.class,
                            e -> assertThat(e.getCode()).isEqualTo("INVALID_STREAM_TOKEN"));
        }
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new StreamTokenService("short", Duration.ofMinutes(5), Duration.ofHours(24),
                Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }

    private static StreamTokenService service(Instant now) {
        return new StreamTokenService(SECRET, Duration.ofMinutes(5), Duration.ofHours(24),
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
