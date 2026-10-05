package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.dto.AdminUpdateUserRequest;
import com.epam.java.specialization.authservice.dto.UserPage;
import com.epam.java.specialization.authservice.dto.UserResponse;
import com.epam.java.specialization.authservice.exception.SelfModificationForbiddenException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.UserStatus;
import com.epam.java.specialization.authservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvc(new AdminUserController(userService), jwtService);
    }

    @Test
    void listUsersUsesDefaultPaging() throws Exception {
        when(userService.listUsers(null, null, null, 0, 20)).thenReturn(page(user(1L, UserStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listUsersPassesFilters() throws Exception {
        when(userService.listUsers("ali", Role.ARTIST, UserStatus.BLOCKED, 1, 5)).thenReturn(page());

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", "ali").param("role", "ARTIST").param("status", "BLOCKED")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void listUsersRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(userService);
    }

    @Test
    void listUsersRejectsUnknownRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("role", "SUPERUSER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
        verifyNoInteractions(userService);
    }

    @Test
    void updateUserPassesCallerId() throws Exception {
        when(userService.updateUser(1L, 2L, new AdminUpdateUserRequest(null, UserStatus.BLOCKED)))
                .thenReturn(user(2L, UserStatus.BLOCKED));

        mockMvc.perform(patch("/api/v1/admin/users/2").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"BLOCKED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void updateUserMapsSelfModification() throws Exception {
        when(userService.updateUser(1L, 1L, new AdminUpdateUserRequest(null, UserStatus.BLOCKED)))
                .thenThrow(new SelfModificationForbiddenException());

        mockMvc.perform(patch("/api/v1/admin/users/1").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"BLOCKED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_FORBIDDEN"));
    }

    @Test
    void updateUserRequiresCaller() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"BLOCKED"}
                                """))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    private static UserResponse user(Long id, UserStatus status) {
        return new UserResponse(id, "user" + id + "@example.com", "user" + id, Role.LISTENER, status,
                null, null, null);
    }

    private static UserPage page(UserResponse... users) {
        return new UserPage(List.of(users), new UserPage.PageMetadata(0, 20, users.length, users.length == 0 ? 0 : 1));
    }
}
