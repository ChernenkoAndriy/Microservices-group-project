package com.epam.java.specialization.authservice.service;

import com.epam.java.specialization.authservice.api.dto.AdminUpdateUserRequestDto;
import com.epam.java.specialization.authservice.api.dto.UpdateProfileRequestDto;
import com.epam.java.specialization.authservice.api.dto.UserPageDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.exception.BadRequestException;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.SelfModificationForbiddenException;
import com.epam.java.specialization.authservice.exception.UserAlreadyExistsException;
import com.epam.java.specialization.authservice.internal.api.dto.UserSummaryDto;
import com.epam.java.specialization.authservice.jwt.RefreshTokenService;
import com.epam.java.specialization.authservice.mapper.InternalUserMapper;
import com.epam.java.specialization.authservice.mapper.UserMapper;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final InternalUserMapper internalUserMapper;

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        return userMapper.toProfile(findUser(userId));
    }

    @Transactional
    public UserProfileDto updateProfile(Long userId, UpdateProfileRequestDto request) {
        if (request.getDisplayName() == null && request.getAvatarUrl() == null) {
            throw new BadRequestException("EMPTY_UPDATE", "At least one field must be present");
        }
        User user = findUser(userId);

        String displayName = request.getDisplayName();
        if (displayName != null && !displayName.equals(user.getUsername())) {
            if (userRepository.existsByUsername(displayName)) {
                throw UserAlreadyExistsException.usernameTaken(displayName);
            }
            user.setUsername(displayName);
        }
        if (request.getAvatarUrl() != null) {
            if (!isHttpUrl(request.getAvatarUrl())) {
                throw new BadRequestException("INVALID_AVATAR_URL", "avatarUrl must be an absolute http(s) URL");
            }
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User saved = userRepository.saveAndFlush(user);
        log.debug("Updated profile of user id={}", userId);
        return userMapper.toProfile(saved);
    }

    @Transactional(readOnly = true)
    public UserPageDto listUsers(String q, Role role, UserStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return userMapper.toPage(userRepository.findAll(UserSpecifications.search(q, role, status), pageRequest));
    }

    @Transactional
    public UserProfileDto updateUser(Long callerId, Long userId, AdminUpdateUserRequestDto request) {
        Role role = userMapper.toModel(request.getRole());
        UserStatus status = userMapper.toModel(request.getStatus());
        if (role == null && status == null) {
            throw new BadRequestException("EMPTY_UPDATE", "At least one field must be present");
        }
        User user = findUser(userId);

        boolean demotesSelf = role != null && role != Role.ADMIN;
        boolean blocksSelf = status == UserStatus.BLOCKED;
        if (userId.equals(callerId) && (demotesSelf || blocksSelf)) {
            throw new SelfModificationForbiddenException();
        }

        if (role != null) {
            user.setRole(role);
        }
        if (status != null) {
            if (status == UserStatus.BLOCKED && !user.isBlocked()) {
                refreshTokenService.revokeAll(userId);
            }
            user.setStatus(status);
        }

        User saved = userRepository.saveAndFlush(user);
        log.debug("User id={} updated user id={}: role={} status={}", callerId, userId, saved.getRole(), saved.getStatus());
        return userMapper.toProfile(saved);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getUsersByIds(Collection<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .map(internalUserMapper::toSummary)
                .toList();
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityDoesNotExistException("User with id " + userId + " does not exist"));
    }
}
