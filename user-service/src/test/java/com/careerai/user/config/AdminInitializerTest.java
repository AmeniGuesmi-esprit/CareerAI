package com.careerai.user.config;

import com.careerai.user.model.Role;
import com.careerai.user.model.User;
import com.careerai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminInitializerTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AdminInitializer initializer = new AdminInitializer(repository, encoder);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(initializer, "adminEmail", " Admin@CareerAI.test ");
        ReflectionTestUtils.setField(initializer, "adminPassword", "Test-Admin@123");
        ReflectionTestUtils.setField(initializer, "adminNom", "Admin");
        ReflectionTestUtils.setField(initializer, "adminPrenom", "CareerAI");
    }

    @Test
    void createsTheAdminOnFirstStart() {
        when(repository.existsByEmail("admin@careerai.test")).thenReturn(false);

        initializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@careerai.test");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEmailVerified()).isTrue();
        assertThat(encoder.matches("Test-Admin@123", admin.getMotDePasse())).isTrue();
    }

    /** Au redémarrage, l'admin existant (et son mot de passe éventuellement changé) est conservé. */
    @Test
    void doesNotOverwriteAnExistingAdmin() {
        when(repository.existsByEmail("admin@careerai.test")).thenReturn(true);

        initializer.run();

        verify(repository, never()).save(any());
    }
}
