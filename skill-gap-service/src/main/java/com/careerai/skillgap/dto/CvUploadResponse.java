package com.careerai.skillgap.dto;

import com.careerai.skillgap.model.CvUpload;

import java.time.LocalDateTime;

public record CvUploadResponse(Long id, Long userId, String fileName, String fileType,
                               long sizeBytes, int textLength, LocalDateTime uploadedAt) {

    public static CvUploadResponse from(CvUpload cv) {
        return new CvUploadResponse(cv.getId(), cv.getUserId(), cv.getOriginalFileName(), cv.getFileType(),
                cv.getSizeBytes(), cv.getExtractedText().length(), cv.getUploadedAt());
    }
}
