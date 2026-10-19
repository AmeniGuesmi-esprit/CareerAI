package com.careerai.interview.rag;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextNorm {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private TextNorm() { }

    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static String slug(String s) {
        String slug = NON_ALNUM.matcher(fold(s)).replaceAll("-");
        return slug.replaceAll("^-+|-+$", "");
    }
}
