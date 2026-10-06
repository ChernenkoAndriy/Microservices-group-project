package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.api.dto.LoginRequestDto;
import com.epam.java.specialization.authservice.api.dto.RegisterRequestDto;
import com.epam.java.specialization.authservice.api.dto.RegistrationRoleDto;
import com.epam.java.specialization.authservice.api.dto.TokenResponseDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.exception.InvalidCredentialsException;
import com.epam.java.specialization.authservice.exception.InvalidRefreshTokenException;
import com.epam.java.specialization.authservice.exception.UserAlreadyExistsException;
import com.epam.java.specialization.authservice.idempotency.IdempotencyRecord;
import com.epam.java.specialization.authservice.idempotency.IdempotencyRecordRepository;
import com.epam.java.specialization.authservice.idempotency.IdempotencyService;
import com.epam.java.specialization.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private IdempotencyRecordRepository idempotencyRecords;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvc(new AuthController(authService,
                new IdempotencyService(idempotencyRecords, JsonMapper.builder().build(), Clock.systemUTC())));
    }

    @Test
    void registerReturnsCreatedUser() throws Exception {
        when(authService.register(new RegisterRequestDto("alice@example.com", "password123", "alice", RegistrationRoleDto.LISTENER)))
                .thenReturn(new UserProfileDto(1L, "alice@example.com", "alice", UserRoleDto.LISTENER, UserStatusDto.ACTIVE,
                        null, null));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"displayName":"alice","email":"alice@example.com","role":"LISTENER","password":"password123"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.displayName").value("alice"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerValidatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"displayName":" alice","email":"not-an-email","password":"short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("displayName", "email", "role", "password")));
        verifyNoInteractions(authService);
    }

    @Test
    void registerMapsConflict() throws Exception {
        when(authService.register(any())).thenThrow(UserAlreadyExistsException.emailTaken("alice@example.com"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"displayName":"alice","email":"alice@example.com","role":"LISTENER","password":"password123"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login(new LoginRequestDto("alice@example.com", "password123")))
                .thenReturn(new TokenResponseDto("access", TokenResponseDto.TokenTypeEnum.BEARER, 600, "refresh", 2_592_000));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"alice@example.com","password":"password123"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000));
    }

    @Test
    void loginMapsInvalidCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"alice@example.com","password":"wrong"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshReturnsNewTokens() throws Exception {
        when(authService.refresh("old")).thenReturn(new TokenResponseDto("access2", TokenResponseDto.TokenTypeEnum.BEARER, 600, "refresh2", 2_592_000));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                        {"refreshToken":"old"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh2"));
    }

    @Test
    void refreshMapsInvalidToken() throws Exception {
        when(authService.refresh("old")).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                        {"refreshToken":"old"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshRequiresToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                        {"refreshToken":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("""
                        {"refreshToken":"refresh"}
                        """))
                .andExpect(status().isNoContent());
        verify(authService).logout("refresh");
    }

    @Test
    void registerWithIdempotencyKeyStoresTheResponse() throws Exception {
        when(authService.register(any())).thenReturn(new UserProfileDto(1L, "alice@example.com", "alice",
                UserRoleDto.LISTENER, UserStatusDto.ACTIVE, null, null));

        mockMvc.perform(post("/api/v1/auth/register").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_ALICE))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyService.REPLAYED_HEADER));
        verify(idempotencyRecords).save(any(IdempotencyRecord.class));
    }

    @Test
    void registerRepeatWithSameKeyReplaysFirstResponse() throws Exception {
        when(authService.register(any())).thenReturn(new UserProfileDto(1L, "alice@example.com", "alice",
                UserRoleDto.LISTENER, UserStatusDto.ACTIVE, null, null));
        mockMvc.perform(post("/api/v1/auth/register").header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON).content(REGISTER_ALICE));
        var stored = org.mockito.ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecords).save(stored.capture());
        when(idempotencyRecords.findById("register:key-1")).thenReturn(Optional.of(stored.getValue()));

        mockMvc.perform(post("/api/v1/auth/register").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_ALICE))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyService.REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.id").value(1));
        verify(authService).register(any());
    }

    @Test
    void registerReusingKeyWithOtherBodyIsUnprocessable() throws Exception {
        when(idempotencyRecords.findById("register:key-1")).thenReturn(Optional.of(new IdempotencyRecord(
                "register:key-1", "another-hash", 201, "{}", Instant.now(), Instant.now().plusSeconds(60))));

        mockMvc.perform(post("/api/v1/auth/register").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_ALICE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        verify(authService, never()).register(any());
    }

    @Test
    void wrongMethodIsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownPathIsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unexpectedErrorIsProblemDetail() throws Exception {
        when(authService.login(any())).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"alice@example.com","password":"password123"}
                        """))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    private static final String REGISTER_ALICE = """
            {"displayName":"alice","email":"alice@example.com","role":"LISTENER","password":"password123"}
            """;
}
