package com.careerai.competence.ai.eval;

import com.careerai.competence.ai.AdvisorService;
import com.careerai.competence.dto.AdviceResponse;
import com.careerai.competence.dto.AdviceResponse.Metriques;
import com.careerai.competence.exception.AiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModelEvaluatorTest {

    private final AdvisorService advisor = mock(AdvisorService.class);
    private final ModelEvaluator evaluator = new ModelEvaluator(advisor, 0, 0);

    private static AdviceResponse resp(String text) {
        return new AdviceResponse(text, "VERIFIEE", null, new Metriques("m", 500, 200L, 700, 4.5), "l");
    }

    @Test
    void compare_runsEveryCaseForEveryModel() {
        when(advisor.generate(anyString(), anyString(), eq(true))).thenReturn(resp("Réponse correcte pour tu et les tests."));
        List<EvaluationCase> two = EvaluationDataset.CASES.subList(0, 2);

        List<ModelSummary> res = evaluator.compare(List.of("a", "b"), two);

        assertThat(res).extracting(ModelSummary::modele).containsExactly("a", "b");
        assertThat(res).allSatisfy(s -> assertThat(s.total()).isEqualTo(2));
        verify(advisor, times(4)).generate(anyString(), anyString(), eq(true));
    }

    @Test
    void failingCall_isRecordedAsError_notThrown() {
        when(advisor.generate(anyString(), anyString(), anyBoolean()))
                .thenThrow(new AiException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        ModelSummary s = evaluator.evaluate("a", EvaluationDataset.CASES.subList(0, 1));

        assertThat(s.erreurs()).isEqualTo(1);
        assertThat(s.details().get(0).erreur()).isEqualTo("down");
    }

    @Test
    void latencyAndScore_comeFromAdviceMetrics() {
        when(advisor.generate(anyString(), anyString(), anyBoolean())).thenReturn(resp("ok"));

        CaseResult r = evaluator.runCase("a", EvaluationDataset.CASES.get(0));

        assertThat(r.latenceMs()).isEqualTo(500);
        assertThat(r.score()).isEqualTo(4.5);
    }
}
