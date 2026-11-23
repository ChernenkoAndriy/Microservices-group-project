package com.epam.java.specialization.notificationservice.kafka;

import com.epam.java.specialization.notificationservice.model.NotificationLog;
import com.epam.java.specialization.notificationservice.repository.NotificationLogRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        topics = {"library.artist-follows", "auth.user.snapshot", "catalog.track.published"}
)
class ReleaseEmailFlowTest {

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private NotificationLogRepository logs;
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void send(String topic, String key, String type, String json) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        record.headers().add("eventType", type.getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private static String snapshot(long userId, String email, String status) {
        return """
                {"eventId":"%s","occurredAt":"2030-01-01T00:00:00Z","userId":%d,"email":"%s","role":"LISTENER","status":"%s"}
                """.formatted(UUID.randomUUID(), userId, email, status);
    }

    private static String follow(long userId, long artistId) {
        return """
                {"eventId":"%s","occurredAt":"2030-01-01T00:00:00Z","userId":%d,"artistId":%d}
                """.formatted(UUID.randomUUID(), userId, artistId);
    }

    private static String published(long artistId, String title) {
        return """
                {"eventId":"%s","occurredAt":"2030-01-02T00:00:00Z","trackId":9,"artistId":%d,"title":"%s","genres":["pop"]}
                """.formatted(UUID.randomUUID(), artistId, title);
    }

    @Test
    void emailsOnlyActiveFollowersWhoWantNewReleases() throws Exception {
        send("auth.user.snapshot", "101", "UserSnapshot", snapshot(101, "fan@example.com", "ACTIVE"));
        send("auth.user.snapshot", "102", "UserSnapshot", snapshot(102, "quiet@example.com", "ACTIVE"));
        send("auth.user.snapshot", "103", "UserSnapshot", snapshot(103, "blocked@example.com", "BLOCKED"));
        send("auth.user.snapshot", "104", "UserSnapshot", snapshot(104, "gone@example.com", "ACTIVE"));
        for (long user : new long[]{101, 102, 103, 104}) {
            send("library.artist-follows", "7", "ArtistFollowedEvent", follow(user, 7));
        }
        send("library.artist-follows", "7", "ArtistUnfollowedEvent", follow(104, 7));

        // user 102 switches new-release emails off
        mvc.perform(put("/api/v1/notifications/preferences").header("X-User-Id", "102")
                        .contentType("application/json").content("{\"newReleases\":false,\"yearSummary\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newReleases").value(false));

        await().atMost(15, SECONDS).pollDelay(2, SECONDS).until(() -> true);
        send("catalog.track.published", "9", "TrackPublishedEvent", published(7, "Lukewarm Take"));

        await().atMost(20, SECONDS).untilAsserted(() -> {
            List<NotificationLog> sent = logs.findAll();
            assertThat(sent).extracting(NotificationLog::getRecipientEmail).containsExactly("fan@example.com");
            assertThat(sent.get(0).getTemplate()).isEqualTo("NEW_RELEASE");
            assertThat(sent.get(0).getStatus()).isEqualTo("SENT");
            assertThat(sent.get(0).getSubject()).endsWith("just released Lukewarm Take");
        });
    }

    @Test
    void preferencesDefaultToEnabledAndNeedALogin() throws Exception {
        mvc.perform(get("/api/v1/notifications/preferences").header("X-User-Id", "555"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newReleases").value(true))
                .andExpect(jsonPath("$.yearSummary").value(true));
        mvc.perform(get("/api/v1/notifications/preferences")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRequireTheAdminRoleAndManageTemplates() throws Exception {
        mvc.perform(get("/api/v1/admin/notification-templates").header("X-User-Id", "1").header("X-User-Roles", "LISTENER"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/notification-templates").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='NEW_RELEASE')]").exists());

        String body = "{\"code\":\"TEST_MAIL\",\"subject\":\"Hi {{displayName}}\",\"body\":\"<p>x</p>\"}";
        mvc.perform(post("/api/v1/admin/notification-templates").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/notification-templates").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/admin/notification-templates/TEST_MAIL").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                        .contentType("application/json").content("{\"subject\":\"New\",\"body\":\"<p>y</p>\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/admin/notifications?size=5").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(5));
    }
}
