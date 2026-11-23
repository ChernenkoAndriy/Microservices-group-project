package com.epam.java.specialization.libraryservice.repository;

import com.epam.java.specialization.libraryservice.model.TrackLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface TrackLikeRepository extends JpaRepository<TrackLike, Long> {

    Optional<TrackLike> findByUserIdAndTrackId(Long userId, Long trackId);

    Page<TrackLike> findByUserId(Long userId, Pageable pageable);

    Page<TrackLike> findByUserIdAndTrackIdIn(Long userId, Collection<Long> trackIds, Pageable pageable);
}
