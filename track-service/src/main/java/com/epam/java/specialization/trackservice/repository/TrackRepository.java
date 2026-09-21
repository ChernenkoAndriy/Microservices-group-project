package com.epam.java.specialization.trackservice.repository;

import com.epam.java.specialization.trackservice.model.Track;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> {
}
