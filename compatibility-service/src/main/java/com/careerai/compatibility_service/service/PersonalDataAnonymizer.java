package com.careerai.compatibility_service.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masque les données personnelles avant tout envoi au LLM. */
@Component
public class PersonalDataAnonymizer {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.|(?:linkedin|github|gitlab)\\.com/)\\S+");
    private static final Pattern PHONE_CANDIDATE = Pattern.compile("\\+?\\d[\\d\\s().-]{6,}\\d");
    /** Une date de CV : 2022, 01.2022, 01/2022, 15/03/2022 ou 2022-01 (année plausible 19xx / 20xx). */
    private static final String DATE = "(?:(?:\\d{1,2}[./-]){0,2}(?:19|20)\\d{2}|(?:19|20)\\d{2}(?:[./-]\\d{1,2}){1,2})";
    /** Une date seule ou une période « date - date » : jamais un numéro de téléphone. */
    private static final Pattern DATE_PERIOD = Pattern.compile("^" + DATE + "(?:\\s*[-–]\\s*" + DATE + ")?$");

    public String anonymize(String text) {
        if (text == null) {
            return "";
        }
        String result = EMAIL.matcher(text).replaceAll("[EMAIL]");
        result = URL.matcher(result).replaceAll("[LIEN]");
        return maskPhones(result);
    }

    private String maskPhones(String text) {
        Matcher m = PHONE_CANDIDATE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String candidate = m.group();
            int digits = candidate.replaceAll("\\D", "").length();
            // "2021 - 2023" ou "01.2022 - 06.2022" sont des périodes, pas des numéros
            boolean date = DATE_PERIOD.matcher(candidate.trim()).matches();
            m.appendReplacement(sb, Matcher.quoteReplacement(digits >= 8 && !date ? "[TELEPHONE]" : candidate));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}