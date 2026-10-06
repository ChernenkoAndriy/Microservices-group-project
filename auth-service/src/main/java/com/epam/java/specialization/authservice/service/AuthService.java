package com.epam.java.specialization.authservice.service;

import com.epam.java.specialization.authservice.dto.LoginRequest;
import com.epam.java.specialization.authservice.dto.RegisterRequest;
import com.epam.java.specialization.authservice.dto.TokenResponse;
import com.epam.java.specialization.authservice.dto.UserResponse;
import com.epam.java.specialization.authservice.exception.AccountBlockedException;
import com.epam.java.specialization.authservice.exception.BadRequestException;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.InvalidCredentialsException;
import com.epam.java.specialization.authservice.exception.InvalidRefreshTokenException;
import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.exception.UserAlreadyExistsException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import com.epam.java.specialization.authservice.jwt.RefreshTokenService;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;
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
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.debug("Registering user username={} email={} role={}", request.username(), request.email(), request.role());
        if (request.role() == Role.ADMIN) {
            throw new BadRequestException("ROLE_NOT_ALLOWED", "ADMIN role cannot be self-assigned");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw UserAlreadyExistsException.emailTaken(request.email());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw UserAlreadyExistsException.usernameTaken(request.username());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        log.debug("Registered user id={}", saved.getId());
        return UserResponse.from(saved);
    }

    /**
     * @throws InvalidCredentialsException if the email is unknown or the password is wrong
     * @throws AccountBlockedException     if the credentials are right but the account is blocked
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        log.debug("Login attempt for email={}", request.email());
        User user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        if (user.isBlocked()) {
            throw new AccountBlockedException();
        }

        log.debug("Login succeeded for user id={}", user.getId());
        return issueTokens(user);
    }

    /**
     * Rotates tokens: the submitted refresh token is revoked and a new pair is issued.
     *
     * @throws InvalidRefreshTokenException if the refresh token is unknown, expired or revoked
     * @throws AccountBlockedException      if the account is blocked
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        User user = refreshTokenService.consume(refreshToken);
        if (user.isBlocked()) {
            throw new AccountBlockedException();
        }
        log.debug("Refreshed tokens for user id={}", user.getId());
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
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

    private TokenResponse issueTokens(User user) {
        return TokenResponse.bearer(
                jwtService.generateToken(user),
                jwtService.getTtlSeconds(),
                refreshTokenService.issue(user),
                refreshTokenService.getTtlSeconds());
    }
}
