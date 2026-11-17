package daemmerwald;

import java.awt.Color;

/** Gewänder aus Pimpernells Laden. Jedes hat eine Farbe, ein Zubehör und ein Gadget. */
public enum Outfit {
    WANDERER("Wanderkluft", "Deine alte Kleidung. Bequem, geflickt, ehrlich.", 0, null, Gadget.NONE, Accessory.NONE),
    EISENHAUT("Eisenhaut-Wams", "Gehärtetes Leder mit Silberfäden. Schattenwesen rauben dir 60 % weniger Mana.", 120,
        new Color(90, 96, 110), Gadget.DEFENSE, Accessory.PAULDRONS),
    FLUEGELMANTEL("Flügelmantel", "Ein Mantel aus Nachtfaltergewebe. Halte [Leertaste], um zu fliegen – solange dein Mana reicht.", 420,
        new Color(60, 70, 140), Gadget.FLIGHT, Accessory.BIG_WINGS),
    WINDLAEUFER("Windläufer-Stiefel & Weste", "Du läufst 30 % schneller, auch ohne Trank.", 160,
        new Color(80, 150, 120), Gadget.SPEED, Accessory.SCARF),
    MONDROBE("Mondrobe", "Gewebt aus Mondlicht: doppelte Mana-Regeneration.", 200,
        new Color(170, 180, 230), Gadget.MANA, Accessory.HALO),
    LATERNENMANTEL("Laternenmantel", "Eine eingenähte Glühlaterne – dein Licht reicht doppelt so weit.", 90,
        new Color(170, 120, 60), Gadget.LIGHT, Accessory.LANTERN),
    TAUCHERANZUG("Seegras-Taucheranzug", "Du kannst durch tiefes Wasser waten und schwimmen.", 260,
        new Color(40, 120, 130), Gadget.SWIM, Accessory.BUBBLE),
    SCHATTENTARN("Schattentarnmantel", "Schattenwesen bemerken dich erst aus nächster Nähe.", 230,
        new Color(36, 30, 46), Gadget.STEALTH, Accessory.HOOD),
    SPRUNGFEDER("Sprungfeder-Gamaschen", "Du springst dreimal so hoch.", 140,
        new Color(200, 160, 70), Gadget.JUMP, Accessory.SCARF),
    GLUECKSWESTE("Glücksweste mit Kleeblatt", "Für jede gesammelte Zutat gibt es manchmal eine zweite.", 180,
        new Color(70, 160, 70), Gadget.LUCK, Accessory.CLOVER),
    SAMMLERSCHUERZE("Sammlerschürze", "Zutaten fliegen dir aus 6 m Entfernung zu.", 110,
        new Color(140, 100, 70), Gadget.MAGNET, Accessory.SATCHEL),
    GELEHRTENROBE("Gelehrtenrobe", "+20 % Erfahrung für alles, was du tust.", 300,
        new Color(110, 40, 50), Gadget.WISDOM, Accessory.HOOD),
    UHRMACHERWAMS("Uhrmacher-Wams", "+20 % Zeit in allen zeitbegrenzten Prüfungen.", 240,
        new Color(120, 90, 50), Gadget.TIME, Accessory.MONOCLE),
    DORNENPANZER("Dornenpanzer", "Schattenwesen, die dich berühren, werden sofort vertrieben.", 380,
        new Color(70, 50, 60), Gadget.THORNS, Accessory.PAULDRONS),
    FESTGEWAND("Festgewand der Lichtwiese", "Freundliche Lichtfüchse folgen dir und schenken doppelt Mana.", 150,
        new Color(240, 200, 120), Gadget.FRIENDS, Accessory.FLOWERS),
    STURMKAPPE("Sturmkappe", "Regen und Nebel stören deine Sicht kaum noch.", 120,
        new Color(90, 110, 140), Gadget.CLEARSIGHT, Accessory.HOOD),
    HAENDLERROCK("Händlerrock", "Du verdienst 30 % mehr Glimmertaler.", 260,
        new Color(130, 60, 120), Gadget.GREED, Accessory.SATCHEL),
    STERNENGEWAND("Sternengewand", "Das Prachtstück: Flug, Schutz und Lichtschein in einem.", 1200,
        new Color(30, 30, 70), Gadget.ALL, Accessory.BIG_WINGS);

    public enum Gadget { NONE, DEFENSE, FLIGHT, SPEED, MANA, LIGHT, SWIM, STEALTH, JUMP, LUCK, MAGNET, WISDOM, TIME, THORNS, FRIENDS, CLEARSIGHT, GREED, ALL }

    public enum Accessory { NONE, PAULDRONS, BIG_WINGS, SCARF, HALO, LANTERN, BUBBLE, HOOD, CLOVER, SATCHEL, MONOCLE, FLOWERS }

    public final String title, desc;
    public final int price;
    public final Color color;
    public final Gadget gadget;
    public final Accessory accessory;

    Outfit(String title, String desc, int price, Color color, Gadget gadget, Accessory accessory) {
        this.title = title;
        this.desc = desc;
        this.price = price;
        this.color = color;
        this.gadget = gadget;
        this.accessory = accessory;
    }

    public boolean has(Gadget g) { return gadget == g || (gadget == Gadget.ALL && (g == Gadget.FLIGHT || g == Gadget.DEFENSE || g == Gadget.LIGHT)); }

    /** Look der Spielfigur mit der Farbe dieses Gewands. */
    public Look apply(Look base) {
        if (color == null) return base;
        return new Look(base.kind, base.skin, color, base.accent, base.hair, base.wing, base.horns);
    }
}
