package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.api.TracksApi;
import com.epam.java.specialization.trackservice.api.dto.CreateTrackRequestDto;
import com.epam.java.specialization.trackservice.api.dto.TrackDto;
import com.epam.java.specialization.trackservice.api.dto.TrackPageDto;
import com.epam.java.specialization.trackservice.api.dto.TrackSortDto;
import com.epam.java.specialization.trackservice.api.dto.UpdateTrackRequestDto;
import com.epam.java.specialization.trackservice.exception.CatalogException;
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
    public ResponseEntity<TrackDto> updateTrack(Long trackId, UpdateTrackRequestDto request) {
        Long artistId = currentUser.requireRole(CurrentUser.ARTIST);
        return ResponseEntity.ok(trackService.update(trackId, artistId, request));
    }

    @Override
    public ResponseEntity<Void> deleteTrack(Long trackId) {
        Long callerId = currentUser.id().orElseThrow(() -> CatalogException.unauthorized("Authentication is required"));
        boolean admin = currentUser.hasRole(CurrentUser.ADMIN);
        if (!admin && !currentUser.hasRole(CurrentUser.ARTIST)) {
            throw CatalogException.forbidden("The ARTIST or ADMIN role is required");
        }
        trackService.delete(trackId, callerId, admin);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TrackPageDto> searchTracks(String q, String genre, Long artistId, Set<Long> ids,
                                                     TrackSortDto sort, Integer page, Integer size) {
        return ResponseEntity.ok(trackService.search(q, genre, artistId, ids, sort, page, size));
    }
}
