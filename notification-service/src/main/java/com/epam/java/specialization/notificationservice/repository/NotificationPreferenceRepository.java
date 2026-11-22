package com.epam.java.specialization.notificationservice.repository;

import com.epam.java.specialization.notificationservice.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
}
