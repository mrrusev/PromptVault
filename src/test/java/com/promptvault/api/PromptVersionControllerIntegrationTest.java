package com.promptvault.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
import com.promptvault.models.PromptCreateRequest;
import com.promptvault.models.PromptResponse;
import com.promptvault.models.PromptUpdateRequest;
import com.promptvault.models.PromptVersionResponse;
import com.promptvault.models.RegisterRequest;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.PromptVersionRepository;
import com.promptvault.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PromptVersionControllerIntegrationTest {

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
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void createVersions_assignSequentialNumbers_andListReturnsThemAscending() {
        String token = registerAndGetToken("alice", "password1");
        Long collectionId = createCollection(token, "Work");
        PromptResponse prompt = createPrompt(token, collectionId, "Title", "Content");

        PromptVersionResponse first = createVersion(token, prompt.id());
        PromptVersionResponse second = createVersion(token, prompt.id());

        assertThat(first.versionNumber()).isEqualTo(1);
        assertThat(second.versionNumber()).isEqualTo(2);

        List<PromptVersionResponse> versions = webTestClient.get().uri("/prompts/" + prompt.id() + "/versions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PromptVersionResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).versionNumber()).isEqualTo(1);
        assertThat(versions.get(1).versionNumber()).isEqualTo(2);
    }

    @Test
    void restore_copiesOldVersionContentBackOntoLivePrompt() {
        String token = registerAndGetToken("bob", "password1");
        Long collectionId = createCollection(token, "Work");
        PromptResponse prompt = createPrompt(token, collectionId, "Title", "original content");

        createVersion(token, prompt.id());

        webTestClient.patch().uri("/prompts/" + prompt.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptUpdateRequest(null, "edited content"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/prompts/" + prompt.id() + "/versions/1/restore")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PromptResponse.class)
                .value(response -> assertThat(response.content()).isEqualTo("original content"));

        webTestClient.get().uri("/prompts/" + prompt.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PromptResponse.class)
                .value(response -> assertThat(response.content()).isEqualTo("original content"));
    }

    // -------------------------------------------------------------------------
    // Not-found
    // -------------------------------------------------------------------------

    @Test
    void restore_nonExistentVersion_returns404() {
        String token = registerAndGetToken("carol", "password1");
        Long collectionId = createCollection(token, "Work");
        PromptResponse prompt = createPrompt(token, collectionId, "Title", "Content");

        webTestClient.post().uri("/prompts/" + prompt.id() + "/versions/999/restore")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    // -------------------------------------------------------------------------
    // Ownership isolation — user B must not reach user A's prompt versions
    // -------------------------------------------------------------------------

    @Test
    void createVersion_onAnotherUsersPrompt_returns404() {
        String tokenA = registerAndGetToken("dave", "password1");
        String tokenB = registerAndGetToken("erin", "password1");
        Long collectionId = createCollection(tokenA, "Dave's");
        PromptResponse prompt = createPrompt(tokenA, collectionId, "Private", "secret");

        webTestClient.post().uri("/prompts/" + prompt.id() + "/versions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listVersions_onAnotherUsersPrompt_returns404() {
        String tokenA = registerAndGetToken("frank", "password1");
        String tokenB = registerAndGetToken("grace", "password1");
        Long collectionId = createCollection(tokenA, "Frank's");
        PromptResponse prompt = createPrompt(tokenA, collectionId, "Private", "secret");
        createVersion(tokenA, prompt.id());

        webTestClient.get().uri("/prompts/" + prompt.id() + "/versions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void restore_onAnotherUsersPrompt_returns404() {
        String tokenA = registerAndGetToken("heidi", "password1");
        String tokenB = registerAndGetToken("ivan", "password1");
        Long collectionId = createCollection(tokenA, "Heidi's");
        PromptResponse prompt = createPrompt(tokenA, collectionId, "Private", "secret");
        createVersion(tokenA, prompt.id());

        webTestClient.post().uri("/prompts/" + prompt.id() + "/versions/1/restore")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isNotFound();
    }

    // -------------------------------------------------------------------------
    // Unauthenticated
    // -------------------------------------------------------------------------

    @Test
    void createVersion_withoutToken_returns401() {
        webTestClient.post().uri("/prompts/1/versions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listVersions_withoutToken_returns401() {
        webTestClient.get().uri("/prompts/1/versions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void restore_withoutToken_returns401() {
        webTestClient.post().uri("/prompts/1/versions/1/restore")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
