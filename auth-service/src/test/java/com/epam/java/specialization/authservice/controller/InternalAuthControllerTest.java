package com.epam.java.specialization.authservice.controller;

import com.epam.java.specialization.authservice.internal.api.dto.UserRoleDto;
import com.epam.java.specialization.authservice.internal.api.dto.UserStatusDto;
import com.epam.java.specialization.authservice.internal.api.dto.UserSummaryDto;
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
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvc(new InternalAuthController(userService));
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
