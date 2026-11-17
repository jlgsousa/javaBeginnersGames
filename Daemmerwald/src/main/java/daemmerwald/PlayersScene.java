package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/** Neues Spiel: allein oder zu zweit am geteilten Bildschirm? */
public class PlayersScene implements Scene {
    private final GamePanel game;
    private final Input in;
    private int sel;
    private double t;

    public PlayersScene(GamePanel game) {
        this.game = game;
        this.in = game.input;
    }

    private RoundRectangle2D card(int i) { return new RoundRectangle2D.Double(i == 0 ? 250 : 670, 250, 360, 300, 30, 30); }

    @Override
    public void update(double dt) {
        t += dt;
        if (in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A, KeyEvent.VK_1, KeyEvent.VK_NUMPAD1)) sel = 0;
        if (in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D, KeyEvent.VK_2, KeyEvent.VK_NUMPAD2)) sel = 1;
        for (int i = 0; i < 2; i++) {
            if (card(i).contains(in.mouseX, in.mouseY)) {
                sel = i;
                if (in.mouseClicked) { choose(); return; }
            }
        }
        if (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) { choose(); return; }
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) game.setScene(new TitleScene(game));
    }

    private void choose() {
        game.state.twoPlayers = sel == 1;
        game.setScene(new NameScene(game, 1));
    }

    @Override
    public void render(Graphics2D g) {
        Art.backdrop(g, t);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 44));
        Art.shadowCentered(g, "Wer zieht in den Dämmerwald?", 640, 150, Art.GOLD);
        Art.ornament(g, 640, 178, 420, t);
        String[][] txt = {
            {"1 Spieler", "Allein durch den Wald", "Tastatur & Maus"},
            {"2 Spieler", "Zu zweit, geteilter Bildschirm", "Spieler 2: Pfeiltasten oder Gamepad"}};
        for (int i = 0; i < 2; i++) {
            RoundRectangle2D r = card(i);
            boolean s = i == sel;
            Art.panel(g, r.getX(), r.getY(), r.getWidth(), r.getHeight());
            if (s) {
                g.setStroke(new BasicStroke(3f));
                g.setColor(Art.alpha(Art.GOLD, (int) (170 + 80 * Math.sin(t * 4))));
                g.draw(new RoundRectangle2D.Double(r.getX() - 6, r.getY() - 6, r.getWidth() + 12, r.getHeight() + 12, 36, 36));
            }
            double cx = r.getCenterX();
            // kleine Figuren-Silhouetten
            int n = i + 1;
            for (int k = 0; k < n; k++) {
                double fx = cx + (n == 1 ? 0 : (k == 0 ? -46 : 46));
                Color c = k == 0 ? new Color(150, 200, 255) : new Color(255, 190, 120);
                Art.glow(g, fx, 350, 60, c, s ? 90 : 40);
                g.setColor(Art.alpha(c, 230));
                g.fill(new java.awt.geom.Ellipse2D.Double(fx - 14, 300, 28, 28));
                g.fill(new RoundRectangle2D.Double(fx - 20, 332, 40, 62, 18, 18));
            }
            if (i == 1) {
                g.setColor(Art.alpha(Art.GOLD, 140));
                g.setStroke(new BasicStroke(2f));
                g.drawLine((int) cx, 292, (int) cx, 402);
            }
            g.setFont(new Font("Serif", Font.BOLD, 34));
            Art.shadowCentered(g, txt[i][0], cx, 452, s ? Color.WHITE : Art.PARCH);
            g.setFont(Art.ITALIC);
            g.setColor(new Color(220, 215, 235));
            Art.centered(g, txt[i][1], cx, 488);
            g.setFont(Art.SMALL);
            g.setColor(new Color(190, 185, 210));
            Art.centered(g, txt[i][2], cx, 516);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 185, 210));
        Art.centered(g, "← → wählen · Enter bestätigen · Esc zurück", 640, 620);
    }
}
