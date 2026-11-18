package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Random;
import java.util.function.Consumer;

/** Gemeinsamer Rahmen aller Prüfungen: Einleitung -> Spiel -> Ergebnis. */
public abstract class MiniGame implements Scene {
    public enum Result { WIN, LOSE, CANCEL }

    protected enum Phase { INTRO, PLAY, RESULT }

    protected final GamePanel game;
    protected final Input in;
    protected final CharacterDef hero;
    protected final Random rnd = new Random();
    protected Phase phase = Phase.INTRO;
    protected double t, total;
    protected boolean won;
    public Consumer<Result> onDone;
    /** Schwierigkeitsstufe (1 = leicht, steigt bei jedem Sieg). */
    public int level = 1;
    public double xpMult = 1;

    public int xpReward() { return (int) ((30 + 20 * level) * xpMult); }

    protected MiniGame(GamePanel game) {
        this.game = game;
        this.in = game.input;
        this.hero = game.state.hero;
    }

    protected abstract String title();

    protected abstract String subtitle();

    protected abstract String[] rules();

    protected abstract void start();

    protected abstract void play(double dt);

    protected abstract void draw(Graphics2D g);

    protected String winText() { return "Prüfung bestanden."; }

    protected String loseText() { return "Prüfung nicht bestanden."; }

    protected Color tint() { return new Color(20, 30, 60); }

    protected double timeFactor() {
        return hero.timeFactor() * (game.state.has(Ability.ZEIT) ? 1.15 : 1.0) * (game.state.gadget(Outfit.Gadget.TIME) ? 1.2 : 1.0);
    }

    protected void end(boolean w) {
        won = w;
        phase = Phase.RESULT;
        t = 0;
    }

    private void finish(Result r) {
        if (onDone != null) onDone.accept(r);
    }

    @Override
    public void update(double dt) {
        t += dt;
        total += dt;
        switch (phase) {
            case INTRO -> {
                if (in.wasPressed(KeyEvent.VK_ESCAPE)) {
                    finish(Result.CANCEL);
                } else if (t > 0.3 && (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE) || in.mouseClicked)) {
                    phase = Phase.PLAY;
                    t = 0;
                    start();
                }
            }
            case PLAY -> {
                if (in.wasPressed(KeyEvent.VK_ESCAPE)) end(false);
                else play(dt);
            }
            case RESULT -> {
                if (t > 0.8 && (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_E) || in.mouseClicked)) {
                    finish(won ? Result.WIN : Result.LOSE);
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        Art.backdrop(g, total);
        g.setColor(Art.alpha(tint(), 110));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        if (phase == Phase.INTRO) {
            renderIntro(g);
            return;
        }
        draw(g);
        g.setFont(Art.SMALL);
        g.setColor(new Color(200, 200, 220, 160));
        Art.str(g, "[Esc] Aufgeben", 20, GamePanel.H - 16);
        if (phase == Phase.RESULT) renderResult(g);
    }

    private void renderIntro(Graphics2D g) {
        double w = 820, h = 470, x = (GamePanel.W - w) / 2, y = (GamePanel.H - h) / 2;
        Art.panel(g, x, y, w, h);
        g.setFont(Art.SMALL);
        g.setColor(new Color(180, 180, 200));
        Art.centered(g, "PRÜFUNG  ·  STUFE " + level + "  ·  BELOHNUNG " + xpReward() + " EP", GamePanel.W / 2.0, y + 44);
        g.setFont(Art.H1);
        Art.shadowCentered(g, title(), GamePanel.W / 2.0, y + 92, Art.GOLD);
        g.setFont(Art.ITALIC);
        g.setColor(new Color(200, 205, 225));
        Art.centered(g, subtitle(), GamePanel.W / 2.0, y + 128);
        g.setFont(Art.BODY);
        g.setColor(Art.PARCH);
        double ry = y + 180;
        for (String r : rules()) {
            ry = Art.drawWrapped(g, "•  " + r, x + 60, ry, w - 120, 27) + 6;
        }
        g.setFont(Art.UI);
        boolean blink = Math.sin(total * 4) > -0.4;
        Art.shadowCentered(g, "[Enter] Beginnen        [Esc] Zurück", GamePanel.W / 2.0, y + h - 30,
            blink ? Art.GOLD : Art.alpha(Art.GOLD, 140));
    }

    private void renderResult(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, (int) Math.min(150, t * 400)));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        double w = 640, h = 230, x = (GamePanel.W - w) / 2, y = (GamePanel.H - h) / 2;
        Art.panel(g, x, y, w, h);
        g.setFont(Art.H1);
        Art.shadowCentered(g, won ? "Bestanden" : "Gescheitert", GamePanel.W / 2.0, y + 70, won ? Art.GOOD : Art.DANGER);
        g.setFont(Art.BODY);
        g.setColor(Art.PARCH);
        double ty = y + 115;
        for (String l : Art.wrap(g, won ? winText() : loseText(), w - 80)) {
            Art.centered(g, l, GamePanel.W / 2.0, ty);
            ty += 26;
        }
        if (t > 0.8) {
            g.setFont(Art.UI);
            Art.shadowCentered(g, "[Enter] Weiter", GamePanel.W / 2.0, y + h - 26, Art.GOLD);
        }
    }

    /** Kopfzeile mit zwei Werten. */
    protected void topBar(Graphics2D g, String left, String right, double frac, Color c) {
        Art.panel(g, 240, 14, 800, 54);
        g.setFont(Art.UI);
        Art.shadowText(g, left, 262, 47, Art.PARCH);
        FontMetrics fm = g.getFontMetrics();
        Art.shadowText(g, right, 1018 - fm.stringWidth(right), 47, Art.PARCH);
        if (frac >= 0) Art.bar(g, 470, 34, 340, 14, frac, c);
    }

    protected boolean hit(double x, double y, double cx, double cy, double r) {
        return Math.hypot(x - cx, y - cy) < r;
    }
}
