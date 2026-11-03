package com.epam.java.specialization.notificationservice.kafka;

import com.epam.java.specialization.notificationservice.event.AuthEmailEvent;
import com.epam.java.specialization.notificationservice.model.NotificationLog;
import com.epam.java.specialization.notificationservice.model.ProcessedEvent;
import com.epam.java.specialization.notificationservice.repository.NotificationLogRepository;
import com.epam.java.specialization.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class AuthEmailEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuthEmailEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final JsonMapper jsonMapper;

    public AuthEmailEventConsumer(ProcessedEventRepository processedEventRepository,
                                  NotificationLogRepository notificationLogRepository,
                                  JsonMapper jsonMapper) {
        this.processedEventRepository = processedEventRepository;
        this.notificationLogRepository = notificationLogRepository;
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

        deliver(event, "SENT");
        processedEventRepository.save(new ProcessedEvent(event.getEventId().toString(), Instant.now()));
        log.info("Processed {} email to {} eventId={}", event.getTemplate(), event.getTo(), event.getEventId());
    }

    @DltHandler
    public void onDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event exhausted all retries, topic={}", topic);
        try {
            AuthEmailEvent event = deserialize(payload);
            deliver(event, "FAILED");
        } catch (Exception e) {
            log.error("Could not record failed DLT event from topic={}", topic, e);
        }
    }

    private void deliver(AuthEmailEvent event, String status) {
        // Real email sending (e.g. JavaMailSender) goes here; for now we record the delivery.
        NotificationLog entry = new NotificationLog();
        entry.setEventId(event.getEventId().toString());
        entry.setRecipientEmail(event.getTo());
        entry.setTemplate(event.getTemplate() != null ? event.getTemplate().getValue() : "UNKNOWN");
        entry.setSubject(subjectFor(event.getTemplate()));
        entry.setStatus(status);
        entry.setCreatedAt(Instant.now());
        notificationLogRepository.save(entry);
    }

    private AuthEmailEvent deserialize(String payload) {
        try {
            return jsonMapper.readValue(payload, AuthEmailEvent.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot deserialize AuthEmailEvent: " + e.getMessage(), e);
        }
    }

    private static String subjectFor(AuthEmailEvent.TemplateEnum template) {
        if (template == null) return "Spotty notification";
        return switch (template) {
            case WELCOME -> "Welcome to Spotty!";
            case ACCOUNT_BLOCKED -> "Your Spotty account has been suspended";
            case ACCOUNT_UNBLOCKED -> "Your Spotty account has been restored";
        };
    }
}
