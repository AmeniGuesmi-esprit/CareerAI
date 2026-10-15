package com.careerai.user.config;

import com.careerai.user.model.Role;
import com.careerai.user.model.User;
import com.careerai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crée le compte administrateur au premier démarrage (sans écraser un compte existant). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")    private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;
    @Value("${app.admin.nom}")      private String adminNom;
    @Value("${app.admin.prenom}")   private String adminPrenom;

    @Override
    public void run(String... args) {
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .nom(adminNom)
                .prenom(adminPrenom)
                .email(email)
                .telephone("00000000")
                .adresse("-")
                .motDePasse(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .emailVerified(true)
                .build());
        log.info("Compte administrateur créé : {}", email);
    }
}
