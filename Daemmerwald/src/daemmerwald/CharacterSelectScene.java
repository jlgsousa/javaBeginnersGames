package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/** Auswahl einer von zehn Figuren. */
public class CharacterSelectScene implements Scene {
    private final GamePanel game;
    private final Input in;
    private final List<CharacterDef> all = CharacterDef.ALL;
    private int sel;
    private double t;
    private static final double CW = 132, CH = 200, X0 = 40, Y0 = 120, GAP = 12;

    public CharacterSelectScene(GamePanel game) {
        this.game = game;
        this.in = game.input;
    }

    private double cx(int i) { return X0 + (i % 5) * (CW + GAP); }

    private double cy(int i) { return Y0 + (i / 5) * (CH + 40); }

    @Override
    public void update(double dt) {
        t += dt;
        if (in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A)) sel = (sel + 9) % 10;
        if (in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) sel = (sel + 1) % 10;
        if (in.wasPressed(KeyEvent.VK_UP, KeyEvent.VK_W, KeyEvent.VK_DOWN, KeyEvent.VK_S)) sel = (sel + 5) % 10;
        for (int i = 0; i < 10; i++) {
            if (in.mouseX > cx(i) && in.mouseX < cx(i) + CW && in.mouseY > cy(i) && in.mouseY < cy(i) + CH) {
                if (in.mouseClicked) {
                    if (sel == i) { choose(); return; }
                    sel = i;
                }
            }
        }
        if (in.mouseClicked && in.mouseX > 920 && in.mouseX < 1200 && in.mouseY > 620 && in.mouseY < 676) { choose(); return; }
        if (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) choose();
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) game.setScene(new TitleScene(game));
    }

    private void choose() {
        game.state.hero = all.get(sel);
        game.setScene(new WorldScene(game));
    }

    @Override
    public void render(Graphics2D g) {
        Art.backdrop(g, t);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, 1280, 720);
        g.setFont(Art.H1);
        Art.shadowText(g, "Wähle deine Figur", 40, 70, Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 190, 215));
        g.drawString("Obere Reihe: Trolle  ·  Untere Reihe: Feen", 40, 98);
        for (int i = 0; i < all.size(); i++) {
            CharacterDef c = all.get(i);
            double x = cx(i), y = cy(i);
            boolean s = i == sel;
            RoundRectangle2D r = new RoundRectangle2D.Double(x, y, CW, CH, 16, 16);
            g.setColor(s ? new Color(54, 46, 84, 235) : new Color(18, 20, 34, 210));
            g.fill(r);
            if (s) Art.glow(g, x + CW / 2, y + 100, 90, c.look.accent, 80);
            g.setStroke(new BasicStroke(s ? 3f : 1.5f));
            g.setColor(s ? Art.GOLD : Art.alpha(Art.GOLD, 70));
            g.draw(r);
            Art.drawCharacter(g, c.look, x + CW / 2, y + 150, 1.6, t + i, s, 1);
            g.setFont(new Font("Serif", Font.BOLD, 15));
            Art.shadowCentered(g, c.name.split(" ")[0], x + CW / 2, y + 178, s ? Color.WHITE : Art.PARCH);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(180, 180, 200));
            Art.centered(g, c.epithet, x + CW / 2, y + 193);
        }
        // Details
        CharacterDef c = all.get(sel);
        Art.panel(g, 770, 30, 480, 660);
        Art.glow(g, 1010, 230, 160, c.look.accent, 70);
        Art.drawCharacter(g, c.look, 1010, 330, 3.3, t, false, 1);
        g.setFont(Art.H2);
        Art.shadowCentered(g, c.name, 1010, 380, Art.GOLD);
        g.setFont(Art.ITALIC);
        g.setColor(new Color(210, 210, 235));
        Art.centered(g, c.epithet + "  ·  " + (c.isTroll() ? "Troll" : "Fee"), 1010, 408);
        g.setFont(new Font("Serif", Font.PLAIN, 18));
        g.setColor(Art.PARCH);
        Art.drawWrapped(g, c.bio, 800, 445, 420, 23);
        String[] names = {"Macht", "Fokus", "Geschick"};
        String[] hint = {"Mana & Duellkraft", "Zeit in Prüfungen", "Tempo & Trefferfläche"};
        int[] vals = {c.macht, c.fokus, c.geschick};
        Color[] cols = {Art.MAGIC, Art.GOLD, Art.GOOD};
        for (int i = 0; i < 3; i++) {
            double y = 560 + i * 26;
            g.setFont(Art.UI);
            g.setColor(Art.PARCH);
            g.drawString(names[i], 800, (float) y);
            Art.bar(g, 900, y - 12, 150, 12, vals[i] / 5.0, cols[i]);
            g.setFont(Art.SMALL);
            g.setColor(new Color(170, 170, 190));
            g.drawString(hint[i], 1062, (float) y);
        }
        RoundRectangle2D btn = new RoundRectangle2D.Double(920, 630, 180, 44, 22, 22);
        boolean pulse = Math.sin(t * 4) > 0;
        g.setColor(pulse ? new Color(90, 70, 140) : new Color(70, 56, 110));
        g.fill(btn);
        g.setColor(Art.GOLD);
        g.draw(btn);
        g.setFont(Art.UI);
        Art.shadowCentered(g, "Wählen [Enter]", 1010, 658, Color.WHITE);
    }
}
