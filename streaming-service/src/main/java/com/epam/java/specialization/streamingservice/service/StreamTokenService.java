package com.epam.java.specialization.streamingservice.service;

import com.epam.java.specialization.streamingservice.web.StreamingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Signed, short-lived playback tokens (api-contracts/streaming-service/openapi.yaml, {@code streamToken}).
 * A token is {@code base64url(payload) "." base64url(HMAC-SHA256(payload))} with the payload
 * {@code v1|userId|trackId|type|expiresAtEpochSeconds}. It is bound to the caller, the track and the type, so
 * {@code <audio src>} can stream without an Authorization header.
 */
@Service
public class StreamTokenService {

    public enum Type { STREAM, OFFLINE }

    public record Claims(long userId, long trackId, Type type, Instant expiresAt) {
    }

    public record Issued(String token, Claims claims) {
    }

    private static final String VERSION = "v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration streamTtl;
    private final Duration offlineTtl;
    private final Clock clock;

    public StreamTokenService(@Value("${stream-token.secret}") String secret,
                              @Value("${stream-token.stream-ttl:PT5M}") Duration streamTtl,
                              @Value("${stream-token.offline-ttl:PT24H}") Duration offlineTtl,
                              Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("stream-token.secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.streamTtl = streamTtl;
        this.offlineTtl = offlineTtl;
        this.clock = clock;
    }

    public Issued issue(long userId, long trackId, Type type) {
        Instant expiresAt = clock.instant().plus(type == Type.OFFLINE ? offlineTtl : streamTtl);
        Claims claims = new Claims(userId, trackId, type, expiresAt);
        String payload = String.join("|", VERSION, Long.toString(userId), Long.toString(trackId), type.name(),
                Long.toString(expiresAt.getEpochSecond()));
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return new Issued(ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(sign(payloadBytes)),
                claims);
    }

    /**
     * @throws StreamingException 401 {@code INVALID_STREAM_TOKEN} when the token is missing, malformed, forged or
     *                            expired; 403 {@code STREAM_TOKEN_MISMATCH} when it was issued for another track
     */
    public Claims verify(String token, long trackId) {
        if (token == null || token.isBlank()) {
            throw StreamingException.invalidToken("A stream token is required");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw StreamingException.invalidToken("Malformed stream token");
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = DECODER.decode(parts[0]);
            signature = DECODER.decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw StreamingException.invalidToken("Malformed stream token");
        }
        if (!MessageDigest.isEqual(sign(payload), signature)) {
            throw StreamingException.invalidToken("Invalid stream token signature");
        }
        Claims claims = parse(new String(payload, StandardCharsets.UTF_8));
        if (!clock.instant().isBefore(claims.expiresAt())) {
            throw StreamingException.invalidToken("The stream token has expired");
        }
        if (claims.trackId() != trackId) {
            throw StreamingException.tokenMismatch(trackId);
        }
        return claims;
    }

    private static Claims parse(String payload) {
        String[] fields = payload.split("\\|");
        if (fields.length != 5 || !VERSION.equals(fields[0])) {
            throw StreamingException.invalidToken("Malformed stream token");
        }
        try {
            return new Claims(Long.parseLong(fields[1]), Long.parseLong(fields[2]), Type.valueOf(fields[3]),
                    Instant.ofEpochSecond(Long.parseLong(fields[4])));
        } catch (IllegalArgumentException e) {
            throw StreamingException.invalidToken("Malformed stream token");
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
