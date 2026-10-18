package com.careerai.experience.security;

import io.jsonwebtoken.Claims;
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
 * Valide le JWT (meme secret que user-service / api-gateway) et authentifie la requete.
 * Instancie manuellement dans SecurityConfig (volontairement pas un @Component).
 * Le service reste donc protege meme s'il est appele directement, sans passer par la gateway.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final Key signingKey;

    public JwtAuthFilter(String base64Secret) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(header.substring(7))
                        .getBody();

                Object userId = claims.get("userId");
                String role = String.valueOf(claims.get("role"));

                if (userId instanceof Number number) {
                    AuthUser user = new AuthUser(number.longValue(), claims.getSubject(), role);
                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // Token invalide ou expire : pas d'authentification -> 401 renvoye par Spring Security
            }
        }
        chain.doFilter(request, response);
    }
}
