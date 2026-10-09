package com.epam.java.specialization.authservice.service;

import org.mockito.Spy;
import org.mapstruct.factory.Mappers;
import com.epam.java.specialization.authservice.mapper.UserMapper;
import com.epam.java.specialization.authservice.mapper.InternalUserMapper;
import com.epam.java.specialization.authservice.api.dto.AdminUpdateUserRequestDto;
import com.epam.java.specialization.authservice.api.dto.PageMetadataDto;
import com.epam.java.specialization.authservice.api.dto.UpdateProfileRequestDto;
import com.epam.java.specialization.authservice.api.dto.UserPageDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.exception.BadRequestException;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.SelfModificationForbiddenException;
import com.epam.java.specialization.authservice.exception.UserAlreadyExistsException;
import com.epam.java.specialization.authservice.jwt.RefreshTokenService;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;
import com.epam.java.specialization.authservice.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static com.epam.java.specialization.authservice.TestUsers.admin;
import static com.epam.java.specialization.authservice.TestUsers.listener;
import static com.epam.java.specialization.authservice.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;

    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    @Spy
    private InternalUserMapper internalUserMapper = Mappers.getMapper(InternalUserMapper.class);

    @InjectMocks
    private UserService userService;

    @Nested
    class GetProfile {

        @Test
        void returnsProfileOfExistingUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));

            UserProfileDto response = userService.getProfile(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getEmail()).isEqualTo("user1@example.com");
        }

        @Test
        void throwsForUnknownUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile(1L)).isInstanceOf(EntityDoesNotExistException.class);
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void rejectsEmptyUpdate() {
            assertThatThrownBy(() -> userService.updateProfile(1L, profileUpdate(null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("EMPTY_UPDATE");
            verifyNoInteractions(userRepository);
        }

        @Test
        void rejectsNonHttpAvatarUrl() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));

            assertThatThrownBy(() -> userService.updateProfile(1L, profileUpdate(null, "not a url")))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("INVALID_AVATAR_URL");
        }

        @Test
        void updatesUsernameAndAvatar() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            returnSavedUser();

            UserProfileDto response = userService.updateProfile(1L, profileUpdate("alice", "https://cdn/a.png"));

            assertThat(response.getDisplayName()).isEqualTo("alice");
            assertThat(response.getAvatarUrl()).isEqualTo("https://cdn/a.png");
        }

        @Test
        void keepsOmittedFields() {
            User user = listener(1L);
            user.setAvatarUrl("https://cdn/old.png");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            returnSavedUser();

            UserProfileDto response = userService.updateProfile(1L, profileUpdate("alice", null));

            assertThat(response.getAvatarUrl()).isEqualTo("https://cdn/old.png");
        }

        @Test
        void rejectsUsernameTakenByAnotherUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));
            when(userRepository.existsByUsername("taken")).thenReturn(true);

            assertThatThrownBy(() -> userService.updateProfile(1L, profileUpdate("taken", null)))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .extracting("code").isEqualTo("USERNAME_ALREADY_TAKEN");
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void allowsResubmittingOwnUsername() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));
            returnSavedUser();

            userService.updateProfile(1L, profileUpdate("user1", null));

            verify(userRepository, never()).existsByUsername(anyString());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsersSortsNewestFirstAndMapsPage() {
        PageRequest expectedPage = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listener(1L), admin(2L)), expectedPage, 12));

        UserPageDto page = userService.listUsers("ali", Role.LISTENER, UserStatus.ACTIVE, 2, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(expectedPage);
        assertThat(page.getItems()).extracting(UserProfileDto::getId).containsExactly(1L, 2L);
        assertThat(page.getPage()).isEqualTo(new PageMetadataDto(2, 5, 12L, 3));
    }

    @Nested
    class UpdateUser {

        @Test
        void rejectsEmptyUpdate() {
            assertThatThrownBy(() -> userService.updateUser(1L, 2L, adminUpdate(null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("EMPTY_UPDATE");
        }

        @Test
        void throwsForUnknownUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(1L, 2L, adminUpdate(UserRoleDto.ARTIST, null)))
                    .isInstanceOf(EntityDoesNotExistException.class);
        }

        @Test
        void blockingAnotherUserRevokesTheirRefreshTokens() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(listener(2L)));
            returnSavedUser();

            UserProfileDto response = userService.updateUser(1L, 2L, adminUpdate(null, UserStatusDto.BLOCKED));

            assertThat(response.getStatus()).isEqualTo(UserStatusDto.BLOCKED);
            verify(refreshTokenService).revokeAll(2L);
        }

        @Test
        void blockingAlreadyBlockedUserDoesNotRevokeAgain() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.LISTENER, UserStatus.BLOCKED)));
            returnSavedUser();

            userService.updateUser(1L, 2L, adminUpdate(null, UserStatusDto.BLOCKED));

            verifyNoInteractions(refreshTokenService);
        }

        @Test
        void unblocksAndChangesRole() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.LISTENER, UserStatus.BLOCKED)));
            returnSavedUser();

            UserProfileDto response = userService.updateUser(1L, 2L,
                    adminUpdate(UserRoleDto.ARTIST, UserStatusDto.ACTIVE));

            assertThat(response.getRole()).isEqualTo(UserRoleDto.ARTIST);
            assertThat(response.getStatus()).isEqualTo(UserStatusDto.ACTIVE);
            verifyNoInteractions(refreshTokenService);
        }

        @Test
        void adminCannotBlockThemselves() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1L)));

            assertThatThrownBy(() -> userService.updateUser(1L, 1L, adminUpdate(null, UserStatusDto.BLOCKED)))
                    .isInstanceOf(SelfModificationForbiddenException.class);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void adminCannotDemoteThemselves() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1L)));

            assertThatThrownBy(() -> userService.updateUser(1L, 1L, adminUpdate(UserRoleDto.LISTENER, null)))
                    .isInstanceOf(SelfModificationForbiddenException.class);
        }

        @Test
        void adminMayResubmitOwnAdminRole() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1L)));
            returnSavedUser();

            UserProfileDto response = userService.updateUser(1L, 1L, adminUpdate(UserRoleDto.ADMIN, UserStatusDto.ACTIVE));

            assertThat(response.getRole()).isEqualTo(UserRoleDto.ADMIN);
        }
    }

    @Nested
    class GetUsersByIds {

        @Test
        void getUsersByIdsLoadsAllInOneQuery() {
            when(userRepository.findAllById(List.of(1L, 2L, 99L))).thenReturn(List.of(listener(1L), listener(2L)));

            assertThat(userService.getUsersByIds(List.of(1L, 2L, 99L)))
                    .extracting(summary -> summary.getDisplayName())
                    .containsExactly("user1", "user2");
            verify(userRepository).findAllById(List.of(1L, 2L, 99L));
        }
    }

    private void returnSavedUser() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static UpdateProfileRequestDto profileUpdate(String displayName, String avatarUrl) {
        return new UpdateProfileRequestDto()
                .displayName(displayName)
                .avatarUrl(avatarUrl);
    }

    private static AdminUpdateUserRequestDto adminUpdate(UserRoleDto role, UserStatusDto status) {
        return new AdminUpdateUserRequestDto().role(role).status(status);
    }
}
