package com.epam.java.specialization.authservice.service;

import com.epam.java.specialization.authservice.dto.LoginRequest;
import com.epam.java.specialization.authservice.dto.RegisterRequest;
import com.epam.java.specialization.authservice.dto.TokenResponse;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.InvalidCredentialsException;
import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.exception.UserAlreadyExistsException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public User register(RegisterRequest request) {
        log.debug("Registering user username={} email={} role={}", request.username(), request.email(), request.role());
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email is already registered: " + request.email());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("Username is already taken: " + request.username());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        log.debug("Registered user id={}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        log.debug("Login attempt for email={}", request.email());
        User user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        String token = jwtService.generateToken(user);
        log.debug("Login succeeded for user id={}", user.getId());
        return TokenResponse.bearer(token, jwtService.getTtlSeconds());
    }

    public boolean isTokenValid(String token) {
        boolean valid = jwtService.isTokenValid(token);
        log.debug("Token validation result: {}", valid);
        return valid;
    }

    /**
     * @throws TokenIsNotValidException    if the token is not valid
     * @throws EntityDoesNotExistException if the token is valid but its user no longer exists
     */
    @Transactional(readOnly = true)
    public Long getUserIdFromToken(String token) {
        Long userId = jwtService.extractUserId(token);
        if (!userRepository.existsById(userId)) {
            throw new EntityDoesNotExistException("User with id " + userId + " does not exist");
        }
        log.debug("Resolved user id={} from token", userId);
        return userId;
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityDoesNotExistException("User with email " + email + " does not exist"));
    }
}
