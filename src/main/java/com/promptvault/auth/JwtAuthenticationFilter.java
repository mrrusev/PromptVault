package com.promptvault.auth;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        return jwtService.parse(token)
                .map(this::toAuthentication)
                .map(auth -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                // Present but invalid/expired token: leave the context empty so the
                // authorization layer produces a 401; never throw a raw exception.
                .orElseGet(() -> chain.filter(exchange));
    }

    private Authentication toAuthentication(Claims claims) {
        AuthenticatedUser principal =
                new AuthenticatedUser(jwtService.getUid(claims), jwtService.getUsername(claims));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
