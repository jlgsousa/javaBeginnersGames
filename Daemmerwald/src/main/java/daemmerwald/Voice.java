package daemmerwald;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Figuren sprechen ihre Sätze laut – mit den eingebauten Stimmen des Betriebssystems
 * (Windows: System.Speech, macOS: say, Linux: espeak-ng). Ist keine Stimme da, bleibt es still.
 * Erzählertext (mit * markiert) wird nicht gesprochen, nur was Figuren sagen.
 */
public final class Voice {
    private Voice() { }

    /** Sprache der Stimmen: "de", "pt" (Portugal) oder "en". */
    public static volatile String lang = "de";
    public static volatile boolean enabled = true;
    public static volatile float volume = 0.9f;

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static Process current;
    private static List<String[]> macVoices;
    private static final Object LOCK = new Object();

    /** Spricht einen Satz (bricht den vorigen ab). */
    public static void say(String text, boolean female, String who) {
        if (!enabled || text == null || text.isBlank() || Boolean.getBoolean("dw.noaudio")) return;
        String clean = text.replace("„", "").replace("“", "").replace("»", "").replace("«", "").replace("✦", "").replace("…", "...").trim();
        // individuelle Stimmlage je Figur
        int h = who == null ? 0 : Math.abs(who.hashCode());
        int pitchShift = (h % 5) - 2;
        Thread t = new Thread(() -> speak(clean, female, pitchShift), "voice");
        t.setDaemon(true);
        t.start();
    }

    public static void stop() {
        synchronized (LOCK) {
            if (current != null) { current.destroy(); current = null; }
        }
    }

    private static void speak(String text, boolean female, int shift) {
        stop();
        try {
            ProcessBuilder pb;
            if (OS.contains("win")) {
                String culture = switch (lang) { case "pt" -> "pt-PT"; case "en" -> "en-GB"; default -> "de-DE"; };
                String ps = "Add-Type -AssemblyName System.Speech;"
                    + "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
                    + "try{$s.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::" + (female ? "Female" : "Male")
                    + ",[System.Speech.Synthesis.VoiceAge]::Adult,0,[System.Globalization.CultureInfo]::new('" + culture + "'))}catch{};"
                    + "$s.Volume=" + (int) (volume * 100) + ";$s.Rate=" + Math.max(-2, Math.min(2, shift)) + ";"
                    + "$t=[Console]::In.ReadToEnd();$s.Speak($t)";
                pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps);
            } else if (OS.contains("mac")) {
                String v = macVoice(female);
                List<String> cmd = new ArrayList<>(List.of("say", "-r", String.valueOf(175 + shift * 8)));
                if (v != null) { cmd.add("-v"); cmd.add(v); }
                cmd.add("-f");
                cmd.add("-");
                pb = new ProcessBuilder(cmd);
            } else {
                String v = switch (lang) { case "pt" -> "pt-pt"; case "en" -> "en-gb"; default -> "de"; };
                pb = new ProcessBuilder("espeak-ng", "-v", v + (female ? "+f3" : "+m3"), "-s", "155", "-p", String.valueOf(50 + shift * 6), "-a", String.valueOf((int) (volume * 150)), "--stdin");
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            synchronized (LOCK) { current = p; }
            try (OutputStream os = p.getOutputStream()) { os.write(text.getBytes(StandardCharsets.UTF_8)); }
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            p.waitFor();
        } catch (Exception e) {
            // keine Sprachausgabe auf diesem Rechner – dann eben nur Text
        }
    }

    /** macOS: passende Stimme für Sprache und Geschlecht aus der Liste von "say -v ?". */
    private static String macVoice(boolean female) {
        if (macVoices == null) {
            macVoices = new ArrayList<>();
            try {
                Process p = new ProcessBuilder("say", "-v", "?").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] parts = line.trim().split("\\s{2,}|\\s+(?=[a-z]{2}_[A-Z]{2})");
                        if (parts.length >= 2) macVoices.add(new String[]{parts[0].trim(), parts[1].trim().split("\\s")[0]});
                    }
                }
            } catch (Exception ignored) { }
        }
        String loc = switch (lang) { case "pt" -> "pt_PT"; case "en" -> "en_GB"; default -> "de_DE"; };
        java.util.Set<String> fem = java.util.Set.of("Anna", "Petra", "Helena", "Joana", "Catarina", "Kate", "Serena", "Stephanie", "Martha", "Flo", "Sandy", "Shelley", "Grandma");
        String any = null;
        for (String[] v : macVoices) {
            if (!v[1].startsWith(loc.substring(0, 2))) continue;
            boolean exact = v[1].equals(loc);
            boolean isFem = fem.contains(v[0].split(" ")[0]);
            if (isFem == female && exact) return v[0];
            if (any == null || (exact && isFem == female)) any = v[0];
        }
        return any;
    }
}
