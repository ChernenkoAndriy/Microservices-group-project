package com.epam.java.specialization.authservice.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.spring7.Spring7MockMvcTestTarget;
import com.epam.java.specialization.authservice.config.JacksonConfig;
import com.epam.java.specialization.authservice.controller.InternalAuthController;
import com.epam.java.specialization.authservice.exception.GlobalExceptionHandler;
import com.epam.java.specialization.authservice.filter.InternalTokenFilter;
import com.epam.java.specialization.authservice.mapper.InternalUserMapper;
import com.epam.java.specialization.authservice.mapper.InternalUserMapperImpl;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;
import com.epam.java.specialization.authservice.security.SecurityConfig;
import com.epam.java.specialization.authservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Provider side of the contract catalog-service -> auth-service. Replays every interaction of
 * {@code pacts/catalog-service-auth-service.json} (written by track-service's {@code AuthServiceContractTest})
 * against the real web layer of auth-service: {@link InternalAuthController}, {@link InternalTokenFilter},
 * {@link SecurityConfig}, {@link GlobalExceptionHandler}, the real {@link InternalUserMapper} and the
 * application's Jackson settings. Only the database lookup ({@link UserService}) is replaced, by the provider
 * states below. If the response no longer matches what the consumer expects, the test fails.
 */
@Tag("contract")
@Provider("auth-service")
@PactFolder("../pacts")
@WebMvcTest(InternalAuthController.class)
@Import({AuthServiceProviderContractTest.WebSecurity.class, SecurityConfig.class, JacksonConfig.class,
        InternalUserMapperImpl.class, InternalTokenFilter.class})
@TestPropertySource(properties = "internal-api.token=dev-internal-token")
class AuthServiceProviderContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InternalUserMapper mapper;
    @MockitoBean
    private UserService userService;

    /** The "database" of the current provider state. */
    private final Map<Long, User> users = new HashMap<>();

    @BeforeEach
    void setUp(PactVerificationContext context) {
        users.clear();
        // Same logic as UserService.getUsersByIds: unknown ids are skipped, known users are mapped to summaries.
        when(userService.getUsersByIds(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(users::get).filter(Objects::nonNull).map(mapper::toSummary).toList();
        });
        context.setTarget(new Spring7MockMvcTestTarget(mockMvc));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("users 7 (ARTIST) and 8 (LISTENER) exist")
    void artistAndListenerExist() {
        users.put(7L, user(7L, "Artist Seven", Role.ARTIST));
        users.put(8L, user(8L, "Listener Eight", Role.LISTENER));
    }

    @State("no user with id 999 exists")
    void noUsers() {
        users.clear();
    }

    /** The web slice does not start Spring Security by itself; the app gets it from the auto-configuration. */
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurity {
    }

    private static User user(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail("user" + id + "@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return user;
    }
}
