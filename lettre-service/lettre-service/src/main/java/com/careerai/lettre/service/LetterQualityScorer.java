package com.careerai.lettre.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Évaluation chiffrée AUTOMATIQUE d'une lettre générée, reproductible et sans LLM.
 *
 * <p>Elle vérifie les contraintes du prompt de génération (longueur, structure, objet, signature,
 * absence de markdown et de coordonnées) et détecte deux formes d'hallucination mesurables :
 * <ul>
 *   <li>une compétence technique citée dans la lettre mais absente du CV ;</li>
 *   <li>un nombre cité dans la lettre mais absent du CV et de l'offre.</li>
 * </ul>
 * Elle ne juge ni le style ni la persuasion : ces critères restent notés par des humains
 * (grille 1-5 de docs/evaluation-ia.md). Le score est un indicateur, pas une vérité absolue.
 */
public final class LetterQualityScorer {

    /** Bornes imposées par le prompt de génération. */
    public static final int MIN_WORDS = 250;
    public static final int MAX_WORDS = 350;

    /**
     * Lexique de compétences vérifiables. Une entrée qui commence par une majuscule est recherchée en
     * respectant la casse (« React », « Go », « Swift »... sont aussi des mots courants).
     */
    static final List<String> SKILLS = List.of(
            "java", "python", "javascript", "typescript", "kotlin", "scala", "php", "c++", "c#", ".net",
            "Go", "Rust", "Swift", "ruby", "dart", "matlab", "r studio",
            "spring boot", "spring cloud", "hibernate", "django", "flask", "fastapi", "laravel", "symfony",
            "node.js", "nodejs", "Express.js", "React", "react native", "angular", "vue.js", "vuejs", "next.js",
            "flutter", "android", "ios",
            "sql", "mysql", "postgresql", "oracle", "mongodb", "redis", "elasticsearch", "cassandra",
            "kafka", "rabbitmq", "graphql", "microservices", "microservice",
            "docker", "kubernetes", "openshift", "terraform", "ansible", "jenkins", "gitlab ci", "github actions",
            "ci/cd", "aws", "azure", "gcp", "google cloud", "linux",
            "git", "jira", "scrum", "kanban", "sonarqube", "junit", "selenium", "cypress", "postman",
            "tensorflow", "pytorch", "scikit-learn", "pandas", "machine learning", "deep learning", "nlp",
            "power bi", "tableau", "hadoop", "Spark", "airflow",
            "figma", "photoshop", "illustrator", "seo", "google ads", "google analytics", "hubspot", "salesforce",
            "sap", "Sage", "Excel", "ifrs", "comptabilité analytique", "paie");

