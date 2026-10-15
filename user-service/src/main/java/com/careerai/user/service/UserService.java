package com.careerai.user.service;

import com.careerai.user.dto.*;
import com.careerai.user.model.Role;
import com.careerai.user.model.User;
import com.careerai.user.repository.UserRepository;
import com.careerai.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    // ───────────────────────── Authentification ─────────────────────────

    /** Inscription publique : le rôle est toujours USER. */
    @Transactional
    public ApiResponse<Void> register(RegisterRequest request) {
        return createUser(request, Role.USER, false);
    }

    @Transactional
    public ApiResponse<Void> verifyEmail(String token) {
        Optional<User> optUser = userRepository.findByVerificationToken(token);
        if (optUser.isEmpty()) {
            return ApiResponse.error("Token de vérification invalide");
        }
        User user = optUser.get();
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);
        return ApiResponse.success("Email vérifié avec succès", null);
    }

    public ApiResponse<AuthResponse> login(LoginRequest request) {
        Optional<User> optUser = userRepository.findByEmail(normalize(request.getEmail()));
        if (optUser.isEmpty()
                || !passwordEncoder.matches(request.getMotDePasse(), optUser.get().getMotDePasse())) {
            return ApiResponse.error("Email ou mot de passe incorrect");
        }
        User user = optUser.get();
        if (!user.isEnabled()) {
            return ApiResponse.error("Compte désactivé. Contactez l'administrateur.");
        }
        if (!user.isEmailVerified()) {
            return ApiResponse.error("Veuillez vérifier votre email avant de vous connecter");
        }
        String token = jwtService.generateToken(user);
        return ApiResponse.success("Connexion réussie", new AuthResponse(token, toResponse(user)));
    }

    @Transactional
    public ApiResponse<Void> forgotPassword(ForgotPasswordRequest request) {
        String message = "Si cet email existe, un lien vous a été envoyé.";
        Optional<User> optUser = userRepository.findByEmail(normalize(request.getEmail()));
        if (optUser.isEmpty()) {
            return ApiResponse.success(message, null);
        }
        User user = optUser.get();
        String resetToken = UUID.randomUUID().toString();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
        return ApiResponse.success(message, null);
    }

    @Transactional
    public ApiResponse<Void> resetPassword(ResetPasswordRequest request) {
        Optional<User> optUser = userRepository.findByPasswordResetToken(request.getToken());
        if (optUser.isEmpty()) {
            return ApiResponse.error("Lien de réinitialisation invalide ou expiré.");
        }
        User user = optUser.get();
        if (user.getPasswordResetTokenExpiry() == null
                || user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Lien de réinitialisation expiré. Veuillez faire une nouvelle demande.");
        }
        user.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ApiResponse.success("Mot de passe réinitialisé avec succès.", null);
    }

    // ───────────────────────── Profil (utilisateur connecté) ─────────────────────────

    public ApiResponse<UserResponse> getMe(String email) {
        return userRepository.findByEmail(email)
                .map(u -> ApiResponse.success("Profil récupéré", toResponse(u)))
                .orElse(ApiResponse.error("Utilisateur non trouvé"));
    }

    @Transactional
    public ApiResponse<UserResponse> updateMe(String email, UpdateProfileRequest request) {
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) return ApiResponse.error("Utilisateur non trouvé");

        if (request.getMotDePasse() != null && !request.getMotDePasse().isBlank()
                && request.getMotDePasse().length() < 8) {
            return ApiResponse.error("Le mot de passe doit contenir au moins 8 caractères");
        }

        User user = optUser.get();
        user.setNom(request.getNom().trim());
        user.setPrenom(request.getPrenom().trim());
        user.setTelephone(request.getTelephone().trim());
        user.setAdresse(request.getAdresse().trim());
        user.setUpdatedAt(LocalDateTime.now());
        if (request.getMotDePasse() != null && !request.getMotDePasse().isBlank()) {
            user.setMotDePasse(passwordEncoder.encode(request.getMotDePasse()));
        }
        userRepository.save(user);
        return ApiResponse.success("Profil mis à jour avec succès", toResponse(user));
    }

    @Transactional
    public ApiResponse<Void> deleteMe(String email) {
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) return ApiResponse.error("Utilisateur non trouvé");
        User user = optUser.get();
        if (isLastAdmin(user)) {
            return ApiResponse.error("Impossible de supprimer le dernier administrateur");
        }
        userRepository.delete(user);
        return ApiResponse.success("Compte supprimé avec succès", null);
    }

    // ───────────────────────── Administration ─────────────────────────

    public ApiResponse<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream().map(this::toResponse).toList();
        return ApiResponse.success("Utilisateurs récupérés", users);
    }

    public ApiResponse<UserResponse> getById(Long id) {
        return userRepository.findById(id)
                .map(u -> ApiResponse.success("Utilisateur récupéré", toResponse(u)))
                .orElse(ApiResponse.error("Utilisateur non trouvé"));
    }

    /** Création par un admin : compte déjà vérifié, rôle au choix. */
    @Transactional
    public ApiResponse<Void> createByAdmin(AdminCreateUserRequest request) {
        return createUser(request, request.getRole(), true);
    }

    @Transactional
    public ApiResponse<UserResponse> changeRole(Long id, Role role) {
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) return ApiResponse.error("Utilisateur non trouvé");
        User user = optUser.get();
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN && isLastAdmin(user)) {
            return ApiResponse.error("Impossible de rétrograder le dernier administrateur");
        }
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ApiResponse.success("Rôle mis à jour", toResponse(user));
    }

    @Transactional
    public ApiResponse<UserResponse> setEnabled(Long id, boolean enabled) {
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) return ApiResponse.error("Utilisateur non trouvé");
        User user = optUser.get();
        if (!enabled && isLastAdmin(user)) {
            return ApiResponse.error("Impossible de désactiver le dernier administrateur");
        }
        user.setEnabled(enabled);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ApiResponse.success(enabled ? "Compte activé" : "Compte désactivé", toResponse(user));
    }

    @Transactional
    public ApiResponse<Void> deleteById(Long id) {
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) return ApiResponse.error("Utilisateur non trouvé");
        User user = optUser.get();
        if (isLastAdmin(user)) {
            return ApiResponse.error("Impossible de supprimer le dernier administrateur");
        }
        userRepository.delete(user);
        return ApiResponse.success("Utilisateur supprimé", null);
    }

    // ───────────────────────── Utilitaires ─────────────────────────

    private ApiResponse<Void> createUser(RegisterRequest request, Role role, boolean verified) {
        String email = normalize(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            return ApiResponse.error("Cet email est déjà utilisé");
        }

        String verificationToken = verified ? null : UUID.randomUUID().toString();
        User user = User.builder()
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .email(email)
                .telephone(request.getTelephone().trim())
                .adresse(request.getAdresse().trim())
                .motDePasse(passwordEncoder.encode(request.getMotDePasse()))
                .role(role)
                .emailVerified(verified)
                .verificationToken(verificationToken)
                .build();
        userRepository.save(user);

        if (!verified) {
            emailService.sendVerificationEmail(email, verificationToken);
            return ApiResponse.success("Inscription réussie. Veuillez vérifier votre email.", null);
        }
        return ApiResponse.success("Utilisateur créé avec succès", null);
    }

    private boolean isLastAdmin(User user) {
        return user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1;
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .nom(u.getNom())
                .prenom(u.getPrenom())
                .email(u.getEmail())
                .telephone(u.getTelephone())
                .adresse(u.getAdresse())
                .role(u.getRole())
                .emailVerified(u.isEmailVerified())
                .enabled(u.isEnabled())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
