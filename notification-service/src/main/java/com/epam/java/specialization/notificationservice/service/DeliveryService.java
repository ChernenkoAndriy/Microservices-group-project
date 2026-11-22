package com.epam.java.specialization.notificationservice.service;

import com.epam.java.specialization.notificationservice.model.NotificationLog;
import com.epam.java.specialization.notificationservice.model.NotificationTemplate;
import com.epam.java.specialization.notificationservice.repository.NotificationLogRepository;
import com.epam.java.specialization.notificationservice.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/** Renders a template for one recipient, sends it, and records the outcome in the delivery log. */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final NotificationTemplateRepository templates;
    private final NotificationLogRepository logs;
    private final TemplateRenderer renderer;
    private final MailGateway mail;
    private final Clock clock;

    public DeliveryService(NotificationTemplateRepository templates, NotificationLogRepository logs,
                           TemplateRenderer renderer, MailGateway mail, Clock clock) {
        this.templates = templates;
        this.logs = logs;
        this.renderer = renderer;
        this.mail = mail;
        this.clock = clock;
    }

    /** @return the log entry: SENT, or FAILED (missing or inactive template, or the mail server refused) */
    public NotificationLog deliver(String eventId, String to, String templateCode, Map<String, String> variables) {
        NotificationLog entry = newEntry(eventId, to, templateCode);
        Optional<NotificationTemplate> template = templates.findById(templateCode);
        if (template.isEmpty() || !template.get().isActive()) {
            entry.setSubject(templateCode);
            entry.setStatus("FAILED");
            entry.setFailureReason(template.isEmpty() ? "Template " + templateCode + " does not exist"
                    : "Template " + templateCode + " is inactive");
            log.warn("Not sending {} to {}: {}", templateCode, to, entry.getFailureReason());
            return logs.save(entry);
        }
        String subject = renderer.renderSubject(template.get().getSubject(), variables);
        String body = renderer.renderBody(template.get().getBody(), variables);
        entry.setSubject(subject);
        try {
            mail.send(to, subject, body);
            entry.setStatus("SENT");
            entry.setSentAt(clock.instant());
        } catch (Exception e) {
            entry.setStatus("FAILED");
            entry.setFailureReason(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("Could not send {} to {}", templateCode, to, e);
        }
        return logs.save(entry);
    }

    /** Records an email that could not even be attempted (for example an event that exhausted its retries). */
    public NotificationLog recordFailure(String eventId, String to, String templateCode, String subject, String reason) {
        NotificationLog entry = newEntry(eventId, to, templateCode);
        entry.setSubject(subject);
        entry.setStatus("FAILED");
        entry.setFailureReason(reason);
        return logs.save(entry);
    }

    private NotificationLog newEntry(String eventId, String to, String templateCode) {
        NotificationLog entry = new NotificationLog();
        entry.setEventId(eventId);
        entry.setRecipientEmail(to);
        entry.setTemplate(templateCode);
        entry.setAttempts(1);
        entry.setCreatedAt(clock.instant());
        return entry;
    }
}
