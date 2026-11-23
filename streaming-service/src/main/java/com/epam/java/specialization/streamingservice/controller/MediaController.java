package com.epam.java.specialization.streamingservice.controller;

import com.epam.java.specialization.streamingservice.api.MediaUploadApi;
import com.epam.java.specialization.streamingservice.api.dto.AudioQualityDto;
import com.epam.java.specialization.streamingservice.api.dto.CoverUploadResponseDto;
import com.epam.java.specialization.streamingservice.service.CoverImage;
import com.epam.java.specialization.streamingservice.web.StreamingException;
import com.epam.java.specialization.streamingservice.api.dto.TranscodingJobStatusDto;
import com.epam.java.specialization.streamingservice.api.dto.TranscodingStatusDto;
import com.epam.java.specialization.streamingservice.client.TrackOwnership;
import com.epam.java.specialization.streamingservice.metrics.StreamingMetrics;
import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import com.epam.java.specialization.streamingservice.service.TranscodingService;
import com.epam.java.specialization.streamingservice.web.CurrentUser;
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

    private static final long MAX_COVER_BYTES = 10L * 1024 * 1024;

    private final StreamingStorageService storageService;
    private final TranscodingService transcodingService;
    private final StreamingMetrics metrics;
    private final CurrentUser currentUser;
    private final TrackOwnership trackOwnership;

    public MediaController(StreamingStorageService storageService, TranscodingService transcodingService,
                           StreamingMetrics metrics, CurrentUser currentUser, TrackOwnership trackOwnership) {
        this.storageService = storageService;
        this.transcodingService = transcodingService;
        this.metrics = metrics;
        this.currentUser = currentUser;
        this.trackOwnership = trackOwnership;
    }

    @Override
    public ResponseEntity<TranscodingStatusDto> uploadMaster(Long trackId, MultipartFile file) {
        trackOwnership.requireOwner(trackId, currentUser.requireId());
        try {
            storageService.uploadAudio(StreamingStorageService.masterKey(trackId), file);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the master file", e);
        }
        metrics.recordUploadedMaster(file.getSize());
        TranscodingJob job = transcodingService.queue(trackId, file.getOriginalFilename(), file.getSize());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(job));
    }

    @Override
    public ResponseEntity<CoverUploadResponseDto> uploadCover(Long trackId, MultipartFile file) {
        trackOwnership.requireOwner(trackId, currentUser.requireId());
        if (file.getSize() > MAX_COVER_BYTES) {
            throw new StreamingException(HttpStatus.CONTENT_TOO_LARGE, "COVER_TOO_LARGE",
                    "The cover must be at most 10 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new StreamingException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The file could not be read");
        }
        CoverImage image = CoverImage.inspect(bytes).orElseThrow(() -> new StreamingException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE", "The cover must be a JPEG, PNG or WebP image"));
        if (!image.isSquareWithinLimits()) {
            throw new StreamingException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_DIMENSIONS",
                    "The cover must be square, 500-5000 px per side (got " + image.width() + "x" + image.height() + ")");
        }
        try {
            storageService.putBytes(StreamingStorageService.coverKey(trackId), bytes, image.contentType());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the cover", e);
        }
        transcodingService.coverChanged(trackId);
        String url = storageService.coverUrlOrNull(trackId);
        CoverUploadResponseDto body = new CoverUploadResponseDto(trackId, url,
                OffsetDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getCover(Long trackId) {
        String type = storageService.contentTypeOrNull(StreamingStorageService.coverKey(trackId));
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Track " + trackId + " has no cover");
        }
        try {
            byte[] bytes = storageService.getAudioStream(StreamingStorageService.coverKey(trackId)).readAllBytes();
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(type))
                    .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic())
                    .body(new org.springframework.core.io.ByteArrayResource(bytes));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the cover", e);
        }
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
