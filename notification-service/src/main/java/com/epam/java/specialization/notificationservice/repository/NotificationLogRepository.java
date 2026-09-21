package com.epam.java.specialization.notificationservice.repository;

import com.epam.java.specialization.notificationservice.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}
