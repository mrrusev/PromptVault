package com.promptvault.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.promptvault.auth.AuthenticatedUser;
import com.promptvault.models.AuthResponse;
import com.promptvault.models.LoginRequest;
import com.promptvault.models.RegisterRequest;
import com.promptvault.models.UserDto;
import com.promptvault.services.AuthService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public Mono<UserDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Mono.just(new UserDto(principal.id(), principal.username()));
    }
}
