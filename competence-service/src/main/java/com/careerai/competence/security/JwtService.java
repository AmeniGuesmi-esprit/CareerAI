package com.careerai.competence.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Optional;

/** Valide les JWT émis par user-service (même secret Base64) et en extrait les claims. */
@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
