package com.epam.java.specialization.recommendationservice.controller;

import com.epam.java.specialization.recommendationservice.api.RecommendationsApi;
import com.epam.java.specialization.recommendationservice.api.dto.RecommendationFeedDto;
import com.epam.java.specialization.recommendationservice.service.RecommendationService;
import com.epam.java.specialization.recommendationservice.web.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController implements RecommendationsApi {

    private final RecommendationService recommendations;
    private final CurrentUser currentUser;

    public RecommendationController(RecommendationService recommendations, CurrentUser currentUser) {
        this.recommendations = recommendations;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<RecommendationFeedDto> getMyRecommendations(Integer limit, String genre) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recommendations.feedFor(currentUser.id(), limit == null ? 20 : limit, genre));
    }
}
