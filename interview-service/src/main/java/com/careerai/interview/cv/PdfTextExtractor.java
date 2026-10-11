package com.careerai.interview.cv;

import com.careerai.interview.config.InterviewConfig.InterviewProperties;
import com.careerai.interview.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PdfTextExtractor {

    private static final int MIN_CHARS = 50;

    private final int maxChars;

    public PdfTextExtractor(InterviewProperties props) {
        this.maxChars = props.maxCvChars();
    }

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_MISSING", "Le fichier CV est obligatoire.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".pdf")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CV_NOT_PDF", "Seuls les fichiers PDF sont acceptés.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || !"%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.US_ASCII))) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CV_NOT_PDF",
                        "Le contenu du fichier n'est pas un PDF valide.");
            }
            try (PDDocument document = Loader.loadPDF(bytes)) {
                String text = new PDFTextStripper().getText(document)
                        .replace("\r", "")
                        .replaceAll("[ \\t]+", " ")
                        .replaceAll("\\n{3,}", "\n\n")
                        .trim();
                if (text.length() < MIN_CHARS) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CV_NO_TEXT",
                            "Aucun texte exploitable : le PDF est probablement un scan (image).");
                }
                return text.length() > maxChars ? text.substring(0, maxChars) : text;
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CV_UNREADABLE",
                    "Impossible de lire le PDF (fichier corrompu ou protégé par mot de passe).");
        }
    }
}
