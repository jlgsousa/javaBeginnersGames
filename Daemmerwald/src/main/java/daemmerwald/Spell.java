package daemmerwald;

import java.awt.Color;

/**
 * Zauber, die man im Unterricht im Palast lernt. Jeder Zauber hat ein Tastenmuster (Pfeile),
 * das man in der Stunde üben muss, und löst eigene Rätsel in der Welt.
 * Muster: 0 = hoch, 1 = rechts, 2 = runter, 3 = links.
 */
public enum Spell {
    FUNKE("Funkenstoß", "Fulgar", "Der erste Zauber jedes Schülers: ein Lichtgeschoss, das Schattenwesen vertreibt.",
        new Color(150, 210, 255), new int[]{0, 0}),
    LICHT("Lichtzauber", "Lumora", "Ein Licht, das Schattenschleier auflöst und Verborgenes zeigt.",
        new Color(255, 245, 190), new int[]{0, 1, 0, 3}),
    FLAMME("Flammenzauber", "Ignara", "Ein Feuer, das Dornenranken verbrennt und kalte Feuerschalen entzündet.",
        new Color(255, 140, 60), new int[]{2, 1, 0, 1, 2}),
    SCHWEBE("Schwebezauber", "Levara", "Hebt schwere Dinge in die Luft: Felsbrocken, Wandteppiche, Steinplatten.",
        new Color(190, 160, 255), new int[]{0, 0, 3, 1, 0}),
    FROST("Frostzauber", "Glacira", "Lässt Wasser zu Eis gefrieren – so wird ein See zum Weg.",
        new Color(150, 230, 255), new int[]{2, 3, 2, 1, 2, 0}),
    OEFFNEN("Öffnungszauber", "Reserra", "Öffnet Schlösser, Truhen und versiegelte Platten.",
        new Color(255, 210, 90), new int[]{1, 2, 3, 0, 1, 1});

    public final String title, word, text;
    public final Color color;
    public final int[] pattern;

    Spell(String title, String word, String text, Color color, int[] pattern) {
        this.title = title;
        this.word = word;
        this.text = text;
        this.color = color;
        this.pattern = pattern;
    }

    /** Reihenfolge der Unterrichtsstunden. */
    public static final Spell[] LESSONS = {LICHT, FLAMME, SCHWEBE, FROST, OEFFNEN};

    public String label() { return word + " (" + title + ")"; }
}
