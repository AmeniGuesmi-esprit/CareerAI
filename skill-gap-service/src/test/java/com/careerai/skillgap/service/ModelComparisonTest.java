package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.AnalysisCreateRequest;
import com.careerai.skillgap.dto.AnalysisResponse;
import com.careerai.skillgap.dto.AnalysisResult;
import com.careerai.skillgap.model.CvUpload;
import com.careerai.skillgap.repository.SkillGapAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comparaison réelle de modèles Groq sur le vrai pipeline (anonymisation, RAG lexical sur les 135 fiches,
 * prompt système, validation) : seul le modèle change.
 *
 * Désactivé par défaut (appels payants en quota) ; lancement explicite :
 *   mvn test -Dtest=ModelComparisonTest -Dcompare.models=openai/gpt-oss-120b,openai/gpt-oss-20b -Dcompare.runs=2
 * avec la clé dans la variable d'environnement LLM_API_KEY. Résultat : target/model-comparison.md
 */
@EnabledIfSystemProperty(named = "compare.models", matches = ".+")
class ModelComparisonTest {

    private static final String CV = """
            Sami Trabelsi
            sami.trabelsi@example.com | +216 22 123 456
            12 rue de Marseille, 1002 Tunis
            Profil : développeur Java junior, 2 ans d'expérience.
            Compétences : Java 17, Spring Boot, Spring Data JPA, MySQL, Angular, Git, API REST.
            Expérience : 2022 - 2024 Développeur Java chez Sofrecom : API REST Spring Boot pour la facturation,
            tests unitaires JUnit, revue de code.
            Formation : Diplôme d'ingénieur en informatique, ESPRIT (2022).
            Langues : français, anglais (B2).
            """;

    private static final String OFFER = """
            Développeur Java Backend confirmé (H/F)
            Missions :
            - Développer des microservices Java avec Spring Boot
            - Conteneuriser avec Docker et déployer sur Kubernetes
            - Mettre en place la CI/CD avec GitLab CI
            - Échanges asynchrones avec Apache Kafka
            Profil recherché : 3 ans d'expérience minimum, maîtrise de Docker et Kubernetes exigée,
            connaissance de Kafka souhaitée, une certification cloud est un plus.
            Avantages : télétravail partiel, mutuelle, tickets restaurant.
            Candidatures : rh@entreprise.tn
            """;

    /** Écarts attendus (exigés ou souhaités par l'offre, absents du CV) et atouts attendus (présents dans les deux). */
    private static final List<String> EXPECTED_GAPS = List.of("docker", "kubernetes", "kafka", "ci");
    private static final List<String> EXPECTED_STRENGTHS = List.of("java", "spring");

    private record Row(String model, int run, int calls, long latencyMs, int promptTokens, int completionTokens,
                       boolean valid, int gapsFound, int strengthsFound, int missingCount, int score,
                       int anchored, int leaks, String error) {}

    @Test
    void compareModels() throws Exception {
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("LLM_API_KEY absente");
        String url = System.getenv().getOrDefault("LLM_API_URL", "https://api.groq.com/openai/v1");
        List<String> models = List.of(System.getProperty("compare.models").split(","));
        int runs = Integer.parseInt(System.getProperty("compare.runs", "1"));
        ObjectMapper mapper = new ObjectMapper();

        // Contexte RAG calculé comme en production (recherche lexicale sur la vraie base de connaissances)
        PiiMasker masker = new PiiMasker();
        String context = ragContext(mapper, masker.mask(OFFER.strip()).text(), masker.anonymizeCv(CV));
        Files.createDirectories(Path.of("target", "model-comparison-raw"));
        Files.writeString(Path.of("target", "model-comparison-raw", "contexte-rag.txt"), context, StandardCharsets.UTF_8);

        List<Row> rows = new ArrayList<>();
        for (String model : models) {
            String effort = model.startsWith("openai/gpt-oss") ? "low" : "";
            for (int run = 1; run <= runs; run++) {
                List<LlmClient.Completion> calls = new ArrayList<>();
                LlmClient llm = new LlmClient(mapper, url, apiKey, model.strip(), 2500, effort, 90) {
                    @Override
                    public String complete(String system, String user) {
                        LlmClient.Completion c = call(system, user);
                        calls.add(c);
                        return c.text();
                    }
                };
                SkillGapService service = service(llm, context, mapper);
                AnalysisResult r = null;
                String error = "";
                try {
                    r = service.analyze(1L, request()).result();
                } catch (Exception e) {
                    error = e.getMessage();
                }
                long latency = calls.stream().mapToLong(LlmClient.Completion::latencyMs).sum();
                int in = calls.stream().mapToInt(LlmClient.Completion::promptTokens).sum();
                int out = calls.stream().mapToInt(LlmClient.Completion::completionTokens).sum();
                rows.add(r == null
                        ? new Row(model, run, calls.size(), latency, in, out, false, 0, 0, 0, 0, 0, 0, error)
                        : new Row(model, run, calls.size(), latency, in, out, true,
                                countFound(r, EXPECTED_GAPS, true), countFound(r, EXPECTED_STRENGTHS, false),
                                r.missingSkills().size(), r.matchScore(), anchored(r, mapper), leaks(r), ""));
                if (!calls.isEmpty()) {
                    Path raw = Path.of("target", "model-comparison-raw", model.replace('/', '_') + "-" + run + ".txt");
                    Files.createDirectories(raw.getParent());
                    Files.writeString(raw, calls.get(0).text(), StandardCharsets.UTF_8);
                }
                Thread.sleep(Long.getLong("compare.pause-ms", 20_000));   // marge pour la limite de jetons par minute
            }
        }
        String report = report(rows, runs);
        System.out.println(report);
        Files.writeString(Path.of("target", "model-comparison.md"), report, StandardCharsets.UTF_8);
    }

