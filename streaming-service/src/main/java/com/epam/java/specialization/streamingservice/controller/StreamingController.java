package com.epam.java.specialization.streamingservice.controller;

import com.epam.java.specialization.streamingservice.api.StreamingApi;
import com.epam.java.specialization.streamingservice.api.dto.AudioQualityDto;
import com.epam.java.specialization.streamingservice.service.PlayReporter;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import com.epam.java.specialization.streamingservice.web.CurrentUser;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StreamingController implements StreamingApi {

    private final StreamingStorageService storageService;
    private final PlayReporter playReporter;
    private final CurrentUser currentUser;

    public StreamingController(StreamingStorageService storageService, PlayReporter playReporter,
                               CurrentUser currentUser) {
        this.storageService = storageService;
        this.playReporter = playReporter;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<Resource> streamTrack(Long trackId, AudioQualityDto quality, String range) {
        InputStreamResource audio;
        try {
            audio = new InputStreamResource(storageService.getAudioStream(StreamingStorageService.masterKey(trackId)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No audio for track " + trackId, e);
        }
        // Counted in catalog-service by the next ReportPlays batch (gRPC client streaming).
        playReporter.recordPlay(trackId, currentUser.findId(), 0);
        return ResponseEntity.ok(audio);
    }
}
