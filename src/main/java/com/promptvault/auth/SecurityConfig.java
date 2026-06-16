package com.promptvault.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String AUTH_REGISTER = "/auth/register";
    private static final String AUTH_LOGIN = "/auth/login";

    private static final String[] SWAGGER_PATHS = {
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         JwtAuthenticationFilter jwtAuthenticationFilter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(SWAGGER_PATHS).permitAll()
                        .pathMatchers(HttpMethod.POST, AUTH_REGISTER, AUTH_LOGIN).permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(setStatus(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((exchange, denied) ->
                                applyStatus(exchange, HttpStatus.FORBIDDEN)))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static org.springframework.security.web.server.ServerAuthenticationEntryPoint setStatus(
            HttpStatus status) {
        return (exchange, ex) -> applyStatus(exchange, status);
    }

    private static Mono<Void> applyStatus(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
}
