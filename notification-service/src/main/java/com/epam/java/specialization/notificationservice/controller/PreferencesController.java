package com.epam.java.specialization.notificationservice.controller;

import com.epam.java.specialization.notificationservice.api.PreferencesApi;
import com.epam.java.specialization.notificationservice.api.dto.NotificationPreferencesDto;
import com.epam.java.specialization.notificationservice.api.dto.UpdateNotificationPreferencesRequestDto;
import com.epam.java.specialization.notificationservice.model.NotificationPreference;
import com.epam.java.specialization.notificationservice.repository.NotificationPreferenceRepository;
import com.epam.java.specialization.notificationservice.web.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZoneOffset;

@RestController
public class PreferencesController implements PreferencesApi {

    private final NotificationPreferenceRepository preferences;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PreferencesController(NotificationPreferenceRepository preferences, CurrentUser currentUser, Clock clock) {
        this.preferences = preferences;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<NotificationPreferencesDto> getMyNotificationPreferences() {
        Long userId = currentUser.requireId();
        return ResponseEntity.ok(preferences.findById(userId)
                .map(PreferencesController::toDto)
                .orElseGet(() -> new NotificationPreferencesDto(true, true)));
    }

    @Override
    @Transactional
    public ResponseEntity<NotificationPreferencesDto> updateMyNotificationPreferences(
            UpdateNotificationPreferencesRequestDto request) {
        Long userId = currentUser.requireId();
        NotificationPreference saved = preferences.findById(userId)
                .map(p -> {
                    p.replace(request.getNewReleases(), request.getYearSummary(), clock.instant());
                    return p;
                })
                .orElseGet(() -> new NotificationPreference(userId, request.getNewReleases(),
                        request.getYearSummary(), clock.instant()));
        return ResponseEntity.ok(toDto(preferences.save(saved)));
    }

    private static NotificationPreferencesDto toDto(NotificationPreference p) {
        NotificationPreferencesDto dto = new NotificationPreferencesDto(p.isNewReleases(), p.isYearSummary());
        dto.setUpdatedAt(p.getUpdatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
