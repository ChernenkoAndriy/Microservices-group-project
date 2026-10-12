package com.epam.java.specialization.streamingservice.controller;

import com.epam.java.specialization.streamingservice.api.MediaUploadApi;
import com.epam.java.specialization.streamingservice.api.dto.AudioQualityDto;
import com.epam.java.specialization.streamingservice.api.dto.TranscodingJobStatusDto;
import com.epam.java.specialization.streamingservice.api.dto.TranscodingStatusDto;
import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import com.epam.java.specialization.streamingservice.service.TranscodingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class MediaController implements MediaUploadApi {

    private final StreamingStorageService storageService;
    private final TranscodingService transcodingService;

    public MediaController(StreamingStorageService storageService, TranscodingService transcodingService) {
        this.storageService = storageService;
        this.transcodingService = transcodingService;
    }

    @Override
    public ResponseEntity<TranscodingStatusDto> uploadMaster(Long trackId, MultipartFile file) {
        try {
            storageService.uploadAudio(StreamingStorageService.masterKey(trackId), file);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the master file", e);
        }
        TranscodingJob job = transcodingService.queue(trackId, file.getOriginalFilename(), file.getSize());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(job));
    }

    @Override
    public ResponseEntity<TranscodingStatusDto> getTranscodingStatus(Long trackId) {
        return transcodingService.find(trackId)
                .map(job -> ResponseEntity.ok(toDto(job)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No master has been uploaded for track " + trackId));
    }

    private static TranscodingStatusDto toDto(TranscodingJob job) {
        Set<AudioQualityDto> qualities = job.getQualities().stream()
                .map(AudioQualityDto::fromValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        TranscodingStatusDto dto = new TranscodingStatusDto(
                job.getTrackId(),
                TranscodingJobStatusDto.fromValue(job.getStatus().name()),
                job.getProgressPercent(),
                qualities,
                toOffset(job.getCreatedAt()),
                toOffset(job.getUpdatedAt()));
        dto.setDurationSeconds(job.getDurationSeconds());
        dto.setMasterFileName(job.getMasterFileName());
        dto.setMasterSizeBytes(job.getMasterSizeBytes());
        dto.setFailureReason(job.getFailureReason());
        return dto;
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
