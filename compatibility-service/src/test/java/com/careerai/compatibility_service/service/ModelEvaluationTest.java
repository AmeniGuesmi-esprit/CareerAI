package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.config.AppConfig.AiProperties;
import com.careerai.compatibility_service.dto.AnalysisDtos.AiResult;
import com.careerai.compatibility_service.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Évaluation chiffrée de la qualité ET comparaison de modèles, sur le vrai pipeline de production
 * (anonymisation, agent avec l'outil rechercher_competences, schéma JSON imposé, validation) : seul le modèle change.
 * Jeu de référence annoté : src/test/resources/evaluation/cases.json (fourchette de score attendue, manques attendus,
 * compétences présentes qui ne doivent PAS être signalées manquantes, cas d'injection de prompt).
 *
 * Désactivé par défaut (appels réels à Ollama, plusieurs minutes par analyse sur CPU) ; lancement explicite :
 *   mvnw test -Dtest=ModelEvaluationTest -Deval.models=qwen2.5:7b,qwen2.5:3b -Deval.runs=2
 * Réglages (optionnels) : -Deval.prompts=v1,v2 (prompt tuning), -Deval.temperatures=0,0.2,0.7 (hyperparamètre),
 * -Deval.output=nom-du-rapport. Résultats : target/evaluation/<nom>.md (+ .json brut).
 */
@EnabledIfSystemProperty(named = "eval.models", matches = ".+")
class ModelEvaluationTest {

    record Case(String id, String description, String cv, String job, int scoreMin, int scoreMax,
                List<String> expectedMissing, List<String> forbiddenMissing, boolean injection) { }

    record Run(String model, String caseId, int run, boolean valid, String error, Integer score,
               long latencyMs, int llmCalls, int promptTokens, int completionTokens, int verifiedSkills,
               List<String> missingFound, List<String> missingExpectedButAbsent, List<String> falseGaps,
               List<String> missingSkills) { }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void evaluateModels() throws Exception {
        String url = System.getProperty("eval.url", "http://localhost:11434");
        List<String> models = List.of(System.getProperty("eval.models").split(","));
        int runs = Integer.parseInt(System.getProperty("eval.runs", "1"));
        List<Case> cases;
        try (InputStream in = new ClassPathResource("evaluation/cases.json").getInputStream()) {
            cases = mapper.readValue(in, new TypeReference<>() { });
        }
        PersonalDataAnonymizer anonymizer = new PersonalDataAnonymizer();
        SkillReferential referential = new SkillReferential(mapper);
        AiResponseParser parser = new AiResponseParser(mapper);

        // Plan d'expérience : modèles × versions du prompt × températures (une colonne du rapport par configuration)
        List<String> prompts = List.of(System.getProperty("eval.prompts", "v1").split(","));
        List<Double> temperatures = Arrays.stream(System.getProperty("eval.temperatures", "0.2").split(","))
                .map(String::trim).map(Double::parseDouble).toList();
        String output = System.getProperty("eval.output", "model-comparison");

        List<String> configs = new ArrayList<>();
        List<Run> all = new ArrayList<>();
        for (String model : models) {
            model = model.trim();
            warmUp(url, model); // le chargement du modèle en mémoire n'est pas compté dans la latence
            for (String prompt : prompts) {
                for (double temperature : temperatures) {
                    String config = label(model, prompt.trim(), temperature, prompts.size(), temperatures.size());
                    configs.add(config);
                    OllamaAnalysisClient client = new OllamaAnalysisClient(
                            new AiProperties(model, url, 900, 8192, temperature, prompt.trim()),
                            parser, referential, mapper);
                    for (Case c : cases) {
                        for (int r = 1; r <= runs; r++) {
                            Run run = evaluate(client, config, c, r, anonymizer);
                            all.add(run);
                            System.out.printf("[eval] %s %s #%d -> score=%s valide=%s %d ms%n",
                                    config, c.id(), r, run.score(), run.valid(), run.latencyMs());
                        }
                    }
                }
            }
        }

        Path dir = Path.of("target", "evaluation");
        Files.createDirectories(dir);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(output + ".json").toFile(), all);
        Files.writeString(dir.resolve(output + ".md"), report(configs, runs, cases, all), StandardCharsets.UTF_8);
        System.out.println("[eval] rapport : " + dir.resolve(output + ".md").toAbsolutePath());
    }

    /** "qwen2.5:7b", ou "qwen2.5:7b · prompt v2 · T=0.7" quand plusieurs prompts / températures sont comparés. */
    static String label(String model, String prompt, double temperature, int promptCount, int temperatureCount) {
        StringBuilder s = new StringBuilder(model);
        if (promptCount > 1) s.append(" · prompt ").append(prompt);
        if (temperatureCount > 1) s.append(" · T=").append(temperature);
        return s.toString();
    }

    private Run evaluate(OllamaAnalysisClient client, String model, Case c, int run, PersonalDataAnonymizer anonymizer) {
        long start = System.nanoTime();
        try {
            OllamaAnalysisClient.Analyzed a = client.analyzeWithMetrics(anonymizer.anonymize(c.cv()),
                    anonymizer.anonymize(c.job()));
            AiResult r = a.result();
            String missingText = normalize(String.join(" ; ", r.missingSkills()));
            List<String> found = new ArrayList<>();
            List<String> absent = new ArrayList<>();
            for (String expected : c.expectedMissing()) {
                (mentions(missingText, expected) ? found : absent).add(expected);
            }
            List<String> falseGaps = c.forbiddenMissing().stream().filter(f -> mentions(missingText, f)).toList();
            return new Run(model, c.id(), run, true, null, r.score(), a.metrics().latencyMs(),
                    a.metrics().llmCalls(), a.metrics().promptTokens(), a.metrics().completionTokens(),
                    r.verifiedSkills().size(), found, absent, falseGaps, r.missingSkills());
        } catch (ApiException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Run(model, c.id(), run, false, e.getCode() + " : " + e.getMessage(), null, ms,
                    0, 0, 0, 0, List.of(), c.expectedMissing(), List.of(), List.of());
        }
    }

    /** Un libellé attendu "a|b" est trouvé si l'une des variantes apparaît comme mot entier (sans casse ni accents). */
    static boolean mentions(String normalizedText, String alternatives) {
        for (String alt : alternatives.split("\\|")) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalize(alt)) + "(?![\\p{L}\\p{N}])");
            if (p.matcher(normalizedText).find()) return true;
        }
        return false;
    }

    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void warmUp(String url, String model) {
        RestClient.create().post().uri(url + "/api/chat").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "stream", false,
                        "messages", List.of(Map.of("role", "user", "content", "Réponds OK."))))
                .retrieve().toBodilessEntity();
    }

    // ── Rapport ─────────────────────────────────────────────────────────────

    private String report(List<String> configs, int runs, List<Case> cases, List<Run> all) {
        StringBuilder md = new StringBuilder();
        md.append("# Évaluation et comparaison des modèles — compatibility-service\n\n")
                .append("Généré le ").append(LocalDateTime.now().withNano(0)).append(" · ")
                .append(cases.size()).append(" cas annotés × ").append(runs).append(" exécution(s) par configuration · ")
                .append("pipeline de production (anonymisation, agent + outil, schéma JSON, validation).\n\n");

        md.append("| Indicateur | ").append(String.join(" | ", configs)).append(" |\n|---|")
                .append("---|".repeat(configs.size())).append('\n');
        List<Map<String, String>> cols = configs.stream().map(m -> summary(cases, runs,
                all.stream().filter(r -> r.model().equals(m.trim())).toList())).toList();
        for (String key : cols.get(0).keySet()) {
            md.append("| ").append(key);
            cols.forEach(c -> md.append(" | ").append(c.get(key)));
            md.append(" |\n");
        }

        md.append("\n## Détail par cas\n\n| Configuration | Cas | Attendu | Score | Manques trouvés | Manques oubliés | Faux manques | Outil | Latence |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        Map<String, Case> byId = new HashMap<>();
        cases.forEach(c -> byId.put(c.id(), c));
        for (Run r : all) {
            Case c = byId.get(r.caseId());
            md.append("| ").append(r.model()).append(" | ").append(r.caseId()).append(runs > 1 ? " #" + r.run() : "")
                    .append(" | ").append(c.scoreMin()).append("–").append(c.scoreMax())
                    .append(" | ").append(r.valid() ? r.score() : "✗ " + r.error())
                    .append(" | ").append(join(r.missingFound()))
                    .append(" | ").append(join(r.missingExpectedButAbsent()))
                    .append(" | ").append(join(r.falseGaps()))
                    .append(" | ").append(r.verifiedSkills())
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f s", r.latencyMs() / 1000.0))
                    .append(" |\n");
        }
        md.append("""

                ## Définition des indicateurs

                - **Validité** : analyses rendues et validées (JSON conforme, score 0-100, résumé présent).
                - **Score dans la fourchette** : score compris dans la fourchette annotée du cas.
                - **Écart moyen** : distance moyenne du score à la fourchette (0 si dedans), en points.
                - **Concordance du classement** : part des paires de cas que le modèle ordonne comme l'annotation
                  (1,00 = classement parfait ; 0,50 = hasard).
                - **Rappel des manques** : part des compétences manquantes attendues effectivement signalées.
                - **Taux de faux manques** : part des compétences PRÉSENTES dans le CV (souvent sous un synonyme)
                  signalées à tort comme manquantes — c'est l'erreur la plus pénalisante pour le candidat.
                - **Résistance à l'injection** : sur les cas piégés, score resté dans la fourchette basse attendue.
                - **Usage de l'outil** : analyses où l'agent a appelé `rechercher_competences` avec au moins un résultat.
                - **Latence** : durée cumulée des appels au LLM pour une analyse (modèle déjà chargé en mémoire).
                - **Écart-type du score** : stabilité entre exécutions répétées d'un même cas (si exécutions ≥ 2).
                """);
        return md.toString();
    }

    private Map<String, String> summary(List<Case> cases, int runs, List<Run> runsOfModel) {
        List<Run> valid = runsOfModel.stream().filter(Run::valid).toList();
        Map<String, Case> byId = new HashMap<>();
        cases.forEach(c -> byId.put(c.id(), c));

        int inRange = 0;
        double gap = 0;
        for (Run r : valid) {
            Case c = byId.get(r.caseId());
            int s = r.score();
            if (s >= c.scoreMin() && s <= c.scoreMax()) inRange++;
            gap += s < c.scoreMin() ? c.scoreMin() - s : Math.max(0, s - c.scoreMax());
        }
        int expected = 0, found = 0, forbidden = 0, falseGaps = 0;
        for (Run r : valid) {
            Case c = byId.get(r.caseId());
            expected += c.expectedMissing().size();
            found += r.missingFound().size();
            forbidden += c.forbiddenMissing().size();
            falseGaps += r.falseGaps().size();
        }
        List<Run> injections = valid.stream().filter(r -> byId.get(r.caseId()).injection()).toList();
        long resisted = injections.stream().filter(r -> r.score() <= byId.get(r.caseId()).scoreMax()).count();
        long toolUsed = valid.stream().filter(r -> r.verifiedSkills() > 0).count();
        List<Long> lat = valid.stream().map(Run::latencyMs).sorted().toList();

        Map<String, String> m = new LinkedHashMap<>();
        m.put("Validité", ratio(valid.size(), runsOfModel.size()));
        m.put("Score dans la fourchette", ratio(inRange, valid.size()));
        m.put("Écart moyen à la fourchette", valid.isEmpty() ? "—" : fmt(gap / valid.size()) + " pt");
        m.put("Concordance du classement", fmt2(concordance(cases, valid)));
        m.put("Rappel des manques", ratio(found, expected));
        m.put("Taux de faux manques", ratio(falseGaps, forbidden));
        m.put("Résistance à l'injection", ratio((int) resisted, injections.size()));
        m.put("Usage de l'outil", ratio((int) toolUsed, valid.size()));
        m.put("Latence moyenne", lat.isEmpty() ? "—" : fmt(lat.stream().mapToLong(Long::longValue).average().orElse(0) / 1000) + " s");
        m.put("Latence p95", lat.isEmpty() ? "—" : fmt(lat.get((int) Math.ceil(0.95 * lat.size()) - 1) / 1000.0) + " s");
        m.put("Jetons moyens (envoyés + générés)", valid.isEmpty() ? "—" : String.valueOf(Math.round(
                valid.stream().mapToInt(r -> r.promptTokens() + r.completionTokens()).average().orElse(0))));
        m.put("Écart-type du score", runs < 2 ? "— (1 exécution)" : fmt(meanStd(valid)) + " pt");
        return m;
    }

    /** Concordance de paires (type Kendall) entre le centre des fourchettes annotées et le score moyen prédit. */
    private static double concordance(List<Case> cases, List<Run> valid) {
        Map<String, Double> predicted = new HashMap<>();
        for (Case c : cases) {
            OptionalDouble avg = valid.stream().filter(r -> r.caseId().equals(c.id())).mapToInt(Run::score).average();
            if (avg.isPresent()) predicted.put(c.id(), avg.getAsDouble());
        }
        int pairs = 0, concordant = 0;
        for (int i = 0; i < cases.size(); i++) {
            for (int j = i + 1; j < cases.size(); j++) {
                Case a = cases.get(i), b = cases.get(j);
                double ga = (a.scoreMin() + a.scoreMax()) / 2.0, gb = (b.scoreMin() + b.scoreMax()) / 2.0;
                if (Math.abs(ga - gb) < 10 || !predicted.containsKey(a.id()) || !predicted.containsKey(b.id())) {
                    continue; // paires trop proches dans l'annotation : non départageables
                }
                pairs++;
                if (Math.signum(ga - gb) == Math.signum(predicted.get(a.id()) - predicted.get(b.id()))) concordant++;
            }
        }
        return pairs == 0 ? Double.NaN : (double) concordant / pairs;
    }

    private static double meanStd(List<Run> valid) {
        Map<String, List<Integer>> byCase = new HashMap<>();
        valid.forEach(r -> byCase.computeIfAbsent(r.caseId(), k -> new ArrayList<>()).add(r.score()));
        return byCase.values().stream().filter(l -> l.size() > 1).mapToDouble(l -> {
            double mean = l.stream().mapToInt(Integer::intValue).average().orElse(0);
            return Math.sqrt(l.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / (l.size() - 1));
        }).average().orElse(0);
    }

    private static String ratio(int num, int den) {
        return den == 0 ? "—" : num + "/" + den + " (" + Math.round(100.0 * num / den) + " %)";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return Double.isNaN(v) ? "—" : String.format(Locale.ROOT, "%.2f", v);
    }

    private static String join(List<String> items) {
        return items.isEmpty() ? "—" : String.join(", ", items);
    }
}
