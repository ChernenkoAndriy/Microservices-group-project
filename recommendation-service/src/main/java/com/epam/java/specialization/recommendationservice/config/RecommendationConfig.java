package com.epam.java.specialization.recommendationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Clock;

@Configuration
public class RecommendationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The service's own topic; the consumed topics are created by their producers. */
    @Bean
    NewTopic feedServedTopic(@Value("${events.topics.feed-served}") String name,
                             @Value("${events.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
