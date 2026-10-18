package com.careerai.user.security;

import com.careerai.user.model.Role;
import com.careerai.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static com.careerai.user.TestData.JWT_SECRET;
import static com.careerai.user.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static JwtService jwtService(String secret, long expirationMs) {
        JwtService s = new JwtService();
        ReflectionTestUtils.setField(s, "jwtSecret", secret);
        ReflectionTestUtils.setField(s, "jwtExpiration", expirationMs);
        return s;
    }

    private final JwtService jwt = jwtService(JWT_SECRET, 3_600_000);
    private final User sami = user(42, "sami@example.com", Role.USER);

    @Test
    void generatedTokenIsValidAndCarriesTheEmail() {
        String token = jwt.generateToken(sami);

        assertThat(jwt.isTokenValid(token)).isTrue();
        assertThat(jwt.extractEmail(token)).isEqualTo("sami@example.com");
    }

    /** skill-gap-service lit userId et role dans ce jeton : ces claims font partie du contrat entre services. */
    @Test
    void tokenContainsTheClaimsUsedByTheOtherServices() {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(JWT_SECRET))).build()
                .parseClaimsJws(jwt.generateToken(sami)).getBody();

        assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration()).isInTheFuture();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String otherSecret = Base64.getEncoder().encodeToString("un-autre-secret-de-test-de-32-octets!!".getBytes());
        String forged = jwtService(otherSecret, 3_600_000).generateToken(sami);

        assertThat(jwt.isTokenValid(forged)).isFalse();
    }

    @Test
    void modifiedTokenIsRejected() {
        String token = jwt.generateToken(sami);
        String[] parts = token.split("\\.");
        // payload remplacé (ex. pour se donner le rôle ADMIN) : la signature ne correspond plus
        String fakePayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"sami@example.com\",\"role\":\"ADMIN\"}".getBytes());

        assertThat(jwt.isTokenValid(parts[0] + "." + fakePayload + "." + parts[2])).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = jwtService(JWT_SECRET, -1_000).generateToken(sami);

        assertThat(jwt.isTokenValid(expired)).isFalse();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwt.isTokenValid("pas-un-jwt")).isFalse();
        assertThat(jwt.isTokenValid("")).isFalse();
    }
}
