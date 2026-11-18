package daemmerwald;

/**
 * Die Geschichte von Teil 2 in Kapiteln: „Das Erwachen unter Sternenhall“.
 * Fortschritt steht im Spielstand (stages "chapter" und "chstep").
 */
public final class Chapters {
    private Chapters() { }

    public static final String[] TITLES = {
        "", "Die Ankunft", "Das erste Geheimnis", "Der verbotene Wald", "Das Dorf", "Die vergessene Bibliothek", "Die alten Ruinen", "Das Erwachen"
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
                case 1 -> new String[]{head, "Melde dich bei Professorin Funkelstein", "Am Pult im großen Saal, gleich links hinter dem Tor"};
                case 2 -> new String[]{head, "Lerne Lumen im Unterricht", "Professorin Funkelstein, großer Saal"};
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
}
