package com.promptvault.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.promptvault.repository.AuthResponse;
import com.promptvault.repository.LoginRequest;
import com.promptvault.repository.RegisterRequest;
import com.promptvault.repository.User;
import com.promptvault.repository.UserDto;
import com.promptvault.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
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

    @Test
    void registerReturns201WithTokenAndUser() {
        AuthResponse response = register("alice", "password1");

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.user().id()).isNotNull();
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void registerDuplicateReturns409() {
        register("bob", "password1");

        webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("bob", "password1"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void registerInvalidBodyReturns400() {
        webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("ab", "short"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void loginReturns200WithToken() {
        register("carol", "password1");

        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("carol", "password1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty()
                .jsonPath("$.user.username").isEqualTo("carol");
    }

    @Test
    void loginBadCredentialsReturns401() {
        register("dave", "password1");

        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("dave", "wrongpassword"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meWithValidTokenReturnsCurrentUser() {
        AuthResponse response = register("erin", "password1");

        webTestClient.get().uri("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + response.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDto.class)
                .value(user -> {
                    assertThat(user.id()).isEqualTo(response.user().id());
                    assertThat(user.username()).isEqualTo("erin");
                });
    }

    @Test
    void meWithoutTokenReturns401() {
        webTestClient.get().uri("/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meWithGarbageTokenReturns401() {
        webTestClient.get().uri("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        register("frank", "password1");

        User stored = userRepository.findByUsername("frank").block();

        assertThat(stored).isNotNull();
        assertThat(stored.getPassword()).isNotEqualTo("password1");
        assertThat(stored.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("password1", stored.getPassword())).isTrue();
    }
}
