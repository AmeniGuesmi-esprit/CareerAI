package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.CvUploadResponse;
import com.careerai.skillgap.exception.ApiException;
import com.careerai.skillgap.model.CvUpload;
import com.careerai.skillgap.repository.CvUploadRepository;
import com.careerai.skillgap.repository.SkillGapAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Import des CV : contrôles de format et de taille, stockage sur disque (dossier temporaire). */
class CvServiceTest {

    private static final long USER = 42L;

    @TempDir Path storageDir;

    private final CvUploadRepository cvRepository = mock(CvUploadRepository.class);
    private final SkillGapAnalysisRepository analysisRepository = mock(SkillGapAnalysisRepository.class);
    private CvService service;

    @BeforeEach
    void setUp() {
        service = new CvService(cvRepository, analysisRepository, new FileStorage(storageDir.toString()),
                new TextExtractor());
        ReflectionTestUtils.setField(service, "maxChars", 9000);
        when(cvRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertBadRequest(Runnable call, String messagePart) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(messagePart)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void txtCvIsStoredUnderAGeneratedName() throws IOException {
        CvUploadResponse res = service.upload(USER, file("Mon CV.txt", "Compétences : Java, Spring Boot"));

        ArgumentCaptor<CvUpload> saved = ArgumentCaptor.forClass(CvUpload.class);
        verify(cvRepository).save(saved.capture());
        CvUpload cv = saved.getValue();
        assertThat(cv.getUserId()).isEqualTo(USER);
        assertThat(cv.getExtractedText()).isEqualTo("Compétences : Java, Spring Boot");
        // jamais le nom fourni par le client sur le disque (évite ../../ et les collisions)
        assertThat(cv.getStoredFileName()).matches("[0-9a-f-]{36}\\.txt");
        assertThat(Files.readString(storageDir.resolve(cv.getStoredFileName()))).contains("Java");
        assertThat(res.fileName()).isEqualTo("Mon CV.txt");
    }

    @Test
    void unsupportedFormatIsRejected() {
        assertBadRequest(() -> service.upload(USER, file("virus.exe", "MZ...")), "Format non supporté");
        assertThat(storageDir.toFile().list()).isEmpty();
        verify(cvRepository, never()).save(any());
    }

    @Test
    void emptyFileIsRejected() {
        assertBadRequest(() -> service.upload(USER, file("cv.txt", "")), "Fichier manquant");
    }

    /** L'extension ne suffit pas : le contenu doit vraiment être un PDF. */
    @Test
    void fakePdfIsRejected() {
        assertBadRequest(() -> service.upload(USER, file("cv.pdf", "ceci n'est pas un pdf")), "pas un PDF valide");
    }

    @Test
    void fileWithoutTextIsRejected() {
        assertBadRequest(() -> service.upload(USER, file("cv.txt", "   \n\n  ")), "Aucun texte lisible");
    }

    @Test
    void tooLongCvIsRejected() {
        assertBadRequest(() -> service.upload(USER, file("cv.txt", "a".repeat(9001))), "CV trop long");
        verify(cvRepository, never()).save(any());
    }

    @Test
    void deletingACvRemovesItsAnalysesThenTheFile() throws IOException {
        Files.writeString(storageDir.resolve("stored.txt"), "cv");
        CvUpload cv = CvUpload.builder().id(7L).userId(USER).storedFileName("stored.txt").extractedText("cv").build();
        when(cvRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(cv));

        service.delete(USER, 7L);

        InOrder order = inOrder(analysisRepository, cvRepository);
        order.verify(analysisRepository).deleteByCvId(7L);
        order.verify(cvRepository).delete(cv);
        assertThat(storageDir.resolve("stored.txt")).doesNotExist();
    }

    @Test
    void cvOfAnotherUserIsNotFound() {
        when(cvRepository.findByIdAndUserId(7L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 7L))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(analysisRepository, never()).deleteByCvId(any());
    }
}
