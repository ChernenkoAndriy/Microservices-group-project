package com.epam.java.specialization.authservice.service;

import org.mockito.Spy;
import org.mapstruct.factory.Mappers;
import com.epam.java.specialization.authservice.mapper.UserMapper;
import com.epam.java.specialization.authservice.api.dto.LoginRequestDto;
import com.epam.java.specialization.authservice.api.dto.RegisterRequestDto;
import com.epam.java.specialization.authservice.api.dto.RegistrationRoleDto;
import com.epam.java.specialization.authservice.api.dto.TokenResponseDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.exception.AccountBlockedException;
import com.epam.java.specialization.authservice.exception.InvalidCredentialsException;
import com.epam.java.specialization.authservice.exception.InvalidRefreshTokenException;
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

    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @InjectMocks
    private AuthService authService;

    @Nested
    class Register {

        private final RegisterRequestDto request =
                new RegisterRequestDto("alice@example.com", "password123", "alice", RegistrationRoleDto.LISTENER);

        @Test
        void savesActiveUserWithHashedPassword() {
            when(passwordEncoder.encode("password123")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            UserProfileDto response = authService.register(request);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
            assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getDisplayName()).isEqualTo("alice");
            assertThat(response.getEmail()).isEqualTo("alice@example.com");
            assertThat(response.getRole()).isEqualTo(UserRoleDto.LISTENER);
            assertThat(response.getStatus()).isEqualTo(UserStatusDto.ACTIVE);
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

        private final LoginRequestDto request = new LoginRequestDto("user1@example.com", "password123");

        @Test
        void issuesAccessAndRefreshTokens() {
            User user = listener(1L);
            when(userRepository.findByEmail("user1@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
            stubTokenIssuing(user);

            TokenResponseDto response = authService.login(request);

            assertThat(response).isEqualTo(new TokenResponseDto("access", TokenResponseDto.TokenTypeEnum.BEARER, 600, "refresh", 2_592_000));
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

            TokenResponseDto response = authService.refresh("old-refresh");

            assertThat(response.getAccessToken()).isEqualTo("access");
            assertThat(response.getRefreshToken()).isEqualTo("refresh");
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

    private void stubTokenIssuing(User user) {
        when(jwtService.generateToken(user)).thenReturn("access");
        when(jwtService.getTtlSeconds()).thenReturn(600L);
        when(refreshTokenService.issue(user)).thenReturn("refresh");
        when(refreshTokenService.getTtlSeconds()).thenReturn(2_592_000L);
    }
}
