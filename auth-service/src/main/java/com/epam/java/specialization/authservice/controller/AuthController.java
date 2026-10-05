package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        User savedUser = userRepository.save(user); //no hash for now
        return ResponseEntity.ok(savedUser);
    }

    @GetMapping("/users/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        User user = userRepository.findByEmail(email);
        if (user != null) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Received test request. Correlation ID: {}", correlationId);
        return ResponseEntity.ok("Auth service is working");
    }
}