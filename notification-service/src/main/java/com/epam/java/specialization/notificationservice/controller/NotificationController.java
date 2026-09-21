package com.epam.java.specialization.notificationservice.controller;

import com.epam.java.specialization.notificationservice.model.NotificationLog;
import com.epam.java.specialization.notificationservice.repository.NotificationLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @PostMapping
    public ResponseEntity<NotificationLog> createNotification(@RequestBody NotificationLog log) {
        NotificationLog savedLog = notificationLogRepository.save(log);
        return ResponseEntity.ok(savedLog);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationLog> getNotificationById(@PathVariable Long id) {
        return notificationLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
