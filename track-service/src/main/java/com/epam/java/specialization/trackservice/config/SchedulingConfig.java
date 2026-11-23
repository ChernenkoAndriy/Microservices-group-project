package com.epam.java.specialization.trackservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the outbox relay ({@code OutboxRelay}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
