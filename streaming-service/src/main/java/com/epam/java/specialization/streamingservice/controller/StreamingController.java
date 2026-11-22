package com.epam.java.specialization.streamingservice.controller;

import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatus;
import com.epam.java.specialization.streamingservice.api.StreamingApi;
import com.epam.java.specialization.streamingservice.api.dto.AudioQualityDto;
import com.epam.java.specialization.streamingservice.api.dto.StreamTokenDto;
import com.epam.java.specialization.streamingservice.api.dto.StreamTokenTypeDto;
import com.epam.java.specialization.streamingservice.client.CatalogGrpcClient;
import com.epam.java.specialization.streamingservice.service.PlayReporter;
import com.epam.java.specialization.streamingservice.service.StreamTokenService;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import com.epam.java.specialization.streamingservice.web.ByteRange;
import com.epam.java.specialization.streamingservice.web.CurrentUser;
import com.epam.java.specialization.streamingservice.web.StreamingException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.Optional;

@RestController
public class StreamingController implements StreamingApi {

    private static final Logger log = LoggerFactory.getLogger(StreamingController.class);
    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final StreamingStorageService storageService;
    private final PlayReporter playReporter;
    private final CurrentUser currentUser;
    private final StreamTokenService tokens;
    private final CatalogGrpcClient catalog;

    public StreamingController(StreamingStorageService storageService, PlayReporter playReporter,
                               CurrentUser currentUser, StreamTokenService tokens, CatalogGrpcClient catalog) {
        this.storageService = storageService;
        this.playReporter = playReporter;
        this.currentUser = currentUser;
        this.tokens = tokens;
        this.catalog = catalog;
    }

    /** Only published tracks get a token; catalog-service is asked over the existing gRPC channel. */
    @Override
    public ResponseEntity<StreamTokenDto> issueStreamToken(Long trackId, StreamTokenTypeDto type) {
        Long userId = currentUser.requireId();
        requirePlayable(trackId);
        StreamTokenService.Type tokenType = type == StreamTokenTypeDto.OFFLINE
                ? StreamTokenService.Type.OFFLINE : StreamTokenService.Type.STREAM;
        StreamTokenService.Issued issued = tokens.issue(userId, trackId, tokenType);
        StreamTokenDto dto = new StreamTokenDto(issued.token(), StreamTokenTypeDto.fromValue(tokenType.name()),
                issued.claims().expiresAt().atOffset(java.time.ZoneOffset.UTC),
                "/api/v1/streaming/" + trackId + "?token=" + issued.token());
        return ResponseEntity.ok(dto);
    }

    /**
     * Serves the audio with a single byte range. A play is counted once per playback: for the request that starts
     * at byte 0, not for every seek. The quality falls back to the only stored file (the master) for now.
     */
    @Override
    public ResponseEntity<Resource> streamTrack(Long trackId, AudioQualityDto quality, String range) {
        StreamTokenService.Claims claims = tokens.verify(currentRequest().getParameter("token"), trackId);
        String key = StreamingStorageService.masterKey(trackId);
        long size;
        try {
            size = storageService.objectSize(key);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No audio for track " + trackId, e);
        }
        Optional<ByteRange> byteRange = ByteRange.parse(range, size);

        InputStream audio;
        try {
            audio = byteRange.isPresent()
                    ? storageService.getAudioStream(key, byteRange.get().start(), byteRange.get().length())
                    : storageService.getAudioStream(key);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No audio for track " + trackId, e);
        }
        if (byteRange.map(r -> r.start() == 0).orElse(true)) {
            // Counted in catalog-service by the next ReportPlays batch (gRPC client streaming).
            playReporter.recordPlay(trackId, claims.userId(), 0);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(AUDIO_MPEG);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl("private, max-age=300");
        if (claims.type() == StreamTokenService.Type.OFFLINE) {
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("spotty-track-" + trackId + ".mp3").build());
        } else {
            headers.setContentDisposition(ContentDisposition.inline().build());
        }
        if (byteRange.isPresent()) {
            ByteRange r = byteRange.get();
            headers.setContentLength(r.length());
            headers.set(HttpHeaders.CONTENT_RANGE, r.contentRange(size));
            return new ResponseEntity<>(new InputStreamResource(audio), headers, HttpStatus.PARTIAL_CONTENT);
        }
        headers.setContentLength(size);
        return new ResponseEntity<>(new InputStreamResource(audio), headers, HttpStatus.OK);
    }

    private void requirePlayable(Long trackId) {
        TrackInfo track;
        try {
            track = catalog.getTrackInfo(trackId);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Track " + trackId + " does not exist");
            }
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getStatus().getDescription());
            }
            log.warn("Could not check track id={} in catalog-service", trackId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not check the track in catalog-service", e);
        }
        if (track.getStatus() != TrackStatus.TRACK_STATUS_PUBLISHED) {
            throw StreamingException.notPlayable(trackId);
        }
    }

    /** The {@code token} query parameter is not a generated method parameter (security scheme {@code streamToken}). */
    private static HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
