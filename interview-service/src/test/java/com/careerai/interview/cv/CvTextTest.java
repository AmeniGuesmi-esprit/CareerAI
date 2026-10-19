package com.careerai.interview.cv;

import com.careerai.interview.config.InterviewConfig.InterviewProperties;
import com.careerai.interview.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvTextTest {

    private final PersonalDataAnonymizer anonymizer = new PersonalDataAnonymizer();
    private final PdfTextExtractor extractor = new PdfTextExtractor(new InterviewProperties(
            "http://compat", 12000, 6, 3, 10, 5000));

    @Test
    void masqueLesEmailsEtLesTelephones() {
        assertThat(anonymizer.anonymize("Contact : farah@esprit.tn")).isEqualTo("Contact : [EMAIL]");
        assertThat(anonymizer.anonymize("Tel +216 20 123 456")).isEqualTo("Tel [TELEPHONE]");
    }

    @Test
    void conserveLesPlagesDeDatesEtMasqueLesLiens() {
        assertThat(anonymizer.anonymize("Stage 2022 - 2023")).isEqualTo("Stage 2022 - 2023");
        assertThat(anonymizer.anonymize("Profil https://github.com/utilisateur")).isEqualTo("Profil [LIEN]");
    }

    @Test
    void extraitLeTexteD_unPdfValide() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                pdf("Developpeur Java Spring Boot avec cinq ans d'experience en microservices et Docker."));
        assertThat(extractor.extract(file)).contains("Developpeur Java Spring Boot");
    }

    @Test
    void refuseUnFichierQuiNEstPasUnPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", "pas un pdf".getBytes());
        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CV_NOT_PDF");
    }

    private static byte[] pdf(String text) throws IOException {
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
}
