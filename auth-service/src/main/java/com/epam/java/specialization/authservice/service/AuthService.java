package com.epam.java.specialization.authservice.service;

import com.epam.java.specialization.authservice.api.dto.LoginRequestDto;
import com.epam.java.specialization.authservice.api.dto.RegisterRequestDto;
import com.epam.java.specialization.authservice.api.dto.TokenResponseDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.exception.AccountBlockedException;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.InvalidCredentialsException;
import com.epam.java.specialization.authservice.exception.InvalidRefreshTokenException;
import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.exception.UserAlreadyExistsException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import com.epam.java.specialization.authservice.jwt.RefreshTokenService;
import com.epam.java.specialization.authservice.mapper.UserMapper;
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
    public UserProfileDto register(RegisterRequestDto request) {
        log.debug("Registering user displayName={} email={} role={}",
                request.getDisplayName(), request.getEmail(), request.getRole());
        if (userRepository.existsByEmail(request.getEmail())) {
            throw UserAlreadyExistsException.emailTaken(request.getEmail());
        }
        if (userRepository.existsByUsername(request.getDisplayName())) {
            throw UserAlreadyExistsException.usernameTaken(request.getDisplayName());
        }

        User user = new User();
        user.setUsername(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setRole(UserMapper.toModel(request.getRole()));
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User saved = userRepository.save(user);
        log.debug("Registered user id={}", saved.getId());
        return UserMapper.toProfile(saved);
    }

    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {
        log.debug("Login attempt for email={}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        if (user.isBlocked()) {
            throw new AccountBlockedException();
        }

        log.debug("Login succeeded for user id={}", user.getId());
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponseDto refresh(String refreshToken) {
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

    @Transactional(readOnly = true)
    public Long getUserIdFromToken(String token) {
        Long userId = jwtService.extractUserId(token);
        if (!userRepository.existsById(userId)) {
            throw new EntityDoesNotExistException("User with id " + userId + " does not exist");
        }
        log.debug("Resolved user id={} from token", userId);
        return userId;
    }

    private TokenResponseDto issueTokens(User user) {
        return new TokenResponseDto(
                jwtService.generateToken(user),
                TokenResponseDto.TokenTypeEnum.BEARER,
                Math.toIntExact(jwtService.getTtlSeconds()),
                refreshTokenService.issue(user),
                Math.toIntExact(refreshTokenService.getTtlSeconds()));
    }
}
