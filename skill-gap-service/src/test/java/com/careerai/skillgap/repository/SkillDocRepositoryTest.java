package com.careerai.skillgap.repository;

import com.careerai.skillgap.model.SkillDoc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {RepositoryTestSupport.H2_URL, RepositoryTestSupport.H2_DRIVER,
        RepositoryTestSupport.H2_USER, RepositoryTestSupport.H2_PASSWORD, RepositoryTestSupport.DDL})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SkillDocRepositoryTest {

    @Autowired SkillDocRepository repo;

    private static SkillDoc doc(String slug, String embedding) {
        SkillDoc d = new SkillDoc();
        d.setSlug(slug);
        d.setSkill(slug.toUpperCase());
        d.setCategory("DevOps");
        d.setContent("Fiche " + slug + " : parcours d'apprentissage et ressources.");
        d.setEmbedding(embedding);
        d.setEmbeddingModel(embedding == null ? null : "test-model");
        return d;
    }

    @Test
    void findBySlug_findsTheRightCard() {
        repo.save(doc("docker", null));
        repo.save(doc("kubernetes", null));

        assertThat(repo.findBySlug("docker")).get().extracting(SkillDoc::getSkill).isEqualTo("DOCKER");
        assertThat(repo.findBySlug("inconnu")).isEmpty();
    }

    @Test
    void embeddingQueries_splitIndexedAndNotIndexedCards() {
        repo.save(doc("docker", "[0.1,0.2]"));
        repo.save(doc("kubernetes", null));
        repo.save(doc("terraform", null));

        assertThat(repo.findByEmbeddingIsNull()).extracting(SkillDoc::getSlug)
                .containsExactlyInAnyOrder("kubernetes", "terraform");
        assertThat(repo.findByEmbeddingIsNotNull()).extracting(SkillDoc::getSlug).containsExactly("docker");
    }

    @Test
    void slug_mustBeUnique() {
        repo.saveAndFlush(doc("docker", null));

        assertThatThrownBy(() -> repo.saveAndFlush(doc("docker", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateBySlug_keepsASingleCard() {
        repo.saveAndFlush(doc("docker", null));
        SkillDoc existing = repo.findBySlug("docker").orElseThrow();
        existing.setContent("Contenu mis à jour");
        existing.setEmbedding("[0.3,0.4]");
        repo.saveAndFlush(existing);

        assertThat(repo.count()).isEqualTo(1);
        assertThat(repo.findBySlug("docker").orElseThrow().getContent()).isEqualTo("Contenu mis à jour");
        assertThat(repo.findByEmbeddingIsNull()).isEmpty();
    }
}
