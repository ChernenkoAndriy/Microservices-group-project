package com.epam.java.specialization.authservice.repository;

import com.epam.java.specialization.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
}
