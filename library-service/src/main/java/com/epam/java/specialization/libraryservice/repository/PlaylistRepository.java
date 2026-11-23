package com.epam.java.specialization.libraryservice.repository;

import com.epam.java.specialization.libraryservice.model.Playlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Page<Playlist> findByOwnerId(Long ownerId, Pageable pageable);

    long countByOwnerId(Long ownerId);

    /** Rows of {@code [playlistId, trackCount]}. */
    @Query("select e.playlist.id, count(e) from PlaylistEntry e where e.playlist.id in :ids group by e.playlist.id")
    List<Object[]> countTracks(Collection<Long> ids);
}
