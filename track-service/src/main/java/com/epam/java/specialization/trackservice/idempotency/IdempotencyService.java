package com.epam.java.specialization.trackservice.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public <T> ResponseEntity<T> execute(String scope, String key, String fingerprint, Class<T> bodyType,
                                         Supplier<ResponseEntity<T>> operation) {
        if (key == null) {
            return operation.get();
        }
        String id = scope + ":" + key;
        String requestHash = sha256(fingerprint);
        Instant now = clock.instant();

        var stored = repository.findById(id).filter(r -> r.getExpiresAt().isAfter(now));
        if (stored.isPresent()) {
            IdempotencyRecord record = stored.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            log.debug("Replaying stored response for idempotency key {}", id);
            T body = record.getBody() == null ? null : jsonMapper.readValue(record.getBody(), bodyType);
            return ResponseEntity.status(record.getStatus()).header(REPLAYED_HEADER, "true").body(body);
        }

        ResponseEntity<T> response = operation.get();
        String body = response.getBody() == null ? null : jsonMapper.writeValueAsString(response.getBody());
        repository.save(new IdempotencyRecord(
                id, requestHash, response.getStatusCode().value(), body, now, now.plus(TTL)));
        return response;
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
