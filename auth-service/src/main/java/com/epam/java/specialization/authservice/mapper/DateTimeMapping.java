package com.epam.java.specialization.authservice.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Conversions MapStruct has no built-in for. Static, so mappers call it without an injected instance.
 */
public final class DateTimeMapping {

    private DateTimeMapping() {
    }

    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
