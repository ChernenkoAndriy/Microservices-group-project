package com.epam.java.specialization.notificationservice.repository;

import com.epam.java.specialization.notificationservice.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationLogRepository
        extends JpaRepository<NotificationLog, Long>, JpaSpecificationExecutor<NotificationLog> {
}
