package com.promptvault.auth;

import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.promptvault.repository.entities.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String UID_CLAIM = "uid";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(HexFormat.of().parseHex(properties.secret()));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.expiration());
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(UID_CLAIM, user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and verifies the signature/expiry of the token. Any failure
     * (bad signature, expired, malformed) is swallowed into an empty result so
     * no parser internals ever leak to the caller or the response.
     */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    public Long getUid(Claims claims) {
        return claims.get(UID_CLAIM, Long.class);
    }
}
