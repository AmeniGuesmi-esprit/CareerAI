package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.AnalysisCreateRequest;
import com.careerai.skillgap.dto.AnalysisResponse;
import com.careerai.skillgap.dto.AnalysisResult;
import com.careerai.skillgap.model.CvUpload;
import com.careerai.skillgap.repository.SkillGapAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comparaison réelle de modèles Groq sur le vrai pipeline (anonymisation, RAG lexical sur les 135 fiches,
 * prompt système, validation) : seul le modèle change.
 *
 * Deux modes :
 * - par défaut (mvn test) : un faux serveur Groq local répond à la place des modèles. Le test vérifie toute la
 *   chaîne de comparaison (pipeline réel, RAG, mesures, nouvelle tentative, rapport) sans quota ni réseau.
 *   Rapport : target/model-comparison-hors-ligne.md
 * - comparaison réelle, lancement explicite :
 *   mvn test -Dtest=ModelComparisonTest -Dcompare.models=openai/gpt-oss-120b,openai/gpt-oss-20b -Dcompare.runs=2
 *   avec la clé dans la variable d'environnement LLM_API_KEY. Rapport : target/model-comparison.md
 */
class ModelComparisonTest {

    /** Modèles simulés du mode hors ligne : un qui répond correctement, un qui renvoie une réponse inutilisable. */
    private static final String FAKE_GOOD = "simulation/modele-correct";
    private static final String FAKE_BROKEN = "simulation/modele-casse";

    /** Réponse que donnerait un bon modèle pour ce CV et cette offre. */
    private static final String FAKE_ANSWER = """

            {"matchScore": 45,
             "summary": "Bon socle Java et Spring Boot, mais l'offre exige Docker, Kubernetes, Kafka et la CI/CD.",
             "strengths": ["Java 17", "Spring Boot", "API REST"],
             "missingSkills": [
               {"skill": "Docker", "priority": "HIGH", "reason": "Exigé par l'offre.",
                "howToImprove": "Conteneuriser un projet Spring Boot en suivant la documentation officielle."},
               {"skill": "Kubernetes", "priority": "HIGH", "reason": "Exigé par l'offre.",
                "howToImprove": "Déployer ce conteneur sur un cluster local avec Minikube."},
               {"skill": "Apache Kafka", "priority": "MEDIUM", "reason": "Souhaité par l'offre.",
                "howToImprove": "Réaliser un échange asynchrone producteur / consommateur."},
               {"skill": "CI/CD (GitLab CI)", "priority": "MEDIUM", "reason": "Demandé dans les missions.",
                "howToImprove": "Écrire un pipeline qui lance les tests puis construit l'image."}],
             "cvTips": ["Chiffrer l'impact des API REST réalisées chez Sofrecom."]}
            """;

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
        String requested = System.getProperty("compare.models", "");
        boolean real = !requested.isBlank();
        ObjectMapper mapper = new ObjectMapper();
        HttpServer fake = real ? null : startFakeGroq(mapper);
        try {
            if (real) {
                String apiKey = System.getenv("LLM_API_KEY");
                if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("LLM_API_KEY absente");
                String url = System.getenv().getOrDefault("LLM_API_URL", "https://api.groq.com/openai/v1");
                List<Row> rows = compare(mapper, url, apiKey, List.of(requested.split(",")),
                        Integer.parseInt(System.getProperty("compare.runs", "1")), Long.getLong("compare.pause-ms", 20_000));
                writeReport(rows, Path.of("target", "model-comparison.md"));
            } else {
                String url = "http://127.0.0.1:" + fake.getAddress().getPort() + "/v1";
                List<Row> rows = compare(mapper, url, "cle-de-test", List.of(FAKE_GOOD, FAKE_BROKEN), 1, 0);
                String report = writeReport(rows, Path.of("target", "model-comparison-hors-ligne.md"));
                checkOfflineResults(rows, report);
            }
        } finally {
            if (fake != null) fake.stop(0);
        }
    }

    /** Lance la même analyse pour chaque modèle et mesure appels, latence, jetons et qualité. */
    private static List<Row> compare(ObjectMapper mapper, String url, String apiKey, List<String> models,
                                     int runs, long pauseMs) throws Exception {

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
                if (pauseMs > 0) Thread.sleep(pauseMs);   // marge pour la limite de jetons par minute
            }
        }
        return rows;
    }

    private static String writeReport(List<Row> rows, Path file) throws Exception {
        int runs = rows.stream().mapToInt(Row::run).max().orElse(1);
        String report = report(rows, runs);
        System.out.println(report);
        Files.createDirectories(file.getParent());
        Files.writeString(file, report, StandardCharsets.UTF_8);
        return report;
    }

    /** Mode hors ligne : les mesures et le rapport doivent refléter exactement ce que le faux Groq a répondu. */
    private static void checkOfflineResults(List<Row> rows, String report) {
        Row good = rows.stream().filter(r -> r.model().equals(FAKE_GOOD)).findFirst().orElseThrow();
        Row broken = rows.stream().filter(r -> r.model().equals(FAKE_BROKEN)).findFirst().orElseThrow();

        assertThat(good.valid()).isTrue();
        assertThat(good.calls()).isEqualTo(1);
        assertThat(good.gapsFound()).as("écarts attendus : Docker, Kubernetes, Kafka, CI").isEqualTo(4);
        assertThat(good.strengthsFound()).as("atouts attendus : Java, Spring").isEqualTo(2);
        assertThat(good.missingCount()).isEqualTo(4);
        assertThat(good.score()).isEqualTo(45);
        assertThat(good.leaks()).isZero();
        assertThat(good.promptTokens()).isEqualTo(1500);
        assertThat(good.completionTokens()).isEqualTo(400);
        assertThat(good.latencyMs()).isGreaterThanOrEqualTo(0);

        assertThat(broken.valid()).isFalse();
        assertThat(broken.calls()).as("une seule nouvelle tentative sur une réponse inutilisable").isEqualTo(2);
        assertThat(broken.error()).isNotBlank();

        assertThat(report).contains("| " + FAKE_GOOD + " | 1 | 1 | oui |", "| " + FAKE_BROKEN + " | 1 | 2 | non |");
    }

    /** Faux Groq : répond selon le modèle demandé, avec un champ usage comme la vraie API. */
    private static HttpServer startFakeGroq(ObjectMapper mapper) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                String content = FAKE_GOOD.equals(request.path("model").asText())
                        ? FAKE_ANSWER : "Désolé, je ne peux pas produire de JSON.";
                ObjectNode body = mapper.createObjectNode();
                ObjectNode choice = body.putArray("choices").addObject();
                choice.put("finish_reason", "stop");
                choice.putObject("message").put("role", "assistant").put("content", content);
                body.putObject("usage").put("prompt_tokens", 1500).put("completion_tokens", 400);
                byte[] bytes = mapper.writeValueAsBytes(body);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        return server;
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
