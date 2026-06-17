package com.promptvault.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.promptvault.models.AuthResponse;
import com.promptvault.models.CollectionRequest;
import com.promptvault.models.CollectionResponse;
import com.promptvault.models.DashboardResponse;
import com.promptvault.models.PromptCreateRequest;
import com.promptvault.models.PromptResponse;
import com.promptvault.models.PromptVersionResponse;
import com.promptvault.models.RegisterRequest;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.PromptVersionRepository;
import com.promptvault.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PromptVersionRepository promptVersionRepository;

    @Autowired
    private PromptRepository promptRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        // Versions reference prompts, which reference collections and users via FK;
        // delete child rows first.
        promptVersionRepository.deleteAll().block();
        promptRepository.deleteAll().block();
        collectionRepository.deleteAll().block();
        userRepository.deleteAll().block();
    }

    private AuthResponse register(String username, String password) {
        return webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest(username, password))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private String registerAndGetToken(String username, String password) {
        return register(username, password).token();
    }

    private Long createCollection(String token, String name) {
        CollectionResponse created = webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest(name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CollectionResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        return created.id();
    }

    private PromptResponse createPrompt(String token, Long collectionId, String title, String content) {
        PromptResponse created = webTestClient.post().uri("/prompts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptCreateRequest(collectionId, title, content))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PromptResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        return created;
    }

    private PromptVersionResponse createVersion(String token, Long promptId) {
        PromptVersionResponse created = webTestClient.post().uri("/prompts/" + promptId + "/versions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PromptVersionResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        return created;
    }

    // -------------------------------------------------------------------------
    // Happy path — seeded workspace aggregates correctly
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_withSeededWorkspace_returnsAggregateStats() {
        String token = registerAndGetToken("alice", "password1");
        createCollection(token, "Work");
        Long collectionId = createCollection(token, "Personal");

        createPrompt(token, collectionId, "First", "c1");
        createPrompt(token, collectionId, "Second", "c2");
        PromptResponse latest = createPrompt(token, collectionId, "Third", "c3");

        // Distribute 5 versions across prompts.
        createVersion(token, latest.id());
        createVersion(token, latest.id());
        createVersion(token, latest.id());
        createVersion(token, latest.id());
        createVersion(token, latest.id());

        webTestClient.get().uri("/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .value(response -> {
                    assertThat(response.totalCollections()).isEqualTo(2L);
                    assertThat(response.totalPrompts()).isEqualTo(3L);
                    assertThat(response.totalVersions()).isEqualTo(5L);
                    assertThat(response.latestPrompt()).isNotNull();
                    assertThat(response.latestPrompt().id()).isEqualTo(latest.id());
                    assertThat(response.latestPrompt().title()).isEqualTo("Third");
                });
    }

    // -------------------------------------------------------------------------
    // New user with no data — populated 200 with zero counts and null latest
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_withNoData_returnsZeroCountsAndNullLatest() {
        String token = registerAndGetToken("bob", "password1");

        webTestClient.get().uri("/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .value(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.totalCollections()).isZero();
                    assertThat(response.totalPrompts()).isZero();
                    assertThat(response.totalVersions()).isZero();
                    assertThat(response.latestPrompt()).isNull();
                });
    }

    // -------------------------------------------------------------------------
    // Unauthenticated
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_withoutToken_returns401() {
        webTestClient.get().uri("/dashboard")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -------------------------------------------------------------------------
    // Ownership isolation — user B must not see user A's aggregates
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_doesNotLeakAnotherUsersData() {
        String tokenA = registerAndGetToken("carol", "password1");
        Long collectionId = createCollection(tokenA, "Carol's");
        PromptResponse prompt = createPrompt(tokenA, collectionId, "Private", "secret");
        createVersion(tokenA, prompt.id());

        String tokenB = registerAndGetToken("dave", "password1");

        webTestClient.get().uri("/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .value(response -> {
                    assertThat(response.totalCollections()).isZero();
                    assertThat(response.totalPrompts()).isZero();
                    assertThat(response.totalVersions()).isZero();
                    assertThat(response.latestPrompt()).isNull();
                });
    }
}
