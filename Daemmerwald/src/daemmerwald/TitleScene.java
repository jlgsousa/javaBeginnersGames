package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/** Titelbildschirm. */
public class TitleScene implements Scene {
    private final GamePanel game;
    private final Input in;
    private final String[] items = {"Neues Spiel", "Steuerung", "Beenden"};
    private int sel;
    private boolean help;
    private double t;

    public TitleScene(GamePanel game) {
        this.game = game;
        this.in = game.input;
    }

    @Override
    public void update(double dt) {
        t += dt;
        if (help) {
            if (in.confirm() || in.wasPressed(KeyEvent.VK_ESCAPE) || in.mouseClicked) help = false;
            return;
        }
        if (in.wasPressed(KeyEvent.VK_UP, KeyEvent.VK_W)) sel = (sel + items.length - 1) % items.length;
        if (in.wasPressed(KeyEvent.VK_DOWN, KeyEvent.VK_S)) sel = (sel + 1) % items.length;
        for (int i = 0; i < items.length; i++) {
            if (Math.abs(in.mouseX - 640) < 160 && Math.abs(in.mouseY - (430 + i * 64)) < 26) {
                sel = i;
                if (in.mouseClicked) choose();
            }
        }
        if (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) choose();
    }

    private void choose() {
        switch (sel) {
            case 0 -> {
                game.state = new GameState();
                game.setScene(new IntroScene(game));
            }
            case 1 -> help = true;
            default -> System.exit(0);
        }
    }

    @Override
    public void render(Graphics2D g) {
        Art.backdrop(g, t);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 0, 1280, 720);
        Art.glow(g, 640, 210, 380, new Color(150, 120, 255), 50);
        g.setFont(Art.SMALL);
        g.setColor(new Color(200, 200, 230));
        Art.centered(g, "E I N E   G E S C H I C H T E   A U S   W U R Z E L H A I N", 640, 140);
        g.setFont(Art.TITLE);
        Art.shadowCentered(g, "Das Flüstern", 640, 225, Art.GOLD);
        Art.shadowCentered(g, "im Dämmerwald", 640, 300, Art.GOLD);
        g.setFont(Art.ITALIC);
        g.setColor(new Color(210, 210, 235));
        Art.centered(g, "Trolle. Feen. Ein sterbender Wald. Und ein Pakt, der mehr kostet, als alle zugeben.", 640, 350);

        for (int i = 0; i < items.length; i++) {
            double y = 430 + i * 64;
            boolean s = i == sel;
            RoundRectangle2D r = new RoundRectangle2D.Double(480, y - 30, 320, 50, 24, 24);
            g.setColor(s ? new Color(60, 50, 90, 220) : new Color(20, 20, 36, 170));
            g.fill(r);
            g.setColor(s ? Art.GOLD : Art.alpha(Art.GOLD, 80));
            g.setStroke(new BasicStroke(2f));
            g.draw(r);
            g.setFont(Art.H2);
            Art.shadowCentered(g, items[i], 640, y + 4, s ? Color.WHITE : Art.PARCH);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(160, 160, 190));
        Art.centered(g, "Pfeiltasten / Maus  ·  Enter zum Bestätigen", 640, 690);

        if (help) {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, 0, 1280, 720);
            Art.panel(g, 240, 110, 800, 500);
            g.setFont(Art.H1);
            Art.shadowCentered(g, "Steuerung", 640, 170, Art.GOLD);
            g.setFont(Art.BODY);
            g.setColor(Art.PARCH);
            String[] l = {
                "WASD / Pfeiltasten  –  laufen",
                "E / Enter  –  sprechen, Dialog weiter",
                "Leertaste  –  Lichtzauber: verbrennt die Welke, vertreibt Schattenwesen",
                "Umschalt  –  Windschritt (ab Stufe 2)",
                "M  –  Karte      Esc  –  Tagebuch / Pause",
                "Prüfungen: Maus oder Tastatur – steht jeweils dabei.",
                "Jede Prüfung ist beliebig oft wiederholbar und wird jedes Mal schwerer.",
                "Mit Erfahrung steigst du auf und lernst neue Fähigkeiten."
            };
            for (int i = 0; i < l.length; i++) g.drawString(l[i], 290, 230 + i * 40);
            g.setFont(Art.UI);
            Art.shadowCentered(g, "[Enter] zurück", 640, 585, Art.GOLD);
        }
    }
}
