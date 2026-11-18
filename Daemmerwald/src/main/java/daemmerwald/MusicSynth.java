package daemmerwald;

import java.util.Random;

/**
 * Prozedurale Orchestermusik ohne Gesang: Streicherflächen, Ostinati, tiefe Blechbläser-„Braams",
 * Trommeln, Harfe und Glocken, mit Hall. Alles wird beim Start berechnet – es gibt keine Audiodateien.
 */
public final class MusicSynth {
    public static final int RATE = 32000;
    private final float[] l, r;
    private final int n;
    private final Random rnd = new Random(7);

    private MusicSynth(float seconds) {
        n = (int) (seconds * RATE);
        l = new float[n];
        r = new float[n];
    }

    static double freq(int midi) { return 440 * Math.pow(2, (midi - 69) / 12.0); }

    // ---------------------------------------------------------------- Instrumente

    /** Streicherfläche: mehrere verstimmte Sägezähne, weich gefiltert. */
    void pad(int[] notes, double start, double dur, double vol, double bright) {
        for (int note : notes) {
            for (int v = 0; v < 4; v++) {
                double f = freq(note) * (1 + (v - 1.5) * 0.0035);
                double pan = 0.5 + (v - 1.5) * 0.18;
                voiceSaw(f, start, dur, vol / notes.length * 0.6, 0.9 + dur * 0.08, dur * 0.35, bright, pan, 5 + v);
            }
        }
    }

    private void voiceSaw(double f, double start, double dur, double vol, double att, double rel, double bright, double pan, double vibHz) {
        int s0 = (int) (start * RATE), len = (int) ((dur + rel) * RATE);
        double ph = rnd.nextDouble(), lp = 0, lp2 = 0;
        double cutoff = Math.min(0.45, f * (2 + bright * 6) / RATE);
        double a = 1 - Math.exp(-2 * Math.PI * cutoff);
        for (int i = 0; i < len; i++) {
            int k = s0 + i;
            if (k < 0) continue;
            if (k >= n) k -= n; // nahtlose Schleife
            double t = i / (double) RATE;
            double env = Math.min(1, t / att) * (t > dur ? Math.max(0, 1 - (t - dur) / rel) : 1);
            if (env <= 0) continue;
            double vib = 1 + Math.sin(t * vibHz * 2 * Math.PI * 0.9) * 0.0025;
            ph += f * vib / RATE;
            ph -= Math.floor(ph);
            double saw = 2 * ph - 1;
            lp += a * (saw - lp);
            lp2 += a * (lp - lp2);
            double s = lp2 * env * vol;
            l[k] += s * (1 - pan) * 2 * 0.5;
            r[k] += s * pan * 2 * 0.5;
        }
    }

    /** Kurzer gezupfter/gestrichener Ton für Ostinati. */
    void stacc(int note, double start, double dur, double vol, double pan) {
        double f = freq(note);
        int s0 = (int) (start * RATE), len = (int) ((dur + 0.12) * RATE);
        double ph = rnd.nextDouble(), lp = 0;
        double a = 1 - Math.exp(-2 * Math.PI * Math.min(0.4, f * 5 / RATE));
        for (int i = 0; i < len; i++) {
            int k = s0 + i;
            if (k >= n) k -= n;
            double t = i / (double) RATE;
            double env = Math.min(1, t / 0.012) * Math.exp(-t * 7) * (t > dur ? Math.max(0, 1 - (t - dur) / 0.12) : 1);
            ph += f / RATE;
            ph -= Math.floor(ph);
            lp += a * ((2 * ph - 1) - lp);
            double s = lp * env * vol;
            l[k] += s * (1 - pan);
            r[k] += s * pan;
        }
    }

