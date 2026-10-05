package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.dto.UserResponse;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.UserStatus;
import com.epam.java.specialization.authservice.service.AuthService;
import com.epam.java.specialization.authservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

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
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvc(new InternalAuthController(authService, userService), jwtService);
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
                new UserResponse(2L, "bob@example.com", "bob", Role.ARTIST, UserStatus.ACTIVE, null, null, null));

        mockMvc.perform(get("/internal/users/bob@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.role").value("ARTIST"));
    }
}
