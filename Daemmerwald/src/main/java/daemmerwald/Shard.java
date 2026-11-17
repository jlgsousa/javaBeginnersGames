package daemmerwald;

import java.awt.Color;

/** Die sechs Splitter des Paktsteins. */
public enum Shard {
    WISSEN("Splitter des Wissens", "Bibliothek der Akademie (Norden)", new Color(140, 180, 255)),
    LICHT("Splitter des Lichts", "Feenhof am Mondsee (Westen)", new Color(255, 230, 150)),
    STEIN("Splitter des Steins", "Troll-Steinbruch (Osten)", new Color(230, 150, 90)),
    WASSER("Splitter des Wassers", "Hütte im Moor (Süden)", new Color(110, 220, 200)),
    ERINNERUNG("Splitter der Erinnerung", "Pilzkreis (Südwesten)", new Color(220, 140, 255)),
    WIND("Splitter des Windes", "Dornenschlucht (Südosten, hinter der Welke)", new Color(200, 255, 170));

    public final String title, where;
    public final Color color;

    Shard(String title, String where, Color color) {
        this.title = title;
        this.where = where;
        this.color = color;
    }
}
