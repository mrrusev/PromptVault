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
import com.promptvault.models.RegisterRequest;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.PromptRepository;
import com.promptvault.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PromptControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PromptRepository promptRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        // Prompts reference collections and users via FK; delete child rows first.
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

    // -------------------------------------------------------------------------
    // CRUD happy path
    // -------------------------------------------------------------------------

    @Test
    void create_withValidToken_returns201WithStampedOwner() {
        AuthResponse auth = register("alice", "password1");
        Long collectionId = createCollection(auth.token(), "Work");

        webTestClient.post().uri("/prompts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptCreateRequest(collectionId, "Refactor", "Make it reactive"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PromptResponse.class)
                .value(response -> {
                    assertThat(response.id()).isNotNull();
                    assertThat(response.title()).isEqualTo("Refactor");
                    assertThat(response.content()).isEqualTo("Make it reactive");
                    assertThat(response.collectionId()).isEqualTo(collectionId);
                    assertThat(response.ownerId()).isEqualTo(auth.user().id());
                    assertThat(response.createdAt()).isNotNull();
                });
    }

    @Test
    void list_includesCreatedPrompt_andGetByIdReturns200() {
        String token = registerAndGetToken("bob", "password1");
        Long collectionId = createCollection(token, "Work");
        PromptResponse created = createPrompt(token, collectionId, "Title", "Content");

        List<PromptResponse> prompts = webTestClient.get().uri("/prompts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PromptResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0).id()).isEqualTo(created.id());

        webTestClient.get().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PromptResponse.class)
                .value(response -> assertThat(response.id()).isEqualTo(created.id()));
    }

    @Test
    void list_filteredByCollectionId_returnsOnlyThatCollection() {
        String token = registerAndGetToken("carol", "password1");
        Long collectionA = createCollection(token, "A");
        Long collectionB = createCollection(token, "B");
        createPrompt(token, collectionA, "In A", "content");
        createPrompt(token, collectionB, "In B", "content");

        List<PromptResponse> inA = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/prompts").queryParam("collectionId", collectionA).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PromptResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(inA).hasSize(1);
        assertThat(inA.get(0).collectionId()).isEqualTo(collectionA);
    }

    @Test
    void patch_singleField_changesItAndKeepsOther() {
        String token = registerAndGetToken("dave", "password1");
        Long collectionId = createCollection(token, "Work");
        PromptResponse created = createPrompt(token, collectionId, "Original title", "Original content");

        webTestClient.patch().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptUpdateRequest("New title", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PromptResponse.class)
                .value(response -> {
                    assertThat(response.title()).isEqualTo("New title");
                    assertThat(response.content()).isEqualTo("Original content");
                });
    }

    @Test
    void delete_ownedPrompt_returns204_thenGetReturns404() {
        String token = registerAndGetToken("erin", "password1");
        Long collectionId = createCollection(token, "Work");
        PromptResponse created = createPrompt(token, collectionId, "Title", "Content");

        webTestClient.delete().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    // -------------------------------------------------------------------------
    // Validation / not-found
    // -------------------------------------------------------------------------

    @Test
    void patch_nonExistentId_returns404() {
        String token = registerAndGetToken("frank", "password1");

        webTestClient.patch().uri("/prompts/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptUpdateRequest("New title", null))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void patch_emptyBody_returns400() {
        String token = registerAndGetToken("grace", "password1");
        Long collectionId = createCollection(token, "Work");
        PromptResponse created = createPrompt(token, collectionId, "Title", "Content");

        webTestClient.patch().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptUpdateRequest(null, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void create_missingCollection_returns404() {
        String token = registerAndGetToken("heidi", "password1");

        webTestClient.post().uri("/prompts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptCreateRequest(999999L, "Title", "Content"))
                .exchange()
                .expectStatus().isNotFound();
    }

    // -------------------------------------------------------------------------
    // Ownership isolation — user B must not reach user A's data
    // -------------------------------------------------------------------------

    @Test
    void getById_anotherUsersPrompt_returns404() {
        String tokenA = registerAndGetToken("ivan", "password1");
        String tokenB = registerAndGetToken("judy", "password1");
        Long collectionId = createCollection(tokenA, "Ivan's");
        PromptResponse created = createPrompt(tokenA, collectionId, "Private", "secret");

        webTestClient.get().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void patch_anotherUsersPrompt_returns404() {
        String tokenA = registerAndGetToken("ken", "password1");
        String tokenB = registerAndGetToken("laura", "password1");
        Long collectionId = createCollection(tokenA, "Ken's");
        PromptResponse created = createPrompt(tokenA, collectionId, "Private", "secret");

        webTestClient.patch().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptUpdateRequest("Hacked", null))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void delete_anotherUsersPrompt_returns404() {
        String tokenA = registerAndGetToken("mike", "password1");
        String tokenB = registerAndGetToken("nina", "password1");
        Long collectionId = createCollection(tokenA, "Mike's");
        PromptResponse created = createPrompt(tokenA, collectionId, "Private", "secret");

        webTestClient.delete().uri("/prompts/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void list_excludesAnotherUsersPrompts() {
        String tokenA = registerAndGetToken("olen", "password1");
        String tokenB = registerAndGetToken("pat", "password1");
        Long collectionA = createCollection(tokenA, "A's");
        Long collectionB = createCollection(tokenB, "B's");
        createPrompt(tokenA, collectionA, "A's prompt", "content");
        createPrompt(tokenB, collectionB, "B's prompt", "content");

        List<PromptResponse> listForA = webTestClient.get().uri("/prompts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PromptResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(listForA).hasSize(1);
        assertThat(listForA).allMatch(response -> response.title().equals("A's prompt"));
    }

    @Test
    void create_intoAnotherUsersCollection_returns404() {
        String tokenA = registerAndGetToken("quinn", "password1");
        String tokenB = registerAndGetToken("rose", "password1");
        Long collectionA = createCollection(tokenA, "A's private");

        webTestClient.post().uri("/prompts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptCreateRequest(collectionA, "Sneaky", "content"))
                .exchange()
                .expectStatus().isNotFound();
    }

    // -------------------------------------------------------------------------
    // Unauthenticated
    // -------------------------------------------------------------------------

    @Test
    void list_withoutToken_returns401() {
        webTestClient.get().uri("/prompts")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void create_withoutToken_returns401() {
        webTestClient.post().uri("/prompts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptCreateRequest(1L, "Title", "Content"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getById_withoutToken_returns401() {
        webTestClient.get().uri("/prompts/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void patch_withoutToken_returns401() {
        webTestClient.patch().uri("/prompts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PromptUpdateRequest("New title", null))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void delete_withoutToken_returns401() {
        webTestClient.delete().uri("/prompts/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
