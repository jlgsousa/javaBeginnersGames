package daemmerwald;

import java.awt.Color;

/**
 * Zauber, die man im Unterricht im Palast lernt. Jeder Zauber hat ein Tastenmuster (Pfeile),
 * das man in der Stunde üben muss, und löst eigene Rätsel in der Welt.
 * Muster: 0 = hoch, 1 = rechts, 2 = runter, 3 = links.
 */
public enum Spell {
    FUNKE("Energieblitz", "Arcanum", "Ein magischer Energieblitz: vertreibt Gegner und stößt sie zurück.",
        new Color(150, 200, 255), new int[]{0, 0}),
    LICHT("Lichtzauber", "Lumen", "Erhellt dunkle Räume, löst Schattenschleier auf und zeigt verborgene Geheimnisse.",
        new Color(255, 245, 190), new int[]{0, 1, 0, 3}),
    FLAMME("Feuerzauber", "Ignis", "Erzeugt Feuer: verbrennt Dornenranken und Hindernisse, entzündet kalte Feuerschalen.",
        new Color(255, 140, 60), new int[]{2, 1, 0, 1, 2}),
    SCHWEBE("Schwebezauber", "Levitaris", "Hebt Gegenstände an und bewegt sie: Felsbrocken, Wandteppiche, Steinplatten.",
        new Color(190, 160, 255), new int[]{0, 0, 3, 1, 0}),
    FROST("Frostzauber", "Glacira", "Fortgeschritten: lässt Wasser zu Eis gefrieren – so wird ein See zum Weg.",
        new Color(150, 230, 255), new int[]{2, 3, 2, 1, 2, 0}),
    OEFFNEN("Öffnungszauber", "Reserra", "Fortgeschritten: öffnet Schlösser, Truhen und versiegelte Platten.",
        new Color(255, 210, 90), new int[]{1, 2, 3, 0, 1, 1}),
    SCHILD("Schutzschild", "Protego", "Ein schimmernder Schild umgibt dich einige Sekunden und hält jeden Angriff ab.",
        new Color(120, 255, 200), new int[]{0, 2, 0, 2, 1, 3});

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
    public static final Spell[] LESSONS = {LICHT, FLAMME, SCHWEBE, SCHILD, FROST, OEFFNEN};

    /** Die fünf Grundzauber des ersten Jahres (Arcanum kann man von Anfang an). */
    public boolean basic() { return this == FUNKE || this == LICHT || this == FLAMME || this == SCHWEBE || this == SCHILD; }

    public String label() { return word + " (" + title + ")"; }
}
