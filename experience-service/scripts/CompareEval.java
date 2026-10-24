import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compare les resultats de plusieurs executions de PitchEvaluationTest (un fichier results-*.csv par modele ou variante).
 *
 * Lancement (JDK 17, aucune dependance) :
 *   java scripts/CompareEval.java target/eval
 *   java scripts/CompareEval.java target/eval gemini-2.5-flash-lite=0.10,0.40 gemini-3.5-flash-lite=0.10,0.40
 *
 * Si les executions n'ont pas les memes cas (ex. evaluation partielle), seuls les cas communs sont compares.
 * Arguments : dossier des resultats, puis (facultatif) des prix "nom=entree,sortie" en USD par million de jetons.
 * "nom" est comparé au nom du fichier (results-NOM.csv). Le cout est recalcule ici depuis les jetons :
 * pas besoin de relancer l'evaluation pour changer de tarif. Sans prix, la colonne cout est "n/a".
 * Ecrit aussi comparaison.md dans le dossier.
 */
public class CompareEval {

    private record Row(String id, String outcome, boolean conforme, long latencyMs, int calls,
                       int promptTokens, int outputTokens, int thoughtTokens, int factAnomalies, int lengthAnomalies) {
    }

    private record Run(String name, List<Row> rows) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage : java scripts/CompareEval.java <dossier> [nom=prixEntree,prixSortie ...]");
            System.exit(1);
        }
        Path dir = Path.of(args[0]);
        List<String[]> prices = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            String[] p = kv[1].split(",");
            prices.add(new String[]{kv[0], p[0], p[1]});
        }

        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(f -> f.getFileName().toString().startsWith("results-")
                    && f.getFileName().toString().endsWith(".csv")).sorted().collect(Collectors.toList());
        }
        if (files.isEmpty()) {
            System.err.println("Aucun results-*.csv dans " + dir);
            System.exit(1);
        }

        List<Run> runs = new ArrayList<>();
        for (Path f : files) {
            String n = f.getFileName().toString();
            runs.add(new Run(n.substring("results-".length(), n.length() - ".csv".length()), readRows(f)));
        }

        // comparaison equitable : on ne garde que les cas presents dans TOUTES les executions
        java.util.Set<String> common = null;
        for (Run r : runs) {
            java.util.Set<String> ids = r.rows().stream().map(Row::id).collect(Collectors.toSet());
            if (common == null) {
                common = new java.util.HashSet<>(ids);
            } else {
                common.retainAll(ids);
            }
        }
        final java.util.Set<String> commonIds = common;
        List<Run> restricted = new ArrayList<>();
        for (Run r : runs) {
            restricted.add(new Run(r.name(), r.rows().stream().filter(x -> commonIds.contains(x.id())).collect(Collectors.toList())));
        }
        runs = restricted;

        StringBuilder sb = new StringBuilder();
        sb.append("Comparaison sur les ").append(commonIds.size()).append(" cas communs a toutes les executions.\n\n");
        sb.append("| Indicateur |");
        for (Run r : runs) {
            sb.append(' ').append(r.name()).append(" |");
        }
        sb.append("\n|---|").append("---|".repeat(runs.size())).append('\n');

        line(sb, "Cas evalues", runs, r -> String.valueOf(r.rows().size()));
        line(sb, "Conformes (comportement attendu)", runs, r -> pct(count(r, Row::conforme), r.rows().size()));
        line(sb, "Succes au premier essai", runs, r -> pct(count(r, x -> x.outcome().equals("OK_PREMIER_ESSAI")), treated(r)));
        line(sb, "Succes apres retry", runs, r -> pct(count(r, x -> x.outcome().equals("OK_APRES_RETRY")), treated(r)));
        line(sb, "Taux de retry (parmi les reussites)", runs, r -> pct(count(r, x -> x.outcome().equals("OK_APRES_RETRY")),
                count(r, x -> x.outcome().startsWith("OK"))));
        line(sb, "Echec pitch (juge)", runs, r -> pct(count(r, x -> x.outcome().equals("ECHEC_PITCH")), treated(r)));
        line(sb, "Echec API", runs, r -> pct(count(r, x -> x.outcome().equals("ECHEC_API")), treated(r)));
        line(sb, "Autre erreur", runs, r -> pct(count(r, x -> x.outcome().equals("ERREUR_AUTRE")), treated(r)));
        line(sb, "Latence mediane par pitch reussi (ms)", runs, r -> String.valueOf(median(success(r), Row::latencyMs)));
        line(sb, "Latence moyenne par appel LLM (ms) *", runs, r -> String.format(Locale.ROOT, "%.0f", latencyPerCall(r)));
        line(sb, "Appels LLM par pitch reussi", runs, r -> String.format(Locale.ROOT, "%.1f", mean(success(r), x -> x.calls())));
        line(sb, "Jetons par pitch reussi (total)", runs, r -> String.format(Locale.ROOT, "%.0f",
                mean(success(r), x -> x.promptTokens() + x.outputTokens() + x.thoughtTokens())));
        line(sb, "Anomalies de contenu / de longueur (pitchs)", runs, r -> count(success(r), x -> x.factAnomalies() > 0)
                + " / " + count(success(r), x -> x.lengthAnomalies() > 0));
        line(sb, "Cout estime par pitch reussi (USD)", runs, r -> {
            String[] price = priceFor(r.name(), prices);
            if (price == null) {
                return "n/a";
            }
            double in = Double.parseDouble(price[1]);
            double out = Double.parseDouble(price[2]);
            // cout de tous les appels (y compris les echecs) rapporte au nombre de pitchs reussis
            double total = r.rows().stream().mapToDouble(x -> x.promptTokens() * in / 1e6
                    + (x.outputTokens() + x.thoughtTokens()) * out / 1e6).sum();
            long ok = count(r, x -> x.outcome().startsWith("OK"));
            return ok == 0 ? "n/a" : String.format(Locale.ROOT, "%.5f", total / ok);
        });

        sb.append("\n\\* latence totale du cas divisee par le nombre d'appels ; inclut les retries reseau et les timeouts, ")
                .append("donc sensible a la charge de l'API (a relancer plusieurs fois pour lisser le bruit).\n");

        System.out.println(sb);
        Files.writeString(dir.resolve("comparaison.md"), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Ecrit : " + dir.resolve("comparaison.md").toAbsolutePath());
    }

    // ---------- lecture ----------

    private static List<Row> readRows(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] c = lines.get(i).split(";", -1);
            rows.add(new Row(c[0], c[2], Boolean.parseBoolean(c[3]), Long.parseLong(c[4]), Integer.parseInt(c[5]),
                    Integer.parseInt(c[7]), Integer.parseInt(c[8]), Integer.parseInt(c[9]),
                    Integer.parseInt(c[12]), Integer.parseInt(c[13])));
        }
        return rows;
    }

    // ---------- statistiques ----------

    private interface Pred {
        boolean test(Row r);
    }

    private interface ToNum {
        double apply(Row r);
    }

    private static long count(Run r, Pred p) {
        return count(r.rows(), p);
    }

    private static long count(List<Row> rows, Pred p) {
        return rows.stream().filter(p::test).count();
    }

    /** Cas a traiter : tous sauf ceux dont le rejet est le comportement attendu (ENTREE_REJETEE conforme). */
    private static long treated(Run r) {
        return r.rows().stream().filter(x -> !(x.outcome().equals("ENTREE_REJETEE") && x.conforme())).count();
    }

    private static List<Row> success(Run r) {
        return r.rows().stream().filter(x -> x.outcome().startsWith("OK")).collect(Collectors.toList());
    }

    private static double mean(List<Row> rows, ToNum f) {
        return rows.stream().mapToDouble(f::apply).average().orElse(0);
    }

    private static long median(List<Row> rows, ToNum f) {
        List<Long> v = rows.stream().map(x -> (long) f.apply(x)).sorted().collect(Collectors.toList());
        return v.isEmpty() ? 0 : v.get((v.size() - 1) / 2);
    }

    private static double latencyPerCall(Run r) {
        long calls = r.rows().stream().mapToLong(Row::calls).sum();
        long ms = r.rows().stream().filter(x -> x.calls() > 0).mapToLong(Row::latencyMs).sum();
        return calls == 0 ? 0 : (double) ms / calls;
    }

    private static String pct(long part, long whole) {
        return whole == 0 ? "n/a" : String.format(Locale.ROOT, "%.0f %% (%d/%d)", 100.0 * part / whole, part, whole);
    }

    private static String[] priceFor(String runName, List<String[]> prices) {
        for (String[] p : prices) {
            if (runName.equals(p[0]) || runName.startsWith(p[0] + "__")) {
                return p;
            }
        }
        return null;
    }

    private interface Cell {
        String get(Run r);
    }

    private static void line(StringBuilder sb, String label, List<Run> runs, Cell cell) {
        sb.append("| ").append(label).append(" |");
        for (Run r : runs) {
            sb.append(' ').append(cell.get(r)).append(" |");
        }
        sb.append('\n');
    }
}
