package com.epam.java.specialization.recommendationservice.controller;

import com.epam.java.specialization.recommendationservice.api.ChartsApi;
import com.epam.java.specialization.recommendationservice.api.dto.ChartDto;
import com.epam.java.specialization.recommendationservice.api.dto.ChartPeriodDto;
import com.epam.java.specialization.recommendationservice.service.RecommendationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class ChartController implements ChartsApi {

    private final RecommendationService recommendations;

    public ChartController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    public ResponseEntity<ChartDto> getTopChart(ChartPeriodDto period, String genre, Integer limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(recommendations.chart(period, genre, limit == null ? 50 : limit));
    }
}
