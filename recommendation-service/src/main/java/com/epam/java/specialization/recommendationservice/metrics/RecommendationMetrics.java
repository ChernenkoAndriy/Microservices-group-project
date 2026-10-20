package com.epam.java.specialization.recommendationservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the recommendation service, exported at {@code /actuator/prometheus}.
 * <ul>
 *   <li>{@code spotty_analytics_event_save_seconds} (Timer): how long writing a listen event to ClickHouse takes.</li>
 * </ul>
 */
@Component
public class RecommendationMetrics {

    public static final String EVENT_SAVE = "spotty.analytics.event.save";

    private final Timer eventSave;

    public RecommendationMetrics(MeterRegistry registry) {
        this.eventSave = Timer.builder(EVENT_SAVE)
                .description("Time to write a listen event to ClickHouse")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordEventSave(Runnable save) {
        eventSave.record(save);
    }
}
