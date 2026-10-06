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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.epam.java.specialization.authservice.TestUsers.listener;
import static com.epam.java.specialization.authservice.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Nested
    class Register {

        private final RegisterRequest request =
                new RegisterRequest("alice", "alice@example.com", Role.LISTENER, "password123");

        @Test
        void savesActiveUserWithHashedPassword() {
            when(passwordEncoder.encode("password123")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            UserResponse response = authService.register(request);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
            assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.username()).isEqualTo("alice");
            assertThat(response.email()).isEqualTo("alice@example.com");
            assertThat(response.role()).isEqualTo(Role.LISTENER);
            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void rejectsSelfAssignedAdminRole() {
            RegisterRequest adminRequest = new RegisterRequest("root", "root@example.com", Role.ADMIN, "password123");

            assertThatThrownBy(() -> authService.register(adminRequest))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("ROLE_NOT_ALLOWED");
            verifyNoInteractions(userRepository);
        }

        @Test
        void rejectsTakenEmail() {
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .extracting("code").isEqualTo("EMAIL_ALREADY_REGISTERED");
            verify(userRepository, never()).save(any());
        }

        @Test
        void rejectsTakenUsername() {
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .extracting("code").isEqualTo("USERNAME_ALREADY_TAKEN");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class Login {

        private final LoginRequest request = new LoginRequest("user1@example.com", "password123");

        @Test
        void issuesAccessAndRefreshTokens() {
            User user = listener(1L);
            when(userRepository.findByEmail("user1@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
            stubTokenIssuing(user);

            TokenResponse response = authService.login(request);

            assertThat(response).isEqualTo(new TokenResponse("access", "Bearer", 600, "refresh", 2_592_000));
        }

        @Test
        void rejectsUnknownEmail() {
            when(userRepository.findByEmail("user1@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
            verifyNoInteractions(jwtService, refreshTokenService);
        }

        @Test
        void rejectsWrongPassword() {
            when(userRepository.findByEmail("user1@example.com")).thenReturn(Optional.of(listener(1L)));
            when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
            verifyNoInteractions(jwtService, refreshTokenService);
        }

        @Test
        void rejectsBlockedAccountWithCorrectPassword() {
            when(userRepository.findByEmail("user1@example.com"))
                    .thenReturn(Optional.of(user(1L, Role.LISTENER, UserStatus.BLOCKED)));
            when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AccountBlockedException.class);
            verifyNoInteractions(jwtService, refreshTokenService);
        }
    }

    @Nested
    class Refresh {

        @Test
        void issuesNewTokenPairForConsumedToken() {
            User user = listener(1L);
            when(refreshTokenService.consume("old-refresh")).thenReturn(user);
            stubTokenIssuing(user);

            TokenResponse response = authService.refresh("old-refresh");

            assertThat(response.accessToken()).isEqualTo("access");
            assertThat(response.refreshToken()).isEqualTo("refresh");
        }

        @Test
        void rejectsBlockedAccount() {
            when(refreshTokenService.consume("old-refresh")).thenReturn(user(1L, Role.LISTENER, UserStatus.BLOCKED));

            assertThatThrownBy(() -> authService.refresh("old-refresh")).isInstanceOf(AccountBlockedException.class);
            verify(refreshTokenService, never()).issue(any());
        }

        @Test
        void propagatesInvalidRefreshToken() {
            when(refreshTokenService.consume("bad")).thenThrow(new InvalidRefreshTokenException());

            assertThatThrownBy(() -> authService.refresh("bad")).isInstanceOf(InvalidRefreshTokenException.class);
        }
    }

    @Test
    void logoutRevokesRefreshToken() {
        authService.logout("refresh");

        verify(refreshTokenService).revoke("refresh");
    }

    @Test
    void isTokenValidDelegatesToJwtService() {
        when(jwtService.isTokenValid("token")).thenReturn(true);

        assertThat(authService.isTokenValid("token")).isTrue();
    }

    @Nested
    class GetUserIdFromToken {

        @Test
        void returnsIdOfExistingUser() {
            when(jwtService.extractUserId("token")).thenReturn(7L);
            when(userRepository.existsById(7L)).thenReturn(true);

            assertThat(authService.getUserIdFromToken("token")).isEqualTo(7L);
        }

        @Test
        void throwsWhenUserNoLongerExists() {
            when(jwtService.extractUserId("token")).thenReturn(7L);
            when(userRepository.existsById(7L)).thenReturn(false);

            assertThatThrownBy(() -> authService.getUserIdFromToken("token"))
                    .isInstanceOf(EntityDoesNotExistException.class);
        }

        @Test
        void propagatesInvalidToken() {
            when(jwtService.extractUserId("junk")).thenThrow(new TokenIsNotValidException("bad"));

            assertThatThrownBy(() -> authService.getUserIdFromToken("junk"))
                    .isInstanceOf(TokenIsNotValidException.class);
            verifyNoInteractions(userRepository);
        }
    }

    private void stubTokenIssuing(User user) {
        when(jwtService.generateToken(user)).thenReturn("access");
        when(jwtService.getTtlSeconds()).thenReturn(600L);
        when(refreshTokenService.issue(user)).thenReturn("refresh");
        when(refreshTokenService.getTtlSeconds()).thenReturn(2_592_000L);
    }
}
