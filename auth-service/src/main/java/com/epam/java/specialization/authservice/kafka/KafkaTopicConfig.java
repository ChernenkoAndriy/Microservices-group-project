package com.epam.java.specialization.authservice.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic authUserEmailTopic() {
        return TopicBuilder.name("auth.user.email")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
