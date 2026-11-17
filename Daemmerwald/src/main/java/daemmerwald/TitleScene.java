package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/** Titelbildschirm. */
public class TitleScene implements Scene {
    private final GamePanel game;
    private final Input in;
    private final String[] items = GameState.saveExists()
        ? new String[]{"Fortsetzen", "Neues Spiel", "Steuerung", "Beenden"}
        : new String[]{"Neues Spiel", "Steuerung", "Beenden"};
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
        String it = items[sel];
        if (it.equals("Fortsetzen")) {
            GameState s = GameState.load();
            if (s != null) {
                game.state = s;
                DialogBox.playerName = s.playerName;
                game.setScene(new WorldScene(game));
            }
            return;
        }
        int idx = it.equals("Neues Spiel") ? 0 : it.equals("Steuerung") ? 1 : 2;
        switch (idx) {
            case 0 -> {
                game.state = new GameState();
                game.setScene(new NameScene(game));
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
        Art.ornament(g, 640, 322, 520, t);
        g.setFont(Art.ITALIC);
        g.setColor(new Color(210, 210, 235));
        Art.centered(g, "Trolle. Feen. Ein sterbender Wald. Und ein Pakt, der mehr kostet, als alle zugeben.", 640, 360);

        for (int i = 0; i < items.length; i++) {
            double y = 430 + i * 64;
            boolean s = i == sel;
            RoundRectangle2D r = new RoundRectangle2D.Double(480, y - 30, 320, 50, 28, 28);
            if (s) { Art.star(g, 462, y - 5, 7 + Math.sin(t * 4) * 2, new Color(255, 230, 160)); Art.star(g, 818, y - 5, 7 + Math.sin(t * 4 + 1) * 2, new Color(255, 230, 160)); }
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
        if (!Mixamo.status.isEmpty()) {
            g.setColor(new Color(230, 220, 190));
            Art.centered(g, Mixamo.status, 640, 666);
        }

        if (help) {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, 0, 1280, 720);
            Art.panel(g, 240, 110, 800, 500);
            g.setFont(Art.H1);
            Art.shadowCentered(g, "Steuerung", 640, 170, Art.GOLD);
            g.setFont(Art.BODY);
            g.setColor(Art.PARCH);
            String[] l = {
                "WASD  –  laufen      Maus  –  Kamera drehen      Mausrad  –  Zoom",
                "Linksklick / Q  –  Lichtzauber (verbrennt die Welke, vertreibt Schattenwesen)",
                "Leertaste  –  springen / flattern / fliegen (mit Flügelmantel)",
                "Umschalt  –  rennen (ab Stufe 2)      E  –  sprechen, benutzen",
                "Tab / Esc  –  Menü mit Karte, Garderobe, Inventar, Missionen, Speichern",
                "1–4  –  Tränke trinken. Zutaten sammelst du, indem du darüber läufst.",
                "Alle Prüfungen, Rennen und Rätsel sind endlos wiederholbar.",
                "Ruhe findest du im Café Wolkenrand auf der Klippe im Süden."
            };
            for (int i = 0; i < l.length; i++) g.drawString(l[i], 290, 230 + i * 40);
            g.setFont(Art.UI);
            Art.shadowCentered(g, "[Enter] zurück", 640, 585, Art.GOLD);
        }
    }
}
