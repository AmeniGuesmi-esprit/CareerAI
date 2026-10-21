package com.careerai.skillgap.repository;

import com.careerai.skillgap.model.CvUpload;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static com.careerai.skillgap.repository.RepositoryTestSupport.cv;
import static com.careerai.skillgap.repository.RepositoryTestSupport.setDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {RepositoryTestSupport.H2_URL, RepositoryTestSupport.H2_DRIVER,
        RepositoryTestSupport.H2_USER, RepositoryTestSupport.H2_PASSWORD, RepositoryTestSupport.DDL})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CvUploadRepositoryTest {

    @Autowired CvUploadRepository repo;
    @Autowired EntityManager em;

    @Test
    void save_fillsIdAndUploadDate() {
        CvUpload saved = repo.saveAndFlush(cv(1L, "cv.pdf"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUploadedAt()).isNotNull().isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void findByUser_returnsOnlyHisCvs_newestFirst() {
        CvUpload old = repo.save(cv(1L, "ancien.pdf"));
        CvUpload recent = repo.save(cv(1L, "recent.pdf"));
        repo.save(cv(2L, "autre-utilisateur.pdf"));
        setDate(em, CvUpload.class, "uploadedAt", old.getId(), LocalDateTime.of(2026, 1, 1, 10, 0));
        setDate(em, CvUpload.class, "uploadedAt", recent.getId(), LocalDateTime.of(2026, 6, 1, 10, 0));

        List<CvUpload> list = repo.findByUserIdOrderByUploadedAtDesc(1L);

        assertThat(list).extracting(CvUpload::getOriginalFileName).containsExactly("recent.pdf", "ancien.pdf");
    }

    @Test
    void findByUser_withoutCv_returnsEmptyList() {
        assertThat(repo.findByUserIdOrderByUploadedAtDesc(99L)).isEmpty();
    }

    @Test
    void findByIdAndUserId_ownerGetsIt_otherUserGetsNothing() {
        Long id = repo.save(cv(1L, "cv.pdf")).getId();

        assertThat(repo.findByIdAndUserId(id, 1L)).isPresent();
        assertThat(repo.findByIdAndUserId(id, 2L)).as("un autre utilisateur ne voit pas ce CV").isEmpty();
    }

    @Test
    void storedFileName_mustBeUnique() {
        CvUpload a = cv(1L, "a.pdf");
        CvUpload b = cv(2L, "b.pdf");
        b.setStoredFileName(a.getStoredFileName());
        repo.saveAndFlush(a);

        assertThatThrownBy(() -> repo.saveAndFlush(b)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userId_isRequired() {
        CvUpload noUser = cv(null, "cv.pdf");

        assertThatThrownBy(() -> repo.saveAndFlush(noUser)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void longExtractedText_isStoredEntirely() {
        String text = "Compétence ".repeat(20_000);   // ~220 000 caractères (colonne LONGTEXT)
        CvUpload big = cv(1L, "long.pdf");
        big.setExtractedText(text);
        Long id = repo.saveAndFlush(big).getId();
        em.clear();

        assertThat(repo.findById(id).orElseThrow().getExtractedText()).isEqualTo(text);
    }
}
