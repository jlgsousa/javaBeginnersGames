package daemmerwald;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.function.Supplier;

/** Zeigt „Die Welt wird geladen …“ und baut die nächste Szene erst im folgenden Bild (sonst wirkt das Spiel eingefroren). */
public class LoadingScene implements Scene {
    private final GamePanel game;
    private final Supplier<Scene> next;
    private int frames;
    private double t;

    public LoadingScene(GamePanel game, Supplier<Scene> next) {
        this.game = game;
        this.next = next;
    }

    @Override
    public void update(double dt) {
        t += dt;
        if (++frames == 3) game.setScene(next.get());
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(10, 8, 18));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 40));
        Art.shadowCentered(g, "Die Welt wird geladen …", GamePanel.W / 2.0, GamePanel.H / 2.0, Art.GOLD);
        g.setFont(Art.SMALL);
        Art.centered(g, "Sternenhall öffnet seine Tore", GamePanel.W / 2.0, GamePanel.H / 2.0 + 40);
    }
}
