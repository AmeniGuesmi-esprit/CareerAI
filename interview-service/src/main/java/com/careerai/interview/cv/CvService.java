package com.careerai.interview.cv;

import com.careerai.interview.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CvService {

    public record CvSummary(Long id, String label, String fileName, int textLength, LocalDateTime createdAt) { }

    private final CvRepository repository;
    private final PdfTextExtractor extractor;
    private final PersonalDataAnonymizer anonymizer;

    @Transactional
    public CvSummary upload(Long userId, MultipartFile file, String label) {
        String text = anonymizer.anonymize(extractor.extract(file));
        String fileName = safeFileName(file.getOriginalFilename());
        String finalLabel = (label == null || label.isBlank() ? fileName : label.trim());
        CvDocument saved = repository.save(CvDocument.builder()
                .userId(userId)
                .label(finalLabel.length() > 150 ? finalLabel.substring(0, 150) : finalLabel)
                .fileName(fileName)
                .anonymizedText(text)
                .build());
        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public Page<CvSummary> list(Long userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public CvSummary get(Long userId, Long id) {
        return toSummary(find(userId, id));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(find(userId, id));
    }

    @Transactional(readOnly = true)
    public CvDocument find(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "CV introuvable : " + id));
    }

    private CvSummary toSummary(CvDocument c) {
        return new CvSummary(c.getId(), c.getLabel(), c.getFileName(), c.getAnonymizedText().length(), c.getCreatedAt());
    }

    private static String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "cv.pdf";
        }
        String clean = name.replaceAll("[\\\\/]+", "_");
        return clean.length() > 255 ? clean.substring(0, 255) : clean;
    }
}
