package daemmerwald;

import java.awt.Color;

/** Aussehen einer Figur, wird prozedural gezeichnet. */
public class Look {
    public static final int TROLL = 0, FAIRY = 1, SHROOM = 2, WRAITH = 3, MOUSE = 4;
    public final int kind;
    public final Color skin, cloth, accent, hair, wing;
    public final boolean horns;

    public Look(int kind, Color skin, Color cloth, Color accent, Color hair, Color wing, boolean horns) {
        this.kind = kind;
        this.skin = skin;
        this.cloth = cloth;
        this.accent = accent;
        this.hair = hair;
        this.wing = wing;
        this.horns = horns;
    }

    public static Look troll(int[] skin, int[] cloth, int[] accent, int[] hair, boolean horns) {
        return new Look(TROLL, c(skin), c(cloth), c(accent), c(hair), Color.WHITE, horns);
    }

    public static Look fairy(int[] skin, int[] cloth, int[] accent, int[] hair, int[] wing) {
        return new Look(FAIRY, c(skin), c(cloth), c(accent), c(hair), c(wing), false);
    }

    public static int[] rgb(int r, int g, int b) { return new int[]{r, g, b}; }

    private static Color c(int[] v) { return new Color(v[0], v[1], v[2]); }
}
