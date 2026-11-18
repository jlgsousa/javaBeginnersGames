package daemmerwald;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alle Texte werden gesprochen: echte deutsche Sprachaufnahmen, vorab mit der freien Sprachsynthese Piper erzeugt
 * (assets/voice): Frauenstimme „Kerstin“ (f), Männerstimme „Thorsten“ (m) und eine ruhige Erzählerin (n).
 * Sätze mit Namen, Zahlen oder Gegenständen werden aus aufgenommenen Stücken zusammengesetzt.
 */
public final class Voice {
    private Voice() { }

    /** Sprache der Stimmen: "de", "pt" (Portugal) oder "en". */
    public static volatile String lang = "de";
    public static volatile boolean enabled = true;
    public static volatile float volume = 1f;

    /** Wird von der Musik (Main-Thread) bereitgestellt: spielt Dateien nacheinander mit Tonhöhe. */
    public interface Player {
        void play(List<String> assets, float pitch);
        void stop();
    }

    public static Player player;
    private static List<String> pieces;

    /** Eine Figur spricht. key = Originalzeile aus dem Spieltext. */
    public static void say(String key, boolean female, String who) {
        int h = who == null ? 0 : Math.abs(who.hashCode());
        speak(key, female ? "f" : "m", 0.95f + (h % 11) / 100f);
    }

    /** Die Erzählerin liest. */
    public static void narrate(String key) { speak(key, "n", 0.97f); }

    /** Mehrere Absätze nacheinander vorlesen (Bücher). */
    public static void narrateAll(List<String> keys) {
        if (!ready()) return;
        List<String> paths = new ArrayList<>();
        for (String k : keys) paths.addAll(resolve(k, "n"));
        if (paths.isEmpty()) stop(); else player.play(paths, 0.97f);
    }

    private static boolean ready() { return enabled && player != null && lang.equals("de"); }

    private static void speak(String key, String v, float pitch) {
        if (!ready() || key == null || key.isBlank()) return;
        List<String> paths = resolve(key, v);
        if (Boolean.getBoolean("dw.voicelog")) System.out.println("Stimme " + v + ": " + paths);
        if (paths.isEmpty()) stop(); else player.play(paths, pitch);
    }

    public static void stop() {
        if (player != null) player.stop();
    }

    private static String path(String v, String key) { return "voice/" + lang + "/" + v + "/" + hash(key) + ".ogg"; }

    private static boolean exists(String p) { return Voice.class.getResource("/" + p) != null; }

    /** Ganzer Satz – oder zusammengesetzt aus bekannten Stücken und Zahlen. */
    static List<String> resolve(String key, String v) {
        List<String> out = new ArrayList<>();
        String p = path(v, key);
        if (exists(p)) { out.add(p); return out; }
        String t = key.trim();
        if (!t.equals(key) && exists(path(v, t))) { out.add(path(v, t)); return out; }
        // Stücke suchen (längste zuerst, ohne Überlappung), dazu Zahlen
        int n = key.length();
        String[] at = new String[n];
        int[] len = new int[n];
        boolean[] used = new boolean[n];
        for (String piece : pieces()) {
            if (piece.length() > n) continue;
            int from = 0, i;
            while ((i = key.indexOf(piece, from)) >= 0) {
                boolean free = true;
                for (int j = i; j < i + piece.length(); j++) if (used[j]) { free = false; break; }
                if (free) {
                    for (int j = i; j < i + piece.length(); j++) used[j] = true;
                    at[i] = piece;
                    len[i] = piece.length();
                }
                from = i + piece.length();
            }
        }
        Matcher m = Pattern.compile("\\d{1,4}").matcher(key);
        while (m.find()) {
            if (used[m.start()]) continue;
            int val = Integer.parseInt(m.group());
            if (val > 1000) continue;
            at[m.start()] = String.valueOf(val);
            len[m.start()] = m.group().length();
            for (int j = m.start(); j < m.end(); j++) used[j] = true;
        }
        for (int i = 0; i < n; i++) {
            if (at[i] == null) continue;
            String pp = path(v, at[i]);
            if (exists(pp)) out.add(pp);
        }
        return out;
    }

    private static synchronized List<String> pieces() {
        if (pieces != null) return pieces;
        pieces = new ArrayList<>();
        try (InputStream in = Voice.class.getResourceAsStream("/voice/de/pieces.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) if (line.trim().length() >= 3) pieces.add(line.replace("\\n", "\n"));
            }
        } catch (Exception ignored) { }
        pieces.sort((a, b) -> b.length() - a.length());
        return pieces;
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
