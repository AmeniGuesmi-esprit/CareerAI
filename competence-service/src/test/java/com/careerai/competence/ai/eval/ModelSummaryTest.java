package com.careerai.competence.ai.eval;

import com.careerai.competence.ai.eval.EvaluationCase.Categorie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSummaryTest {

    private static CaseResult ok(String statut, Double score, long ms, List<String> v) {
        return new CaseResult("X", Categorie.CONSEIL, statut, score, ms, v, null, "r");
    }

    @Test
    void percentile_nearestRank() {
        List<Long> l = List.of(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L);
        assertThat(ModelSummary.percentile(l, 50)).isEqualTo(500);
        assertThat(ModelSummary.percentile(l, 95)).isEqualTo(1000);
        assertThat(ModelSummary.percentile(List.of(), 95)).isZero();
    }

    @Test
    void summary_computesRatesScoreAndLatency() {
        var results = List.of(
                ok("VERIFIEE", 5.0, 1000, List.of()),
                ok("A_VERIFIER", 3.0, 3000, List.of("trop long")),
                new CaseResult("E", Categorie.CONSEIL, null, null, 0, List.of(), "quota", null));

        ModelSummary s = ModelSummary.of("m", results);

        assertThat(s.total()).isEqualTo(3);
        assertThat(s.erreurs()).isEqualTo(1);
        assertThat(s.conformes()).isEqualTo(1);
        assertThat(s.tauxConformite()).isEqualTo(33.3);
        assertThat(s.scoreMoyen()).isEqualTo(4.0);
        assertThat(s.tauxVerifiee()).isEqualTo(50.0);
        assertThat(s.latenceMoyenneMs()).isEqualTo(2000);
    }

    @Test
    void summary_withoutScores_hasNullScore() {
        assertThat(ModelSummary.of("m", List.of(ok("NON_VERIFIEE", null, 10, List.of()))).scoreMoyen()).isNull();
    }

    @Test
    void dataset_isWellFormed() {
        assertThat(EvaluationDataset.CASES).hasSize(15);
        assertThat(EvaluationDataset.CASES.stream().map(EvaluationCase::id).distinct().count()).isEqualTo(15);
        assertThat(EvaluationDataset.CASES.stream().map(EvaluationCase::categorie).distinct().count())
                .isEqualTo(Categorie.values().length);
    }

    @Test
    void report_containsEveryModelAndCsvHeader() {
        ModelSummary s = ModelSummary.of("modele-a", List.of(ok("VERIFIEE", 4.5, 800, List.of())));
        assertThat(ReportWriter.markdown(List.of(s), "juge", "2026-10-21"))
                .contains("modele-a", "4.5", "800 ms", "Conformité");
        assertThat(ReportWriter.csv(List.of(s))).startsWith("modele,cas,categorie").contains("modele-a,X,CONSEIL,VERIFIEE,4.5,800,true");
    }
}
