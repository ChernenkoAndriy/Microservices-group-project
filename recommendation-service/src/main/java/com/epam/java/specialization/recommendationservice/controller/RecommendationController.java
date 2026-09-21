package com.epam.java.specialization.recommendationservice.controller;

import com.epam.java.specialization.recommendationservice.model.ListenEvent;
import com.epam.java.specialization.recommendationservice.repository.AnalyticsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final AnalyticsRepository analyticsRepository;

    public RecommendationController(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @PostMapping("/events")
    public ResponseEntity<String> recordEvent(@RequestBody ListenEvent event) {
        analyticsRepository.saveEvent(event);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Подію аналітики успішно збережено в ClickHouse для користувача: " + event.getUserId());
    }

    @GetMapping("/users/{userId}/events")
    public ResponseEntity<List<ListenEvent>> getUserEvents(@PathVariable String userId) {
        List<ListenEvent> events = analyticsRepository.getEventsByUser(userId);
        return ResponseEntity.ok(events);
    }
}