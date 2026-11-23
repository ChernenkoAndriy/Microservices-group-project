package com.epam.java.specialization.trackservice.outbox;

import com.epam.java.specialization.trackservice.client.ArtistDirectory;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.GenreRepository;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import com.epam.java.specialization.trackservice.service.TrackService;
import com.epam.java.specialization.trackservice.support.PostgresTestcontainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Transactional Outbox end to end on real infrastructure: PostgreSQL (the relay's FOR UPDATE SKIP LOCKED query
 * does not run on H2) and a Kafka broker, both started by Testcontainers.
 * Publishing a track writes the event to outbox_event in the same transaction, the relay sends it to Kafka
 * and marks the row as sent.
 */
@SpringBootTest(properties = {
        "outbox.relay.enabled=true",
        "outbox.relay.poll-interval-ms=200",
        "spring.kafka.admin.auto-create=true",
})
@Import({PostgresTestcontainer.class, OutboxToKafkaIntegrationTest.KafkaTestcontainer.class})
class OutboxToKafkaIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTestcontainer {

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
        }
    }

    @Autowired
    private TrackService trackService;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private KafkaContainer kafka;

    @MockitoBean
    private ArtistDirectory artistDirectory;

    @Value("${events.topics.track-published}")
    private String publishedTopic;

    @Test
    void publishedTrackEventIsSavedInTheOutboxAndDeliveredToKafka() {
        Long trackId = trackRepository.save(awaitingAudio("Outbox Anthem")).getId();

        trackService.publish(trackId, 180);

        assertThat(outbox.findAll())
                .as("the event is stored in the same transaction as the track")
                .anyMatch(event -> event.getTopic().equals(publishedTopic)
                        && event.getMessageKey().equals(trackId.toString()));

        List<ConsumerRecord<String, String>> records = readTopic(publishedTopic, trackId.toString());
        assertThat(records).as("the relay delivered the event to Kafka").isNotEmpty();
        assertThat(records.getFirst().value()).contains(trackId.toString());

        await().atMost(WAIT).untilAsserted(() -> assertThat(outbox.countBySentAtIsNull()).isZero());
    }

    private List<ConsumerRecord<String, String>> readTopic(String topic, String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(WAIT);
            while (found.isEmpty() && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(item -> {
                    if (key.equals(item.key())) {
                        found.add(item);
                    }
                });
            }
        }
        return found;
    }

    private Track awaitingAudio(String title) {
        Track track = new Track();
        track.setTitle(title);
        track.setArtistId(7L);
        track.setGenres(List.of(genreRepository.findById("pop").orElseThrow()));
        track.setStatus(TrackStatus.AWAITING_AUDIO);
        return track;
    }
}
