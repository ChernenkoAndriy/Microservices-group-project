package com.epam.java.specialization.trackservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Turns on the retry and dead letter topics declared by {@code @RetryableTopic}. */
@Configuration
@EnableKafkaRetryTopic
public class KafkaConfig {

    /** Resumes a retry topic's partition once its back-off delay is over. */
    @Bean
    public RetryTopicSchedulerWrapper retryTopicSchedulerWrapper() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.initialize();
        return new RetryTopicSchedulerWrapper(scheduler);
    }
}
