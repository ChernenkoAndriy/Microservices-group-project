package com.epam.java.specialization.notificationservice.repository;

import com.epam.java.specialization.notificationservice.model.ArtistFollower;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistFollowerRepository extends JpaRepository<ArtistFollower, String> {

    List<ArtistFollower> findByArtistId(Long artistId);
}
