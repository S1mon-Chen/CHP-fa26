package com.bookstore.assignment4;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final JwtParser parser;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.audience}") String audience,
                      @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        if (ttlSeconds < 1 || ttlSeconds > 86400) {
            throw new IllegalArgumentException("JWT TTL must be between 1 and 86400 seconds");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
        parser = Jwts.parser().verifyWith(key).requireIssuer(issuer).requireAudience(audience)
                .sig().clear().add(Jwts.SIG.HS256).and().build();
    }

    public String issue(String username) {
        Instant now = Instant.now();
        return Jwts.builder().subject(username).issuer(issuer).audience().add(audience).and()
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .id(UUID.randomUUID().toString()).signWith(key, Jwts.SIG.HS256).compact();
    }

    public String verify(String token) {
        // parseSignedClaims verifies signature, issuer, audience, exp and optional nbf.
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Required claims missing");
        }
        return claims.getSubject();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
