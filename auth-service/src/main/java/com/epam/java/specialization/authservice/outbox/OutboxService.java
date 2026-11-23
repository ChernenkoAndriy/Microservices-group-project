package com.epam.java.specialization.authservice.outbox;

import com.epam.java.specialization.authservice.tracing.TraceContextCarrier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.epam.java.specialization.authservice.model.User;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final TraceContextCarrier traceContext;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, String messageKey, Object payload) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize outbox payload for topic " + topic, e);
        }
        repository.save(OutboxEvent.pending(UUID.randomUUID(), topic, messageKey, json, clock.instant(),
                traceContext.currentTraceparent()));
    }

    /** Puts the full current state of the user on {@code auth.user.snapshot} (api-contracts/auth-service/asyncapi.yaml). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveUserSnapshot(User user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("occurredAt", clock.instant().toString());
        payload.put("userId", user.getId());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        payload.put("status", user.getStatus().name());
        save("auth.user.snapshot", user.getId().toString(), payload);
    }
}
