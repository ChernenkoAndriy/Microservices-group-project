package com.epam.java.specialization.libraryservice.controller;

import com.epam.java.specialization.libraryservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.libraryservice.client.catalog.api.InternalTracksApi;
import com.epam.java.specialization.libraryservice.client.catalog.dto.InternalTrackInfoDto;
import com.epam.java.specialization.libraryservice.client.catalog.dto.TrackStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlaylistApiIntegrationTest {

    private static final AtomicLong USER_IDS = new AtomicLong(9000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;

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
    void playlistLifecycle() throws Exception {
        long id = create("Songs to cancel plans to");

        mockMvc.perform(get("/api/v1/library/playlists/" + id).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Songs to cancel plans to"))
                .andExpect(jsonPath("$.trackCount").value(0))
                .andExpect(jsonPath("$.tracks.length()").value(0));

        for (long track : new long[]{3, 1, 2}) {
            mockMvc.perform(put("/api/v1/library/playlists/" + id + "/tracks/" + track).header("X-User-Id", userId))
                    .andExpect(status().isOk());
        }
        // Adding again keeps the position
        mockMvc.perform(put("/api/v1/library/playlists/" + id + "/tracks/3").header("X-User-Id", userId))
                .andExpect(jsonPath("$.trackCount").value(3))
                .andExpect(jsonPath("$.tracks[0].trackId").value(3))
                .andExpect(jsonPath("$.tracks[0].position").value(1));

        mockMvc.perform(delete("/api/v1/library/playlists/" + id + "/tracks/3").header("X-User-Id", userId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/library/playlists/" + id).header("X-User-Id", userId))
                .andExpect(jsonPath("$.tracks[0].trackId").value(1))
                .andExpect(jsonPath("$.tracks[0].position").value(1))
                .andExpect(jsonPath("$.tracks[1].trackId").value(2))
                .andExpect(jsonPath("$.tracks[1].position").value(2));

        mockMvc.perform(patch("/api/v1/library/playlists/" + id).header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Regrets, vol. 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Regrets, vol. 2"));

        mockMvc.perform(get("/api/v1/library/playlists").header("X-User-Id", userId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].trackCount").value(2));

        mockMvc.perform(delete("/api/v1/library/playlists/" + id).header("X-User-Id", userId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/library/playlists/" + id).header("X-User-Id", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void playlistsArePrivate() throws Exception {
        long id = create("Mine");

        mockMvc.perform(get("/api/v1/library/playlists/" + id).header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYLIST_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/library/playlists/" + id + "/tracks/5").header("X-User-Id", "1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/library/playlists/" + id).header("X-User-Id", "1"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/library/playlists/" + id).header("X-User-Id", userId))
                .andExpect(status().isOk());
    }

    @Test
    void validation() throws Exception {
        mockMvc.perform(post("/api/v1/library/playlists").header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        long id = create("Valid");
        mockMvc.perform(patch("/api/v1/library/playlists/" + id).header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(tracksApi.getTrackInfo(404L)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        mockMvc.perform(put("/api/v1/library/playlists/" + id + "/tracks/404").header("X-User-Id", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
    }

    private long create(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/library/playlists").header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asLong();
    }
}
