package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;

/** Erzähltext vor der Charakterwahl. */
public class IntroScene implements Scene {
    private final GamePanel game;
    private final Input in;
    private int page;
    private double t, total;

    public IntroScene(GamePanel game) {
        this.game = game;
        this.in = game.input;
    }

    private int spoken = -1;

    @Override
    public void update(double dt) {
        t += dt;
        total += dt;
        // Der Erzähler liest die Anfangsgeschichte vor (Musik leise im Hintergrund)
        if (spoken != page && page < Story.INTRO.length) { spoken = page; Voice.narrate(Story.INTRO[page].trim()); }
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { Voice.stop(); game.setScene(new CharacterSelectScene(game)); return; }
        if (t > 0.4 && (in.confirm() || in.mouseClicked)) {
            page++;
            t = 0;
            if (page >= Story.INTRO.length) { Voice.stop(); game.setScene(new CharacterSelectScene(game)); }
        }
    }

    @Override
    public void render(Graphics2D g) {
        Art.backdrop(g, total);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, 1280, 720);
        if (page >= Story.INTRO.length) return;
        int a = (int) (Math.min(1, t * 1.5) * 255);
        g.setFont(new Font("Serif", Font.PLAIN, 28));
        g.setColor(Art.alpha(Art.PARCH, a));
        java.util.List<String> lines = Art.wrap(g, Story.INTRO[page], 860);
        double y = 360 - lines.size() * 21;
        for (String l : lines) {
            Art.centered(g, l, 640, y);
            y += 42;
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(170, 170, 200, a));
        Art.centered(g, (page + 1) + " / " + Story.INTRO.length + "     [Enter] weiter     [Esc] überspringen", 640, 680);
    }
}
