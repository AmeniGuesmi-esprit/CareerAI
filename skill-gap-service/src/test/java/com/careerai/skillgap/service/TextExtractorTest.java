package com.careerai.skillgap.service;

import com.careerai.skillgap.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Extraction du texte des CV, sur de vrais fichiers PDF et DOCX générés pour le test. */
class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void extractsTextFromAPdf() throws IOException {
        String text = extractor.extract(pdf("Competences : Java, Spring Boot", "Experience : Developpeur Java"), "pdf");

        assertThat(text).contains("Competences : Java, Spring Boot", "Experience : Developpeur Java");
    }

    @Test
    void extractsTextFromADocx() throws IOException {
        String text = extractor.extract(docx("Compétences : Angular, TypeScript", "Langues : anglais B2"), "docx");

        assertThat(text).contains("Compétences : Angular, TypeScript", "Langues : anglais B2");
    }

    @Test
    void readsUtf8TextFilesAndNormalizesLineBreaks() {
        byte[] data = "Ligne 1\r\n\r\n\r\n\r\nLigne 2 é".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(data, "txt")).isEqualTo("Ligne 1\n\nLigne 2 é");
    }

    @Test
    void rejectsAFileThatIsNotReallyADocx() {
        assertThatThrownBy(() -> extractor.extract("texte".getBytes(), "docx"))
                .isInstanceOf(ApiException.class).hasMessageContaining("pas un DOCX valide");
    }

    @Test
    void rejectsACorruptedPdf() {
        assertThatThrownBy(() -> extractor.extract("%PDF-1.7 contenu tronqué".getBytes(), "pdf"))
                .isInstanceOf(ApiException.class).hasMessageContaining("illisible");
    }

    private static byte[] pdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -20);
                }
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx(String... paragraphs) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphs) doc.createParagraph().createRun().setText(p);
            doc.write(out);
            return out.toByteArray();
        }
    }
}
