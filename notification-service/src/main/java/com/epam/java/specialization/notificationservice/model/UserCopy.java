package com.epam.java.specialization.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Local copy of a user (email and status), kept from UserSnapshot events. */
@Entity
@Table(name = "user_copies")
public class UserCopy {

    @Id
    private Long userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Instant occurredAt;

    protected UserCopy() {
    }

    public UserCopy(Long userId, String email, String role, String status, Instant occurredAt) {
        this.userId = userId;
        update(email, role, status, occurredAt);
    }

    public void update(String email, String role, String status, Instant occurredAt) {
        this.email = email;
        this.role = role;
        this.status = status;
        this.occurredAt = occurredAt;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
}
