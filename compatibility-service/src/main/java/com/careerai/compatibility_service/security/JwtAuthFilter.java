package com.careerai.compatibility_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.Base64;
import java.util.List;

/**
 * Valide le JWT émis par user-service (même secret que la gateway) et place l'id de l'utilisateur (claim "userId")
 * comme principal. L'identité vient donc du jeton signé, jamais d'un en-tête ou d'un paramètre de la requête :
 * appeler le service directement sur son port ne permet plus d'agir au nom d'un autre utilisateur.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final Key signingKey;

    public JwtAuthFilter(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET manquant : définir la variable d'environnement (même valeur que user-service et api-gateway).");
        }
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = Jwts.parserBuilder().setSigningKey(signingKey).build()
                        .parseClaimsJws(header.substring(7)).getBody();
                if (claims.get("userId") instanceof Number id) {
                    var auth = new UsernamePasswordAuthenticationToken(id.longValue(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role"))));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Jeton invalide, expiré ou d'un autre émetteur : pas d'authentification → 401
            }
        }
        chain.doFilter(request, response);
    }
}
