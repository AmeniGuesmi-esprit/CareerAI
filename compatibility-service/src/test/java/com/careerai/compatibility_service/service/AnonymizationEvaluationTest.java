package com.careerai.compatibility_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Évaluation chiffrée du masquage des données personnelles sur un corpus annoté
 * (src/test/resources/evaluation/pii-corpus.json) : précision, rappel et F1 par type, et taux de sur-masquage
 * sur des textes sans donnée personnelle (dates, versions, montants). Les entités hors périmètre
 * (noms, adresses, dates de naissance, identifiants) sont mesurées à part : c'est le risque résiduel documenté.
 * Rapport : target/evaluation/anonymisation.md
 */
class AnonymizationEvaluationTest {

    record Pii(String type, String value) { }

    record Item(String text, List<Pii> pii, boolean outOfScope) { }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\[(EMAIL|TELEPHONE|LIEN)]");

    private static List<Item> corpus;
    private final PersonalDataAnonymizer anonymizer = new PersonalDataAnonymizer();

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = new ClassPathResource("evaluation/pii-corpus.json").getInputStream()) {
            corpus = new ObjectMapper().readValue(in, new TypeReference<>() { });
        }
    }

    private static final class Counts {
        int tp, fn, fp;

        double precision() { return tp + fp == 0 ? 1 : (double) tp / (tp + fp); }
        double recall() { return tp + fn == 0 ? 1 : (double) tp / (tp + fn); }
        double f1() { double p = precision(), r = recall(); return p + r == 0 ? 0 : 2 * p * r / (p + r); }
    }

    @Test
    void mesureLaPrecisionEtLeRappelDuMasquage() throws Exception {
        Map<String, Counts> byType = new TreeMap<>();
        Counts total = new Counts();
        int clean = 0, cleanAltered = 0, outOfScope = 0, outOfScopeMasked = 0;
        List<String> errors = new ArrayList<>();

        for (Item item : corpus) {
            String out = anonymizer.anonymize(item.text());
            if (item.outOfScope()) {
                for (Pii p : item.pii()) {
                    outOfScope++;
                    if (!out.contains(p.value())) outOfScopeMasked++;
                }
                continue;
            }
            if (item.pii().isEmpty()) {
                clean++;
                if (!out.equals(item.text())) {
                    cleanAltered++;
                    errors.add("sur-masquage : « " + item.text() + " » → « " + out + " »");
                }
            }
            // Vrais positifs / faux négatifs : la valeur a-t-elle disparu, remplacée par le bon type ?
            Map<String, Integer> placeholders = countPlaceholders(out);
            for (Pii p : item.pii()) {
                Counts c = byType.computeIfAbsent(p.type(), k -> new Counts());
                boolean masked = !out.contains(p.value()) && placeholders.getOrDefault(p.type(), 0) > 0;
                if (masked) {
                    c.tp++;
                    total.tp++;
                    placeholders.merge(p.type(), -1, Integer::sum);
                } else {
                    c.fn++;
                    total.fn++;
                    errors.add("non masqué (" + p.type() + ") : « " + item.text() + " » → « " + out + " »");
                }
            }
            // Faux positifs : marqueurs restants qui ne correspondent à aucune donnée annotée
            for (Map.Entry<String, Integer> e : placeholders.entrySet()) {
                if (e.getValue() > 0) {
                    byType.computeIfAbsent(e.getKey(), k -> new Counts()).fp += e.getValue();
                    total.fp += e.getValue();
                }
            }
        }

        writeReport(byType, total, clean, cleanAltered, outOfScope, outOfScopeMasked, errors);

        assertThat(errors).as("erreurs de masquage dans le périmètre").isEmpty();
        assertThat(total.recall()).isEqualTo(1.0);
        assertThat(total.precision()).isEqualTo(1.0);
        assertThat(cleanAltered).isZero();
    }

    private static Map<String, Integer> countPlaceholders(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) counts.merge(m.group(1), 1, Integer::sum);
        return counts;
    }

    private static void writeReport(Map<String, Counts> byType, Counts total, int clean, int cleanAltered,
                                    int outOfScope, int outOfScopeMasked, List<String> errors) throws Exception {
        StringBuilder md = new StringBuilder("# Évaluation du masquage des données personnelles\n\n")
                .append("Corpus annoté : ").append(corpus.size()).append(" textes de CV / d'offre.\n\n")
                .append("| Type | Vrais positifs | Faux négatifs | Faux positifs | Précision | Rappel | F1 |\n")
                .append("|---|---|---|---|---|---|---|\n");
        byType.forEach((type, c) -> row(md, type, c));
        row(md, "**Total**", total);
        md.append("\n- Textes sans donnée personnelle (dates, versions, montants) laissés intacts : ")
                .append(clean - cleanAltered).append("/").append(clean).append('\n')
                .append("- Hors périmètre (nom, adresse, date de naissance, identifiant) masqués : ")
                .append(outOfScopeMasked).append("/").append(outOfScope)
                .append(" — risque résiduel documenté dans le README.\n");
        if (!errors.isEmpty()) {
            md.append("\n## Erreurs\n\n");
            errors.forEach(e -> md.append("- ").append(e).append('\n'));
        }
        Path dir = Path.of("target", "evaluation");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("anonymisation.md"), md.toString(), StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder md, String type, Counts c) {
        md.append("| ").append(type).append(" | ").append(c.tp).append(" | ").append(c.fn).append(" | ").append(c.fp)
                .append(String.format(Locale.ROOT, " | %.2f | %.2f | %.2f |%n", c.precision(), c.recall(), c.f1()));
    }
}
