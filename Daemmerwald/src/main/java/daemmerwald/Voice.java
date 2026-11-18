package daemmerwald;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Figuren sprechen ihre Sätze laut: echte deutsche Sprachaufnahmen, vorab mit der freien Sprachsynthese
 * Piper erzeugt (Stimmen „Kerstin“ und „Thorsten“, assets/voice). Jede Figur bekommt ihre eigene Stimmlage.
 * Erzählertext (mit * markiert) bleibt stumm. Gibt es zu einem Satz keine Aufnahme, bleibt es still.
 */
public final class Voice {
    private Voice() { }

    /** Sprache der Stimmen: "de", "pt" (Portugal) oder "en". */
    public static volatile String lang = "de";
    public static volatile boolean enabled = true;
    public static volatile float volume = 1f;

    /** Wird von der Musik (Main-Thread) bereitgestellt: spielt eine Datei mit Tonhöhe. */
    public interface Player {
        void play(String asset, float pitch);
        void stop();
    }

    public static Player player;

    /** Spricht einen Satz (bricht den vorigen ab). key = Originalzeile aus dem Spieltext. */
    public static void say(String key, boolean female, String who) {
        if (!enabled || key == null || key.isBlank() || player == null) return;
        String path = "voice/" + lang + "/" + (female ? "f" : "m") + "/" + hash(key) + ".ogg";
        if (Boolean.getBoolean("dw.voicelog")) System.out.println("Stimme: " + path + " " + (Voice.class.getResource("/" + path) != null));
        if (Voice.class.getResource("/" + path) == null) { stop(); return; }
        int h = who == null ? 0 : Math.abs(who.hashCode());
        float pitch = 0.94f + (h % 13) / 100f;
        player.play(path, pitch);
    }

    public static void stop() {
        if (player != null) player.stop();
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
