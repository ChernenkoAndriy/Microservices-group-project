package com.epam.java.specialization.recommendationservice.controller;

import com.epam.java.specialization.recommendationservice.api.AnalyticsApi;
import com.epam.java.specialization.recommendationservice.api.dto.PlayReportDto;
import com.epam.java.specialization.recommendationservice.model.ListenEvent;
import com.epam.java.specialization.recommendationservice.repository.AnalyticsRepository;
import com.epam.java.specialization.recommendationservice.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RestController
public class AnalyticsController implements AnalyticsApi {

    private final AnalyticsRepository analyticsRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AnalyticsController(AnalyticsRepository analyticsRepository, CurrentUser currentUser, Clock clock) {
        this.analyticsRepository = analyticsRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<Void> reportPlay(PlayReportDto playReport) {
        Long userId = currentUser.id();
        Instant now = clock.instant();
        Instant playedAt = playReport.getPlayedAt() != null ? playReport.getPlayedAt().toInstant() : now;
        if (playedAt.isAfter(now.plus(Duration.ofMinutes(5))) || playedAt.isBefore(now.minus(Duration.ofDays(30)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "playedAt must be at most 5 minutes in the future and 30 days in the past");
        }
        analyticsRepository.saveEvent(new ListenEvent(
                String.valueOf(userId),
                String.valueOf(playReport.getTrackId()),
                playReport.getListenedSeconds().longValue(),
                LocalDateTime.ofInstant(playedAt, ZoneOffset.UTC)));
        return ResponseEntity.accepted().build();
    }
}
