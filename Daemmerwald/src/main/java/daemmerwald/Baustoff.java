package daemmerwald;

import java.awt.Color;

/** Baustoffe für das eigene Haus: drei werden in der Welt gesammelt, drei an der Werkbank kombiniert. */
public enum Baustoff {
    HOLZ("Holz", "Totholz und Äste im Wald", new Color(170, 120, 70), true),
    STEIN("Stein", "Felsen, Hänge und Steinbruch", new Color(170, 170, 175), true),
    LEHM("Lehm", "Ufer, Bäche und feuchte Senken", new Color(190, 110, 70), true),
    BRETTER("Bretter", "an der Werkbank kombinieren", new Color(220, 175, 110), false),
    ZIEGEL("Ziegel", "an der Werkbank kombinieren", new Color(200, 80, 60), false),
    KRISTALLGLAS("Kristallglas", "an der Werkbank kombinieren", new Color(170, 230, 255), false);

    public final String title, where;
    public final Color color;
    public final boolean raw;

    Baustoff(String title, String where, Color color, boolean raw) {
        this.title = title;
        this.where = where;
        this.color = color;
        this.raw = raw;
    }
}
