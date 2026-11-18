package daemmerwald;

/**
 * Die Geschichte von Teil 2 in Kapiteln: „Das Erwachen unter Sternenhall“.
 * Fortschritt steht im Spielstand (stages "chapter" und "chstep").
 */
public final class Chapters {
    private Chapters() { }

    public static final String[] TITLES = {
        "", "Die Ankunft", "Das erste Geheimnis", "Der verbotene Wald", "Das Dorf", "Die vergessene Bibliothek", "Die alten Ruinen", "Die Kammer von Cabeceiras"
    };

    public static int chapter(GameState st) { return Math.max(1, st.stages.getOrDefault("chapter", 1)); }

    public static int step(GameState st) { return st.stages.getOrDefault("chstep", 0); }

    public static void set(GameState st, int chapter, int step) {
        st.stages.put("chapter", chapter);
        st.stages.put("chstep", step);
    }

    /** Ziel und Hinweis für die Missionsliste. */
    public static String[] objective(GameState st) {
        int c = chapter(st), s = step(st);
        String head = "Kapitel " + c + " · " + (c < TITLES.length ? TITLES[c] : "");
        return switch (c) {
            case 1 -> switch (s) {
                case 0 -> new String[]{head, "Betritt die Akademie Sternenhall", "Das Schloss mit der Kristallspitze, nördlich hinter dem Akademiebaum"};
                case 1 -> new String[]{head, "Melde dich bei Professorin Funkelstein", "In der Zauberklasse – rechts vom großen Saal, nahe dem Tor"};
                case 2 -> new String[]{head, "Lerne Lumen im Unterricht", "Professorin Funkelstein, Zauberklasse"};
                case 3 -> new String[]{head, "Erkunde die Akademie", visitHint(st)};
                case 4 -> new String[]{head, "Ein Symbol leuchtet im großen Saal …", "An der Nordwand, rechts neben dem Kamin"};
                case 5 -> new String[]{head, "Das Symbol ist in die Bibliothek gewandert", "Folge dem Leuchten (Westflügel)"};
                case 6 -> new String[]{head, "Im Trankkeller flüstert es aus der Dunkelheit", "Bring Licht in die dunkle Ecke: Lumen!"};
                default -> new String[]{head, "Kapitel abgeschlossen", ""};
            };
            case 2 -> switch (s) {
                case 0 -> new String[]{head, "Ein neues Gemälde im großen Saal …", "Westwand des Saals – es ist versiegelt"};
                case 1 -> new String[]{head, "Brich die drei Siegel des Gemäldes", sealHint(st)};
                case 2 -> new String[]{head, "Erkunde den verborgenen Raum hinter dem Gemälde", "Lies die Tafel auf dem Pult"};
                default -> new String[]{head, "Kapitel abgeschlossen", ""};
            };
            default -> Saga.objective(st, c >= TITLES.length ? "Epilog" : head, c, s);
        };
    }

    private static String visitHint(GameState st) {
        boolean lib = st.flags.contains("room:library"), cel = st.flags.contains("room:cellar");
        if (!lib && !cel) return "Bibliothek (Westflügel) und Trankkeller (Ostflügel)";
        return !lib ? "Noch: die Bibliothek im Westflügel" : "Noch: der Trankkeller im Ostflügel";
    }

    private static String sealHint(GameState st) {
        StringBuilder sb = new StringBuilder();
        for (Spell s : new Spell[]{Spell.FLAMME, Spell.SCHWEBE, Spell.LICHT}) {
            if (st.flags.contains("seal:" + s.name())) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(st.spells.contains(s) ? s.word : s.word + " (lernen)");
        }
        return "Noch offen: " + sb;
    }

    // ================================================================ Freie Gebiete je Kapitel

    /** Gebiete {Mitte x, Mitte z, Radius, ab Kapitel}. Die Welt wächst mit der Geschichte. */
    public static final float[][] ZONES = {
        {0, -150, 115, 1},      // Akademie, Schloss und etwa 100 m Umgebung
        {-120, -130, 75, 3},    // Weg in den verbotenen Wald (Hain der Alten)
        {-85, -45, 65, 4},      // Laternhain und dein Haus
        {0, 0, 340, 5},         // die ganze Kernwelt
    };

    /** Ab diesem Kapitel ist die endlose Wildnis offen. */
    public static final int WILD = 6;

    public static boolean allowed(GameState st, float x, float z) {
        int c = chapter(st);
        if (c >= WILD || st.flags.contains("finale")) return true;
        for (float[] zn : ZONES) {
            if (c < zn[3]) continue;
            float dx = x - zn[0], dz = z - zn[1];
            if (dx * dx + dz * dz < zn[2] * zn[2]) return true;
        }
        return false;
    }

    /** Ab welchem Kapitel ist dieser Ort offen? */
    public static int opensAt(float x, float z) {
        int best = WILD;
        for (float[] zn : ZONES) {
            float dx = x - zn[0], dz = z - zn[1];
            if (dx * dx + dz * dz < zn[2] * zn[2]) best = Math.min(best, (int) zn[3]);
        }
        return best;
    }
}
