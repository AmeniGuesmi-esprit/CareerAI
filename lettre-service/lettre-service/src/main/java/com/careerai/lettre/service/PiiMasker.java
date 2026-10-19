package com.careerai.lettre.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protège les données personnelles avant l'envoi au LLM :
 *  - e-mail, téléphone, URL : remplacés par des jetons [[EMAIL_1]], [[PHONE_1]]...
 *  - nom du candidat et adresse postale : SUPPRIMÉS du texte (méthode {@link #anonymizeCv}).
 * L'analyse d'écart de compétences n'a besoin ni du nom ni de l'adresse.
 */
@Component
public class PiiMasker {

    public record Masked(String text, Map<String, String> tokens) {}

    private static final Pattern EMAIL = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>\"]+|\\b(?:linkedin|github)\\.com/[^\\s<>\"]+");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])\\+?\\d[\\d ().-]{6,}\\d");
    private static final Pattern YEAR_RANGE = Pattern.compile("(19|20)\\d{2}\\s*[-–]\\s*(19|20)\\d{2}");

    private static final Set<String> SECTION_WORDS = Set.of(
            "cv", "curriculum", "vitae", "resume", "résumé", "profil", "profile", "profil professionnel",
            "expérience", "experience", "expériences", "formation", "education", "compétences", "competences",
            "skills", "projets", "projects", "langues", "languages", "contact", "contacts", "ingénieur",
            "ingenieur", "développeur", "developpeur", "developer", "engineer", "stagiaire", "étudiant", "etudiant");

    private static final Pattern NAME_LABEL = Pattern.compile(
            "(?im)^[ \\t]*(?:nom(?:\\s+et\\s+pr[ée]nom)?|pr[ée]nom|name|full\\s*name)[ \\t]*:[ \\t]*(.+)$");
    private static final Pattern NAME_LINE = Pattern.compile(
            "^\\p{Lu}[\\p{L}'’-]+(?:\\s+\\p{L}[\\p{L}'’.-]*){1,3}$");
    private static final Pattern ADDRESS_LABEL = Pattern.compile(
            "(?i)^\\s*(?:adresse|address|domicile|localisation|location|habite|r[ée]sidence)\\s*:.*$");
    private static final String STREET_WORDS =
            "rue|avenue|av\\.?|boulevard|bd|impasse|route|cit[ée]|r[ée]sidence|lotissement|street|st\\.|road|rd\\.|lane|drive";
    private static final Pattern STREET_NUMBERED = Pattern.compile(
            "(?i).*(?:\\b\\d{1,4}\\s*(?:bis|ter)?\\s*,?\\s*(?:" + STREET_WORDS + ")\\b|\\b(?:" + STREET_WORDS
                    + ")\\s+[\\p{L}'’. -]{2,40},?\\s*\\d{1,4}\\b).*");
    /** Code postal hors plage d'années (ex. 1002 Tunis, 75001 Paris) + ville, sur une ligne qui ne contient que cela. */
    private static final Pattern POSTAL_CITY = Pattern.compile(
            "^[\\s,|•·-]*(?:[A-Za-zÀ-ÿ'’. -]+,\\s*)?(?:(?!19|20)\\d{4}|\\d{5})\\s+\\p{Lu}[\\p{L}'’-]+(?:\\s+\\p{L}[\\p{L}'’-]*){0,2}"
                    + "(?:,\\s*[\\p{L} ]{2,30})?[\\s,|•·-]*$");
    /** Code postal dans la plage 1900-2099 (ex. 2080 Ariana) : exige un pays derrière pour ne pas confondre avec une année. */
    private static final Pattern POSTAL_CITY_COUNTRY = Pattern.compile(
            "^[\\s,|•·-]*(?:[A-Za-zÀ-ÿ'’. -]+,\\s*)?(?:19|20)\\d{2}\\s+\\p{Lu}[\\p{L}'’-]+(?:\\s+\\p{L}[\\p{L}'’-]*){0,2}"
                    + ",\\s*(?i:tunisie|tunisia|france|maroc|alg[ée]rie|belgique|canada|suisse)[\\s,|•·-]*$");

    /**
     * Prépare le texte d'un CV pour le LLM : jetons pour e-mail/téléphone/URL puis suppression
     * du nom du candidat (toutes ses occurrences) et de l'adresse postale.
     */
    public String anonymizeCv(String cvText) {
        if (cvText == null) return "";
        String text = mask(cvText).text();

        // 1) Nom : lignes « Nom : ... » + première ligne de l'en-tête qui ressemble à un nom
        Set<String> names = new LinkedHashSet<>();
        Matcher lm = NAME_LABEL.matcher(text);
        while (lm.find()) names.add(lm.group(1).trim());
        String[] lines = text.split("\\R", -1);
        int seen = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (++seen > 5) break;
            if (looksLikeName(t)) { names.add(t); break; }
        }
        for (String name : names) {
            for (String part : nameVariants(name)) {
                text = Pattern.compile("(?i)(?<![\\p{L}])" + Pattern.quote(part) + "(?![\\p{L}])")
                        .matcher(text).replaceAll("");
            }
        }

        // 2) Adresse postale : suppression des lignes concernées
        StringBuilder sb = new StringBuilder();
        int nonBlank = 0;
        for (String line : text.split("\\R", -1)) {
            String t = line.trim();
            if (!t.isEmpty()) nonBlank++;
            boolean address = ADDRESS_LABEL.matcher(t).matches()
                    || (t.length() <= 120 && STREET_NUMBERED.matcher(t).matches())
                    || (nonBlank <= 15 && t.length() <= 70
                    && (POSTAL_CITY.matcher(t).matches() || POSTAL_CITY_COUNTRY.matcher(t).matches()));
            if (!address) sb.append(line).append('\n');
        }
        return sb.toString().replaceAll("(?m)^[ \\t|•·,-]+$", "").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static boolean looksLikeName(String t) {
        if (t.length() > 40 || t.chars().anyMatch(Character::isDigit) || t.contains("@") || t.contains("[[")) return false;
        if (!NAME_LINE.matcher(t).matches()) return false;
        return Arrays.stream(t.toLowerCase(Locale.ROOT).split("\\s+")).noneMatch(SECTION_WORDS::contains)
                && !SECTION_WORDS.contains(t.toLowerCase(Locale.ROOT));
    }

    /** Nom complet puis chaque mot de 3+ lettres (prénom, nom) pour retirer les occurrences isolées. */
    private static List<String> nameVariants(String name) {
        List<String> out = new ArrayList<>();
        out.add(name.trim());
        for (String w : name.trim().split("\\s+")) {
            if (w.length() >= 3 && !SECTION_WORDS.contains(w.toLowerCase(Locale.ROOT))) out.add(w);
        }
        return out;
    }

    public Masked mask(String input) {
        Map<String, String> tokens = new LinkedHashMap<>();
        String out = replace(input, EMAIL, "EMAIL", tokens, s -> true);
        out = replace(out, URL, "URL", tokens, s -> true);
        out = replace(out, PHONE, "PHONE", tokens, PiiMasker::looksLikePhone);
        return new Masked(out, tokens);
    }

    public String restore(String text, Map<String, String> tokens) {
        String out = text;
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /** true si au moins un jeton a disparu du texte. */
    public boolean hasMissingToken(String text, Map<String, String> tokens) {
        return tokens.keySet().stream().anyMatch(t -> !text.contains(t));
    }

    private static boolean looksLikePhone(String value) {
        long digits = value.chars().filter(Character::isDigit).count();
        return digits >= 8 && digits <= 15 && !YEAR_RANGE.matcher(value).matches();
    }

    private static String replace(String input, Pattern pattern, String type,
                                  Map<String, String> tokens, Predicate<String> accept) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find()) {
            String value = m.group();
            if (!accept.test(value)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
                continue;
            }
            String token = "[[" + type + "_" + (++n) + "]]";
            tokens.put(token, value);
            m.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}