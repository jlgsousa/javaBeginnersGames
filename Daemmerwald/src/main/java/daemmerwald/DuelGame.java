package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;

/** Tastatur: Zauberduell als Kräftemessen zweier Strahlen. */
public class DuelGame extends MiniGame {
    /** Weltpositionen der beiden Duellanten (für die Strahlen in der 3D-Ansicht). */
    public static com.jme3.math.Vector3f heroWorld, enemyWorld;
    private final String enemyName;
    private final Look enemyLook;
    private double p, elapsed, waveT, flash, shield, hitFx;
    private int[] combo;
    private int ci, rounds;
    private boolean blocked;

    public DuelGame(GamePanel g, String enemyName, Look enemyLook) {
        super(g);
        this.enemyName = enemyName;
        this.enemyLook = enemyLook;
    }

    @Override protected String title() { return "Duell: " + enemyName; }

    @Override protected String subtitle() { return "Kein Kampf auf Leben und Tod – ein Ringen der Willen."; }

    @Override protected String[] rules() {
        return new String[]{
            "Die Strahlen prallen aufeinander. Tippe die angezeigten Pfeile (Pfeiltasten oder WASD), um deinen Strahl vorzuschieben.",
            "Ein falscher Pfeil wirft dich zurück, und die Folge beginnt neu.",
            "Erscheint »SCHILD!«, drücke sofort die [Leertaste], sonst trifft dich die Welle.",
            "Schiebe den Strahl ganz bis zu deinem Gegenüber."
        };
    }

    @Override protected String winText() { return "Der Gegenstrahl bricht. Niemand ist verletzt – nur ein Wille hat nachgegeben."; }

    @Override protected String loseText() { return "Du wirst zurückgedrängt und gehst in die Knie. Erschöpft, aber unversehrt."; }

    @Override protected Color tint() { return new Color(50, 20, 60); }

    @Override
    protected void start() {
        p = 0;
        newCombo();
        waveT = 6 + rnd.nextDouble() * 2;
    }

    private void newCombo() {
        int len = Math.min(9, 4 + rounds / 2 + (level - 1) / 2);
        combo = new int[len];
        for (int i = 0; i < len; i++) combo[i] = rnd.nextInt(4);
        ci = 0;
    }

