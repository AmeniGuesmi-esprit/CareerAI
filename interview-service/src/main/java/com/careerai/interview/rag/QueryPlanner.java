package com.careerai.interview.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QueryPlanner {

    public record QueryPlan(String track, String level, List<String> matchedSkills, List<String> gapSkills,
                            List<String> queries) { }

    private static final Map<String, List<String>> TRACK_KEYWORDS = Map.of(
            "backend-java-spring", List.of("java", "spring", "springboot", "jpa", "hibernate", "microservice",
                    "microservices", "eureka", "api", "rest", "maven", "backend", "sql", "docker", "junit"),
            "frontend-angular", List.of("angular", "typescript", "rxjs", "frontend", "front-end", "html", "css",
                    "component", "react", "vue", "javascript"));
    private static final String DEFAULT_TRACK = "backend-java-spring";
    private static final Pattern SENIOR = Pattern.compile("\\b(senior|lead|architecte|expert)\\b");
    private static final Pattern JUNIOR = Pattern.compile("\\b(stage|stagiaire|pfe|alternance|junior|debutant)\\b");
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*(?:ans?|years?)\\b");
    private static final String BEHAVIORAL_QUERY = "entretien comportemental situation tache action resultat STAR";

    public QueryPlan plan(String title, String jobDescription, String cvText, Set<String> vocabulary) {
        String jd = TextNorm.fold(title + " " + jobDescription);
        String cv = TextNorm.fold(cvText);
        String track = routeTrack(jd);
        String level = inferLevel(jd);

        List<String> matched = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        for (String tag : vocabulary.stream().sorted().toList()) {
            boolean inJd = contains(jd, tag);
            boolean inCv = contains(cv, tag);
            if (inJd && inCv && matched.size() < 12) {
                matched.add(tag);
            } else if (inJd && !inCv && gaps.size() < 8) {
                gaps.add(tag);
            }
        }

        List<String> queries = new ArrayList<>();
        queries.add(title + ". " + truncate(jobDescription, 500));
        if (!gaps.isEmpty()) {
            queries.add(String.join(" ", gaps));
        } else if (!matched.isEmpty()) {
            queries.add(String.join(" ", matched));
        }
        queries.add(BEHAVIORAL_QUERY);
        return new QueryPlan(track, level, matched, gaps, queries);
    }

    public static String routeTrack(String foldedText) {
        String best = DEFAULT_TRACK;
        int bestScore = 0;
        for (Map.Entry<String, List<String>> e : TRACK_KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : e.getValue()) {
                if (contains(foldedText, kw)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best;
    }

    static String inferLevel(String foldedText) {
        int maxYears = -1;
        Matcher m = YEARS.matcher(foldedText);
        while (m.find()) {
            maxYears = Math.max(maxYears, Integer.parseInt(m.group(1)));
        }
        if (SENIOR.matcher(foldedText).find() || maxYears >= 5) {
            return "senior";
        }
        if (JUNIOR.matcher(foldedText).find() || (maxYears >= 0 && maxYears < 2)) {
            return "junior";
        }
        return "confirme";
    }

    static boolean contains(String foldedText, String term) {
        Pattern p = Pattern.compile("(?<![a-z0-9+#])" + Pattern.quote(term) + "(?![a-z0-9+#])");
        return p.matcher(foldedText).find();
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }
}
