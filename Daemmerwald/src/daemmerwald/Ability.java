package daemmerwald;

/** Fähigkeiten, die mit steigender Stufe freigeschaltet werden. */
public enum Ability {
    LICHT(1, "Lichtzauber", "[Leertaste] Licht, das die Welke verbrennt und Schattenwesen vertreibt."),
    SPRINT(2, "Windschritt", "[Umschalt] gedrückt halten, um schneller zu laufen."),
    LANZE(3, "Lichtlanze", "Dein Lichtzauber reicht viel weiter."),
    ZEIT(4, "Zeitdehnung", "+15 % Zeit in allen zeitbegrenzten Prüfungen."),
    SCHILD(5, "Seelenschild", "Schattenwesen rauben dir nur halb so viel Mana."),
    BEGLEITER(6, "Irrlichtbegleiter", "Ein Irrlicht folgt dir und vertreibt Schattenwesen von selbst."),
    MOND(8, "Mondsegen", "Doppelte Mana-Regeneration."),
    STERN(10, "Sternenherz", "Deine Zauber im Duell sind deutlich stärker.");

    public final int level;
    public final String title, desc;

    Ability(int level, String title, String desc) {
        this.level = level;
        this.title = title;
        this.desc = desc;
    }
}