    private int readDir() {
        if (in.wasPressed(KeyEvent.VK_UP, KeyEvent.VK_W)) return 0;
        if (in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) return 1;
        if (in.wasPressed(KeyEvent.VK_DOWN, KeyEvent.VK_S)) return 2;
        if (in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A)) return 3;
        return -1;
    }

    @Override
    protected void play(double dt) {
        elapsed += dt;
        flash = Math.max(0, flash - dt);
        shield = Math.max(0, shield - dt);
        hitFx = Math.max(0, hitFx - dt);
        double drain = 0.035 + level * 0.01 + elapsed * 0.0012;
        p -= drain * dt;

        double power = (1 + (hero.macht - 3) * 0.08) * (game.state.has(Ability.STERN) ? 1.3 : 1.0);
        int d = readDir();
        if (d >= 0) {
            if (d == combo[ci]) {
                ci++;
                p += 0.012 * power;
                if (ci == combo.length) {
                    p += 0.13 * power;
                    rounds++;
                    hitFx = 0.4;
                    newCombo();
                }
            } else {
                p -= 0.07;
                flash = 0.35;
                newCombo();
            }
        }
        waveT -= dt;
        boolean warning = waveT < 1.2;
        if (warning && !blocked && in.wasPressed(KeyEvent.VK_SPACE)) {
            blocked = true;
            shield = 0.6;
        }
        if (waveT <= 0) {
            if (!blocked) { p -= 0.15 + level * 0.01; flash = 0.6; }
            blocked = false;
            waveT = Math.max(3.5, 6.5 - level * 0.3) + rnd.nextDouble() * 2;
        }
        if (p >= 1) end(true);
        else if (p <= -1) end(false);
    }

    @Override
    protected void draw(Graphics2D g) {
        double hx = 250, hy = 590, ex = 1030, ey = 590;
        double sx = hx + 50, sy = hy - 110, tx = ex - 50, ty = hy - 120;
        if (Art.THREE_D && heroWorld != null) {
            com.jme3.renderer.Camera cam = game.app.getCamera();
            com.jme3.math.Vector3f a = cam.getScreenCoordinates(heroWorld), b = cam.getScreenCoordinates(enemyWorld);
            sx = a.x * 1280 / cam.getWidth();
            sy = 720 - a.y * 720 / cam.getHeight();
            tx = b.x * 1280 / cam.getWidth();
            ty = 720 - b.y * 720 / cam.getHeight();
            hx = sx;
            hy = sy + 110;
        }
        Art.glow(g, hx, hy - 80, 160, hero.look.accent, 60);
        Art.glow(g, ex, ey - 80, 160, enemyLook.accent, 70);
        Art.drawCharacter(g, hero.look, hx, hy, 2.6, total, false, 1);
        Art.drawCharacter(g, enemyLook, ex, ey, 2.8, total, false, -1);

        double f = (p + 1) / 2;
        double cx = sx + (tx - sx) * f, cy = sy + (ty - sy) * f + Math.sin(total * 13) * 4;
        g.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Art.alpha(Art.MAGIC, 90));
        g.draw(new Line2D.Double(sx, sy, cx, cy));
        g.setColor(Art.alpha(Art.BLIGHT, 90));
        g.draw(new Line2D.Double(cx, cy, tx, ty));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(220, 245, 255));
        g.draw(new Line2D.Double(sx, sy, cx, cy));
        g.setColor(new Color(240, 200, 255));
        g.draw(new Line2D.Double(cx, cy, tx, ty));
        Art.glow(g, cx, cy, 70 + Math.sin(total * 20) * 10 + hitFx * 100, Color.WHITE, 200);
        for (int i = 0; i < 10; i++) {
            double a = total * 7 + i * 0.63, r = 30 + (i * 13 % 30);
            Art.dot(g, cx + Math.cos(a) * r, cy + Math.sin(a * 1.3) * r, 1.5, i % 2 == 0 ? Art.MAGIC : Art.BLIGHT, 220);
        }
        if (shield > 0) {
            g.setStroke(new BasicStroke(4f));
            g.setColor(Art.alpha(Art.MAGIC, (int) (shield / 0.6 * 255)));
            g.draw(new Ellipse2D.Double(hx - 90, hy - 220, 180, 230));
        }
        if (flash > 0) {
            g.setColor(new Color(160, 40, 200, (int) (flash * 200)));
            g.fillRect(0, 0, 1280, 720);
        }
        if (waveT < 1.2 && !blocked && phase == Phase.PLAY) {
            g.setFont(Art.BIG_UI);
            boolean b = Math.sin(total * 20) > 0;
            Art.shadowCentered(g, "SCHILD!  [Leertaste]", 640, 200, b ? Art.DANGER : Color.WHITE);
            g.setStroke(new BasicStroke(3f));
            g.setColor(Art.alpha(Art.DANGER, 160));
            double r = 120 + waveT * 120;
            g.draw(new Ellipse2D.Double(hx - r, hy - 100 - r, r * 2, r * 2));
        }
        // Kombo
        if (combo != null) {
            double bw = 62, total = combo.length * (bw + 10) - 10, x0 = 640 - total / 2;
            Art.panel(g, x0 - 20, 270, total + 40, 92);
            for (int i = 0; i < combo.length; i++) {
                double bx = x0 + i * (bw + 10);
                boolean done = i < ci;
                g.setColor(done ? new Color(80, 160, 220, 200) : new Color(30, 30, 50, 220));
                g.fill(new RoundRectangle2D.Double(bx, 285, bw, 62, 12, 12));
                g.setStroke(new BasicStroke(2f));
                g.setColor(i == ci ? Art.GOLD : Art.alpha(Color.WHITE, 80));
                g.draw(new RoundRectangle2D.Double(bx, 285, bw, 62, 12, 12));
                Art.arrow(g, bx + bw / 2, 316, 18, combo[i], done ? Color.WHITE : Art.PARCH);
            }
        }
        g.setFont(Art.H2);
        Art.shadowText(g, hero.name, 80, 120, Art.MAGIC);
        FontMetrics fm = g.getFontMetrics();
        Art.shadowText(g, enemyName, 1200 - fm.stringWidth(enemyName), 120, Art.BLIGHT);
        topBar(g, "Willenskraft", "Stufe " + level, f, Art.MAGIC);
    }
}