    /** Tiefes Blechbläser-„Braam" mit sich öffnendem Filter. */
    void braam(int root, double start, double dur, double vol) {
        int[] notes = {root, root + 7, root + 12};
        int s0 = (int) (start * RATE), len = (int) ((dur + 1.2) * RATE);
        for (int ni = 0; ni < notes.length; ni++) {
            for (int v = 0; v < 3; v++) {
                double f = freq(notes[ni]) * (1 + (v - 1) * 0.004);
                double ph = rnd.nextDouble(), lp = 0, lp2 = 0;
                for (int i = 0; i < len; i++) {
                    int k = s0 + i;
                    if (k >= n) k -= n;
                    double t = i / (double) RATE;
                    double env = Math.min(1, t / 0.25) * (t > dur ? Math.max(0, 1 - (t - dur) / 1.2) : 1);
                    double open = Math.min(1, t / (dur * 0.6));
                    double cutoff = Math.min(0.45, f * (1.5 + open * 7) / RATE);
                    double a = 1 - Math.exp(-2 * Math.PI * cutoff);
                    ph += f / RATE;
                    ph -= Math.floor(ph);
                    double saw = 2 * ph - 1;
                    lp += a * (saw - lp);
                    lp2 += a * (lp - lp2);
                    double s = Math.tanh(lp2 * 1.6) * env * vol / notes.length;
                    l[k] += s * (0.45 + v * 0.05);
                    r[k] += s * (0.55 - v * 0.05);
                }
            }
        }
    }

    /** Große Trommel (Taiko-artig): fallender Sinus + gefiltertes Rauschen. */
    void drum(double start, double vol, double pitch) {
        int s0 = (int) (start * RATE), len = (int) (1.6 * RATE);
        double ph = 0, nz = 0;
        for (int i = 0; i < len; i++) {
            int k = s0 + i;
            if (k >= n) k -= n;
            double t = i / (double) RATE;
            double f = pitch * (0.55 + 0.45 * Math.exp(-t * 18));
            ph += f / RATE;
            double body = Math.sin(2 * Math.PI * ph) * Math.exp(-t * 3.2);
            nz += 0.08 * ((rnd.nextDouble() * 2 - 1) - nz);
            double hit = nz * Math.exp(-t * 22) * 2.5;
            double s = (body + hit) * vol;
            l[k] += s;
            r[k] += s;
        }
    }

    /** Harfe / Glocke: Sinus mit Obertönen und exponentiellem Ausklang. */
    void pluck(int note, double start, double vol, double decay, double pan, boolean bell) {
        double f = freq(note);
        int s0 = (int) (start * RATE), len = (int) (decay * 4 * RATE);
        for (int i = 0; i < len; i++) {
            int k = s0 + i;
            if (k >= n) k -= n;
            double t = i / (double) RATE;
            double env = Math.min(1, t / 0.004) * Math.exp(-t / decay);
            double w = 2 * Math.PI * f * t;
            double s = bell
                ? Math.sin(w) + 0.5 * Math.sin(w * 2.76) * Math.exp(-t * 3) + 0.25 * Math.sin(w * 5.4) * Math.exp(-t * 6)
                : Math.sin(w) + 0.35 * Math.sin(w * 2) * Math.exp(-t * 2) + 0.15 * Math.sin(w * 3) * Math.exp(-t * 4);
            s *= env * vol;
            l[k] += s * (1 - pan);
            r[k] += s * pan;
        }
    }

