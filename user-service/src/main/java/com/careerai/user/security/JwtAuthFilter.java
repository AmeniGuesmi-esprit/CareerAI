package com.careerai.user.security;

import com.careerai.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Instancié manuellement dans SecurityConfig (volontairement pas un @Component). Lit le JWT, recharge l'utilisateur en base (rôle à jour, compte actif) et authentifie la requête. */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            if (jwtService.isTokenValid(token)) {
                String email = jwtService.extractEmail(token);
                userRepository.findByEmail(email)
                        .filter(u -> u.isEnabled())
                        .ifPresent(u -> {
                            var auth = new UsernamePasswordAuthenticationToken(
                                    u.getEmail(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
        }
        chain.doFilter(request, response);
    }
}
