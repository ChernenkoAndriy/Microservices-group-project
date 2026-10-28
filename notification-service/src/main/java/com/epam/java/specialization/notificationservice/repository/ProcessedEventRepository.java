package com.epam.java.specialization.notificationservice.repository;

import com.epam.java.specialization.notificationservice.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
