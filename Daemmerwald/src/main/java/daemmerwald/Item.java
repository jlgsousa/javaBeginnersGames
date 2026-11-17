package daemmerwald;

import java.awt.Color;

/** Sammelbare Zutaten – sie wachsen je nach Gegend überall in der endlosen Welt. */
public enum Item {
    MONDBLUME("Mondblume", "helle Wiesen und Lichtungen", new Color(190, 210, 255)),
    KROETENMOOS("Krötenmoos", "Ufer, Moor und Tümpel", new Color(120, 200, 90)),
    ASCHWURZ("Aschwurz", "dunkle Orte der Welke", new Color(200, 90, 255)),
    STERNKRISTALL("Sternkristall", "Felsen, Steinbruch und Berge", new Color(255, 240, 160)),
    SEEROSE("Seerose", "am Wasserrand", new Color(255, 170, 210)),
    FEDER("Greifenfeder", "überall verstreut, selten", new Color(255, 200, 120));

    public final String title, where;
    public final Color color;

    Item(String title, String where, Color color) {
        this.title = title;
        this.where = where;
        this.color = color;
    }
}
