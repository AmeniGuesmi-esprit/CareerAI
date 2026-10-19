package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {

    private static final String LINE = "Developpeur Java Spring Boot avec experience en microservices et Docker.";

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    /** Génère un PDF en mémoire : une page par liste de lignes. */
    private static byte[] pdf(List<List<String>> pages, boolean encrypted) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                    cs.setLeading(11);
                    cs.newLineAtOffset(30, 760);
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "secret", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                doc.protect(policy);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] pdf(String... lines) throws IOException {
        return pdf(List.of(List.of(lines)), false);
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("cv", name, "application/pdf", content);
    }

    private void assertRejected(MockMultipartFile f, String code, HttpStatus status, String message) {
        assertThatThrownBy(() -> extractor.extract(f))
                .isInstanceOf(ApiException.class)
                .hasMessage(message)
                .extracting("code", "status")
                .containsExactly(code, status);
    }

    @Test
    void extraitLeTexteDUnPdf() throws IOException {
        String text = extractor.extract(file("cv.pdf", pdf(LINE, "Anglais courant, Scrum.")));

        assertThat(text).isEqualTo(LINE + "\nAnglais courant, Scrum.");
    }

    @Test
    void accepteUneExtensionEnMajuscules() throws IOException {
        assertThat(extractor.extract(file("MON-CV.PDF", pdf(LINE)))).isEqualTo(LINE);
    }

    @Test
    void reduitLesEspacesMultiples() throws IOException {
        assertThat(extractor.extract(file("cv.pdf", pdf("Java      Spring   Boot   " + LINE))))
                .isEqualTo("Java Spring Boot " + LINE);
    }

    @Test
    void tronqueLeTexteA12000Caracteres() throws IOException {
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pages.add(Collections.nCopies(60, LINE)); // ~ 26 000 caractères au total
        }

        String text = extractor.extract(file("cv.pdf", pdf(pages, false)));

        assertThat(text).hasSize(12_000).startsWith(LINE);
    }

    @Test
    void rejetteUnFichierNull() {
        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(ApiException.class)
                .extracting("code", "status")
                .containsExactly("CV_MISSING", HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejetteUnFichierVide() {
        assertRejected(file("cv.pdf", new byte[0]), "CV_MISSING", HttpStatus.BAD_REQUEST,
                "Le fichier CV est obligatoire.");
    }

    @Test
    void rejetteUneExtensionAutreQuePdf() throws IOException {
        assertRejected(file("cv.docx", pdf(LINE)), "CV_NOT_PDF", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Seuls les fichiers PDF sont acceptés.");
    }

    @Test
    void rejetteUnFauxPdfSansSignature() {
        assertRejected(file("cv.pdf", "Ceci n'est pas un PDF".getBytes(StandardCharsets.UTF_8)),
                "CV_NOT_PDF", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Le contenu du fichier n'est pas un PDF valide.");
    }

    @Test
    void rejetteUnPdfCorrompu() {
        assertRejected(file("cv.pdf", "%PDF-1.7\nn'importe quoi".getBytes(StandardCharsets.US_ASCII)),
                "CV_UNREADABLE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Impossible de lire le PDF (fichier corrompu ou protégé par mot de passe).");
    }

    @Test
    void rejetteUnPdfProtegeParMotDePasse() throws IOException {
        assertRejected(file("cv.pdf", pdf(List.of(List.of(LINE)), true)),
                "CV_UNREADABLE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Impossible de lire le PDF (fichier corrompu ou protégé par mot de passe).");
    }

    @Test
    void rejetteUnPdfSansTexteExploitable() throws IOException {
        assertRejected(file("cv.pdf", pdf("Nom Prenom")), "CV_NO_TEXT", HttpStatus.UNPROCESSABLE_ENTITY,
                "Aucun texte exploitable : le PDF est probablement un scan (image).");
    }
}
