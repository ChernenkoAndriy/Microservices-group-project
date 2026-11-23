package com.epam.java.specialization.notificationservice.controller;

import com.epam.java.specialization.notificationservice.api.DeliveryLogApi;
import com.epam.java.specialization.notificationservice.api.dto.DeliveryStatusDto;
import com.epam.java.specialization.notificationservice.api.dto.NotificationLogEntryDto;
import com.epam.java.specialization.notificationservice.api.dto.NotificationLogPageDto;
import com.epam.java.specialization.notificationservice.api.dto.PageMetadataDto;
import com.epam.java.specialization.notificationservice.model.NotificationLog;
import com.epam.java.specialization.notificationservice.repository.NotificationLogRepository;
import com.epam.java.specialization.notificationservice.web.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DeliveryLogController implements DeliveryLogApi {

    private final NotificationLogRepository logs;
    private final CurrentUser currentUser;

    public DeliveryLogController(NotificationLogRepository logs, CurrentUser currentUser) {
        this.logs = logs;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<NotificationLogPageDto> listNotificationLog(DeliveryStatusDto status, String recipientEmail,
                                                                      String templateCode, OffsetDateTime createdFrom,
                                                                      OffsetDateTime createdTo, Integer page, Integer size) {
        currentUser.requireAdmin();
        if (createdFrom != null && createdTo != null && !createdTo.isAfter(createdFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "createdTo must be after createdFrom");
        }
        Specification<NotificationLog> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) {
                where.add(cb.equal(root.get("status"), status.getValue()));
            }
            if (recipientEmail != null) {
                where.add(cb.equal(cb.lower(root.get("recipientEmail")), recipientEmail.toLowerCase()));
            }
            if (templateCode != null) {
                where.add(cb.equal(root.get("template"), templateCode));
            }
            if (createdFrom != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom.toInstant()));
            }
            if (createdTo != null) {
                where.add(cb.lessThan(root.get("createdAt"), createdTo.toInstant()));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
        Page<NotificationLog> result = logs.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        PageMetadataDto meta = new PageMetadataDto();
        meta.setNumber(result.getNumber());
        meta.setSize(result.getSize());
        meta.setTotalElements(result.getTotalElements());
        meta.setTotalPages(result.getTotalPages());
        return ResponseEntity.ok(new NotificationLogPageDto(result.getContent().stream().map(DeliveryLogController::toDto).toList(), meta));
    }

    private static NotificationLogEntryDto toDto(NotificationLog l) {
        NotificationLogEntryDto dto = new NotificationLogEntryDto(l.getId(), l.getRecipientEmail(), l.getTemplate(),
                l.getSubject(), DeliveryStatusDto.fromValue(l.getStatus()), l.getAttempts(),
                l.getCreatedAt().atOffset(ZoneOffset.UTC));
        dto.setFailureReason(l.getFailureReason());
        dto.setSentAt(l.getSentAt() == null ? null : l.getSentAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
