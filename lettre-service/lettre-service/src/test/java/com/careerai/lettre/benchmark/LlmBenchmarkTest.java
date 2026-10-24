package com.careerai.lettre.benchmark;

import com.careerai.lettre.ai.LlmClient;
import com.careerai.lettre.ai.LlmProperties;
import com.careerai.lettre.dto.GenerateLetterRequest;
import com.careerai.lettre.service.LetterQualityScorer;
import com.careerai.lettre.service.LettreAiService;
import com.careerai.lettre.service.PiiMasker;
import com.careerai.lettre.service.TextExtractor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmark RÉEL des modèles de génération de lettres (appels payants/quotas : désactivé par défaut).
 *
 * <p>Lancement (depuis le dossier qui contient pom.xml). URL, clés et modèles sont lus dans
 * src/main/resources/application.properties, exactement comme l'application :
 * <pre>
 *   mvn test -Dtest=LlmBenchmarkTest -Dbenchmark=true
 * </pre>
 * Options : -DBENCH_PROVIDERS=gemini,mistral · -DBENCH_RUNS=3 · -DBENCH_CASES=C01,C02 · -DBENCH_PAUSE_MS=2000
 * (Windows PowerShell : mettre chaque -D... entre guillemets, ex. "-Dbenchmark=true")
 *
 * <p>Protocole : mêmes prompts que la production ({@link LettreAiService#buildGeneratePrompts}), cache désactivé,
 * 1 appel de chauffe non mesuré par fournisseur, fournisseurs alternés à chaque essai pour répartir les variations
 * réseau. Chaque lettre est notée par {@link LetterQualityScorer} et enregistrée pour la notation humaine à l'aveugle.
 *
 * <p>Sorties dans target/benchmark/ : results.csv, summary.md, metrics.json, letters/, human-eval-sheet.csv,
 * human-eval-key.csv.
 */
@Tag("benchmark")
@EnabledIfSystemProperty(named = "benchmark", matches = "(?i)true")
class LlmBenchmarkTest {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Case(String id, String profil, String langue, String ton, String nomEntreprise, String poste,
                String offre, String cv, List<String> interdits) {}

    record Row(String caseId, String provider, String model, int run, long latencyMs, boolean success,
               int outputChars, String error, LetterQualityScorer.Report report, Boolean injectionResisted,
               String letterFile) {}

    private static final List<String> KNOWN_PROVIDERS = List.of("gemini", "cohere", "mistral");
    private static final Path APP_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

    private static final Path OUT = Path.of("target", "benchmark");

    @Test
    void benchmarkProviders() throws Exception {
        List<String> providers = selectedProviders();
        assertThat(providers).as("Aucune clé d'API dans application.properties").isNotEmpty();
        int runs = Integer.parseInt(env("BENCH_RUNS", "3"));
        long pauseMs = Long.parseLong(env("BENCH_PAUSE_MS", "2000"));
        List<Case> cases = selectedCases();

        LlmProperties props = properties(providers);
        LlmClient llm = new LlmClient(props);
        LettreAiService service = new LettreAiService(llm, props, new PiiMasker(), new TextExtractor());

        Files.createDirectories(OUT.resolve("letters"));
        log("Benchmark : %d fournisseur(s) %s, %d cas, %d essai(s) → %d appels mesurés",
                providers.size(), providers, cases.size(), runs, providers.size() * cases.size() * runs);

        // 1) Chauffe (connexion TLS, DNS, éventuel démarrage à froid côté fournisseur) : non mesurée
        for (String p : providers) {
            try {
                var pr = service.buildGeneratePrompts(request(cases.get(0), p));
                llm.chat(p, pr.system(), pr.user(), false);
            } catch (RuntimeException e) {
                log("Chauffe %s en échec : %s", p, e.getMessage());
            }
            pause(pauseMs);
        }
        llm.resetMetrics();

        // 2) Mesures : pour chaque essai et chaque cas, on alterne les fournisseurs
        List<Row> rows = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            for (Case c : cases) {
                List<String> order = new ArrayList<>(providers);
                Collections.rotate(order, run + rows.size()); // ordre différent à chaque passage
                for (String p : order) {
                    rows.add(measure(service, llm, c, p, run));
                    pause(pauseMs);
                }
            }
        }

        // 3) Cache : même requête deux fois sur le premier fournisseur (miss puis hit)
        List<long[]> cache = cacheExperiment(service, llm, cases, providers.get(0), pauseMs);

        writeCsv(rows);
        writeHumanEvalSheet(rows);
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(OUT.resolve("metrics.json"), om.writeValueAsString(llm.metrics()));
        Files.writeString(OUT.resolve("summary.md"), summary(rows, cache, providers, runs, cases.size()));
        log("Terminé : voir %s", OUT.resolve("summary.md").toAbsolutePath());

        assertThat(rows).as("Aucun appel réussi : vérifiez les clés et les quotas").anyMatch(Row::success);
    }

    // ───────────────────────── Mesure d'un appel ─────────────────────────

    private Row measure(LettreAiService service, LlmClient llm, Case c, String provider, int run) throws IOException {
        var prompts = service.buildGeneratePrompts(request(c, provider));
        long t0 = System.nanoTime();
        try {
            LlmClient.Result r = llm.chat(provider, prompts.system(), prompts.user(), false);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String letter = service.cleanGeneratedLetter(r.text());
            var report = LetterQualityScorer.evaluate(letter, c.cv(), c.offre(), c.nomEntreprise(), c.poste(), c.langue());
            Boolean injection = c.interdits() == null || c.interdits().isEmpty() ? null
                    : c.interdits().stream().noneMatch(w -> letter.toLowerCase(Locale.ROOT).contains(w.toLowerCase(Locale.ROOT)));
            String file = "letters/" + provider + "_" + c.id() + "_run" + run + ".txt";
            Files.writeString(OUT.resolve(file), letter, StandardCharsets.UTF_8);
            log("%-8s %s run%d : %5d ms, %3d mots, contraintes %5.1f, hallucination=%s",
                    provider, c.id(), run, ms, report.words(), report.constraintScore(), report.hallucinationSuspected());
            return new Row(c.id(), provider, r.model(), run, ms, true, letter.length(), null, report, injection, file);
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log("%-8s %s run%d : ÉCHEC après %d ms : %s", provider, c.id(), run, ms, e.getMessage());
            return new Row(c.id(), provider, null, run, ms, false, 0, e.getMessage(), null, null, null);
        }
    }

    private List<long[]> cacheExperiment(LettreAiService service, LlmClient llm, List<Case> cases, String provider,
                                         long pauseMs) {
        List<long[]> out = new ArrayList<>();
        for (Case c : cases.subList(0, Math.min(3, cases.size()))) {
            var pr = service.buildGeneratePrompts(request(c, provider));
            try {
                long t0 = System.nanoTime();
                llm.chat(provider, pr.system(), pr.user(), true);
                long miss = (System.nanoTime() - t0) / 1_000;
                long t1 = System.nanoTime();
                llm.chat(provider, pr.system(), pr.user(), true);
                long hit = (System.nanoTime() - t1) / 1_000;
                out.add(new long[]{miss, hit}); // microsecondes
            } catch (RuntimeException e) {
                log("Expérience cache %s en échec : %s", c.id(), e.getMessage());
            }
            pause(pauseMs);
        }
        return out;
    }

    // ───────────────────────── Rapport ─────────────────────────

    private String summary(List<Row> rows, List<long[]> cache, List<String> providers, int runs, int nCases) {
        StringBuilder md = new StringBuilder();
        md.append("# Résultats du benchmark IA — lettre-service\n\n");
        md.append("- Date : ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append('\n');
        md.append("- Protocole : ").append(nCases).append(" cas synthétiques × ").append(runs)
                .append(" essais × ").append(providers.size()).append(" fournisseurs, prompt de production, cache désactivé, ")
                .append("1 appel de chauffe non mesuré par fournisseur, ordre des fournisseurs alterné.\n");
        md.append("- Paramètres : température 0,7 · max_tokens 3000 · timeout 90 s\n\n");

        md.append("## 1. Latence et fiabilité\n\n");
        md.append("| Fournisseur | Modèle | Appels | Succès | p50 (ms) | p95 (ms) | Moyenne (ms) | Écart-type (ms) | Car./s |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (String p : providers) {
            List<Row> all = rows.stream().filter(r -> r.provider().equals(p)).toList();
            List<Row> ok = all.stream().filter(Row::success).toList();
            List<Long> lat = ok.stream().map(Row::latencyMs).toList();
            double cps = ok.stream().mapToDouble(r -> r.outputChars() * 1000.0 / Math.max(1, r.latencyMs())).average().orElse(0);
            md.append(String.format(Locale.ROOT, "| %s | %s | %d | %.0f %% | %d | %d | %.0f | %.0f | %.0f |%n",
                    p, ok.isEmpty() ? "—" : ok.get(0).model(), all.size(), pct(ok.size(), all.size()),
                    LlmClient.percentile(lat, 50), LlmClient.percentile(lat, 95), mean(lat), sd(lat), cps));
        }

        md.append("\n## 2. Qualité mesurée automatiquement\n\n");
        md.append("Score automatique /100 = 60 % respect des contraintes du prompt + 20 % couverture des compétences de l'offre ")
                .append("présentes dans le CV + 20 % absence d'hallucination détectable. Voir `LetterQualityScorer`.\n\n");
        md.append("| Fournisseur | Score auto (moy. ± é.-t.) | Contraintes | Couverture offre | Hallucinations | Longueur OK | Mots (moy.) | Injection résistée |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (String p : providers) {
            List<LetterQualityScorer.Report> rep = reports(rows, p);
            List<Double> auto = rep.stream().map(LetterQualityScorer.Report::autoScore).toList();
            List<Row> inj = rows.stream().filter(r -> r.provider().equals(p) && r.injectionResisted() != null).toList();
            md.append(String.format(Locale.ROOT, "| %s | %.1f ± %.1f | %.1f %% | %.1f %% | %.0f %% (%d/%d) | %.0f %% | %.0f | %s |%n",
                    p, meanD(auto), sdD(auto),
                    rep.stream().mapToDouble(LetterQualityScorer.Report::constraintScore).average().orElse(0),
                    rep.stream().mapToDouble(LetterQualityScorer.Report::offerCoverage).average().orElse(0),
                    pct(rep.stream().filter(LetterQualityScorer.Report::hallucinationSuspected).count(), rep.size()),
                    rep.stream().filter(LetterQualityScorer.Report::hallucinationSuspected).count(), rep.size(),
                    pct(rep.stream().filter(r -> r.checks().get("longueur_250_350_mots")).count(), rep.size()),
                    rep.stream().mapToInt(LetterQualityScorer.Report::words).average().orElse(0),
                    inj.isEmpty() ? "—" : inj.stream().filter(Row::injectionResisted).count() + "/" + inj.size()));
        }

        md.append("\n### Taux de réussite par contrainte\n\n| Contrainte |");
        providers.forEach(p -> md.append(' ').append(p).append(" |"));
        md.append("\n|---|").append("---|".repeat(providers.size())).append('\n');
        List<String> checkNames = rows.stream().filter(r -> r.report() != null).findFirst()
                .map(r -> new ArrayList<>(r.report().checks().keySet())).orElse(new ArrayList<>());
        for (String check : checkNames) {
            md.append("| ").append(check).append(" |");
            for (String p : providers) {
                List<LetterQualityScorer.Report> rep = reports(rows, p);
                md.append(String.format(Locale.ROOT, " %.0f %% |",
                        pct(rep.stream().filter(r -> r.checks().get(check)).count(), rep.size())));
            }
            md.append('\n');
        }

        md.append("\n### Hallucinations détectées (détail)\n\n");
        boolean any = false;
        for (Row r : rows) {
            if (r.report() != null && r.report().hallucinationSuspected()) {
                any = true;
                md.append("- ").append(r.provider()).append(' ').append(r.caseId()).append(" run").append(r.run())
                        .append(" : compétences ").append(r.report().unsupportedSkills())
                        .append(", nombres ").append(r.report().unsupportedNumbers())
                        .append(" → `").append(r.letterFile()).append("`\n");
            }
        }
        if (!any) md.append("Aucune.\n");
        md.append("\nUne détection n'est pas forcément une erreur (ex. durée calculée à partir de dates) : relire la lettre concernée.\n");

        md.append("\n## 3. Cache\n\n");
        if (cache.isEmpty()) {
            md.append("Expérience non réalisée (échecs d'appel).\n");
        } else {
            md.append("| Essai | Sans cache (ms) | Avec cache (ms) | Gain |\n|---|---|---|---|\n");
            int i = 1;
            for (long[] c : cache) {
                md.append(String.format(Locale.ROOT, "| %d | %.0f | %.3f | %.2f %% |%n",
                        i++, c[0] / 1000.0, c[1] / 1000.0, (c[0] - c[1]) * 100.0 / c[0]));
            }
            md.append("\nLe second appel identique ne contacte pas le fournisseur : 0 jeton consommé, 0 quota utilisé.\n");
        }

        md.append("\n## 4. Échecs\n\n");
        List<Row> failed = rows.stream().filter(r -> !r.success()).toList();
        if (failed.isEmpty()) md.append("Aucun.\n");
        failed.forEach(r -> md.append("- ").append(r.provider()).append(' ').append(r.caseId()).append(" run").append(r.run())
                .append(" : ").append(r.error()).append('\n'));

        md.append("\n## 5. Productivité\n\n").append(productivity());

        md.append("\n## 6. Notation humaine\n\n")
                .append("`human-eval-sheet.csv` liste les lettres dans un ordre aléatoire, sans le nom du modèle. ")
                .append("Deux évaluateurs notent chaque lettre de 1 à 5 sur les 5 critères de docs/evaluation-ia.md, ")
                .append("puis on rapproche avec `human-eval-key.csv`.\n");
        return md.toString();
    }

    /** Lit docs/mesures-productivite.csv si des mesures ont été saisies. */
    private String productivity() {
        Path f = Path.of("docs", "mesures-productivite.csv");
        try {
            if (!Files.exists(f)) return "Fichier docs/mesures-productivite.csv absent.\n";
            List<String[]> data = Files.readAllLines(f, StandardCharsets.UTF_8).stream().skip(1)
                    .filter(l -> !l.isBlank() && !l.startsWith("#")).map(l -> l.split(",", -1))
                    .filter(a -> a.length >= 6 && !a[2].isBlank() && !a[3].isBlank()).toList();
            if (data.isEmpty()) return "Aucune mesure saisie dans docs/mesures-productivite.csv.\n";
            List<Double> manual = data.stream().map(a -> Double.parseDouble(a[2].trim())).toList();
            List<Double> ai = data.stream().map(a -> Double.parseDouble(a[3].trim())).toList();
            double m = meanD(manual), a = meanD(ai);
            double corrections = data.stream().mapToDouble(x -> x[4].isBlank() ? 0 : Double.parseDouble(x[4].trim())).average().orElse(0);
            long accepted = data.stream().filter(x -> x[5].trim().equalsIgnoreCase("oui")).count();
            return String.format(Locale.ROOT,
                    "| Mesure | Valeur |%n|---|---|%n| Lettres mesurées | %d |%n| Temps sans IA (moy.) | %.1f min |%n"
                            + "| Temps avec IA, relecture comprise (moy.) | %.1f min |%n| **Gain de temps** | **%.0f %%** |%n"
                            + "| Corrections par lettre (moy.) | %.1f |%n| Lettres acceptées après relecture | %.0f %% |%n",
                    data.size(), m, a, (m - a) * 100 / m, corrections, pct(accepted, data.size()));
        } catch (Exception e) {
            return "docs/mesures-productivite.csv illisible : " + e.getMessage() + "\n";
        }
    }

    private void writeCsv(List<Row> rows) throws IOException {
        StringBuilder csv = new StringBuilder("case_id,provider,model,run,latency_ms,success,output_chars,words,paragraphs,"
                + "constraint_score,offer_coverage,hallucination,unsupported_skills,unsupported_numbers,injection_resisted,"
                + "auto_score,letter_file,error\n");
        for (Row r : rows) {
            var q = r.report();
            csv.append(String.join(",", r.caseId(), r.provider(), str(r.model()), String.valueOf(r.run()),
                    String.valueOf(r.latencyMs()), String.valueOf(r.success()), String.valueOf(r.outputChars()),
                    q == null ? "" : String.valueOf(q.words()), q == null ? "" : String.valueOf(q.paragraphs()),
                    q == null ? "" : String.valueOf(q.constraintScore()), q == null ? "" : String.valueOf(q.offerCoverage()),
                    q == null ? "" : String.valueOf(q.hallucinationSuspected()),
                    q == null ? "" : quote(String.join(" ", q.unsupportedSkills())),
                    q == null ? "" : quote(String.join(" ", q.unsupportedNumbers())),
                    r.injectionResisted() == null ? "" : String.valueOf(r.injectionResisted()),
                    q == null ? "" : String.valueOf(q.autoScore()), str(r.letterFile()), quote(str(r.error()))))
                    .append('\n');
        }
        Files.writeString(OUT.resolve("results.csv"), csv.toString(), StandardCharsets.UTF_8);
    }

    /** Feuille de notation à l'aveugle (ordre aléatoire, sans nom de modèle) et sa clé de correspondance. */
    private void writeHumanEvalSheet(List<Row> rows) throws IOException {
        List<Row> ok = new ArrayList<>(rows.stream().filter(Row::success).toList());
        Collections.shuffle(ok, new Random(42));
        StringBuilder sheet = new StringBuilder("sample_id,case_id,letter_file,evaluateur,fidelite_cv_1_5,adequation_offre_1_5,"
                + "langue_1_5,structure_ton_1_5,contraintes_1_5,fait_invente_oui_non,commentaire\n");
        StringBuilder key = new StringBuilder("sample_id,provider,model,run\n");
        int i = 1;
        for (Row r : ok) {
            String id = String.format("L%03d", i++);
            String blind = "letters/blind/" + id + ".txt";
            Files.createDirectories(OUT.resolve("letters/blind"));
            Files.copy(OUT.resolve(r.letterFile()), OUT.resolve(blind), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            sheet.append(id).append(',').append(r.caseId()).append(',').append(blind).append(",,,,,,,,\n");
            key.append(id).append(',').append(r.provider()).append(',').append(r.model()).append(',').append(r.run()).append('\n');
        }
        Files.writeString(OUT.resolve("human-eval-sheet.csv"), sheet.toString(), StandardCharsets.UTF_8);
        Files.writeString(OUT.resolve("human-eval-key.csv"), key.toString(), StandardCharsets.UTF_8);
    }

    // ───────────────────────── Configuration ─────────────────────────

    private static List<String> selectedProviders() throws IOException {
        java.util.Properties app = appProperties();
        String wanted = env("BENCH_PROVIDERS", "");
        List<String> names = wanted.isBlank() ? KNOWN_PROVIDERS
                : List.of(wanted.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
        return names.stream()
                .filter(n -> !app.getProperty("app.llm.providers." + n + ".api-key", "").isBlank())
                .collect(Collectors.toList());
    }

    /** Même configuration que l'application : URL, clé, modèle et reasoning-effort de application.properties. */
    private static LlmProperties properties(List<String> providers) throws IOException {
        java.util.Properties app = appProperties();
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider(providers.get(0));
        props.setTemperature(Double.parseDouble(app.getProperty("app.llm.temperature", "0.7")));
        props.setMaxTokens(Integer.parseInt(app.getProperty("app.llm.max-tokens", "3000")));
        props.setTimeoutSeconds(90);
        for (String n : providers) {
            String prefix = "app.llm.providers." + n + ".";
            LlmProperties.Provider p = new LlmProperties.Provider();
            p.setUrl(app.getProperty(prefix + "url"));
            p.setApiKey(app.getProperty(prefix + "api-key"));
            p.setModel(app.getProperty(prefix + "model"));
            p.setReasoningEffort(app.getProperty(prefix + "reasoning-effort"));
            props.getProviders().put(n, p);
        }
        return props;
    }

    /** Lit application.properties et remplace ${VAR:défaut} par la variable d'environnement ou la valeur par défaut. */
    private static java.util.Properties appProperties() throws IOException {
        java.util.Properties raw = new java.util.Properties();
        try (var reader = Files.newBufferedReader(APP_PROPERTIES, StandardCharsets.UTF_8)) {
            raw.load(reader);
        }
        java.util.Properties resolved = new java.util.Properties();
        for (String key : raw.stringPropertyNames()) {
            java.util.regex.Matcher m = PLACEHOLDER.matcher(raw.getProperty(key).trim());
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String fromEnv = System.getenv(m.group(1));
                String value = fromEnv != null && !fromEnv.isBlank() ? fromEnv : (m.group(2) == null ? "" : m.group(2));
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
            }
            m.appendTail(sb);
            resolved.setProperty(key, sb.toString());
        }
        return resolved;
    }

    private static List<Case> selectedCases() throws IOException {
        try (InputStream in = LlmBenchmarkTest.class.getResourceAsStream("/benchmark/cases.json")) {
            List<Case> all = new ObjectMapper().readValue(in, new TypeReference<List<Case>>() {});
            String wanted = env("BENCH_CASES", "");
            if (wanted.isBlank()) return all;
            List<String> ids = List.of(wanted.toUpperCase(Locale.ROOT).split("\\s*,\\s*"));
            return all.stream().filter(c -> ids.contains(c.id())).toList();
        }
    }

    private static GenerateLetterRequest request(Case c, String provider) {
        GenerateLetterRequest r = new GenerateLetterRequest();
        r.setNomEntreprise(c.nomEntreprise());
        r.setPoste(c.poste());
        r.setOffre(c.offre());
        r.setCv(c.cv());
        r.setLangue(c.langue());
        r.setTon(c.ton());
        r.setProvider(provider);
        return r;
    }

    // ───────────────────────── Utilitaires ─────────────────────────

    private static List<LetterQualityScorer.Report> reports(List<Row> rows, String provider) {
        return rows.stream().filter(r -> r.provider().equals(provider) && r.report() != null).map(Row::report).toList();
    }

    /** Option lue en -DNOM=valeur, sinon en variable d'environnement. */
    private static String env(String name, String def) {
        String v = System.getProperty(name);
        if (v == null || v.isBlank()) v = System.getenv(name);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }

    private static double mean(List<Long> v) {
        return v.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static double sd(List<Long> v) {
        return sdD(v.stream().map(Long::doubleValue).toList());
    }

    private static double meanD(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double sdD(List<Double> v) {
        if (v.size() < 2) return 0;
        double m = meanD(v);
        return Math.sqrt(v.stream().mapToDouble(x -> (x - m) * (x - m)).sum() / (v.size() - 1));
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"").replaceAll("\\R", " ") + "\"";
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String fmt, Object... args) {
        System.out.println("[benchmark] " + String.format(Locale.ROOT, fmt, args));
    }
}
