package com.careerai.competence.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masque les données personnelles d'un prompt avant son envoi à Gemini : le prompt est écrit librement
 * par l'utilisateur, qui peut y laisser son e-mail, son téléphone ou son nom.
 * Seul le parcours (formation, compétences, objectif) est utile au conseil : le reste est remplacé
 * par une étiquette ([EMAIL], [TÉLÉPHONE]…) pour que la phrase garde son sens.
 *
 * Limites assumées : un nom écrit sans formule d'introduction (« je m'appelle », « nom : »…)
 * n'est pas détecté ; c'est pourquoi la page rappelle aussi de ne pas écrire de données sensibles.
 */
public final class PersonalDataMasker {

    private PersonalDataMasker() {}

    /** Texte masqué et nombre de remplacements (journalisé, jamais le contenu). */
    public record Result(String text, int count) {}

    private record Rule(Pattern pattern, String replacement) {}

    private static final String NAME_WORD = "[A-ZÀ-ÖØ-Ý][\\p{L}'’-]+";

    private static final List<Rule> RULES = List.of(
            // e-mails
            new Rule(Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"), "[EMAIL]"),
            // liens (profil LinkedIn, GitHub, site perso…)
            new Rule(Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+|\\b(?:linkedin\\.com|github\\.com)/\\S+"), "[LIEN]"),
            // IBAN, puis numéro de carte bancaire
            new Rule(Pattern.compile("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}(?:\\s?[A-Z0-9]{1,3})?\\b"), "[IBAN]"),
            new Rule(Pattern.compile("(?<![\\d])(?:\\d{4}[\\s-]?){3}\\d{4}(?![\\d])"), "[CARTE]"),
            // pièce d'identité : « CIN 12345678 », « passeport : X1234567 »
            new Rule(Pattern.compile("(?i)\\b(cin|carte d'identit[ée]|passeport|n° de s[ée]curit[ée] sociale)\\s*(?:n°|:)?\\s*[A-Z0-9]{6,15}\\b"),
                    "$1 [IDENTIFIANT]"),
            // téléphones internationaux : +216 22 123 456, 0033 6 12 34 56 78
            new Rule(Pattern.compile("(?<![\\w.])(?:\\+|00)\\d{1,3}(?:[\\s.-]?\\d){7,12}(?!\\d)"), "[TÉLÉPHONE]"),
            // téléphones français : 06 12 34 56 78
            new Rule(Pattern.compile("(?<![\\w.])0[1-9](?:[\\s.-]?\\d{2}){4}(?!\\d)"), "[TÉLÉPHONE]"),
            // téléphones tunisiens : 22 123 456, 98123456
            new Rule(Pattern.compile("(?<![\\w.,])[2-9]\\d(?:[\\s.]?\\d{3}){2}(?![\\d.,])"), "[TÉLÉPHONE]"),
            // date de naissance
            new Rule(Pattern.compile("(?i)\\b(n[ée]e?\\s+le)\\s+\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}"), "$1 [DATE]"),
            // adresse postale : « 12 rue de Carthage », « avenue Habib Bourguiba »
            new Rule(Pattern.compile("(?i)\\b(?:\\d{1,4}\\s*(?:bis|ter)?,?\\s*)?(?:rue|avenue|av\\.|boulevard|bd|impasse)\\s+[^,.;\\n]{2,60}"),
                    "[ADRESSE]"),
            // nom introduit par une formule : « je m'appelle Karim Mansouri », « nom : Ben Ali »
            new Rule(Pattern.compile("((?i:je m'appelle|je m’appelle|je me nomme|mon nom est|mon nom c'est"
                    + "|nom et pr[ée]nom\\s*:|pr[ée]nom\\s*:|nom\\s*:))\\s*" + NAME_WORD + "(?:\\s+" + NAME_WORD + "){0,2}"),
                    "$1 [NOM]")
    );

    public static Result mask(String text) {
        if (text == null || text.isEmpty()) return new Result(text == null ? "" : text, 0);
        String out = text;
        int count = 0;
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                count++;
                m.appendReplacement(sb, rule.replacement());
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return new Result(out, count);
    }
}
