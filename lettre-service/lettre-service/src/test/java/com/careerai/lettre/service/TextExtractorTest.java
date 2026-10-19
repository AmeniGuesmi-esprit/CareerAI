package com.careerai.lettre.service;

import com.careerai.lettre.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Extraction du texte d'un CV : vrais PDF et DOCX générés en mémoire, faux fichiers refusés. */
class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    private static byte[] pdfWith(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docxWith(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsARealPdf() throws Exception {
        String text = extractor.extract(pdfWith("Developpeur Java Spring Boot"), "pdf");
        assertThat(text).contains("Developpeur Java Spring Boot");
    }

    @Test
    void readsARealDocx() throws Exception {
        String text = extractor.extract(docxWith("Competences : Java, MySQL"), "docx");
        assertThat(text).contains("Competences : Java, MySQL");
    }

    @Test
    void readsATextFileWithAccents() {
        byte[] data = "Compétences : Java\r\nFormation : Licence".getBytes(StandardCharsets.UTF_8);
        assertThat(extractor.extract(data, "txt")).isEqualTo("Compétences : Java\nFormation : Licence");
    }

    @Test
    void collapsesLongRunsOfBlankLines() {
        byte[] data = "A\n\n\n\n\nB".getBytes(StandardCharsets.UTF_8);
        assertThat(extractor.extract(data, "txt")).isEqualTo("A\n\nB");
    }

    @Test
    void anExecutableRenamedToPdfIsRefused() {
        assertThatThrownBy(() -> extractor.extract(new byte[]{'M', 'Z', 0, 1, 2}, "pdf"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("n'est pas un PDF valide");
    }

    @Test
    void anExecutableRenamedToDocxIsRefused() {
        assertThatThrownBy(() -> extractor.extract(new byte[]{'M', 'Z', 0, 1, 2}, "docx"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("n'est pas un DOCX valide");
    }

    @Test
    void aCorruptedPdfIsReportedAsUnreadable() {
        byte[] corrupted = "%PDF-1.7 ceci n'est pas un vrai document".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> extractor.extract(corrupted, "pdf"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("illisible");
    }
}
