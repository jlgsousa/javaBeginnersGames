package daemmerwald;

import java.awt.Color;

/**
 * Raritäten – super seltene Fundstücke für die Sammlung in der Tasche.
 * Manche findet man nur nachts, nur an bestimmten Orten, in Geheimgängen des Palasts oder als Geschenk von Freunden.
 */
public enum Treasure {
    // ---- selten
    GOLDENE_EICHEL("Goldene Eichel", 0, "Unter alten Bäumen im Wald – ganz selten glänzt eine.", "Wer sie pflanzt, dem wächst angeblich ein Baum voller Glück."),
    GLUEHGLAS("Glühwürmchen im Glas", 0, "Auf hellen Wiesen, nur nachts.", "Ein Glas voller winziger Sterne, die summen."),
    MONDMOOS("Silbernes Mondmoos", 0, "Am Ufer des Mondsees, nur nachts.", "Es leuchtet so lange, wie der Mond scheint."),
    REGENSCHUPPE("Regenbogenschuppe", 0, "Am Wasser – nach einem Regenbogen.", "Von einem Fisch, der den Regenbogen gefressen hat."),
    FLUESTERMUSCHEL("Flüsternde Muschel", 0, "An der Südküste, unter der Klippe.", "Halt sie ans Ohr: Sie verrät Geheimnisse."),
    UHRWERKKAEFER("Uhrwerk-Käfer", 0, "Im Feendorf Laternhain.", "Ein kleiner Käfer aus Messing. Er tickt, wenn man ihn streichelt."),
    // ---- sehr selten
    MONDPERLE("Mondperle", 1, "Im Mondsee – nur in klaren Nächten.", "Eine Perle, in der ein kleiner Mond schwimmt."),
    EINHORNHAAR("Einhornhaar", 1, "Auf den hellsten Wiesen, bei Sonnenaufgang.", "Ein silbernes Haar, das nie zerreißt."),
    FEENKRONE("Winzige Feenkrone", 1, "In Laternhain, wenn die Lichterketten leuchten.", "So klein wie ein Fingerhut – und doch eine echte Krone."),
    GEISTERLATERNE("Geisterlaterne", 1, "An dunklen Orten der Welke.", "Ihr kaltes Licht zeigt Dinge, die sonst unsichtbar sind."),
    KRISTALLROSE("Kristallrose", 1, "Ein Geschenk unter echten Freunden.", "Sie blüht nur für die, die sie geschenkt bekommen."),
    STERNENSTAUB("Phiole Sternenstaub", 1, "Im Geheimgang hinter dem Bücherregal des Palasts.", "Ein Hauch davon, und du schwebst einen Atemzug lang."),
    ZEITSAND("Sanduhr voll Zeitsand", 1, "Im Geheimgang hinter dem Kamin des Palasts.", "Der Sand rieselt nach oben."),
    DRACHENSCHUPPE("Drachenschuppe", 1, "Weit draußen in den Bergen der Wildnis.", "Warm wie ein Ofenstein – und härter als Stahl."),
    // ---- legendär
    PHOENIXFEDER("Phönixfeder", 2, "Weit draußen in der Wildnis, wo sonst niemand hinkommt.", "Sie glüht, ohne zu verbrennen."),
    DRACHENEI("Schlafendes Drachenei", 2, "Irgendwo in der endlosen Wildnis – fast nie gesehen.", "Manchmal bewegt es sich ein kleines bisschen."),
    STERNKARTE("Uralte Sternkarte", 2, "In der verborgenen Sternwarte des Palasts.", "Sie zeigt Sterne, die es noch gar nicht gibt."),
    SPIEGELSCHERBE("Spiegelscherbe der Wahrheit", 2, "Im tiefsten Geheimgang unter dem Palast.", "Wer hineinsieht, sieht sich so, wie Freunde einen sehen."),
    HERZSAMEN("Samen des Herzbaums", 2, "Nur von einem allerbesten Freund.", "Daraus könnte ein neuer Herzbaum wachsen.");

    public final String title, where, text;
    /** 0 = selten, 1 = sehr selten, 2 = legendär */
    public final int rarity;

    Treasure(String title, int rarity, String where, String text) {
        this.title = title;
        this.rarity = rarity;
        this.where = where;
        this.text = text;
    }

    public static final String[] RARITY = {"selten", "sehr selten", "legendär"};

    public Color color() {
        return switch (rarity) {
            case 0 -> new Color(110, 180, 255);
            case 1 -> new Color(200, 120, 255);
            default -> new Color(255, 200, 70);
        };
    }

    /** Glanzfarbe des Fundstücks in der Welt. */
    public Color glow() {
        return switch (this) {
            case GOLDENE_EICHEL, PHOENIXFEDER, HERZSAMEN -> new Color(255, 200, 80);
            case GLUEHGLAS, FEENKRONE -> new Color(255, 240, 150);
            case MONDMOOS, MONDPERLE, EINHORNHAAR -> new Color(200, 225, 255);
            case REGENSCHUPPE -> new Color(255, 150, 220);
            case GEISTERLATERNE -> new Color(140, 255, 210);
            case DRACHENSCHUPPE, DRACHENEI -> new Color(255, 110, 70);
            default -> color();
        };
    }
}
