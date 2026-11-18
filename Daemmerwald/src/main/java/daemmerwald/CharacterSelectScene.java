package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/** Auswahl einer von zehn Figuren – die gewählte Figur steht als 3D-Modell im Wald. */
public class CharacterSelectScene implements Scene {
    private final GamePanel game;
    private final Input in;
    private final List<CharacterDef> all = CharacterDef.ALL;
    private int sel;
    private double t;
    private final int player;

    public CharacterSelectScene(GamePanel game) { this(game, 1); }

    public CharacterSelectScene(GamePanel game, int player) {
        this.game = game;
        this.in = game.input;
        this.player = player;
        if (player == 2) sel = game.state.hero.look.female ? 0 : 1;
    }

    public int selected() { return sel; }

    private double cx(int i) { return 30 + i * 122; }

    @Override
    public void update(double dt) {
        t += dt;
        if (in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A)) sel = (sel + 9) % 10;
        if (in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) sel = (sel + 1) % 10;
        for (int i = 0; i < 10; i++) {
            if (in.mouseClicked && in.mouseX > cx(i) && in.mouseX < cx(i) + 116 && in.mouseY > 590 && in.mouseY < 700) {
                if (sel == i) { choose(); return; }
                sel = i;
            }
        }
        if (in.mouseClicked && in.mouseX > 60 && in.mouseX < 300 && in.mouseY > 504 && in.mouseY < 552) { choose(); return; }
        if (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) choose();
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) game.setScene(player == 2 ? new CharacterSelectScene(game, 1) : new TitleScene(game));
    }

    private void choose() {
        if (player == 2) {
            game.state.hero2 = all.get(sel);
            game.setScene(new WorldScene(game));
            return;
        }
        game.state.hero = all.get(sel);
        if (game.state.twoPlayers) {
            game.state.dayTime = 0.235f;
            game.setScene(new CharacterSelectScene(game, 2));
            return;
        }
        game.state.dayTime = 0.235f;
        game.setScene(new WorldScene(game));
    }

    @Override
    public void render(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 170), 560, 0, new Color(0, 0, 0, 0)));
        g.fillRect(0, 0, 640, 720);
        g.setFont(Art.H1);
        String who = !game.state.twoPlayers ? "Wähle deine Figur"
            : "Figur für " + (player == 1 ? game.state.displayName() : game.state.displayName2());
        Art.shadowText(g, who, 40, 66, Art.GOLD);
        if (game.state.twoPlayers) {
            g.setFont(Art.SMALL);
            Art.shadowText(g, player == 1 ? "Spieler 1 · Tastatur & Maus" : "Spieler 2 · Pfeiltasten oder Gamepad", 42, 90,
                player == 1 ? new Color(150, 200, 255) : new Color(255, 190, 120));
        }
        CharacterDef c = all.get(sel);
        g.setFont(new Font("Serif", Font.BOLD, 34));
        Art.shadowText(g, c.name, 40, 130, Color.WHITE);
        g.setFont(Art.ITALIC);
        Art.shadowText(g, c.epithet + "  ·  " + (c.look.female ? "Hexe" : "Zauberer"), 42, 162, new Color(220, 220, 240));
        g.setFont(new Font("Serif", Font.PLAIN, 19));
        g.setColor(Art.PARCH);
        Art.drawWrapped(g, c.bio, 42, 205, 470, 25);
        String[] names = {"Macht", "Fokus", "Geschick"};
        String[] hint = {"Mana & Duellkraft", "Zeit in Prüfungen", "Tempo & Treffer"};
        int[] vals = {c.macht, c.fokus, c.geschick};
        Color[] cols = {Art.MAGIC, Art.GOLD, Art.GOOD};
        for (int i = 0; i < 3; i++) {
            double y = 380 + i * 30;
            g.setFont(Art.UI);
            Art.shadowText(g, names[i], 42, y, Art.PARCH);
            Art.bar(g, 140, y - 12, 160, 12, vals[i] / 5.0, cols[i]);
            g.setFont(Art.SMALL);
            g.setColor(new Color(200, 200, 215));
            g.drawString(hint[i], 312, (float) y);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(200, 200, 215));
        Art.drawWrapped(g, "Neu an der verlassenen Akademie Sternenhall. Zauber lernst du im Unterricht – Arcanum kannst du schon.", 42, 478, 470, 18);
        RoundRectangle2D btn = new RoundRectangle2D.Double(60, 504, 240, 48, 24, 24);
        g.setColor(Math.sin(t * 4) > 0 ? new Color(90, 70, 140) : new Color(70, 56, 110));
        g.fill(btn);
        g.setColor(Art.GOLD);
        g.draw(btn);
        g.setFont(Art.UI);
        Art.shadowCentered(g, "Losgehen  [Enter]", 180, 534, Color.WHITE);

        for (int i = 0; i < all.size(); i++) {
            CharacterDef d = all.get(i);
            double x = cx(i), y = 590;
            boolean s = i == sel;
            RoundRectangle2D r = new RoundRectangle2D.Double(x, y, 116, 110, 14, 14);
            g.setColor(s ? new Color(60, 50, 96, 230) : new Color(12, 14, 26, 200));
            g.fill(r);
            g.setStroke(new BasicStroke(s ? 2.5f : 1.2f));
            g.setColor(s ? Art.GOLD : Art.alpha(Art.GOLD, 80));
            g.draw(r);
            g.setColor(d.look.cloth);
            g.fill(new java.awt.geom.Ellipse2D.Double(x + 46, y + 12, 24, 24));
            g.setColor(d.look.accent);
            g.fill(new java.awt.geom.Ellipse2D.Double(x + 54, y + 20, 8, 8));
            g.setFont(new Font("Serif", Font.BOLD, 16));
            Art.shadowCentered(g, d.name.split(" ")[0], x + 58, y + 60, s ? Color.WHITE : Art.PARCH);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(190, 190, 210));
            Art.centered(g, d.epithet, x + 58, y + 78);
            Art.centered(g, d.look.female ? "Hexe" : "Zauberer", x + 58, y + 95);
        }
    }
}