    private static SkillGapService service(LlmClient llm, String context, ObjectMapper mapper) {
        CvService cvService = mock(CvService.class);
        when(cvService.findMine(anyLong(), anyLong())).thenReturn(CvUpload.builder().id(1L).userId(1L)
                .originalFileName("cv.pdf").fileType("pdf").storedFileName("x.pdf").extractedText(CV).build());
        SkillGapAnalysisRepository repository = mock(SkillGapAnalysisRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RagService rag = mock(RagService.class);
        when(rag.retrieve(anyString(), anyString())).thenReturn(context);
        return new SkillGapService(cvService, repository, new PiiMasker(), llm, rag, mapper, new AnalysisCache(0, 0));
    }

    private static AnalysisCreateRequest request() {
        AnalysisCreateRequest r = new AnalysisCreateRequest();
        r.setCvId(1L);
        r.setJobTitle("Développeur Java Backend");
        r.setJobDescription(OFFER);
        return r;
    }

    /** Même logique que RagService.retrieve (mode lexical) : seuil 0,10, blocs de 250 caractères, top 5, 1 800 caractères. */
    private static String ragContext(ObjectMapper mapper, String jobText, String cvText) throws Exception {
        List<JsonNode> kb = RagService.readKnowledgeBase(mapper);
        List<String> texts = kb.stream()
                .map(n -> RagService.lexicalText(n.path("skill").asText(), n.path("content").asText())).toList();
        double[] scores = RagService.lexicalScores(texts, RagService.requirementsText(jobText), 250);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) if (scores[i] >= 0.10) order.add(i);
        order.sort((a, b) -> Double.compare(scores[b], scores[a]));
        CvCoverage coverage = new CvCoverage(cvText);
        StringBuilder ctx = new StringBuilder();
        int kept = 0;
        for (int i : order) {
            if (kept >= 5) break;
            String skill = kb.get(i).path("skill").asText();
            if (coverage.covers(skill)) continue;
            String line = "- " + skill + " : " + kb.get(i).path("content").asText() + "\n";
            if (ctx.length() + line.length() > 1800) break;
            ctx.append(line);
            kept++;
        }
        return ctx.toString().strip();
    }

    private static int countFound(AnalysisResult r, List<String> expected, boolean inGaps) {
        String text = (inGaps
                ? r.missingSkills().stream().map(m -> m.skill()).toList()
                : r.strengths()).toString().toLowerCase(Locale.ROOT);
        return (int) expected.stream().filter(text::contains).count();
    }

    /**
     * Conseils « howToImprove » appuyés sur la fiche de la même compétence : au moins 2 mots distinctifs
     * (7 lettres ou plus, ou un nom de domaine) de la fiche se retrouvent dans le conseil.
     */
    private static int anchored(AnalysisResult r, ObjectMapper mapper) throws Exception {
        List<JsonNode> kb = RagService.readKnowledgeBase(mapper);
        int count = 0;
        for (var m : r.missingSkills()) {
            String skill = m.skill().toLowerCase(Locale.ROOT);
            String advice = m.howToImprove().toLowerCase(Locale.ROOT);
            for (JsonNode n : kb) {
                String name = n.path("skill").asText().toLowerCase(Locale.ROOT);
                String head = name.split("[ (/]")[0];
                if (head.length() < 2 || !(skill.contains(head) || name.contains(skill))) continue;
                long shared = Arrays.stream(n.path("content").asText().toLowerCase(Locale.ROOT).split("[\\s,;:()]+"))
                        .map(t -> t.replaceAll("[.]+$", ""))
                        .filter(t -> t.length() >= 7 || t.contains("."))
                        .distinct().filter(advice::contains).count();
                if (shared >= 2) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** Fuite d'un terme interne du prompt (« du contexte », « la fiche ») dans le texte affiché au candidat. */
    private static int leaks(AnalysisResult r) {
        String all = (r.summary() + r.strengths() + r.cvTips() + r.missingSkills()).toLowerCase(Locale.ROOT);
        return (int) Pattern.compile("\\b(du|le|au|dans le) contexte\\b|\\bla fiche\\b").matcher(all).results().count();
    }

    private static String report(List<Row> rows, int runs) {
        StringBuilder sb = new StringBuilder("# Comparaison de modèles (skill-gap-service)\n\n");
        sb.append("Même CV, même offre, même contexte RAG ; ").append(runs).append(" exécution(s) par modèle.\n\n");
        sb.append("| Modèle | Essai | Appels | Valide | Latence (ms) | Jetons envoyés | Jetons générés | Écarts /4 | Atouts /2 | Compétences | Conseils appuyés sur une fiche | Fuites « contexte » | Score | Erreur |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row r : rows) {
            sb.append("| ").append(r.model()).append(" | ").append(r.run()).append(" | ").append(r.calls())
                    .append(" | ").append(r.valid() ? "oui" : "non")
                    .append(" | ").append(r.latencyMs()).append(" | ").append(r.promptTokens()).append(" | ")
                    .append(r.completionTokens()).append(" | ").append(r.gapsFound()).append(" | ")
                    .append(r.strengthsFound()).append(" | ").append(r.missingCount()).append(" | ")
                    .append(r.anchored()).append("/").append(r.missingCount()).append(" | ").append(r.leaks())
                    .append(" | ").append(r.score()).append(" | ")
                    .append(r.error().replace("|", "/")).append(" |\n");
        }
        return sb.toString();
    }
}
