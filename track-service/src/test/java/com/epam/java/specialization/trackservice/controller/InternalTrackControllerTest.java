package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.exception.GlobalExceptionHandler;
import com.epam.java.specialization.trackservice.internal.api.dto.InternalTrackInfoDto;
import com.epam.java.specialization.trackservice.internal.api.dto.TrackStatusDto;
import com.epam.java.specialization.trackservice.service.TrackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalTrackControllerTest {

    @Mock
    private TrackService trackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalTrackController(trackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getTrackInfoReturnsOwnerAndStatus() throws Exception {
        when(trackService.getInfo(5L)).thenReturn(new InternalTrackInfoDto(5L, 7L, TrackStatusDto.AWAITING_AUDIO));

        mockMvc.perform(get("/internal/tracks/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.artistId").value(7))
                .andExpect(jsonPath("$.status").value("AWAITING_AUDIO"));
    }

    @Test
    void getTrackInfoOfUnknownTrackIsNotFound() throws Exception {
        when(trackService.getInfo(9L)).thenThrow(CatalogException.notFound("TRACK_NOT_FOUND", "Track 9 not found"));

        mockMvc.perform(get("/internal/tracks/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
    }
}
