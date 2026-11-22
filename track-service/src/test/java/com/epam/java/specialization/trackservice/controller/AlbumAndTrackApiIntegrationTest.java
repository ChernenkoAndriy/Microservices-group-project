package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.client.ArtistDirectory;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.client.auth.dto.UserRoleDto;
import com.epam.java.specialization.trackservice.client.auth.dto.UserStatusDto;
import com.epam.java.specialization.trackservice.client.auth.dto.UserSummaryDto;
import com.epam.java.specialization.trackservice.messaging.TrackEvents;
import com.epam.java.specialization.trackservice.outbox.OutboxEvent;
import com.epam.java.specialization.trackservice.outbox.OutboxEventRepository;
import com.epam.java.specialization.trackservice.service.TrackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Albums, track edit/delete and the outbox events of the catalog, through the REST API with an in-memory database.
 * The artist directory (auth-service) is mocked.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-albums;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
@AutoConfigureMockMvc
class AlbumAndTrackApiIntegrationTest {

    private static final AtomicLong ARTIST_IDS = new AtomicLong(1000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TrackService trackService;
    @Autowired
    private OutboxEventRepository outbox;

    @MockitoBean
    private ArtistDirectory artistDirectory;

    private long artistId;
    private long otherArtistId;

    @BeforeEach
    void setUp() {
        artistId = ARTIST_IDS.incrementAndGet();
        otherArtistId = ARTIST_IDS.incrementAndGet();
        Map<Long, UserSummaryDto> users = Map.of(
                artistId, artist(artistId, "Gary and the Mild Disappointments"),
                otherArtistId, artist(otherArtistId, "Polka Panic"));
        when(artistDirectory.findProfile(anyLong())).thenReturn(new ArtistProfiles(users, true));
        when(artistDirectory.findProfiles(anyCollection())).thenReturn(new ArtistProfiles(users, true));
    }

    @Test
    void albumShowsOnlyPublishedTracksInTrackNumberOrder() throws Exception {
        long albumId = createAlbum(artistId, "Lukewarm Forever");
        long second = createTrack(artistId, "Room Temperature", albumId, 2);
        long first = createTrack(artistId, "Tepid Love", albumId, 1);
        createTrack(artistId, "Still Processing", albumId, 3);

        // Nothing published yet: hidden from listeners, visible to the owner
        mockMvc.perform(get("/api/v1/albums/" + albumId)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/v1/albums/" + albumId), artistId, "ARTIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackCount").value(0));

        trackService.publish(second, 120);
        trackService.publish(first, 95);

        mockMvc.perform(get("/api/v1/albums/" + albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lukewarm Forever"))
                .andExpect(jsonPath("$.type").value("ALBUM"))
                .andExpect(jsonPath("$.artist.name").value("Gary and the Mild Disappointments"))
                .andExpect(jsonPath("$.trackCount").value(2))
                .andExpect(jsonPath("$.tracks[0].id").value(first))
                .andExpect(jsonPath("$.tracks[0].album.title").value("Lukewarm Forever"))
                .andExpect(jsonPath("$.tracks[1].id").value(second));

        mockMvc.perform(get("/api/v1/artists/" + artistId + "/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(albumId))
                .andExpect(jsonPath("$.items[0].trackCount").value(2));

        mockMvc.perform(get("/api/v1/artists/" + artistId))
                .andExpect(jsonPath("$.trackCount").value(2))
                .andExpect(jsonPath("$.albumCount").value(1));
    }

    @Test
    void trackNumberRulesOnCreate() throws Exception {
        long albumId = createAlbum(artistId, "Deductible Bangers");
        createTrack(artistId, "Form 1040 Remix", albumId, 1);

        mockMvc.perform(createTrackRequest(artistId, "Duplicate", albumId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRACK_NUMBER_TAKEN"));
        mockMvc.perform(createTrackRequest(artistId, "No number", albumId, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRACK_NUMBER_REQUIRED"));
        mockMvc.perform(createTrackRequest(otherArtistId, "Not my album", albumId, 2))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_USABLE"));
    }

    @Test
    void createAlbumNeedsTheArtistRole() throws Exception {
        mockMvc.perform(as(post("/api/v1/albums"), artistId, "LISTENER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\",\"type\":\"EP\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateChangesFieldsAndEmitsASnapshot() throws Exception {
        long trackId = createTrack(artistId, "Draft Title", null, null);

        mockMvc.perform(as(patch("/api/v1/tracks/" + trackId), artistId, "ARTIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Final Title\",\"genreSlugs\":[\"folk\",\"jazz\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final Title"))
                .andExpect(jsonPath("$.genres[0].slug").value("folk"));

        JsonNode snapshot = lastEvent(trackId, TrackEvents.TRACK_SNAPSHOT);
        assertThat(snapshot.get("title").asString()).isEqualTo("Final Title");
        assertThat(snapshot.get("status").asString()).isEqualTo("AWAITING_AUDIO");

        mockMvc.perform(as(patch("/api/v1/tracks/" + trackId), otherArtistId, "ARTIST")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Stolen\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(patch("/api/v1/tracks/" + trackId), artistId, "ARTIST")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesDraftsAndUnpublishesPublishedTracks() throws Exception {
        long draft = createTrack(artistId, "Never Released", null, null);
        long released = createTrack(artistId, "Christmas in July", null, null);
        trackService.publish(released, 60);

        mockMvc.perform(as(delete("/api/v1/tracks/" + draft), otherArtistId, "ARTIST"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/v1/tracks/" + draft), artistId, "ARTIST"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/v1/tracks/" + draft), artistId, "ARTIST"))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(delete("/api/v1/tracks/" + released), 1L, "ADMIN"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tracks/" + released)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/v1/tracks/" + released), artistId, "ARTIST"))
                .andExpect(jsonPath("$.status").value("UNPUBLISHED"));
        assertThat(lastEvent(released, TrackEvents.TRACK_SNAPSHOT).get("status").asString())
                .isEqualTo("UNPUBLISHED");
    }

    @Test
    void firstPublishEmitsTrackPublishedOnce() throws Exception {
        long trackId = createTrack(artistId, "Elevator to Nowhere", null, null);

        trackService.publish(trackId, 200);
        trackService.publish(trackId, 201);

        List<OutboxEvent> events = eventsOf(trackId);
        assertThat(events).filteredOn(e -> e.getEventType().equals(TrackEvents.TRACK_PUBLISHED)).hasSize(1);
        assertThat(events).filteredOn(e -> e.getEventType().equals(TrackEvents.TRACK_SNAPSHOT)).hasSize(3);
        JsonNode published = lastEvent(trackId, TrackEvents.TRACK_PUBLISHED);
        assertThat(published.get("artistId").asLong()).isEqualTo(artistId);
        assertThat(published.get("genres").get(0).asString()).isEqualTo("pop");
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getTopic()).startsWith("catalog.track.");
            assertThat(e.getMessageKey()).isEqualTo(String.valueOf(trackId));
        });
    }

    private long createAlbum(long ownerId, String title) throws Exception {
        String body = mockMvc.perform(as(post("/api/v1/albums"), ownerId, "ARTIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"type\":\"ALBUM\",\"releaseDate\":\"2026-10-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asLong();
    }

    private long createTrack(long ownerId, String title, Long albumId, Integer trackNumber) throws Exception {
        String body = mockMvc.perform(createTrackRequest(ownerId, title, albumId, trackNumber))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asLong();
    }

    private MockHttpServletRequestBuilder createTrackRequest(long ownerId, String title, Long albumId,
                                                             Integer trackNumber) {
        StringBuilder json = new StringBuilder("{\"title\":\"" + title + "\",\"genreSlugs\":[\"pop\"]");
        if (albumId != null) {
            json.append(",\"albumId\":").append(albumId);
        }
        if (trackNumber != null) {
            json.append(",\"trackNumber\":").append(trackNumber);
        }
        json.append('}');
        return as(post("/api/v1/tracks"), ownerId, "ARTIST")
                .contentType(MediaType.APPLICATION_JSON).content(json.toString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId, String role) {
        return request.header("X-User-Id", userId).header("X-User-Roles", role);
    }

    private List<OutboxEvent> eventsOf(long trackId) {
        return outbox.findAll().stream()
                .filter(e -> e.getAggregateId().equals(String.valueOf(trackId)))
                .sorted(Comparator.comparing(OutboxEvent::getCreatedAt))
                .toList();
    }

    private JsonNode lastEvent(long trackId, String eventType) {
        List<OutboxEvent> events = eventsOf(trackId).stream()
                .filter(e -> e.getEventType().equals(eventType)).toList();
        assertThat(events).isNotEmpty();
        return jsonMapper.readTree(events.getLast().getPayload());
    }

    private static UserSummaryDto artist(long id, String name) {
        return new UserSummaryDto(id, "artist" + id + "@spotty.test", name, UserRoleDto.ARTIST, UserStatusDto.ACTIVE,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }
}
