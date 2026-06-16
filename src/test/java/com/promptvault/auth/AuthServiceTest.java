package com.promptvault.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.promptvault.exceptions.DuplicateUsernameException;
import com.promptvault.repository.LoginRequest;
import com.promptvault.repository.RegisterRequest;
import com.promptvault.repository.User;
import com.promptvault.repository.UserRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerSuccessEmitsAuthResponse() {
        when(userRepository.existsByUsername("alice")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("password1")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User u = invocation.getArgument(0);
                    u.setId(1L);
                    return Mono.just(u);
                });
        when(jwtService.generateToken(any(User.class))).thenReturn("token-123");

        StepVerifier.create(authService.register(new RegisterRequest("alice", "password1")))
                .assertNext(response -> {
                    org.assertj.core.api.Assertions.assertThat(response.token()).isEqualTo("token-123");
                    org.assertj.core.api.Assertions.assertThat(response.user().id()).isEqualTo(1L);
                    org.assertj.core.api.Assertions.assertThat(response.user().username()).isEqualTo("alice");
                })
                .verifyComplete();
    }

    @Test
    void registerDuplicateUsernameErrors() {
        when(userRepository.existsByUsername("alice")).thenReturn(Mono.just(true));

        StepVerifier.create(authService.register(new RegisterRequest("alice", "password1")))
                .expectError(DuplicateUsernameException.class)
                .verify();
    }

    @Test
    void loginWrongPasswordErrors() {
        User user = new User(1L, "alice", "$2a$hash", null);
        when(userRepository.findByUsername("alice")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        StepVerifier.create(authService.login(new LoginRequest("alice", "wrong")))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void loginUnknownUserErrors() {
        when(userRepository.findByUsername("ghost")).thenReturn(Mono.empty());
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        StepVerifier.create(authService.login(new LoginRequest("ghost", "password1")))
                .expectError(BadCredentialsException.class)
                .verify();
    }
}