    /** Leises Rauschen (Wind, Regen) für Atmosphäre-Schleifen. */
    void noise(double vol, double smooth, double gustHz) {
        double a = 0, b = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double gust = 0.6 + 0.4 * Math.sin(t * gustHz * 2 * Math.PI) * Math.sin(t * gustHz * 1.37 * 2 * Math.PI + 1);
            a += smooth * ((rnd.nextDouble() * 2 - 1) - a);
            b += smooth * ((rnd.nextDouble() * 2 - 1) - b);
            l[i] += a * vol * gust;
            r[i] += b * vol * gust;
        }
    }

    // ---------------------------------------------------------------- Nachbearbeitung

    private void reverb(double mix, double size) {
        int[] combs = {1557, 1617, 1491, 1422, 1277, 1356};
        int[] alls = {225, 556, 441};
        float[] wl = new float[n], wr = new float[n];
        for (int ch = 0; ch < 2; ch++) {
            float[] in = ch == 0 ? l : r, out = ch == 0 ? wl : wr;
            for (int c : combs) {
                int d = (int) (c * size * RATE / 44100.0) + ch * 23;
                float[] buf = new float[d];
                int p = 0;
                float fb = 0.84f, damp = 0.25f, filt = 0;
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < n; i++) {
                        float o = buf[p];
                        filt = o * (1 - damp) + filt * damp;
                        buf[p] = in[i] + filt * fb;
                        p = (p + 1) % d;
                        if (pass == 1) out[i] += o / combs.length;
                    }
                }
            }
            for (int a : alls) {
                int d = (int) (a * size * RATE / 44100.0);
                float[] buf = new float[d];
                int p = 0;
                for (int i = 0; i < n; i++) {
                    float bo = buf[p];
                    float o = -out[i] + bo;
                    buf[p] = out[i] + bo * 0.5f;
                    p = (p + 1) % d;
                    out[i] = o;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            l[i] = (float) (l[i] * (1 - mix * 0.5) + wl[i] * mix * 2.2);
            r[i] = (float) (r[i] * (1 - mix * 0.5) + wr[i] * mix * 2.2);
        }
    }

    /** Normalisiert und liefert 16-Bit-Stereo (little endian). */
    private byte[] finish(double reverbMix, double peak) {
        if (reverbMix > 0) reverb(reverbMix, 1.25);
        float max = 1e-6f;
        for (int i = 0; i < n; i++) max = Math.max(max, Math.max(Math.abs(l[i]), Math.abs(r[i])));
        double g = peak / max;
        byte[] out = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            int a = (int) (Math.tanh(l[i] * g) * 32000), b = (int) (Math.tanh(r[i] * g) * 32000);
            out[i * 4] = (byte) a;
            out[i * 4 + 1] = (byte) (a >> 8);
            out[i * 4 + 2] = (byte) b;
            out[i * 4 + 3] = (byte) (b >> 8);
        }
        return out;
    }

    // ---------------------------------------------------------------- Stücke

    private static int[] chord(int root, boolean minor) { return new int[]{root, root + (minor ? 3 : 4), root + 7, root + 12}; }

    /** Ruhig und hoffnungsvoll (Tag). D-Dur: I – vi – IV – V. */
    public static byte[] day() {
        double bpm = 72, beat = 60 / bpm, bar = beat * 4;
        int[] roots = {50, 47, 43, 45};
        boolean[] minor = {false, true, false, false};
        MusicSynth m = new MusicSynth((float) (bar * 16));
        for (int b = 0; b < 16; b++) {
            int c = b % 4;
            double t = b * bar;
            m.pad(chord(roots[c] + 12, minor[c]), t, bar * 1.02, 0.5, 0.25);
            m.pad(new int[]{roots[c] - 12}, t, bar, 0.35, 0.1);
            int[] ch = chord(roots[c] + 24, minor[c]);
            for (int k = 0; k < 8; k++) m.pluck(ch[k % 4] + (k >= 4 ? 12 : 0), t + k * beat / 2, 0.11, 0.9, 0.3 + (k % 4) * 0.12, false);
            if (b >= 8) {
                int[] mel = {ch[2] + 12, ch[1] + 12, ch[0] + 12, ch[1] + 12};
                m.voiceSaw(freq(mel[c]), t, bar * 0.9, 0.14, 0.6, 1.2, 0.4, 0.5, 5);
            }
        }
        return m.finish(0.35, 0.8);
    }

    /** Geheimnisvoll (Nacht, Titel). D-Moll: i – VI – III – VII. */
    public static byte[] night() {
        double bpm = 60, beat = 60 / bpm, bar = beat * 4;
        int[] roots = {50, 46, 41, 48};
        boolean[] minor = {true, false, false, false};
        MusicSynth m = new MusicSynth((float) (bar * 12));
        for (int b = 0; b < 12; b++) {
            int c = b % 4;
            double t = b * bar;
            m.pad(chord(roots[c], minor[c]), t, bar * 1.05, 0.55, 0.15);
            m.pad(new int[]{roots[c] - 24}, t, bar, 0.4, 0.05);
            int[] ch = chord(roots[c] + 24, minor[c]);
            for (int k = 0; k < 3; k++) m.pluck(ch[(b + k * 2) % 4] + 12, t + k * beat * 1.33 + 0.2, 0.08, 1.6, 0.2 + k * 0.3, true);
            if (b % 4 == 3) m.braam(roots[0] - 24, t + bar * 0.5, bar * 0.45, 0.35);
        }
        return m.finish(0.45, 0.75);
    }

    /** Bedrohlich (dunkle Orte): C-Moll-Bordun, Ostinato, Braams, Trommeln. */
    public static byte[] dark() {
        double bpm = 92, beat = 60 / bpm, bar = beat * 4;
        int[] roots = {36, 36, 32, 34};
        MusicSynth m = new MusicSynth((float) (bar * 12));
        for (int b = 0; b < 12; b++) {
            int c = b % 4;
            double t = b * bar;
            m.pad(new int[]{48, 51, 55}, t, bar, 0.3, 0.1);
            for (int k = 0; k < 8; k++) m.stacc(roots[c] + 12 + (k % 4 == 3 ? 7 : 0), t + k * beat / 2, beat * 0.35, 0.22, 0.4 + (k % 2) * 0.2);
            if (b % 2 == 0) m.braam(roots[c], t, bar * 0.8, 0.5);
            m.drum(t, 0.55, 70);
            if (b % 2 == 1) m.drum(t + beat * 2.5, 0.4, 85);
        }
        return m.finish(0.4, 0.8);
    }

    /** Dramatisch (Duelle, Prüfungen, Rennen). A-Moll: i – VI – III – VII, treibendes Ostinato. */
    public static byte[] drama() {
        double bpm = 112, beat = 60 / bpm, bar = beat * 4;
        int[] roots = {45, 41, 48, 43};
        boolean[] minor = {true, false, false, false};
        MusicSynth m = new MusicSynth((float) (bar * 16));
        for (int b = 0; b < 16; b++) {
            int c = b % 4;
            double t = b * bar;
            int[] ch = chord(roots[c], minor[c]);
            for (int k = 0; k < 16; k++) m.stacc(ch[k % 3] + (k % 8 >= 4 ? 12 : 0), t + k * beat / 4, beat * 0.2, 0.16, 0.3 + (k % 4) * 0.13);
            m.pad(chord(roots[c] + 12, minor[c]), t, bar, 0.38, 0.35);
            m.drum(t, 0.6, 72);
            m.drum(t + beat * 1.5, 0.35, 90);
            m.drum(t + beat * 2, 0.5, 72);
            m.drum(t + beat * 3.5, 0.3, 100);
            if (b % 4 == 0) m.braam(roots[c] - 12, t, bar * 0.7, 0.55);
            if (b >= 8) m.voiceSaw(freq(ch[(b / 2) % 3] + 24), t, bar * 0.95, 0.15, 0.3, 0.8, 0.6, 0.5, 6);
        }
        return m.finish(0.3, 0.85);
    }

    /** Harmonisch und gemütlich (Café). F-Dur mit Septakkorden, Harfe und warme Streicher. */
    public static byte[] cafe() {
        double bpm = 66, beat = 60 / bpm, bar = beat * 4;
        int[][] chords = {{53, 57, 60, 64}, {50, 53, 57, 60}, {46, 50, 53, 57}, {48, 52, 55, 58}};
        MusicSynth m = new MusicSynth((float) (bar * 16));
        for (int b = 0; b < 16; b++) {
            int c = b % 4;
            double t = b * bar;
            m.pad(new int[]{chords[c][0] + 12, chords[c][1] + 12, chords[c][3] + 12}, t, bar * 1.02, 0.32, 0.18);
            m.pluck(chords[c][0] - 12, t, 0.2, 1.4, 0.45, false);
            int[] pat = {0, 2, 1, 3, 2, 1, 3, 2};
            for (int k = 0; k < 8; k++) m.pluck(chords[c][pat[k]] + 12, t + k * beat / 2 + (k % 2) * 0.02, 0.1, 1.1, 0.25 + k * 0.06, false);
            if (b >= 4 && b % 2 == 0) m.pluck(chords[c][3] + 24, t + beat * 2.5, 0.06, 1.8, 0.7, true);
        }
        return m.finish(0.4, 0.7);
    }

    /** Ambiente: Wind/Regen-Rauschen. */
    public static byte[] rain() {
        MusicSynth m = new MusicSynth(12);
        m.noise(0.6, 0.55, 0.08);
        m.noise(0.25, 0.08, 0.05);
        return m.finish(0, 0.6);
    }

    public static byte[] wind() {
        MusicSynth m = new MusicSynth(16);
        m.noise(0.5, 0.02, 0.06);
        return m.finish(0.2, 0.5);
    }

    /** Grillen und gelegentliche Nachtvögel. */
    public static byte[] crickets() {
        MusicSynth m = new MusicSynth(12);
        for (int i = 0; i < 160; i++) {
            double t = m.rnd.nextDouble() * 12;
            for (int k = 0; k < 3; k++) m.pluck(102 + m.rnd.nextInt(3), t + k * 0.045, 0.03, 0.012, m.rnd.nextDouble(), false);
        }
        for (int i = 0; i < 3; i++) {
            double t = m.rnd.nextDouble() * 11;
            m.pluck(72, t, 0.05, 0.25, 0.3, false);
            m.pluck(69, t + 0.35, 0.05, 0.3, 0.3, false);
        }
        return m.finish(0.2, 0.4);
    }

    /** Vogelgezwitscher am Tag. */
    public static byte[] birds() {
        MusicSynth m = new MusicSynth(14);
        for (int i = 0; i < 26; i++) {
            double t = m.rnd.nextDouble() * 13.5;
            int base = 86 + m.rnd.nextInt(8);
            double pan = m.rnd.nextDouble();
            int notes = 2 + m.rnd.nextInt(5);
            for (int k = 0; k < notes; k++) m.pluck(base + (k % 2 == 0 ? 0 : 3 + m.rnd.nextInt(4)), t + k * 0.09, 0.04, 0.05, pan, false);
        }
        return m.finish(0.25, 0.4);
    }

    /** Zauber-Klang. */
    public static byte[] spell() {
        MusicSynth m = new MusicSynth(1.6f);
        int[] up = {74, 78, 81, 86, 90, 93};
        for (int i = 0; i < up.length; i++) m.pluck(up[i], i * 0.05, 0.25, 0.45, 0.3 + i * 0.08, true);
        return m.finish(0.3, 0.6);
    }

    /** Sammel-Klang. */
    public static byte[] chime() {
        MusicSynth m = new MusicSynth(1.2f);
        m.pluck(88, 0, 0.3, 0.3, 0.5, true);
        m.pluck(95, 0.08, 0.25, 0.4, 0.5, true);
        return m.finish(0.25, 0.5);
    }

    /** Kurzer Knall aus gefiltertem Rauschen (Schritte, Treffer). */
    private void thump(double start, double vol, double decay, double smooth, double pan, double toneHz) {
        int s0 = (int) (start * RATE), len = (int) (decay * 5 * RATE);
        double a = 0;
        for (int i = 0; i < len && s0 + i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1, t / 0.003) * Math.exp(-t / decay);
            a += smooth * ((rnd.nextDouble() * 2 - 1) - a);
            double s = (a + (toneHz > 0 ? Math.sin(2 * Math.PI * toneHz * t * (1 - t * 2)) * 0.8 : 0)) * env * vol;
            l[s0 + i] += s * (1 - pan);
            r[s0 + i] += s * pan;
        }
    }

    /** Schritt auf Stein oder Waldboden. */
    public static byte[] step() {
        MusicSynth m = new MusicSynth(0.3f);
        m.thump(0, 1, 0.035, 0.25, 0.5, 90);
        return m.finish(0.05, 0.5);
    }

    /** Schwere Holztür mit Knarren. */
    public static byte[] door() {
        MusicSynth m = new MusicSynth(1.6f);
        for (int i = 0; i < 26000; i++) {
            double t = i / (double) RATE;
            double f = 180 + 90 * Math.sin(t * 7) + t * 60;
            double s = Math.sin(2 * Math.PI * f * t) * Math.sin(2 * Math.PI * 23 * t) * Math.min(1, t * 8) * Math.max(0, 1 - t / 0.8) * 0.3;
            m.l[i] += s; m.r[i] += s;
        }
        m.thump(0.9, 1.2, 0.09, 0.08, 0.5, 60);
        return m.finish(0.35, 0.6);
    }

    /** Ruf einer Eule. */
    public static byte[] owl() {
        MusicSynth m = new MusicSynth(2.4f);
        double[][] calls = {{0, 0.35}, {0.55, 0.18}, {0.85, 0.6}};
        for (double[] c : calls) {
            int s0 = (int) (c[0] * RATE), len = (int) (c[1] * RATE);
            for (int i = 0; i < len; i++) {
                double t = i / (double) RATE;
                double f = 410 - 40 * t / c[1];
                double env = Math.sin(Math.PI * t / c[1]);
                double s = (Math.sin(2 * Math.PI * f * t) + 0.2 * Math.sin(4 * Math.PI * f * t)) * env * 0.4;
                m.l[s0 + i] += s * 0.7; m.r[s0 + i] += s;
            }
        }
        return m.finish(0.5, 0.4);
    }

    /** Treffer auf einem Gegner. */
    public static byte[] hit() {
        MusicSynth m = new MusicSynth(0.7f);
        m.thump(0, 1, 0.08, 0.5, 0.5, 140);
        m.pluck(64, 0, 0.3, 0.15, 0.5, true);
        return m.finish(0.2, 0.6);
    }

    /** Tiefes Brüllen eines Bosses. */
    public static byte[] roar() {
        MusicSynth m = new MusicSynth(2.2f);
        double a = 0, b = 0;
        for (int i = 0; i < (int) (1.9 * RATE); i++) {
            double t = i / (double) RATE;
            double env = Math.min(1, t / 0.15) * Math.max(0, 1 - t / 1.9);
            a += 0.04 * ((m.rnd.nextDouble() * 2 - 1) - a);
            b += 0.04 * ((m.rnd.nextDouble() * 2 - 1) - b);
            double f = 62 + 18 * Math.sin(t * 3);
            double tone = Math.sin(2 * Math.PI * f * t) * (0.6 + 0.4 * Math.sin(2 * Math.PI * 31 * t));
            m.l[i] += (a * 2.5 + tone) * env;
            m.r[i] += (b * 2.5 + tone) * env;
        }
        return m.finish(0.4, 0.75);
    }

    /** Luftzug: Ausweichen, Sturzflug, Wurf. */
    public static byte[] whoosh() {
        MusicSynth m = new MusicSynth(0.8f);
        double a = 0;
        for (int i = 0; i < (int) (0.6 * RATE); i++) {
            double t = i / (double) RATE;
            double sm = 0.05 + 0.3 * Math.sin(Math.PI * t / 0.6);
            a += sm * ((m.rnd.nextDouble() * 2 - 1) - a);
            double env = Math.sin(Math.PI * t / 0.6);
            m.l[i] += a * env * (1 - t); m.r[i] += a * env * t;
        }
        return m.finish(0.1, 0.5);
    }
}
