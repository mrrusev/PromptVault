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
import com.promptvault.models.RegisterRequest;
import com.promptvault.repository.CollectionRepository;
import com.promptvault.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollectionControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        // Collections reference users via FK; delete child rows first.
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

    // -------------------------------------------------------------------------
    // GET /collections
    // -------------------------------------------------------------------------

    @Test
    void list_withValidToken_returns200EmptyArray() {
        String token = registerAndGetToken("alice", "password1");

        webTestClient.get().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CollectionResponse.class)
                .hasSize(0);
    }

    @Test
    void list_withoutToken_returns401() {
        webTestClient.get().uri("/collections")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -------------------------------------------------------------------------
    // POST /collections
    // -------------------------------------------------------------------------

    @Test
    void create_withValidToken_returns201WithNameAndOwnerId() {
        AuthResponse auth = register("bob", "password1");

        webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest("Work Prompts"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CollectionResponse.class)
                .value(r -> {
                    assertThat(r.id()).isNotNull();
                    assertThat(r.name()).isEqualTo("Work Prompts");
                    assertThat(r.ownerId()).isEqualTo(auth.user().id());
                    assertThat(r.createdAt()).isNotNull();
                });
    }

    @Test
    void create_withoutToken_returns401() {
        webTestClient.post().uri("/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest("Work Prompts"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void create_withBlankName_returns400() {
        String token = registerAndGetToken("carol", "password1");

        webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest(""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // -------------------------------------------------------------------------
    // DELETE /collections/{id}
    // -------------------------------------------------------------------------

    @Test
    void delete_ownedCollection_returns204() {
        String token = registerAndGetToken("dave", "password1");

        CollectionResponse created = webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest("To Delete"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CollectionResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();

        webTestClient.delete().uri("/collections/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void delete_withoutToken_returns401() {
        webTestClient.delete().uri("/collections/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void delete_nonExistentId_returns404() {
        String token = registerAndGetToken("erin", "password1");

        webTestClient.delete().uri("/collections/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void delete_anotherUsersCollection_returns404() {
        // User A creates a collection; user B must not be able to delete it.
        String tokenA = registerAndGetToken("frank", "password1");
        String tokenB = registerAndGetToken("grace", "password1");

        CollectionResponse created = webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest("Frank's Private"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CollectionResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();

        webTestClient.delete().uri("/collections/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void list_returnsOnlyOwnersCollections() {
        // User A creates 2 collections, user B creates 1; each should see only their own.
        String tokenA = registerAndGetToken("henry", "password1");
        String tokenB = registerAndGetToken("iris", "password1");

        webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest("Henry's Alpha"))
                .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest("Henry's Beta"))
                .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CollectionRequest("Iris's Only"))
                .exchange().expectStatus().isCreated();

        List<CollectionResponse> henryList = webTestClient.get().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CollectionResponse.class)
                .returnResult()
                .getResponseBody();

        List<CollectionResponse> irisList = webTestClient.get().uri("/collections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CollectionResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(henryList).hasSize(2);
        assertThat(henryList).allMatch(r -> r.name().startsWith("Henry's"));

        assertThat(irisList).hasSize(1);
        assertThat(irisList).allMatch(r -> r.name().equals("Iris's Only"));
    }
}
