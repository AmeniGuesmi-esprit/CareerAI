package com.careerai.interview.cv;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PersonalDataAnonymizer {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.|(?:linkedin|github|gitlab)\\.com/)\\S+");
    private static final Pattern PHONE_CANDIDATE = Pattern.compile("\\+?\\d[\\d\\s().-]{6,}\\d");
    private static final Pattern DATE_RANGE = Pattern.compile("^\\d{4}\\s*[-–]\\s*\\d{4}$");

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
            boolean dateRange = DATE_RANGE.matcher(candidate.trim()).matches();
            m.appendReplacement(sb, Matcher.quoteReplacement(digits >= 8 && !dateRange ? "[TELEPHONE]" : candidate));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
