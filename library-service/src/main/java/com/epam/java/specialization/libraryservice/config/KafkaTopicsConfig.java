package com.epam.java.specialization.libraryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topics library-service produces to (api-contracts/library-service/asyncapi.yaml). */
@Configuration
public class KafkaTopicsConfig {

    /** Keyed by user id: one user's like and unlike of a track stay in order. */
    @Bean
    public NewTopic trackLikesTopic(@Value("${events.topics.track-likes}") String name,
                                    @Value("${events.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    /** Keyed by artist id: the changes to one artist's followers stay in order. */
    @Bean
    public NewTopic artistFollowsTopic(@Value("${events.topics.artist-follows}") String name,
                                       @Value("${events.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
