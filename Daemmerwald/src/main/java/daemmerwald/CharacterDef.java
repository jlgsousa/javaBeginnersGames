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

    public static final List<CharacterDef> ALL = List.of(
        new CharacterDef("Grimbart Moosfaust", "Der Steinrufer", 4, 3, 2,
            Look.troll(rgb(104, 128, 96), rgb(84, 70, 52), rgb(150, 220, 120), rgb(60, 80, 50), false),
            "Dreißig Jahre unter Tage, dann ein Unglück, das ihm die Lust an der Dunkelheit nahm. "
          + "Er studiert spät, aber verbissen. Er spricht mit Steinen – und die Steine, sagt er, antworten ehrlicher als Leute."),
        new CharacterDef("Hralda Eisenwurz", "Die Verbannte", 3, 4, 2,
            Look.troll(rgb(120, 130, 145), rgb(110, 40, 40), rgb(230, 190, 90), rgb(200, 200, 210), true),
            "Einst Gesandte ihres Clans. Sie unterschrieb einen Friedensvertrag, den niemand wollte, "
          + "und verlor dafür ihren Namen im Stammbuch. Bereut hat sie es nie. Zumindest nicht laut."),
        new CharacterDef("Brokk der Leise", "Der Feldheiler", 2, 5, 2,
            Look.troll(rgb(130, 110, 90), rgb(60, 80, 60), rgb(180, 230, 200), rgb(90, 70, 50), false),
            "Spricht selten, hört immer zu. Er hat bei Grenzstreitigkeiten mehr Wunden geflickt, als er zählen will. "
          + "Seine Hände sind ruhig. Sein Schlaf nicht."),
        new CharacterDef("Unna Kieselherz", "Die Runenschmiedin", 3, 2, 4,
            Look.troll(rgb(100, 122, 122), rgb(70, 60, 80), rgb(255, 150, 80), rgb(180, 80, 40), false),
            "Schmiedet Runen in Eisen und hat bei den falschen Leuten Schulden. Ihr Humor ist so trocken wie Schlacke. "
          + "Wer ihr etwas verspricht, sollte es halten."),
        new CharacterDef("Torvak Aschenhorn", "Der Ehemalige", 5, 2, 2,
            Look.troll(rgb(92, 98, 92), rgb(50, 50, 58), rgb(220, 90, 70), rgb(40, 40, 40), true),
            "Früher Söldner, heute Student der Schutzmagie. Über die Jahre dazwischen redet er nicht. "
          + "Er sucht keine Vergebung – nur einen Grund, sie irgendwann zu verdienen."),
        new CharacterDef("Lysandra Taufeuer", "Die Hofintrigantin", 3, 3, 3,
            Look.fairy(rgb(240, 220, 210), rgb(120, 40, 90), rgb(255, 120, 180), rgb(230, 200, 120), rgb(255, 170, 220)),
            "Aufgewachsen zwischen Fächern und Flüstereien am Feenhof. Offiziell Studentin. "
          + "Inoffiziell schreibt sie Berichte für jemanden, dessen Namen sie nie nennt."),
        new CharacterDef("Fenn Distelweber", "Der Schmuggler", 2, 2, 5,
            Look.fairy(rgb(220, 200, 180), rgb(60, 90, 70), rgb(170, 255, 150), rgb(90, 60, 40), rgb(180, 255, 200)),
            "Schmuggelt Mondstaub über die Grenze, um das Studium zu bezahlen. Charmant, schnell, chronisch pleite. "
          + "Kennt jeden Trampelpfad im Dämmerwald."),
        new CharacterDef("Mirelle Nachtglanz", "Die Flügellahme", 4, 4, 1,
            Look.fairy(rgb(200, 200, 222), rgb(40, 50, 100), rgb(140, 180, 255), rgb(30, 30, 60), rgb(120, 140, 220)),
            "Verlor bei einem Experiment die Flugfähigkeit und gewann dafür eine Obsession: Warum stirbt Magie? "
          + "Ihre Notizbücher sind voller Antworten, die niemand hören will."),
        new CharacterDef("Caelan Weidenlicht", "Der Duellant", 4, 2, 3,
            Look.fairy(rgb(235, 215, 195), rgb(200, 190, 170), rgb(255, 230, 140), rgb(240, 240, 230), rgb(255, 240, 180)),
            "Duellmeister der Akademie, ehrgeizig bis zur Erschöpfung. Hinter der Arroganz steckt die Angst, "
          + "dass ohne Siege nichts von ihm übrig bliebe."),
        new CharacterDef("Senna Graufalter", "Die Traumdeuterin", 3, 4, 2,
            Look.fairy(rgb(210, 200, 195), rgb(70, 70, 76), rgb(200, 200, 235), rgb(170, 170, 182), rgb(160, 160, 172)),
            "Mit dreiunddreißig verwitwet. Seitdem liest sie Träume, auch die, die sie nicht lesen will. "
          + "In letzter Zeit träumt der ganze Wald dasselbe.")
    );
}
