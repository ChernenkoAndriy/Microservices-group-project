package com.epam.java.specialization.authservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, String messageKey, Object payload) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize outbox payload for topic " + topic, e);
        }
        repository.save(OutboxEvent.pending(UUID.randomUUID(), topic, messageKey, json, clock.instant()));
    }
}
