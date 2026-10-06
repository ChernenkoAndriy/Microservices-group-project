package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.api.TracksApi;
import com.epam.java.specialization.trackservice.api.dto.CreateTrackRequestDto;
import com.epam.java.specialization.trackservice.api.dto.TrackDto;
import com.epam.java.specialization.trackservice.api.dto.TrackPageDto;
import com.epam.java.specialization.trackservice.api.dto.TrackSortDto;
import com.epam.java.specialization.trackservice.idempotency.IdempotencyService;
import com.epam.java.specialization.trackservice.service.TrackService;
import com.epam.java.specialization.trackservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
public class TrackController implements TracksApi {

    private final TrackService trackService;
    private final IdempotencyService idempotencyService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<TrackDto> createTrack(CreateTrackRequestDto request, String idempotencyKey) {
        Long artistId = currentUser.requireRole(CurrentUser.ARTIST);
        return idempotencyService.execute("createTrack:" + artistId, idempotencyKey, request.toString(),
                TrackDto.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(trackService.create(artistId, request)));
    }

    @Override
    public ResponseEntity<TrackDto> getTrack(Long trackId) {
        Long callerId = currentUser.id().orElse(null);
        return ResponseEntity.ok(trackService.get(trackId, callerId, currentUser.hasRole(CurrentUser.ADMIN)));
    }

    @Override
    public ResponseEntity<TrackPageDto> searchTracks(String q, String genre, Long artistId, Set<Long> ids,
                                                     TrackSortDto sort, Integer page, Integer size) {
        return ResponseEntity.ok(trackService.search(q, genre, artistId, ids, sort, page, size));
    }
}
