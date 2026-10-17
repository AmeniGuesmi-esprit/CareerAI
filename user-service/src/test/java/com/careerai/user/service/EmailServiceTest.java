package com.careerai.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(mailSender);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "noreply@careerai.test");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:4200");
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void verificationEmailContainsTheFrontendLink() {
        emailService.sendVerificationEmail("sami@example.com", "abc-123");

        SimpleMailMessage m = sent();
        assertThat(m.getTo()).containsExactly("sami@example.com");
        assertThat(m.getFrom()).isEqualTo("noreply@careerai.test");
        assertThat(m.getText()).contains("http://localhost:4200/verify-email?token=abc-123");
    }

    @Test
    void resetEmailContainsTheLinkAndItsExpiry() {
        emailService.sendPasswordResetEmail("sami@example.com", "xyz");

        SimpleMailMessage m = sent();
        assertThat(m.getText()).contains("http://localhost:4200/reset-password?token=xyz", "1 heure");
    }

    /** Gmail indisponible : l'inscription ne doit pas échouer pour autant. */
    @Test
    void mailServerFailureDoesNotBreakTheCaller() {
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(() -> emailService.sendVerificationEmail("sami@example.com", "abc"));
    }
}
