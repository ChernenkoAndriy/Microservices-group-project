package com.epam.java.specialization.trackservice.repository;

import com.epam.java.specialization.trackservice.model.Album;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    /** Albums of the artist that have at least one track in the given status. */
    @Query(value = """
            select a from Album a where a.artistId = :artistId
              and exists (select t.id from Track t where t.album = a and t.status = :status)
            """,
            countQuery = """
            select count(a) from Album a where a.artistId = :artistId
              and exists (select t.id from Track t where t.album = a and t.status = :status)
            """)
    Page<Album> findByArtistWithTracksIn(Long artistId, TrackStatus status, Pageable pageable);

    @Query("""
            select count(a) from Album a where a.artistId = :artistId
              and exists (select t.id from Track t where t.album = a and t.status = :status)
            """)
    long countByArtistWithTracksIn(Long artistId, TrackStatus status);

    /** Rows of {@code [albumId, trackCount]} for the tracks in the given status. */
    @Query("""
            select t.album.id, count(t) from Track t
            where t.album.id in :albumIds and t.status = :status group by t.album.id
            """)
    List<Object[]> countTracks(Collection<Long> albumIds, TrackStatus status);
}
