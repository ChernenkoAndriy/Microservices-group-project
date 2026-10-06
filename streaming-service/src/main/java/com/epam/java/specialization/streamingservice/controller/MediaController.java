package com.epam.java.specialization.streamingservice.controller;

import com.epam.java.specialization.streamingservice.api.MediaUploadApi;
import com.epam.java.specialization.streamingservice.api.dto.TranscodingJobStatusDto;
import com.epam.java.specialization.streamingservice.api.dto.TranscodingStatusDto;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;

@RestController
public class MediaController implements MediaUploadApi {

    private final StreamingStorageService storageService;

    public MediaController(StreamingStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public ResponseEntity<TranscodingStatusDto> uploadMaster(Long trackId, MultipartFile file) {
        try {
            storageService.uploadAudio(StreamingStorageService.masterKey(trackId), file);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the master file", e);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TranscodingStatusDto status = new TranscodingStatusDto(
                trackId, TranscodingJobStatusDto.QUEUED, 0, new LinkedHashSet<>(), now, now);
        status.setMasterFileName(file.getOriginalFilename());
        status.setMasterSizeBytes(file.getSize());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }
}
