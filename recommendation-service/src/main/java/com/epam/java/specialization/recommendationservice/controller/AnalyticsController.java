package com.epam.java.specialization.recommendationservice.controller;

import com.epam.java.specialization.recommendationservice.api.AnalyticsApi;
import com.epam.java.specialization.recommendationservice.api.dto.PlayReportDto;
import com.epam.java.specialization.recommendationservice.model.ListenEvent;
import com.epam.java.specialization.recommendationservice.repository.AnalyticsRepository;
import com.epam.java.specialization.recommendationservice.web.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RestController
public class AnalyticsController implements AnalyticsApi {

    private final AnalyticsRepository analyticsRepository;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsRepository analyticsRepository, CurrentUser currentUser) {
        this.analyticsRepository = analyticsRepository;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<Void> reportPlay(PlayReportDto playReport) {
        LocalDateTime playedAt = playReport.getPlayedAt() != null
                ? LocalDateTime.ofInstant(playReport.getPlayedAt().toInstant(), ZoneOffset.UTC)
                : null;
        analyticsRepository.saveEvent(new ListenEvent(
                String.valueOf(currentUser.id()),
                String.valueOf(playReport.getTrackId()),
                playReport.getListenedSeconds().longValue(),
                playedAt));
        return ResponseEntity.accepted().build();
    }
}
