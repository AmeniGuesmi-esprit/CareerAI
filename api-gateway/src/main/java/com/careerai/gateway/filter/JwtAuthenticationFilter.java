package com.careerai.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.Base64;
import java.util.List;

/**
 * Valide le JWT sur toutes les routes sauf les routes publiques.
 * Les en-têtes X-User-* fournis par le client sont toujours supprimés (anti-usurpation),
 * puis réinjectés à partir du token validé pour les services en aval.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PREFIXES = List.of("/api/users/auth/");

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Préflight CORS et routes publiques : on laisse passer (en nettoyant les en-têtes X-User-*)
        if (HttpMethod.OPTIONS.equals(request.getMethod())
                || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            ServerHttpRequest cleaned = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Role");
                        h.remove("X-User-Email");
                    })
                    .build();
            return chain.filter(exchange.mutate().request(cleaned).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey())
                    .build()
                    .parseClaimsJws(authHeader.substring(7))
                    .getBody();

            ServerHttpRequest mutated = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Role");
                        h.remove("X-User-Email");
                        h.add("X-User-Id", String.valueOf(claims.get("userId")));
                        h.add("X-User-Role", String.valueOf(claims.get("role")));
                        h.add("X-User-Email", claims.getSubject());
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
