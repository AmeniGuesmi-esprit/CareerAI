package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.CvUploadResponse;
import com.careerai.skillgap.exception.ApiException;
import com.careerai.skillgap.model.CvUpload;
import com.careerai.skillgap.repository.CvUploadRepository;
import com.careerai.skillgap.repository.SkillGapAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CvService {

    private static final Set<String> ALLOWED = Set.of("pdf", "docx", "txt");

    private final CvUploadRepository cvRepository;
    private final SkillGapAnalysisRepository analysisRepository;
    private final FileStorage storage;
    private final TextExtractor extractor;

    @Value("${analysis.max-cv-chars}")
    private int maxChars;

    public CvUploadResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier manquant");
        }
        String originalName = file.getOriginalFilename() == null ? "cv" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        if (!ALLOWED.contains(ext)) {
            throw ApiException.badRequest("Format non supporté (PDF, DOCX ou TXT)");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.internal("Impossible de lire le fichier");
        }

        String text = extractor.extract(data, ext);
        if (text.isBlank()) {
            throw ApiException.badRequest("Aucun texte lisible dans ce fichier (PDF scanné ?)");
        }
        if (text.length() > maxChars) {
            throw ApiException.badRequest("CV trop long (" + maxChars + " caractères maximum)");
        }

        String stored = storage.save(data, ext);
        CvUpload cv = cvRepository.save(CvUpload.builder()
                .userId(userId)
                .originalFileName(originalName.length() > 255 ? originalName.substring(0, 255) : originalName)
                .fileType(ext)
                .storedFileName(stored)
                .sizeBytes(data.length)
                .extractedText(text)
                .build());
        return CvUploadResponse.from(cv);
    }

    public List<CvUploadResponse> listMine(Long userId) {
        return cvRepository.findByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(CvUploadResponse::from).toList();
    }

    @Transactional
    public void delete(Long userId, Long cvId) {
        CvUpload cv = findMine(userId, cvId);
        analysisRepository.deleteByCvId(cvId);
        cvRepository.delete(cv);
        storage.delete(cv.getStoredFileName());
    }

    /** Un utilisateur ne peut accéder qu'à ses propres CV (sinon 404). */
    public CvUpload findMine(Long userId, Long cvId) {
        return cvRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(() -> ApiException.notFound("CV introuvable"));
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
