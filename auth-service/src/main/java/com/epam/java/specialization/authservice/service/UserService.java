package com.epam.java.specialization.authservice.service;

import com.epam.java.specialization.authservice.dto.AdminUpdateUserRequest;
import com.epam.java.specialization.authservice.dto.UpdateProfileRequest;
import com.epam.java.specialization.authservice.dto.UserPage;
import com.epam.java.specialization.authservice.dto.UserResponse;
import com.epam.java.specialization.authservice.exception.BadRequestException;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.SelfModificationForbiddenException;
import com.epam.java.specialization.authservice.exception.UserAlreadyExistsException;
import com.epam.java.specialization.authservice.jwt.RefreshTokenService;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;
import com.epam.java.specialization.authservice.repository.UserRepository;
import com.epam.java.specialization.authservice.repository.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException("EMPTY_UPDATE", "At least one field must be present");
        }
        User user = findUser(userId);

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw UserAlreadyExistsException.usernameTaken(request.username());
            }
            user.setUsername(request.username());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }

        User saved = userRepository.saveAndFlush(user);
        log.debug("Updated profile of user id={}", userId);
        // TODO emit UserSnapshot once the message broker is in place
        return UserResponse.from(saved);
    }

    /**
     * Newest users first.
     */
    @Transactional(readOnly = true)
    public UserPage listUsers(String q, Role role, UserStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return UserPage.from(userRepository.findAll(UserSpecifications.search(q, role, status), pageRequest));
    }

    /**
     * Changes role and/or status. Blocking a user revokes all of their refresh tokens.
     *
     * @throws SelfModificationForbiddenException if an admin tries to block or demote themselves
     */
    @Transactional
    public UserResponse updateUser(Long callerId, Long userId, AdminUpdateUserRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException("EMPTY_UPDATE", "At least one field must be present");
        }
        User user = findUser(userId);

        boolean demotesSelf = request.role() != null && request.role() != Role.ADMIN;
        boolean blocksSelf = request.status() == UserStatus.BLOCKED;
        if (userId.equals(callerId) && (demotesSelf || blocksSelf)) {
            throw new SelfModificationForbiddenException();
        }

        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.status() != null) {
            if (request.status() == UserStatus.BLOCKED && !user.isBlocked()) {
                refreshTokenService.revokeAll(userId);
            }
            user.setStatus(request.status());
        }

        User saved = userRepository.saveAndFlush(user);
        log.debug("User id={} updated user id={}: role={} status={}", callerId, userId, saved.getRole(), saved.getStatus());
        // TODO emit UserSnapshot once the message broker is in place
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new EntityDoesNotExistException("User with email " + email + " does not exist"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityDoesNotExistException("User with id " + userId + " does not exist"));
    }
}
