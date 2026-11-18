package daemmerwald;

import java.util.List;

import static daemmerwald.Look.rgb;

/**
 * Eine der zehn spielbaren Figuren.
 * Macht   -> Mana, Zauberstärke im Duell
 * Fokus   -> mehr Zeit in zeitbegrenzten Prüfungen
 * Geschick-> Laufgeschwindigkeit, größere Trefferflächen
 */
public class CharacterDef {
    public final String name, epithet, bio;
    public final Look look;
    public final int macht, fokus, geschick;

    public CharacterDef(String name, String epithet, int macht, int fokus, int geschick, Look look, String bio) {
        this.name = name;
        this.epithet = epithet;
        this.macht = macht;
        this.fokus = fokus;
        this.geschick = geschick;
        this.look = look;
        this.bio = bio;
    }

    public boolean isTroll() { return look.kind == Look.TROLL; }

    public double timeFactor() { return 1.0 + (fokus - 3) * 0.08; }

    public double moveSpeed() { return 150 + geschick * 14; }

    public double manaMax() { return 60 + macht * 12; }

    public boolean isFairy() { return look.kind == Look.FAIRY; }

    /** Zehn Menschen, die sehr verschieden aussehen – alle neu an der verlassenen Akademie Sternenhall. */
    public static final List<CharacterDef> ALL = List.of(
        new CharacterDef("Elias Brandt", "Der Kartograf", 3, 4, 2,
            Look.human(rgb(236, 196, 170), rgb(70, 90, 70), rgb(110, 86, 60), rgb(150, 80, 40), rgb(70, 50, 35), false, Look.BEARD),
            "Hat jede Landkarte des Reiches gezeichnet – nur eine nicht: die der Akademie. Auf allen alten Plänen ist an ihrer Stelle "
          + "ein weißer Fleck. Er will wissen, warum."),
        new CharacterDef("Amara Okafor", "Die Runenleserin", 4, 4, 1,
            Look.human(rgb(110, 70, 50), rgb(150, 60, 70), rgb(60, 60, 110), rgb(25, 20, 20), rgb(200, 180, 120), true, Look.LONG),
            "Liest Runen wie andere Leute Briefe. Seit Wochen träumt sie von einem Symbol, das sie noch nie gesehen hat – "
          + "und gestern stand es plötzlich an ihrer Zimmertür."),
        new CharacterDef("Kenji Arai", "Der Uhrmacher", 2, 5, 2,
            Look.human(rgb(232, 200, 160), rgb(60, 60, 75), rgb(40, 40, 50), rgb(20, 20, 25), rgb(90, 60, 40), false, Look.SHORT),
            "Repariert Uhren, die rückwärts laufen, und Spieluhren, die Erinnerungen abspielen. "
          + "Er glaubt, dass die Akademie selbst ein Uhrwerk ist – und dass sie gerade wieder anfängt zu ticken."),
        new CharacterDef("Liv Sandberg", "Die Sturmseglerin", 3, 2, 4,
            Look.human(rgb(245, 214, 196), rgb(70, 110, 160), rgb(220, 210, 190), rgb(235, 205, 130), rgb(120, 80, 50), true, Look.PONYTAIL),
            "Ist mit einem Boot voller Zauberbücher über das Nordmeer gekommen. Furchtlos, laut und immer die Erste, "
          + "die eine verbotene Tür aufmacht."),
        new CharacterDef("Malik Haddad", "Der Alchemist", 4, 3, 2,
            Look.human(rgb(190, 140, 100), rgb(120, 40, 40), rgb(80, 70, 60), rgb(30, 25, 20), rgb(60, 45, 35), false, Look.BEARD),
            "Seine Familie braut seit sieben Generationen Tränke. Er ist der Erste, der die alten Rezepte "
          + "der Akademie lesen kann – und einige davon hätten besser verschlossen bleiben sollen."),
        new CharacterDef("Mei Lin", "Die Lichtweberin", 3, 3, 3,
            Look.human(rgb(240, 210, 180), rgb(220, 200, 230), rgb(90, 50, 110), rgb(20, 18, 22), rgb(240, 240, 240), true, Look.BUN),
            "Webt Licht zu Fäden und Fäden zu Bildern. Als sie das erste Mal durch die Säle von Sternenhall ging, "
          + "flackerten alle Kerzen gleichzeitig – als hätten sie sie erkannt."),
        new CharacterDef("Tobias Lindqvist", "Der Pechvogel", 2, 2, 5,
            Look.human(rgb(250, 222, 205), rgb(200, 140, 50), rgb(70, 90, 120), rgb(240, 220, 160), rgb(150, 50, 40), false, Look.WILD),
            "Stolpert über jede Schwelle und findet dabei jede Geheimtür. Niemand weiß, ob er Glück oder Pech hat. "
          + "Er selbst am allerwenigsten."),
        new CharacterDef("Sofia Márquez", "Die Duellantin", 5, 2, 2,
            Look.human(rgb(210, 160, 120), rgb(40, 40, 45), rgb(130, 30, 40), rgb(70, 40, 25), rgb(30, 30, 30), true, Look.LONG),
            "Gewann jedes Duell ihrer alten Schule. Dann kam der Brief von Sternenhall – ohne Absender, mit ihrem Namen in Goldtinte. "
          + "Sie will wissen, wer sie gerufen hat."),
        new CharacterDef("Darius Mensah", "Der Sternenkundler", 4, 3, 2,
            Look.human(rgb(85, 55, 40), rgb(30, 50, 90), rgb(180, 170, 150), rgb(15, 12, 12), rgb(200, 160, 80), false, Look.SHORT),
            "Zählt Sterne, die andere nicht sehen. Seit dem Neumond leuchtet über der Akademie ein Stern, der in keinem Atlas steht. "
          + "Er ist ihm bis hierher gefolgt."),
        new CharacterDef("Nora Kessler", "Die Archivarin", 3, 4, 2,
            Look.human(rgb(248, 220, 205), rgb(90, 120, 80), rgb(110, 80, 60), rgb(190, 70, 30), rgb(80, 55, 40), true, Look.BRAIDS),
            "Hat die Bibliothek der Hauptstadt geordnet, Buch für Buch. Ein einziges Buch war nirgends verzeichnet: "
          + "eine Chronik von Sternenhall, deren letzte Seite leer ist – noch.")
    );
}
