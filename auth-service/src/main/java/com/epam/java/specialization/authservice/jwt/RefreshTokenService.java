package com.epam.java.specialization.authservice.jwt;

import com.epam.java.specialization.authservice.exception.InvalidRefreshTokenException;
import com.epam.java.specialization.authservice.model.RefreshToken;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes opaque refresh tokens. Only SHA-256 hashes are persisted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;

    /**
     * @return the raw token, to be handed to the client once
     */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plus(properties.refreshTtl()));
        refreshTokenRepository.save(refreshToken);

        log.debug("Issued refresh token for user id={}", user.getId());
        return rawToken;
    }

    /**
     * Revokes the given token and returns its owner. Reusing an already revoked token is treated
     * as theft: every refresh token of that user is revoked.
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired or revoked
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public User consume(String rawToken) {
        RefreshToken token = refreshTokenRepository.findForRotationByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = token.getUser();

        if (token.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
            log.warn("Revoked refresh token reused for user id={}; revoked {} active token(s)", user.getId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        token.setRevoked(true);
        log.debug("Consumed refresh token id={} of user id={}", token.getId(), user.getId());
        return user;
    }

    /**
     * Idempotent: unknown or already revoked tokens are ignored.
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.setRevoked(true);
                    log.debug("Revoked refresh token id={}", token.getId());
                });
    }

    @Transactional
    public void revokeAll(Long userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.debug("Revoked {} refresh token(s) of user id={}", revoked, userId);
    }

    public long getTtlSeconds() {
        return properties.refreshTtl().toSeconds();
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
