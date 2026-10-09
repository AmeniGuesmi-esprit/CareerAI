package com.careerai.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void sendVerificationEmail(String to, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(to, "Vérifiez votre adresse email - CareerAI",
                "Bonjour,\n\n" +
                "Merci de vous être inscrit sur CareerAI.\n" +
                "Cliquez sur le lien ci-dessous pour vérifier votre adresse email :\n\n" +
                link + "\n\n" +
                "L'équipe CareerAI", link);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(to, "Réinitialisation de votre mot de passe - CareerAI",
                "Bonjour,\n\n" +
                "Cliquez sur le lien ci-dessous pour créer un nouveau mot de passe :\n\n" +
                link + "\n\n" +
                "Ce lien expire dans 1 heure.\n" +
                "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.\n\n" +
                "L'équipe CareerAI", link);
    }

    /** L'échec d'envoi ne bloque pas l'inscription : le lien est journalisé pour le développement. */
    private void send(String to, String subject, String text, String link) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Échec d'envoi de l'email à {} : {}. Lien (dev) : {}", to, e.getMessage(), link);
        }
    }
}
