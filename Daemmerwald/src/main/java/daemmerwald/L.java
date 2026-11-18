package daemmerwald;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprachen: Deutsch (Original), Englisch, Portugiesisch (Portugal).
 * Die Übersetzungen stehen in assets/lang/i18n.tsv (Deutsch, Englisch, Portugiesisch je Zeile).
 * Übersetzt wird beim Anzeigen: ganze Sätze direkt, zusammengesetzte Texte Stück für Stück.
 */
public final class L {
    private L() { }

    public static volatile String lang = "de";
    private static final Map<String, String[]> table = new HashMap<>();
    private static List<String> keysByLength = new ArrayList<>();
    private static final Map<String, String> cache = new HashMap<>();
    private static final Path SETTINGS = Mixamo.DIR.getParent().resolve("sprache.txt");

    static {
        try (InputStream in = L.class.getResourceAsStream("/lang/i18n.tsv")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t", -1);
                    if (p.length < 3 || p[0].isEmpty()) continue;
                    table.put(unescape(p[0]), new String[]{unescape(p[1]), unescape(p[2])});
                }
            }
        } catch (Exception e) {
            System.err.println("Übersetzungen nicht lesbar: " + e.getMessage());
        }
        keysByLength = new ArrayList<>(table.keySet());
        keysByLength.removeIf(k -> k.trim().length() < 3);
        keysByLength.sort((a, b) -> b.length() - a.length());
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case '"' -> b.append('"');
                    case '\\' -> b.append('\\');
                    default -> b.append('\\').append(n);
                }
            } else b.append(c);
        }
        return b.toString();
    }

    /** Gibt es schon Übersetzungen? (Sonst nur Deutsch anbieten.) */
    public static boolean hasTranslations() { return !table.isEmpty(); }

    /** Gespeicherte Sprache vom letzten Spiel (für das Titelbild). */
    public static void loadSetting() {
        try {
            if (Files.exists(SETTINGS)) set(Files.readString(SETTINGS).trim());
        } catch (Exception ignored) { }
    }

    public static void set(String l) {
        if (l == null || !(l.equals("de") || l.equals("en") || l.equals("pt")) || table.isEmpty()) l = "de";
        if (!l.equals(lang)) synchronized (cache) { cache.clear(); }
        lang = l;
        Voice.lang = l;
        try {
            Files.createDirectories(SETTINGS.getParent());
            Files.writeString(SETTINGS, l);
        } catch (Exception ignored) { }
    }

    /** Übersetzt einen deutschen Text in die gewählte Sprache. */
    public static String t(String s) {
        if (s == null || s.isEmpty() || lang.equals("de")) return s;
        int col = lang.equals("en") ? 0 : 1;
        synchronized (cache) {
            String c = cache.get(s);
            if (c != null) return c;
        }
        String out;
        String[] e = table.get(s);
        if (e != null && !e[col].isEmpty()) out = e[col];
        else out = pieces(s, col);
        synchronized (cache) {
            if (cache.size() > 30000) cache.clear();
            cache.put(s, out);
        }
        return out;
    }

    /** Zusammengesetzte Texte: bekannte Stücke (längste zuerst) einzeln ersetzen. */
    private static String pieces(String s, int col) {
        if (s.length() < 3) return s;
        boolean[] used = new boolean[s.length()];
        String[] rep = new String[s.length()];
        int[] len = new int[s.length()];
        boolean any = false;
        for (String k : keysByLength) {
            if (k.length() > s.length()) continue;
            int from = 0, i;
            while ((i = s.indexOf(k, from)) >= 0) {
                boolean free = true;
                for (int j = i; j < i + k.length(); j++) if (used[j]) { free = false; break; }
                if (free) {
                    String tr = table.get(k)[col];
                    if (tr.isEmpty()) break;
                    for (int j = i; j < i + k.length(); j++) used[j] = true;
                    rep[i] = tr;
                    len[i] = k.length();
                    any = true;
                }
                from = i + k.length();
            }
        }
        if (!any) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            if (rep[i] != null) { b.append(rep[i]); i += len[i]; }
            else { b.append(s.charAt(i)); i++; }
        }
        return b.toString();
    }
}
