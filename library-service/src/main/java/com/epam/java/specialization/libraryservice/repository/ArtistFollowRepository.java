package com.epam.java.specialization.libraryservice.repository;

import com.epam.java.specialization.libraryservice.model.ArtistFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ArtistFollowRepository extends JpaRepository<ArtistFollow, Long> {

    Optional<ArtistFollow> findByUserIdAndArtistId(Long userId, Long artistId);

    Page<ArtistFollow> findByUserId(Long userId, Pageable pageable);

    Page<ArtistFollow> findByUserIdAndArtistIdIn(Long userId, Collection<Long> artistIds, Pageable pageable);
}
