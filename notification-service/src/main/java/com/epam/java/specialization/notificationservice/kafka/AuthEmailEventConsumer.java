package com.epam.java.specialization.notificationservice.kafka;

import com.epam.java.specialization.notificationservice.event.AuthEmailEvent;
import com.epam.java.specialization.notificationservice.model.ProcessedEvent;
import com.epam.java.specialization.notificationservice.repository.ProcessedEventRepository;
import com.epam.java.specialization.notificationservice.service.DeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

@Component
public class AuthEmailEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuthEmailEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final DeliveryService deliveryService;
    private final JsonMapper jsonMapper;

    public AuthEmailEventConsumer(ProcessedEventRepository processedEventRepository,
                                  DeliveryService deliveryService,
                                  JsonMapper jsonMapper) {
        this.processedEventRepository = processedEventRepository;
        this.deliveryService = deliveryService;
        this.jsonMapper = jsonMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "auth.user.email", groupId = "notification-service")
    @Transactional
    public void consume(String payload) {
        AuthEmailEvent event = deserialize(payload);

        if (processedEventRepository.existsById(event.getEventId().toString())) {
            log.debug("Skipping duplicate event id={}", event.getEventId());
            return;
        }

        deliveryService.deliver(event.getEventId().toString(), event.getTo(), templateCode(event),
                event.getVariables() == null ? Map.of() : event.getVariables());
        processedEventRepository.save(new ProcessedEvent(event.getEventId().toString(), Instant.now()));
        log.info("Processed {} email to {} eventId={}", event.getTemplate(), event.getTo(), event.getEventId());
    }

    @DltHandler
    public void onDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event exhausted all retries, topic={}", topic);
        try {
            AuthEmailEvent event = deserialize(payload);
            deliveryService.recordFailure(event.getEventId().toString(), event.getTo(), templateCode(event),
                    templateCode(event), "The event exhausted all retries");
        } catch (Exception e) {
            log.error("Could not record failed DLT event from topic={}", topic, e);
        }
    }

    private static String templateCode(AuthEmailEvent event) {
        return event.getTemplate() != null ? event.getTemplate().getValue() : "UNKNOWN";
    }

    private AuthEmailEvent deserialize(String payload) {
        try {
            return jsonMapper.readValue(payload, AuthEmailEvent.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot deserialize AuthEmailEvent: " + e.getMessage(), e);
        }
    }
}
