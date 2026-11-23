package com.epam.java.specialization.trackservice.contract;

import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.epam.java.specialization.trackservice.client.ArtistDirectory;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.client.auth.dto.UserRoleDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonArray;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer side of the contract catalog-service (track-service) -> auth-service {@code GET /internal/users}.
 * <p>
 * The test runs the real Spring wiring of the client: {@code AuthClientConfig} (generated HTTP interface client,
 * {@code X-Internal-Token} header, application JSON mapper with {@code fail-on-unknown-properties}) and
 * {@link ArtistDirectory} (resilience4j). Instead of auth-service a Pact mock server answers on a fixed port.
 * On success Pact writes {@code pacts/catalog-service-auth-service.json}, which auth-service then verifies
 * against its real controller ({@code AuthServiceProviderContractTest}).
 */
@Tag("contract")
@PactConsumerTest
@PactTestFor(providerName = AuthServiceContractTest.PROVIDER, port = AuthServiceContractTest.MOCK_PORT,
        pactVersion = PactSpecVersion.V4)
@SpringBootTest(properties = {
        "auth-client.base-url=http://localhost:" + AuthServiceContractTest.MOCK_PORT,
        "auth-client.internal-token=dev-internal-token",
        // No real infrastructure is needed: H2 instead of PostgreSQL, gRPC on a random port.
        "spring.datasource.url=jdbc:h2:mem:contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.grpc.server.port=0",
        "spring.kafka.listener.auto-startup=false",
        // A contract mismatch must surface as a failure, not be hidden by retries
        "resilience4j.retry.instances.auth-service.max-attempts=1",
})
class AuthServiceContractTest {

    static final String CONSUMER = "catalog-service";
    static final String PROVIDER = "auth-service";
    static final String MOCK_PORT = "18081";

    private static final String ISO_DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})";

    @Autowired
    private ArtistDirectory directory;

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    V4Pact artistsExist(PactDslWithProvider builder) {
        return builder
                .given("users 7 (ARTIST) and 8 (LISTENER) exist")
                .uponReceiving("a batch lookup of existing users")
                    .method("GET")
                    .path("/internal/users")
                    .matchQuery("ids", "\\d+", List.of("7", "8"))
                    .matchHeader("X-Internal-Token", ".+", "dev-internal-token")
                .willRespondWith()
                    .status(200)
                    .matchHeader("Content-Type", "application/json.*", "application/json")
                    .body(newJsonArray(users -> {
                        users.object(u -> {
                            u.numberType("id", 7L);
                            u.stringType("email", "user7@example.com");
                            u.stringType("displayName", "Artist Seven");
                            u.stringMatcher("role", "LISTENER|ARTIST|ADMIN", "ARTIST");
                            u.stringMatcher("status", "ACTIVE|BLOCKED", "ACTIVE");
                            u.stringMatcher("createdAt", ISO_DATE_TIME, "2026-10-01T10:00:00Z");
                        });
                        users.object(u -> {
                            u.numberType("id", 8L);
                            u.stringType("email", "user8@example.com");
                            u.stringType("displayName", "Listener Eight");
                            u.stringMatcher("role", "LISTENER|ARTIST|ADMIN", "LISTENER");
                            u.stringMatcher("status", "ACTIVE|BLOCKED", "ACTIVE");
                            u.stringMatcher("createdAt", ISO_DATE_TIME, "2026-10-02T10:00:00Z");
                        });
                    }).build())
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    V4Pact unknownUsersAreSkipped(PactDslWithProvider builder) {
        return builder
                .given("no user with id 999 exists")
                .uponReceiving("a batch lookup of an unknown user")
                    .method("GET")
                    .path("/internal/users")
                    .matchQuery("ids", "\\d+", "999")
                    .matchHeader("X-Internal-Token", ".+", "dev-internal-token")
                .willRespondWith()
                    .status(200)
                    .body("[]", "application/json")
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "artistsExist")
    void resolvesArtistNamesAndRoles() {
        ArtistProfiles profiles = directory.findProfiles(List.of(7L, 8L));

        assertThat(profiles.available()).as("auth-service answered (no fallback)").isTrue();
        assertThat(profiles.nameOf(7L)).isEqualTo("Artist Seven");
        assertThat(profiles.nameOf(8L)).isEqualTo("Listener Eight");
        assertThat(profiles.isArtist(7L)).isTrue();
        assertThat(profiles.isArtist(8L)).isFalse();
        assertThat(profiles.find(7L)).hasValueSatisfying(u -> assertThat(u.getRole()).isEqualTo(UserRoleDto.ARTIST));
    }

    @Test
    @PactTestFor(pactMethod = "unknownUsersAreSkipped")
    void unknownArtistIsShownAsUnknown() {
        ArtistProfiles profiles = directory.findProfile(999L);

        assertThat(profiles.available()).isTrue();
        assertThat(profiles.users()).isEqualTo(Map.of());
        assertThat(profiles.nameOf(999L)).isEqualTo(ArtistProfiles.UNKNOWN_ARTIST);
    }
}
