package com.epam.java.specialization.trackservice.repository;

import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long>, JpaSpecificationExecutor<Track> {

    long countByArtistIdAndStatus(Long artistId, TrackStatus status);

    @Query("select min(t.createdAt) from Track t where t.artistId = :artistId")
    Optional<Instant> findFirstCreatedAtByArtistId(Long artistId);

    List<Track> findByArtistIdOrderByCreatedAtAscIdAsc(Long artistId);

    List<Track> findByAlbumIdAndStatusOrderByTrackNumberAscIdAsc(Long albumId, TrackStatus status);

    boolean existsByAlbumIdAndTrackNumber(Long albumId, Integer trackNumber);

    boolean existsByAlbumIdAndTrackNumberAndIdNot(Long albumId, Integer trackNumber, Long id);

    /** Atomic increment in the database, so concurrent reports never lose a play. */
    @Modifying(flushAutomatically = true)
    @Query("update Track t set t.playCount = t.playCount + :plays where t.id = :trackId")
    int addPlays(Long trackId, long plays);

    @Query("select t.playCount from Track t where t.id = :trackId")
    long findPlayCount(Long trackId);
}
