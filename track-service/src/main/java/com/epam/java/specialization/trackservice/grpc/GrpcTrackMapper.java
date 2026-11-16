package com.epam.java.specialization.trackservice.grpc;

import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatusUpdate;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.epam.java.specialization.grpc.catalog.v1.TrackStatus.TRACK_STATUS_AWAITING_AUDIO;
import static com.epam.java.specialization.grpc.catalog.v1.TrackStatus.TRACK_STATUS_FAILED;
import static com.epam.java.specialization.grpc.catalog.v1.TrackStatus.TRACK_STATUS_PROCESSING;
import static com.epam.java.specialization.grpc.catalog.v1.TrackStatus.TRACK_STATUS_PUBLISHED;
import static com.epam.java.specialization.grpc.catalog.v1.TrackStatus.TRACK_STATUS_UNPUBLISHED;

/** Track entity -> messages of catalog_internal.proto. */
@Component
public class GrpcTrackMapper {

    public TrackInfo toTrackInfo(Track track) {
        TrackInfo.Builder info = TrackInfo.newBuilder()
                .setId(track.getId())
                .setArtistId(track.getArtistId())
                .setStatus(toProto(track.getStatus()))
                .setTitle(track.getTitle())
                .setPlayCount(track.getPlayCount());
        // Proto fields cannot be null: an optional field is left unset instead.
        if (track.getDurationSeconds() != null) {
            info.setDurationSeconds(track.getDurationSeconds());
        }
        return info.build();
    }

    public TrackStatusUpdate toStatusUpdate(Track track) {
        Instant updatedAt = track.getUpdatedAt() != null ? track.getUpdatedAt() : track.getCreatedAt();
        TrackStatusUpdate.Builder update = TrackStatusUpdate.newBuilder()
                .setTrackId(track.getId())
                .setStatus(toProto(track.getStatus()));
        if (updatedAt != null) {
            update.setUpdatedAt(toTimestamp(updatedAt));
        }
        return update.build();
    }

    /** The proto enum has its own name prefix and TRACK_STATUS_UNSPECIFIED = 0, which the server never sends. */
    public com.epam.java.specialization.grpc.catalog.v1.TrackStatus toProto(TrackStatus status) {
        return switch (status) {
            case AWAITING_AUDIO -> TRACK_STATUS_AWAITING_AUDIO;
            case PROCESSING -> TRACK_STATUS_PROCESSING;
            case PUBLISHED -> TRACK_STATUS_PUBLISHED;
            case FAILED -> TRACK_STATUS_FAILED;
            case UNPUBLISHED -> TRACK_STATUS_UNPUBLISHED;
        };
    }

    public static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
