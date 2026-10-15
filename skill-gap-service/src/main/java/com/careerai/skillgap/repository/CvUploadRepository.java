package com.careerai.skillgap.repository;

import com.careerai.skillgap.model.CvUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvUploadRepository extends JpaRepository<CvUpload, Long> {
    List<CvUpload> findByUserIdOrderByUploadedAtDesc(Long userId);
    Optional<CvUpload> findByIdAndUserId(Long id, Long userId);
}
