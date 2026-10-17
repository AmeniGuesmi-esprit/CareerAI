package com.careerai.lettre.service;

import com.careerai.lettre.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** Extrait le texte brut d'un CV (PDF, DOCX ou TXT). */
@Component
public class TextExtractor {

    public String extract(byte[] data, String extension) {
        String text;
        try {
            text = switch (extension) {
                case "pdf" -> fromPdf(data);
                case "docx" -> fromDocx(data);
                default -> new String(data, StandardCharsets.UTF_8);
            };
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Fichier illisible ou protégé par mot de passe");
        }
        return text.replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n").strip();
    }

    private String fromPdf(byte[] data) throws Exception {
        if (!startsWith(data, '%', 'P', 'D', 'F')) {
            throw ApiException.badRequest("Le fichier n'est pas un PDF valide");
        }
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // CV en colonnes : évite de mélanger l'ordre des sections
            return stripper.getText(doc);
        }
    }

    private String fromDocx(byte[] data) throws Exception {
        if (!startsWith(data, 'P', 'K')) {
            throw ApiException.badRequest("Le fichier n'est pas un DOCX valide");
        }
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private boolean startsWith(byte[] data, char... prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != (byte) prefix[i]) return false;
        }
        return true;
    }
}
