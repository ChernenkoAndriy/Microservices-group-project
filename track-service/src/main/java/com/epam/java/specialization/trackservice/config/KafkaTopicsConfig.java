package com.epam.java.specialization.trackservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topics catalog-service produces to (api-contracts/catalog-service/asyncapi.yaml), keyed by {@code trackId}. */
@Configuration
public class KafkaTopicsConfig {

    /** Compacted: the latest snapshot of every track stays, so a new consumer can rebuild its copy. */
    @Bean
    public NewTopic trackSnapshotTopic(@Value("${events.topics.track-snapshot}") String name,
                                       @Value("${events.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).compact().build();
    }

    @Bean
    public NewTopic trackPublishedTopic(@Value("${events.topics.track-published}") String name,
                                        @Value("${events.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
