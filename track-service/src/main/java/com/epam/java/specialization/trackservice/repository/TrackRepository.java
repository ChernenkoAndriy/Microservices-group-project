package com.epam.java.specialization.trackservice.repository;

import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long>, JpaSpecificationExecutor<Track> {

    long countByArtistIdAndStatus(Long artistId, TrackStatus status);

    @Query("select min(t.createdAt) from Track t where t.artistId = :artistId")
    Optional<Instant> findFirstCreatedAtByArtistId(Long artistId);
}
