package com.careerai.user.service;

import com.careerai.user.dto.*;
import com.careerai.user.model.Role;
import com.careerai.user.model.User;
import com.careerai.user.repository.UserRepository;
import com.careerai.user.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.careerai.user.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository repository;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;

    /** Vrai BCrypt (coût réduit pour la rapidité) : on vérifie que les mots de passe sont bien hachés. */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, encoder, jwtService, emailService);
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Inscription {

        @Test
        void createsUnverifiedUserAndSendsVerificationEmail() {
            when(repository.existsByEmail("sami@example.com")).thenReturn(false);

            ApiResponse<Void> res = service.register(registerRequest("  Sami@Example.COM "));

            assertThat(res.isSuccess()).isTrue();
            User u = savedUser();
            assertThat(u.getEmail()).isEqualTo("sami@example.com");
            assertThat(u.getNom()).isEqualTo("Trabelsi");
            assertThat(u.getRole()).isEqualTo(Role.USER);
            assertThat(u.isEmailVerified()).isFalse();
            assertThat(u.getVerificationToken()).isNotBlank();
            verify(emailService).sendVerificationEmail("sami@example.com", u.getVerificationToken());
        }

        @Test
        void passwordIsStoredHashed() {
            when(repository.existsByEmail(anyString())).thenReturn(false);

            service.register(registerRequest("sami@example.com"));

            String stored = savedUser().getMotDePasse();
            assertThat(stored).isNotEqualTo("Secret@2026").startsWith("$2");
            assertThat(encoder.matches("Secret@2026", stored)).isTrue();
        }

        @Test
        void rejectsAnEmailAlreadyUsed() {
            when(repository.existsByEmail("sami@example.com")).thenReturn(true);

            ApiResponse<Void> res = service.register(registerRequest("SAMI@example.com"));

            assertThat(res.isSuccess()).isFalse();
            assertThat(res.getMessage()).isEqualTo("Cet email est déjà utilisé");
            verify(repository, never()).save(any());
            verifyNoInteractions(emailService);
        }

        @Test
        void adminCreatesVerifiedAccountWithChosenRoleWithoutEmail() {
            when(repository.existsByEmail(anyString())).thenReturn(false);

            service.createByAdmin(adminCreateRequest("admin2@example.com", Role.ADMIN));

            User u = savedUser();
            assertThat(u.getRole()).isEqualTo(Role.ADMIN);
            assertThat(u.isEmailVerified()).isTrue();
            assertThat(u.getVerificationToken()).isNull();
            verifyNoInteractions(emailService);
        }
    }

    @Nested
    class VerificationEmail {

        @Test
        void validTokenVerifiesTheAccount() {
            User u = user(1, "sami@example.com", Role.USER);
            u.setEmailVerified(false);
            u.setVerificationToken("tok");
            when(repository.findByVerificationToken("tok")).thenReturn(Optional.of(u));

            assertThat(service.verifyEmail("tok").isSuccess()).isTrue();
            assertThat(u.isEmailVerified()).isTrue();
            assertThat(u.getVerificationToken()).isNull();   // lien à usage unique
        }

        @Test
        void unknownTokenIsRejected() {
            when(repository.findByVerificationToken("faux")).thenReturn(Optional.empty());

            assertThat(service.verifyEmail("faux").isSuccess()).isFalse();
            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Connexion {

        private User account() {
            User u = user(5, "sami@example.com", Role.USER);
            u.setMotDePasse(encoder.encode("Secret@2026"));
            return u;
        }

        private LoginRequest login(String email, String password) {
            LoginRequest r = new LoginRequest();
            r.setEmail(email);
            r.setMotDePasse(password);
            return r;
        }

        @Test
        void validCredentialsReturnAToken() {
            User u = account();
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));
            when(jwtService.generateToken(u)).thenReturn("jwt");

            ApiResponse<AuthResponse> res = service.login(login(" SAMI@example.com", "Secret@2026"));

            assertThat(res.isSuccess()).isTrue();
            assertThat(res.getData().getToken()).isEqualTo("jwt");
            assertThat(res.getData().getUser().getEmail()).isEqualTo("sami@example.com");
        }

        /** Même message pour un email inconnu et un mauvais mot de passe : on ne révèle pas quels comptes existent. */
        @Test
        void wrongPasswordAndUnknownEmailGiveTheSameMessage() {
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(account()));
            when(repository.findByEmail("inconnu@example.com")).thenReturn(Optional.empty());

            String wrongPassword = service.login(login("sami@example.com", "mauvais")).getMessage();
            String unknownEmail = service.login(login("inconnu@example.com", "Secret@2026")).getMessage();

            assertThat(wrongPassword).isEqualTo(unknownEmail).isEqualTo("Email ou mot de passe incorrect");
            verifyNoInteractions(jwtService);
        }

        @Test
        void disabledAccountCannotLogIn() {
            User u = account();
            u.setEnabled(false);
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));

            ApiResponse<AuthResponse> res = service.login(login("sami@example.com", "Secret@2026"));

            assertThat(res.isSuccess()).isFalse();
            assertThat(res.getMessage()).contains("désactivé");
            verifyNoInteractions(jwtService);
        }

        @Test
        void unverifiedEmailCannotLogIn() {
            User u = account();
            u.setEmailVerified(false);
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));

            ApiResponse<AuthResponse> res = service.login(login("sami@example.com", "Secret@2026"));

            assertThat(res.isSuccess()).isFalse();
            assertThat(res.getMessage()).contains("vérifier votre email");
            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    class MotDePasseOublie {

        private ForgotPasswordRequest forgot(String email) {
            ForgotPasswordRequest r = new ForgotPasswordRequest();
            r.setEmail(email);
            return r;
        }

        private ResetPasswordRequest reset(String token, String password) {
            ResetPasswordRequest r = new ResetPasswordRequest();
            r.setToken(token);
            r.setNouveauMotDePasse(password);
            return r;
        }

        @Test
        void knownEmailReceivesAResetLinkValidOneHour() {
            User u = user(1, "sami@example.com", Role.USER);
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));

            service.forgotPassword(forgot("sami@example.com"));

            assertThat(u.getPasswordResetToken()).isNotBlank();
            assertThat(u.getPasswordResetTokenExpiry()).isCloseTo(LocalDateTime.now().plusHours(1), within(1, MINUTES));
            verify(emailService).sendPasswordResetEmail("sami@example.com", u.getPasswordResetToken());
        }

        /** Réponse identique que l'email existe ou non : on ne révèle pas quels comptes existent. */
        @Test
        void unknownEmailGetsTheSameAnswerAndNoEmail() {
            User u = user(1, "sami@example.com", Role.USER);
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));
            when(repository.findByEmail("inconnu@example.com")).thenReturn(Optional.empty());

            ApiResponse<Void> known = service.forgotPassword(forgot("sami@example.com"));
            ApiResponse<Void> unknown = service.forgotPassword(forgot("inconnu@example.com"));

            assertThat(unknown.isSuccess()).isTrue();
            assertThat(unknown.getMessage()).isEqualTo(known.getMessage());
            verify(emailService, never()).sendPasswordResetEmail(eq("inconnu@example.com"), anyString());
        }

        @Test
        void validLinkChangesThePasswordOnce() {
            User u = user(1, "sami@example.com", Role.USER);
            u.setPasswordResetToken("tok");
            u.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
            when(repository.findByPasswordResetToken("tok")).thenReturn(Optional.of(u));

            assertThat(service.resetPassword(reset("tok", "Nouveau@2026")).isSuccess()).isTrue();
            assertThat(encoder.matches("Nouveau@2026", u.getMotDePasse())).isTrue();
            assertThat(u.getPasswordResetToken()).isNull();
            assertThat(u.getPasswordResetTokenExpiry()).isNull();
        }

        @Test
        void expiredLinkIsRejected() {
            User u = user(1, "sami@example.com", Role.USER);
            u.setPasswordResetToken("tok");
            u.setPasswordResetTokenExpiry(LocalDateTime.now().minusMinutes(1));
            when(repository.findByPasswordResetToken("tok")).thenReturn(Optional.of(u));

            ApiResponse<Void> res = service.resetPassword(reset("tok", "Nouveau@2026"));

            assertThat(res.isSuccess()).isFalse();
            assertThat(res.getMessage()).contains("expiré");
            assertThat(u.getMotDePasse()).isEqualTo("hash");
            verify(repository, never()).save(any());
        }

        @Test
        void unknownLinkIsRejected() {
            when(repository.findByPasswordResetToken("faux")).thenReturn(Optional.empty());

            assertThat(service.resetPassword(reset("faux", "Nouveau@2026")).isSuccess()).isFalse();
            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Profil {

        private UpdateProfileRequest update(String password) {
            UpdateProfileRequest r = new UpdateProfileRequest();
            r.setNom(" Ben Salah ");
            r.setPrenom("Sami");
            r.setTelephone("+216 22 000 000");
            r.setAdresse("Sfax");
            r.setMotDePasse(password);
            return r;
        }

        @Test
        void updatesProfileAndKeepsPasswordWhenNotProvided() {
            User u = user(1, "sami@example.com", Role.USER);
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));

            ApiResponse<UserResponse> res = service.updateMe("sami@example.com", update(""));

            assertThat(res.isSuccess()).isTrue();
            assertThat(u.getNom()).isEqualTo("Ben Salah");
            assertThat(u.getAdresse()).isEqualTo("Sfax");
            assertThat(u.getMotDePasse()).isEqualTo("hash");
            assertThat(u.getUpdatedAt()).isNotNull();
        }

        @Test
        void newPasswordIsHashed() {
            User u = user(1, "sami@example.com", Role.USER);
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));

            service.updateMe("sami@example.com", update("Nouveau@2026"));

            assertThat(encoder.matches("Nouveau@2026", u.getMotDePasse())).isTrue();
        }

        @Test
        void tooShortPasswordIsRejected() {
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(user(1, "sami@example.com", Role.USER)));

            ApiResponse<UserResponse> res = service.updateMe("sami@example.com", update("123"));

            assertThat(res.isSuccess()).isFalse();
            assertThat(res.getMessage()).contains("8 caractères");
            verify(repository, never()).save(any());
        }

        @Test
        void userCanDeleteTheirAccount() {
            User u = user(1, "sami@example.com", Role.USER);
            when(repository.findByEmail("sami@example.com")).thenReturn(Optional.of(u));

            assertThat(service.deleteMe("sami@example.com").isSuccess()).isTrue();
            verify(repository).delete(u);
        }
    }

    /** L'application doit toujours garder au moins un administrateur. */
    @Nested
    class DernierAdministrateur {

        private final User admin = user(1, "admin@example.com", Role.ADMIN);

        @Test
        void cannotDeleteThemselves() {
            when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
            when(repository.countByRole(Role.ADMIN)).thenReturn(1L);

            assertThat(service.deleteMe("admin@example.com").isSuccess()).isFalse();
            verify(repository, never()).delete(any());
        }

        @Test
        void cannotBeDeletedByIdOrDisabledOrDemoted() {
            when(repository.findById(1L)).thenReturn(Optional.of(admin));
            when(repository.countByRole(Role.ADMIN)).thenReturn(1L);

            assertThat(service.deleteById(1L).isSuccess()).isFalse();
            assertThat(service.setEnabled(1L, false).isSuccess()).isFalse();
            assertThat(service.changeRole(1L, Role.USER).isSuccess()).isFalse();
            assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
            assertThat(admin.isEnabled()).isTrue();
            verify(repository, never()).delete(any());
            verify(repository, never()).save(any());
        }

        @Test
        void anAdminCanBeRemovedWhenAnotherAdminExists() {
            when(repository.findById(1L)).thenReturn(Optional.of(admin));
            when(repository.countByRole(Role.ADMIN)).thenReturn(2L);

            assertThat(service.deleteById(1L).isSuccess()).isTrue();
            verify(repository).delete(admin);
        }

        @Test
        void aUserCanBePromotedToAdmin() {
            User u = user(2, "sami@example.com", Role.USER);
            when(repository.findById(2L)).thenReturn(Optional.of(u));

            ApiResponse<UserResponse> res = service.changeRole(2L, Role.ADMIN);

            assertThat(res.isSuccess()).isTrue();
            assertThat(res.getData().getRole()).isEqualTo(Role.ADMIN);
        }
    }

    @Test
    void unknownUserIdGivesAnError() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.getById(99L).isSuccess()).isFalse();
        assertThat(service.changeRole(99L, Role.ADMIN).isSuccess()).isFalse();
        assertThat(service.setEnabled(99L, true).isSuccess()).isFalse();
        assertThat(service.deleteById(99L).isSuccess()).isFalse();
    }
}
