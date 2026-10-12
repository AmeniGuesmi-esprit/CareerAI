package com.careerai.skillgap.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remplace les données de contact (email, téléphone, URL) par des jetons [[EMAIL_1]], [[PHONE_1]]...
 * avant l'envoi au LLM, (le LLM ne voit jamais les coordonnees du candidat).
 * Le nom et l'adresse postale ne sont pas masqués (ils font partie du texte à traduire).
 */
@Component
public class PiiMasker {

    public record Masked(String text, Map<String, String> tokens) {}

    private static final Pattern EMAIL = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>\"]+|\\b(?:linkedin|github)\\.com/[^\\s<>\"]+");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])\\+?\\d[\\d ().-]{6,}\\d");
    private static final Pattern YEAR_RANGE = Pattern.compile("(19|20)\\d{2}\\s*[-–]\\s*(19|20)\\d{2}");

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
