package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.internal.api.InternalTracksApi;
import com.epam.java.specialization.trackservice.internal.api.dto.InternalTrackInfoDto;
import com.epam.java.specialization.trackservice.internal.api.dto.PublishTrackRequestDto;
import com.epam.java.specialization.trackservice.service.TrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalTrackController implements InternalTracksApi {

    private final TrackService trackService;

    @Override
    public ResponseEntity<InternalTrackInfoDto> getTrackInfo(Long trackId) {
        return ResponseEntity.ok(trackService.getInfo(trackId));
    }

    @Override
    public ResponseEntity<Void> publishTrack(Long trackId, PublishTrackRequestDto request) {
        trackService.publish(trackId, request.getDurationSeconds());
        return ResponseEntity.noContent().build();
    }
}
