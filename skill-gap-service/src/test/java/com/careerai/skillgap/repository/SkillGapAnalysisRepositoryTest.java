package com.careerai.skillgap.repository;

import com.careerai.skillgap.model.CvUpload;
import com.careerai.skillgap.model.SkillGapAnalysis;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static com.careerai.skillgap.repository.RepositoryTestSupport.analysis;
import static com.careerai.skillgap.repository.RepositoryTestSupport.cv;
import static com.careerai.skillgap.repository.RepositoryTestSupport.setDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {RepositoryTestSupport.H2_URL, RepositoryTestSupport.H2_DRIVER,
        RepositoryTestSupport.H2_USER, RepositoryTestSupport.H2_PASSWORD, RepositoryTestSupport.DDL})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SkillGapAnalysisRepositoryTest {

    @Autowired SkillGapAnalysisRepository repo;
    @Autowired CvUploadRepository cvs;
    @Autowired EntityManager em;

    @Test
    void save_fillsIdAndCreationDate_andKeepsCvLink() {
        CvUpload cv = cvs.save(cv(1L, "cv.pdf"));
        SkillGapAnalysis saved = repo.saveAndFlush(analysis(1L, cv, "Développeur Java"));
        em.clear();

        SkillGapAnalysis read = repo.findById(saved.getId()).orElseThrow();
        assertThat(read.getCreatedAt()).isNotNull();
        assertThat(read.getCv().getId()).isEqualTo(cv.getId());
        assertThat(read.getModel()).isEqualTo("openai/gpt-oss-120b");
    }

    @Test
    void findByUser_returnsOnlyHisAnalyses_newestFirst() {
        CvUpload cv1 = cvs.save(cv(1L, "cv1.pdf"));
        CvUpload cv2 = cvs.save(cv(2L, "cv2.pdf"));
        SkillGapAnalysis old = repo.save(analysis(1L, cv1, "Ancienne offre"));
        SkillGapAnalysis recent = repo.save(analysis(1L, cv1, "Offre récente"));
        repo.save(analysis(2L, cv2, "Offre d'un autre utilisateur"));
        setDate(em, SkillGapAnalysis.class, "createdAt", old.getId(), LocalDateTime.of(2026, 1, 1, 10, 0));
        setDate(em, SkillGapAnalysis.class, "createdAt", recent.getId(), LocalDateTime.of(2026, 6, 1, 10, 0));

        List<SkillGapAnalysis> list = repo.findByUserIdOrderByCreatedAtDesc(1L);

        assertThat(list).extracting(SkillGapAnalysis::getJobTitle).containsExactly("Offre récente", "Ancienne offre");
    }

    @Test
    void findByIdAndUserId_ownerGetsIt_otherUserGetsNothing() {
        CvUpload cv = cvs.save(cv(1L, "cv.pdf"));
        Long id = repo.save(analysis(1L, cv, "Offre")).getId();

        assertThat(repo.findByIdAndUserId(id, 1L)).isPresent();
        assertThat(repo.findByIdAndUserId(id, 2L)).as("un autre utilisateur ne voit pas cette analyse").isEmpty();
    }

    @Test
    void deleteByCvId_removesOnlyTheAnalysesOfThatCv() {
        CvUpload deleted = cvs.save(cv(1L, "a-supprimer.pdf"));
        CvUpload kept = cvs.save(cv(1L, "a-garder.pdf"));
        repo.save(analysis(1L, deleted, "Offre 1"));
        repo.save(analysis(1L, deleted, "Offre 2"));
        repo.save(analysis(1L, kept, "Offre 3"));
        em.flush();

        repo.deleteByCvId(deleted.getId());
        em.flush();
        em.clear();

        assertThat(repo.findByUserIdOrderByCreatedAtDesc(1L))
                .extracting(SkillGapAnalysis::getJobTitle).containsExactly("Offre 3");
    }

    /** Une analyse dépend de son CV : la clé étrangère empêche de supprimer un CV qui a encore des analyses. */
    @Test
    void cvWithAnalyses_cannotBeDeletedBeforeItsAnalyses() {
        CvUpload cv = cvs.save(cv(1L, "cv.pdf"));
        repo.save(analysis(1L, cv, "Offre"));
        em.flush();

        assertThatThrownBy(() -> {
            cvs.delete(cv);
            cvs.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteAnalysesThenCv_succeeds() {
        CvUpload cv = cvs.save(cv(1L, "cv.pdf"));
        repo.save(analysis(1L, cv, "Offre"));
        em.flush();

        repo.deleteByCvId(cv.getId());
        cvs.delete(cv);
        cvs.flush();

        assertThat(cvs.findById(cv.getId())).isEmpty();
        assertThat(repo.count()).isZero();
    }

    @Test
    void cv_isRequired() {
        SkillGapAnalysis noCv = analysis(1L, null, "Offre");

        assertThatThrownBy(() -> repo.saveAndFlush(noCv)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
