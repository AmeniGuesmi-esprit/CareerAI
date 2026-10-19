package com.careerai.skillgap.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Indique si une fiche de la base de connaissances porte sur une compétence que le CV démontre déjà.
 * Une fiche est « couverte » quand TOUTES ses technologies clés figurent dans le CV, sur une ligne
 * qui ne les présente pas comme un niveau débutant (« notions », « bases »...). Règle volontairement
 * prudente : en cas de doute (technologie partielle ou niveau débutant), la fiche est conservée.
 */
final class CvCoverage {

    private static final Pattern PARENS = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern SPLIT = Pattern.compile(",|/|\\bet\\b|\\band\\b");
    private static final Pattern LOW_LEVEL = Pattern.compile(
            "\\b(notions?|bases?|debutant|basique|basic|beginner|apprentissage|learning|en cours)\\b");

    private final List<String> lines;

    CvCoverage(String cvText) {
        this.lines = Arrays.stream((cvText == null ? "" : cvText).split("\\R"))
                .map(CvCoverage::normalize)
                .filter(l -> !l.isBlank())
                .toList();
    }

    /** true si le CV démontre déjà toutes les technologies clés de la fiche. */
    boolean covers(String skillTitle) {
        Set<String> terms = keyTerms(skillTitle);
        return !terms.isEmpty() && terms.stream().allMatch(this::mastered);
    }

    /** Technologies clés : contenu des parenthèses s'il y en a, sinon le titre (séparé par , / et). */
    static Set<String> keyTerms(String title) {
        Matcher m = PARENS.matcher(title);
        String source = normalize(m.find() ? m.group(1) : title);
        return SPLIT.splitAsStream(source)
                .map(String::strip)
                .map(t -> t.startsWith("apache ") ? t.substring(7) : t)
                .filter(t -> t.length() >= 2)
                .collect(Collectors.toSet());
    }

    private boolean mastered(String term) {
        Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])");
        return lines.stream().anyMatch(l -> p.matcher(l).find() && !LOW_LEVEL.matcher(l).find());
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
