package com.epam.java.specialization.notificationservice.repository;

import com.epam.java.specialization.notificationservice.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> {

    List<NotificationTemplate> findAllByOrderByCodeAsc();
}
