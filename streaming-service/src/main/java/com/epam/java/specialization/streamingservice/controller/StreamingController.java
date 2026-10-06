package com.epam.java.specialization.streamingservice.controller;

import com.epam.java.specialization.streamingservice.api.StreamingApi;
import com.epam.java.specialization.streamingservice.api.dto.AudioQualityDto;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StreamingController implements StreamingApi {

    private final StreamingStorageService storageService;

    public StreamingController(StreamingStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public ResponseEntity<Resource> streamTrack(Long trackId, AudioQualityDto quality, String range) {
        try {
            return ResponseEntity.ok(new InputStreamResource(
                    storageService.getAudioStream(StreamingStorageService.masterKey(trackId))));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No audio for track " + trackId, e);
        }
    }
}
