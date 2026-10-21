package com.careerai.competence.ai.eval;

import com.careerai.competence.ai.AdvisorService;
import com.careerai.competence.ai.GeminiClient;
import com.careerai.competence.ai.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Banc d'essai RÉEL (appelle Gemini) : désactivé par défaut pour ne pas consommer le quota ni casser la CI.
 *
 *   export GEMINI_API_KEY=...            # PowerShell : $env:GEMINI_API_KEY="..."
 *   mvn test -Dtest=ModelBenchmarkTest -Dbenchmark=true
 *   (optionnel) -Dbenchmark.models=modele1,modele2,modele3  -Dbenchmark.pauseMs=5000
 *
 * Écrit docs/evaluation/rapport-modeles.md et rapport-modeles.csv : ce sont les vrais chiffres à coller dans le rapport.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ModelBenchmarkTest {

    @Test
    void compareModels_andWriteReport() throws Exception {
        String key = System.getenv("GEMINI_API_KEY");
        String judge = System.getProperty("benchmark.judge", "gemini-3.1-flash-lite");
        List<String> models = Arrays.stream(System.getProperty("benchmark.models",
                "gemini-3.1-flash-lite,gemini-2.5-flash,gemini-2.5-flash-lite").split(",")).map(String::trim).toList();
        long pause = Long.parseLong(System.getProperty("benchmark.pauseMs", "5000"));

        AdvisorService advisor = new AdvisorService(new GeminiClient(key, judge), new RateLimiter());
        List<ModelSummary> summaries = new ModelEvaluator(advisor, pause).compare(models, EvaluationDataset.CASES);

        Path dir = Path.of("docs", "evaluation");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("rapport-modeles.md"),
                ReportWriter.markdown(summaries, judge, LocalDate.now().toString()));
        Files.writeString(dir.resolve("rapport-modeles.csv"), ReportWriter.csv(summaries));

        assertThat(summaries).hasSameSizeAs(models);
    }
}
