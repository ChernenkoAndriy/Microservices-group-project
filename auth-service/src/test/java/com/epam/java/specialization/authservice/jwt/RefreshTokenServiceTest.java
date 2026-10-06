package com.epam.java.specialization.authservice.jwt;

import com.epam.java.specialization.authservice.exception.InvalidRefreshTokenException;
import com.epam.java.specialization.authservice.model.RefreshToken;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static com.epam.java.specialization.authservice.TestUsers.listener;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("unused", Duration.ofMinutes(10), REFRESH_TTL);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);
    }

    @Test
    void issueStoresOnlyHashOfReturnedToken() {
        User user = listener(1L);

        String rawToken = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        RefreshToken token = saved.getValue();
        assertThat(rawToken).matches("[A-Za-z0-9_-]{43}");
        assertThat(token.getTokenHash()).isEqualTo(sha256(rawToken)).isNotEqualTo(rawToken);
        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt()).isCloseTo(Instant.now().plus(REFRESH_TTL), within(Duration.ofSeconds(5)));
    }

    @Test
    void issueGeneratesDistinctTokens() {
        User user = listener(1L);

        assertThat(refreshTokenService.issue(user)).isNotEqualTo(refreshTokenService.issue(user));
        verify(refreshTokenRepository, times(2)).save(any());
    }

    @Nested
    class Consume {

        @Test
        void revokesValidTokenAndReturnsOwner() {
            RefreshToken token = storedToken(false, Instant.now().plusSeconds(60));
            when(refreshTokenRepository.findForRotationByTokenHash(sha256("raw"))).thenReturn(Optional.of(token));

            User owner = refreshTokenService.consume("raw");

            assertThat(owner).isSameAs(token.getUser());
            assertThat(token.isRevoked()).isTrue();
            verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
        }

        @Test
        void rejectsUnknownToken() {
            when(refreshTokenRepository.findForRotationByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.consume("raw"))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        void rejectsExpiredToken() {
            RefreshToken token = storedToken(false, Instant.now().minusSeconds(1));
            when(refreshTokenRepository.findForRotationByTokenHash(sha256("raw"))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> refreshTokenService.consume("raw"))
                    .isInstanceOf(InvalidRefreshTokenException.class);
            assertThat(token.isRevoked()).isFalse();
            verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
        }

        @Test
        void reuseOfRevokedTokenRevokesAllTokensOfOwner() {
            RefreshToken token = storedToken(true, Instant.now().plusSeconds(60));
            when(refreshTokenRepository.findForRotationByTokenHash(sha256("raw"))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> refreshTokenService.consume("raw"))
                    .isInstanceOf(InvalidRefreshTokenException.class);
            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }
    }

    @Nested
    class Revoke {

        @Test
        void revokesActiveToken() {
            RefreshToken token = storedToken(false, Instant.now().plusSeconds(60));
            when(refreshTokenRepository.findByTokenHash(sha256("raw"))).thenReturn(Optional.of(token));

            refreshTokenService.revoke("raw");

            assertThat(token.isRevoked()).isTrue();
        }

        @Test
        void ignoresUnknownToken() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatCode(() -> refreshTokenService.revoke("raw")).doesNotThrowAnyException();
        }

        @Test
        void ignoresAlreadyRevokedToken() {
            RefreshToken token = storedToken(true, Instant.now().plusSeconds(60));
            when(refreshTokenRepository.findByTokenHash(sha256("raw"))).thenReturn(Optional.of(token));

            assertThatCode(() -> refreshTokenService.revoke("raw")).doesNotThrowAnyException();
            assertThat(token.isRevoked()).isTrue();
        }
    }

    @Test
    void revokeAllDelegatesToRepository() {
        refreshTokenService.revokeAll(5L);

        verify(refreshTokenRepository).revokeAllByUserId(5L);
    }

    @Test
    void ttlIsTakenFromProperties() {
        assertThat(refreshTokenService.getTtlSeconds()).isEqualTo(REFRESH_TTL.toSeconds());
    }

    private static RefreshToken storedToken(boolean revoked, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(10L);
        token.setUser(listener(1L));
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        return token;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
