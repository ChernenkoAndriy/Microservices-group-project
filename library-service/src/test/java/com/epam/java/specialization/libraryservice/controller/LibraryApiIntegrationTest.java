package com.epam.java.specialization.libraryservice.controller;

import com.epam.java.specialization.libraryservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.libraryservice.client.auth.dto.UserRoleDto;
import com.epam.java.specialization.libraryservice.client.auth.dto.UserStatusDto;
import com.epam.java.specialization.libraryservice.client.auth.dto.UserSummaryDto;
import com.epam.java.specialization.libraryservice.client.catalog.api.InternalTracksApi;
import com.epam.java.specialization.libraryservice.client.catalog.dto.InternalTrackInfoDto;
import com.epam.java.specialization.libraryservice.client.catalog.dto.TrackStatusDto;
import com.epam.java.specialization.libraryservice.messaging.LibraryEvents;
import com.epam.java.specialization.libraryservice.outbox.OutboxEvent;
import com.epam.java.specialization.libraryservice.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Likes and follows through the REST API with an in-memory database; catalog and auth clients are mocked. */
@SpringBootTest
@AutoConfigureMockMvc
class LibraryApiIntegrationTest {

    private static final AtomicLong USER_IDS = new AtomicLong(5000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutboxEventRepository outbox;

    @MockitoBean
    private InternalTracksApi tracksApi;
    @MockitoBean
    private InternalUsersApi usersApi;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = String.valueOf(USER_IDS.incrementAndGet());
        when(tracksApi.getTrackInfo(anyLong())).thenAnswer(call -> ResponseEntity.ok(
                new InternalTrackInfoDto(call.getArgument(0), 1L, TrackStatusDto.PUBLISHED)));
    }

    @Test
    void likeIsIdempotentAndEmitsOneEvent() throws Exception {
        mockMvc.perform(post("/api/v1/library/likes/11").header("X-User-Id", userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackId").value(11));
        mockMvc.perform(post("/api/v1/library/likes/11").header("X-User-Id", userId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/library/likes/12").header("X-User-Id", userId))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/library/likes").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].trackId").value(12))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(get("/api/v1/library/likes").param("trackIds", "11,99").header("X-User-Id", userId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].trackId").value(11));

        List<OutboxEvent> events = eventsFor(userId);
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(LibraryEvents.TRACK_LIKED, LibraryEvents.TRACK_LIKED);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getTopic()).isEqualTo("library.track-likes");
            assertThat(e.getPayload()).contains("\"userId\":" + userId);
        });
    }

    @Test
    void unlikeIsIdempotentAndEmitsOnlyWhenRemoved() throws Exception {
        mockMvc.perform(post("/api/v1/library/likes/21").header("X-User-Id", userId)).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/library/likes/21").header("X-User-Id", userId))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/library/likes/21").header("X-User-Id", userId))
                .andExpect(status().isNoContent());

        assertThat(eventsFor(userId)).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(LibraryEvents.TRACK_LIKED, LibraryEvents.TRACK_UNLIKED);
        mockMvc.perform(get("/api/v1/library/likes").header("X-User-Id", userId))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void cannotLikeUnknownOrUnpublishedTracks() throws Exception {
        when(tracksApi.getTrackInfo(404L)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        when(tracksApi.getTrackInfo(405L)).thenReturn(ResponseEntity.ok(
                new InternalTrackInfoDto(405L, 1L, TrackStatusDto.PROCESSING)));
        when(tracksApi.getTrackInfo(503L)).thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(post("/api/v1/library/likes/404").header("X-User-Id", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/library/likes/405").header("X-User-Id", userId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/library/likes/503").header("X-User-Id", userId))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void likesNeedAUser() throws Exception {
        mockMvc.perform(get("/api/v1/library/likes")).andExpect(status().isUnauthorized());
    }

    @Test
    void followRules() throws Exception {
        when(usersApi.getUsersByIds(Set.of(7L))).thenReturn(ResponseEntity.ok(List.of(user(7L, UserRoleDto.ARTIST))));
        when(usersApi.getUsersByIds(Set.of(8L))).thenReturn(ResponseEntity.ok(List.of(user(8L, UserRoleDto.LISTENER))));
        when(usersApi.getUsersByIds(Set.of(9L))).thenReturn(ResponseEntity.ok(List.of()));

        mockMvc.perform(post("/api/v1/library/follows/7").header("X-User-Id", userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artistId").value(7));
        mockMvc.perform(post("/api/v1/library/follows/7").header("X-User-Id", userId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/library/follows/8").header("X-User-Id", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/library/follows/9").header("X-User-Id", userId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/library/follows/" + userId).header("X-User-Id", userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_FOLLOW_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/library/follows").header("X-User-Id", userId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].artistId").value(7));

        mockMvc.perform(delete("/api/v1/library/follows/7").header("X-User-Id", userId))
                .andExpect(status().isNoContent());

        List<OutboxEvent> followEvents = outbox.findAll().stream()
                .filter(e -> e.getTopic().equals("library.artist-follows"))
                .filter(e -> e.getPayload().contains("\"userId\":" + userId))
                .toList();
        assertThat(followEvents).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(LibraryEvents.ARTIST_FOLLOWED, LibraryEvents.ARTIST_UNFOLLOWED);
        assertThat(followEvents).allSatisfy(e -> assertThat(e.getMessageKey()).isEqualTo("7"));
    }

    private List<OutboxEvent> eventsFor(String user) {
        return outbox.findAll().stream().filter(e -> e.getMessageKey().equals(user)).toList();
    }

    private static UserSummaryDto user(long id, UserRoleDto role) {
        return new UserSummaryDto(id, "u" + id + "@spotty.test", "User " + id, role, UserStatusDto.ACTIVE,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }
}
