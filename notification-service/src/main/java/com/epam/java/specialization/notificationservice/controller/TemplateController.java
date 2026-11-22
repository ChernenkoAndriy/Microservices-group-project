package com.epam.java.specialization.notificationservice.controller;

import com.epam.java.specialization.notificationservice.api.TemplatesApi;
import com.epam.java.specialization.notificationservice.api.dto.CreateNotificationTemplateRequestDto;
import com.epam.java.specialization.notificationservice.api.dto.NotificationTemplateDto;
import com.epam.java.specialization.notificationservice.api.dto.UpdateNotificationTemplateRequestDto;
import com.epam.java.specialization.notificationservice.model.NotificationTemplate;
import com.epam.java.specialization.notificationservice.repository.NotificationTemplateRepository;
import com.epam.java.specialization.notificationservice.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

@RestController
public class TemplateController implements TemplatesApi {

    private final NotificationTemplateRepository templates;
    private final CurrentUser currentUser;
    private final Clock clock;

    public TemplateController(NotificationTemplateRepository templates, CurrentUser currentUser, Clock clock) {
        this.templates = templates;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationTemplateDto>> listNotificationTemplates() {
        currentUser.requireAdmin();
        return ResponseEntity.ok(templates.findAllByOrderByCodeAsc().stream().map(TemplateController::toDto).toList());
    }

    @Override
    @Transactional
    public ResponseEntity<NotificationTemplateDto> createNotificationTemplate(CreateNotificationTemplateRequestDto request) {
        currentUser.requireAdmin();
        if (templates.existsById(request.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "TEMPLATE_CODE_TAKEN: a template with code " + request.getCode() + " already exists");
        }
        boolean active = request.getActive() == null || request.getActive();
        NotificationTemplate saved = templates.save(new NotificationTemplate(request.getCode(), request.getSubject(),
                request.getBody(), request.getDescription(), active, clock.instant()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<NotificationTemplateDto> getNotificationTemplate(String templateCode) {
        currentUser.requireAdmin();
        return ResponseEntity.ok(toDto(find(templateCode)));
    }

    @Override
    @Transactional
    public ResponseEntity<NotificationTemplateDto> updateNotificationTemplate(String templateCode,
                                                                              UpdateNotificationTemplateRequestDto request) {
        currentUser.requireAdmin();
        NotificationTemplate template = find(templateCode);
        template.replace(request.getSubject(), request.getBody(), request.getDescription(), request.getActive(),
                clock.instant());
        return ResponseEntity.ok(toDto(templates.save(template)));
    }

    private NotificationTemplate find(String code) {
        return templates.findById(code).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Template " + code + " does not exist"));
    }

    private static NotificationTemplateDto toDto(NotificationTemplate t) {
        NotificationTemplateDto dto = new NotificationTemplateDto(t.getCode(), t.getSubject(), t.getBody(), t.isActive(),
                t.getCreatedAt().atOffset(ZoneOffset.UTC), t.getUpdatedAt().atOffset(ZoneOffset.UTC));
        dto.setDescription(t.getDescription());
        return dto;
    }
}
