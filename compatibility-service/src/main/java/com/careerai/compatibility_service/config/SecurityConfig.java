package com.careerai.compatibility_service.config;

import com.careerai.compatibility_service.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Value("${app.jwt.secret:}") String jwtSecret) throws Exception {
        // Filtre créé ici (pas un bean) pour qu'il ne s'exécute que dans la chaîne Spring Security
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtSecret);
        http
                .csrf(AbstractHttpConfigurer::disable)  // API sans session ni cookie
                .cors(AbstractHttpConfigurer::disable)  // CORS géré par l'API Gateway
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401 au même format JSON que les autres erreurs du service (lu par le frontend)
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":401,"
                            + "\"code\":\"UNAUTHORIZED\",\"message\":\"Authentification requise : jeton absent, invalide ou expiré.\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/health", "/actuator/info",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
