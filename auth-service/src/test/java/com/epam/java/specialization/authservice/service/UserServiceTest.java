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

    @InjectMocks
    private UserService userService;

    @Nested
    class GetProfile {

        @Test
        void returnsProfileOfExistingUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));

            UserResponse response = userService.getProfile(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("user1@example.com");
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
            assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest(null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("EMPTY_UPDATE");
            verifyNoInteractions(userRepository);
        }

        @Test
        void updatesUsernameAndAvatar() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            returnSavedUser();

            UserResponse response = userService.updateProfile(1L, new UpdateProfileRequest("alice", "https://cdn/a.png"));

            assertThat(response.username()).isEqualTo("alice");
            assertThat(response.avatarUrl()).isEqualTo("https://cdn/a.png");
        }

        @Test
        void keepsOmittedFields() {
            User user = listener(1L);
            user.setAvatarUrl("https://cdn/old.png");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            returnSavedUser();

            UserResponse response = userService.updateProfile(1L, new UpdateProfileRequest("alice", null));

            assertThat(response.avatarUrl()).isEqualTo("https://cdn/old.png");
        }

        @Test
        void rejectsUsernameTakenByAnotherUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));
            when(userRepository.existsByUsername("taken")).thenReturn(true);

            assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest("taken", null)))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .extracting("code").isEqualTo("USERNAME_ALREADY_TAKEN");
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void allowsResubmittingOwnUsername() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(listener(1L)));
            returnSavedUser();

            userService.updateProfile(1L, new UpdateProfileRequest("user1", null));

            verify(userRepository, never()).existsByUsername(anyString());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsersSortsNewestFirstAndMapsPage() {
        PageRequest expectedPage = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listener(1L), admin(2L)), expectedPage, 12));

        UserPage page = userService.listUsers("ali", Role.LISTENER, UserStatus.ACTIVE, 2, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(expectedPage);
        assertThat(page.items()).extracting(UserResponse::id).containsExactly(1L, 2L);
        assertThat(page.page()).isEqualTo(new UserPage.PageMetadata(2, 5, 12, 3));
    }

    @Nested
    class UpdateUser {

        @Test
        void rejectsEmptyUpdate() {
            assertThatThrownBy(() -> userService.updateUser(1L, 2L, new AdminUpdateUserRequest(null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("EMPTY_UPDATE");
        }

        @Test
        void throwsForUnknownUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(1L, 2L, new AdminUpdateUserRequest(Role.ARTIST, null)))
                    .isInstanceOf(EntityDoesNotExistException.class);
        }

        @Test
        void blockingAnotherUserRevokesTheirRefreshTokens() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(listener(2L)));
            returnSavedUser();

            UserResponse response = userService.updateUser(1L, 2L, new AdminUpdateUserRequest(null, UserStatus.BLOCKED));

            assertThat(response.status()).isEqualTo(UserStatus.BLOCKED);
            verify(refreshTokenService).revokeAll(2L);
        }

        @Test
        void blockingAlreadyBlockedUserDoesNotRevokeAgain() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.LISTENER, UserStatus.BLOCKED)));
            returnSavedUser();

            userService.updateUser(1L, 2L, new AdminUpdateUserRequest(null, UserStatus.BLOCKED));

            verifyNoInteractions(refreshTokenService);
        }

        @Test
        void unblocksAndChangesRole() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.LISTENER, UserStatus.BLOCKED)));
            returnSavedUser();

            UserResponse response = userService.updateUser(1L, 2L,
                    new AdminUpdateUserRequest(Role.ARTIST, UserStatus.ACTIVE));

            assertThat(response.role()).isEqualTo(Role.ARTIST);
            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
            verifyNoInteractions(refreshTokenService);
        }

        @Test
        void adminCannotBlockThemselves() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1L)));

            assertThatThrownBy(() -> userService.updateUser(1L, 1L, new AdminUpdateUserRequest(null, UserStatus.BLOCKED)))
                    .isInstanceOf(SelfModificationForbiddenException.class);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void adminCannotDemoteThemselves() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1L)));

            assertThatThrownBy(() -> userService.updateUser(1L, 1L, new AdminUpdateUserRequest(Role.LISTENER, null)))
                    .isInstanceOf(SelfModificationForbiddenException.class);
        }

        @Test
        void adminMayResubmitOwnAdminRole() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1L)));
            returnSavedUser();

            UserResponse response = userService.updateUser(1L, 1L, new AdminUpdateUserRequest(Role.ADMIN, UserStatus.ACTIVE));

            assertThat(response.role()).isEqualTo(Role.ADMIN);
        }
    }

    @Nested
    class GetUserByEmail {

        @Test
        void returnsUser() {
            when(userRepository.findByEmail("user1@example.com")).thenReturn(Optional.of(listener(1L)));

            assertThat(userService.getUserByEmail("user1@example.com").id()).isEqualTo(1L);
        }

        @Test
        void throwsForUnknownEmail() {
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserByEmail("nobody@example.com"))
                    .isInstanceOf(EntityDoesNotExistException.class);
        }
    }

    private void returnSavedUser() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
