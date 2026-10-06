package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.api.dto.UpdateProfileRequestDto;
import com.epam.java.specialization.authservice.api.dto.UserProfileDto;
import com.epam.java.specialization.authservice.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.exception.EntityDoesNotExistException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import com.epam.java.specialization.authservice.service.UserService;
import com.epam.java.specialization.authservice.web.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvc(new UserController(userService, new CurrentUser(jwtService)));
    }

    @Test
    void getMyProfileUsesUserIdHeader() throws Exception {
        when(userService.getProfile(5L)).thenReturn(profile(5L, "bob"));

        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.displayName").value("bob"));
    }

    @Test
    void getMyProfileFallsBackToBearerToken() throws Exception {
        when(jwtService.extractUserId("token")).thenReturn(5L);
        when(userService.getProfile(5L)).thenReturn(profile(5L, "bob"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void getMyProfileWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(userService);
    }

    @Test
    void getMyProfileOfDeletedUserIsNotFound() throws Exception {
        when(userService.getProfile(5L)).thenThrow(new EntityDoesNotExistException("gone"));

        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_DOES_NOT_EXIST"));
    }

    @Test
    void updateMyProfile() throws Exception {
        UpdateProfileRequestDto request = new UpdateProfileRequestDto()
                .displayName("bobby")
                .avatarUrl("https://cdn.example.com/a.png");
        when(userService.updateProfile(5L, request)).thenReturn(profile(5L, "bobby"));

        mockMvc.perform(patch("/api/v1/users/me").header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"displayName":"bobby","avatarUrl":"https://cdn.example.com/a.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("bobby"));
    }

    @Test
    void updateMyProfileValidatesFields() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"displayName":"bobby "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));
        verifyNoInteractions(userService);
    }

    private static UserProfileDto profile(Long id, String displayName) {
        return new UserProfileDto(id, displayName + "@example.com", displayName, UserRoleDto.LISTENER,
                UserStatusDto.ACTIVE, null, null);
    }
}
