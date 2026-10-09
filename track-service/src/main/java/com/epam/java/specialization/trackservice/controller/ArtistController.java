package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.api.ArtistsApi;
import com.epam.java.specialization.trackservice.api.dto.ArtistDto;
import com.epam.java.specialization.trackservice.api.dto.TrackPageDto;
import com.epam.java.specialization.trackservice.api.dto.TrackSortDto;
import com.epam.java.specialization.trackservice.api.dto.TrackStatusDto;
import com.epam.java.specialization.trackservice.mapper.TrackMapper;
import com.epam.java.specialization.trackservice.service.ArtistService;
import com.epam.java.specialization.trackservice.service.TrackService;
import com.epam.java.specialization.trackservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ArtistController implements ArtistsApi {

    private final ArtistService artistService;
    private final TrackService trackService;
    private final CurrentUser currentUser;
    private final TrackMapper trackMapper;

    @Override
    public ResponseEntity<ArtistDto> getArtist(Long artistId) {
        return ResponseEntity.ok(artistService.getArtist(artistId));
    }

    @Override
    public ResponseEntity<TrackPageDto> listArtistTracks(Long artistId, TrackSortDto sort, Integer page, Integer size) {
        return ResponseEntity.ok(trackService.listArtistTracks(artistId, sort, page, size));
    }

    @Override
    public ResponseEntity<TrackPageDto> listMyTracks(TrackStatusDto status, Integer page, Integer size) {
        Long artistId = currentUser.requireRole(CurrentUser.ARTIST);
        return ResponseEntity.ok(trackService.listMyTracks(artistId, trackMapper.toModel(status), page, size));
    }
}