    private static final Pattern EMAIL = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])\\+?\\d[\\d ().-]{6,}\\d");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}_])\\d+(?:[.,]\\d+)?(?![\\p{L}\\p{N}_])");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*");
    private static final Pattern MARKDOWN = Pattern.compile("(?m)\\*\\*|__|```|^\\s{0,3}#{1,6}\\s|^\\s*[-*]\\s+\\S");

    /** Résultat détaillé. Les pourcentages sont entre 0 et 100. */
    public record Report(
            int words,
            int paragraphs,
            Map<String, Boolean> checks,
            double constraintScore,
            double offerCoverage,
            List<String> expectedSkills,
            List<String> unsupportedSkills,
            List<String> unsupportedNumbers,
            boolean hallucinationSuspected,
            double autoScore) {

        public int passedChecks() {
            return (int) checks.values().stream().filter(Boolean::booleanValue).count();
        }
    }

    private LetterQualityScorer() {}

    /**
     * @param letter     lettre produite (déjà nettoyée)
     * @param cv         CV fourni (texte brut)
     * @param offer      offre d'emploi
     * @param company    nom de l'entreprise
     * @param position   poste (peut être null)
     * @param language   "fr" ou "en"
     */
    public static Report evaluate(String letter, String cv, String offer, String company, String position, String language) {
        String text = letter == null ? "" : letter.strip();
        String cvText = cv == null ? "" : cv;
        String offerText = offer == null ? "" : offer;
        String context = cvText + "\n" + offerText + "\n" + (company == null ? "" : company) + "\n"
                + (position == null ? "" : position);

        int words = countWords(text);
        int paragraphs = countBodyParagraphs(text);
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("").strip();
        String lowerFirst = firstLine.toLowerCase(Locale.ROOT);

        Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("longueur_250_350_mots", words >= MIN_WORDS && words <= MAX_WORDS);
        checks.put("structure_3_4_paragraphes", paragraphs >= 3 && paragraphs <= 4);
        checks.put("ligne_objet", lowerFirst.startsWith("objet") || lowerFirst.startsWith("subject")
                || lowerFirst.startsWith("re:"));
        checks.put("signature_a_completer", text.contains("[Prénom Nom]"));
        checks.put("sans_markdown", !MARKDOWN.matcher(text).find());
        checks.put("sans_jeton_technique", !text.contains("[[") && !text.contains("]]"));
        checks.put("sans_coordonnees", !EMAIL.matcher(text).find() && !PHONE.matcher(text).find());
        checks.put("entreprise_citee", company == null || company.isBlank()
                || text.toLowerCase(Locale.ROOT).contains(company.strip().toLowerCase(Locale.ROOT)));
        checks.put("langue_respectee", languageMatches(text, language));

        long passed = checks.values().stream().filter(Boolean::booleanValue).count();
        double constraintScore = round1(passed * 100.0 / checks.size());

        // Compétences de l'offre que le CV couvre réellement : la lettre devrait les citer.
        Set<String> inCv = skillsIn(cvText);
        Set<String> inOffer = skillsIn(offerText);
        Set<String> inLetter = skillsIn(text);
        List<String> expected = new ArrayList<>();
        for (String s : inOffer) if (inCv.contains(s)) expected.add(s);
        long covered = expected.stream().filter(inLetter::contains).count();
        double coverage = expected.isEmpty() ? 100.0 : round1(covered * 100.0 / expected.size());

        // Hallucinations mesurables : compétence absente du CV, nombre absent du CV et de l'offre.
        List<String> unsupportedSkills = new ArrayList<>();
        for (String s : inLetter) if (!inCv.contains(s)) unsupportedSkills.add(s);
        Set<String> contextNumbers = numbersIn(context);
        List<String> unsupportedNumbers = new ArrayList<>();
        for (String n : numbersIn(text)) if (!contextNumbers.contains(n)) unsupportedNumbers.add(n);
        boolean hallucination = !unsupportedSkills.isEmpty() || !unsupportedNumbers.isEmpty();

        double autoScore = round1(0.6 * constraintScore + 0.2 * coverage + 0.2 * (hallucination ? 0 : 100));
        return new Report(words, paragraphs, checks, constraintScore, coverage, expected,
                unsupportedSkills, unsupportedNumbers, hallucination, autoScore);
    }

    // ───────────────────────── Utilitaires ─────────────────────────

    static int countWords(String text) {
        Matcher m = WORD.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /**
     * Paragraphes « de corps » : blocs séparés par une ligne vide, d'au moins 25 mots. L'objet, la formule
     * d'appel, une formule de politesse courte et la signature ne comptent donc pas.
     */
    static int countBodyParagraphs(String text) {
        int n = 0;
        for (String block : text.split("\\R\\s*\\R")) {
            if (countWords(block) >= 25) n++;
        }
        return n;
    }

    static Set<String> skillsIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String skill : SKILLS) {
            boolean caseSensitive = Character.isUpperCase(skill.charAt(0));
            String haystack = caseSensitive ? text : lower;
            String needle = caseSensitive ? skill : skill.toLowerCase(Locale.ROOT);
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}+#])");
            if (p.matcher(haystack).find()) found.add(skill.toLowerCase(Locale.ROOT));
        }
        // « spring boot » couvre déjà « spring » ; « nodejs » et « node.js » sont la même compétence
        if (found.remove("nodejs")) found.add("node.js");
        if (found.remove("vuejs")) found.add("vue.js");
        if (found.remove("microservice")) found.add("microservices");
        return found;
    }

    static Set<String> numbersIn(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace(',', '.'));
        return out;
    }

    /** Heuristique simple : proportion de mots-outils français vs anglais. */
    static boolean languageMatches(String text, String language) {
        String lower = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]+", " ") + " ";
        int fr = 0, en = 0;
        for (String w : List.of(" le ", " la ", " les ", " des ", " et ", " je ", " votre ", " pour ", " dans ", " une "))
            fr += occurrences(lower, w);
        for (String w : List.of(" the ", " and ", " i ", " your ", " for ", " with ", " my ", " to ", " of ", " a "))
            en += occurrences(lower, w);
        return "en".equals(language) ? en > fr : fr > en;
    }

    private static int occurrences(String s, String w) {
        int n = 0;
        for (int i = s.indexOf(w); i >= 0; i = s.indexOf(w, i + 1)) n++;
        return n;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
