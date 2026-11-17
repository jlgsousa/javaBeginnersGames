package daemmerwald;

import java.util.function.Function;

/** Nicht-Spieler-Figur in der Welt. */
public class Npc {
    public final String id, name;
    public final Look look;
    public final double x, y;
    public double radius = 56;
    public Shard shard;
    public Function<GamePanel, MiniGame> game;
    public String[] before = {}, after = {}, win = {}, lose = {};
    public String note;

    public Npc(String id, String name, Look look, double tileX, double tileY) {
        this.id = id;
        this.name = name;
        this.look = look;
        this.x = tileX;
        this.y = tileY;
    }
}
