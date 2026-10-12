package com.epam.java.specialization.streamingservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    /** Keyed by {@code trackId}, so every event of one track lands in one partition, in order. */
    @Bean
    public NewTopic assetReadyTopic(@Value("${events.topics.asset-ready}") String name,
                                    @Value("${events.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
