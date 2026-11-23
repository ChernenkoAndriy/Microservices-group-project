package com.epam.java.specialization.notificationservice.repository;

import com.epam.java.specialization.notificationservice.model.UserCopy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCopyRepository extends JpaRepository<UserCopy, Long> {
}
