package com.promptvault.services;

import java.time.Instant;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.promptvault.auth.JwtService;
import com.promptvault.exceptions.DuplicateUsernameException;
import com.promptvault.models.AuthResponse;
import com.promptvault.models.LoginRequest;
import com.promptvault.models.RegisterRequest;
import com.promptvault.models.UserDto;
import com.promptvault.repository.UserRepository;
import com.promptvault.repository.entities.User;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userRepository.existsByUsername(request.username())
                .flatMap(exists -> exists
                        ? Mono.error(new DuplicateUsernameException(request.username()))
                        : encode(request.password())
                        .map(hash -> newUser(request.username(), hash))
                        .flatMap(userRepository::save))
                .map(this::toAuthResponse);
    }

    public Mono<AuthResponse> login(LoginRequest request) {
        return userRepository.findByUsername(request.username())
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid credentials")))
                .flatMap(user -> matches(request.password(), user.getPassword())
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid credentials")))
                        .thenReturn(user))
                .map(this::toAuthResponse);
    }

    private User newUser(String username, String passwordHash) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordHash);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, new UserDto(user.getId(), user.getUsername()));
    }

    // BCrypt is CPU-bound and blocking; keep it off the event loop.
    private Mono<String> encode(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> matches(String rawPassword, String passwordHash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, passwordHash))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
