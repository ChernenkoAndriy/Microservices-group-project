package com.epam.java.specialization.streamingservice.repository;

import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TranscodingJobRepository extends JpaRepository<TranscodingJob, Long> {

    Optional<TranscodingJob> findByTrackId(Long trackId);

    /** Locks the oldest queued job; another instance skips it instead of waiting for it. */
    @Query(value = """
            SELECT * FROM transcoding_job
            WHERE status = 'QUEUED'
            ORDER BY created_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<TranscodingJob> lockNextQueued();
}
