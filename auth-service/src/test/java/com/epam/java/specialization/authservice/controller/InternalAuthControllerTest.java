package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.internal.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.internal.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.internal.api.dto.UserSummaryDto;
import com.epam.java.specialization.authservice.service.AuthService;
import com.epam.java.specialization.authservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalAuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvc(new InternalAuthController(authService, userService));
    }

    @Test
    void tokenIsValidStripsBearerPrefix() throws Exception {
        when(authService.isTokenValid("token")).thenReturn(true);

        mockMvc.perform(get("/internal/auth/token-is-valid").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void tokenIsValidWithoutHeaderIsFalse() throws Exception {
        when(authService.isTokenValid(null)).thenReturn(false);

        mockMvc.perform(get("/internal/auth/token-is-valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void getUserIdFromToken() throws Exception {
        when(authService.getUserIdFromToken("token")).thenReturn(7L);

        mockMvc.perform(get("/internal/auth/get-user-id-from-token").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void getUserIdFromInvalidTokenIsUnauthorized() throws Exception {
        when(authService.getUserIdFromToken("junk")).thenThrow(new TokenIsNotValidException("bad"));

        mockMvc.perform(get("/internal/auth/get-user-id-from-token").header("Authorization", "Bearer junk"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_IS_NOT_VALID"));
    }

    @Test
    void getUserIdOfDeletedUserIsNotFound() throws Exception {
        when(authService.getUserIdFromToken("token")).thenThrow(new EntityDoesNotExistException("gone"));

        mockMvc.perform(get("/internal/auth/get-user-id-from-token").header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_DOES_NOT_EXIST"));
    }

    @Test
    void getUserByEmail() throws Exception {
        when(userService.getUserByEmail("bob@example.com")).thenReturn(
                summary(2L, "bob"));

        mockMvc.perform(get("/internal/users/bob@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.role").value("ARTIST"));
    }

    @Test
    void getUsersByIdsReturnsKnownUsers() throws Exception {
        when(userService.getUsersByIds(Set.of(2L, 3L))).thenReturn(List.of(summary(2L, "bob")));

        mockMvc.perform(get("/internal/users").param("ids", "2,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("bob"));
    }

    @Test
    void getUsersByIdsRequiresIds() throws Exception {
        mockMvc.perform(get("/internal/users"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(userService);
    }

    private static UserSummaryDto summary(Long id, String displayName) {
        return new UserSummaryDto(id, displayName + "@example.com", displayName, UserRoleDto.ARTIST,
                UserStatusDto.ACTIVE, OffsetDateTime.parse("2026-10-01T10:00:00Z"));
    }
}
