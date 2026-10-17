package com.careerai.competence.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNDU2";

    JwtService service;

    @BeforeEach
    void setUp() {
        service = new JwtService();
        ReflectionTestUtils.setField(service, "jwtSecret", SECRET);
    }

    static String token(String secret, long ttlMs) {
        return Jwts.builder()
                .setClaims(Map.of("userId", 7, "role", "USER"))
                .setSubject("a@b.com")
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void validToken_returnsClaims() {
        Optional<Claims> claims = service.parse(token(SECRET, 60_000));

        assertThat(claims).isPresent();
        assertThat(claims.get().get("userId", Integer.class)).isEqualTo(7);
        assertThat(claims.get().getSubject()).isEqualTo("a@b.com");
    }

    @Test
    void expiredToken_isEmpty() {
        assertThat(service.parse(token(SECRET, -60_000))).isEmpty();
    }

    @Test
    void wrongSignature_isEmpty() {
        String other = Base64.getEncoder().encodeToString("another-secret-another-secret-12345".getBytes());

        assertThat(service.parse(token(other, 60_000))).isEmpty();
    }

    @Test
    void garbage_isEmpty() {
        assertThat(service.parse("not-a-jwt")).isEmpty();
        assertThat(service.parse("")).isEmpty();
    }
}
