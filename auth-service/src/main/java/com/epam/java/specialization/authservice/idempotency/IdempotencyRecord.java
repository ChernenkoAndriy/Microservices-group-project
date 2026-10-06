package com.epam.java.specialization.authservice.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(length = 320)
    private String id;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private int status;

    @Column(columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
