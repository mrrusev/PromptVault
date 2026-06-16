package com.promptvault.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.promptvault.repository.entities.User;

import io.jsonwebtoken.Claims;

class JwtServiceTest {

    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private JwtService jwtService(Duration expiration) {
        return new JwtService(new JwtProperties(SECRET, expiration));
    }

    private User user() {
        return new User(42L, "alice", "$2a$hash", null);
    }

    @Test
    void signAndValidateRoundTrip() {
        JwtService service = jwtService(Duration.ofHours(1));

        String token = service.generateToken(user());
        Optional<Claims> claims = service.parse(token);

        assertThat(claims).isPresent();
        assertThat(service.getUsername(claims.get())).isEqualTo("alice");
        assertThat(service.getUid(claims.get())).isEqualTo(42L);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = jwtService(Duration.ofHours(1));
        String token = service.generateToken(user());

        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("a") ? "b" : "a");

        assertThat(service.parse(tampered)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService service = jwtService(Duration.ofMillis(1));
        String token = service.generateToken(user());

        Thread.sleep(50);

        assertThat(service.parse(token)).isEmpty();
    }
}
