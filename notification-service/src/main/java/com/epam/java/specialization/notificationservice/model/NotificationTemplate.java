package com.epam.java.specialization.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An email template. Double-brace placeholders such as the artist name are filled in when the email is rendered. */
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected NotificationTemplate() {
    }

    public NotificationTemplate(String code, String subject, String body, String description, boolean active, Instant now) {
        this.code = code;
        this.subject = subject;
        this.body = body;
        this.description = description;
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replace(String subject, String body, String description, boolean active, Instant now) {
        this.subject = subject;
        this.body = body;
        this.description = description;
        this.active = active;
        this.updatedAt = now;
    }

    public String getCode() { return code; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
