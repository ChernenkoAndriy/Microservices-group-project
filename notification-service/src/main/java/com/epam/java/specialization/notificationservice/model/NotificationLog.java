package com.epam.java.specialization.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 36)
    private String eventId;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false, length = 50)
    private String template;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 20)
    private String status;

    /** Nullable: rows written before attempts were counted have none (read as 1). */
    private Integer attempts;

    @Column(length = 1000)
    private String failureReason;

    private Instant sentAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAttempts() { return attempts == null ? 1 : attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason == null ? null : failureReason.substring(0, Math.min(failureReason.length(), 1000));
    }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
