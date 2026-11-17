package daemmerwald;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/** Maus: goldene Irrlichter fangen, dunkle meiden. */
public class WispHuntGame extends MiniGame {
    private int target() { return 20 + (level - 1) * 4; }

    private static class Wisp {
        double x, y, vx, vy, ph, life, max;
        boolean dark;
    }

    private final List<Wisp> wisps = new ArrayList<>();
    private final List<double[]> sparks = new ArrayList<>();
    private int score;
    private double timeLeft, timeMax, spawnT, flash;

    public WispHuntGame(GamePanel g) { super(g); }

    @Override protected String title() { return "Die Irrlichter des Mondsees"; }

    @Override protected String subtitle() { return "Kanzler Oberion sieht dir gelangweilt zu."; }

    @Override protected String[] rules() {
        return new String[]{
            "Klicke goldene Irrlichter an: +1.",
            "Dunkle Schattenlichter kosten dich 2 Punkte. Je länger die Prüfung dauert, desto mehr mischen sich darunter.",
            "Sammle " + target() + " Lichter, bevor die Zeit abläuft."
        };
    }

    @Override protected String winText() { return "Der See leuchtet still. Für einen Augenblick wirken selbst die Toten zufrieden."; }

    @Override protected String loseText() { return "Die Lichter zerstreuen sich über dem Wasser."; }

    @Override protected Color tint() { return new Color(20, 40, 70); }

    @Override
    protected void start() {
        score = 0;
        timeMax = timeLeft = 40 * timeFactor();
        spawnT = 0;
    }

    private double radius() { return 20 + hero.geschick * 1.6; }

    @Override
    protected void play(double dt) {
        timeLeft -= dt;
        flash = Math.max(0, flash - dt);
        if (timeLeft <= 0) { end(score >= target()); return; }
        spawnT -= dt;
        if (spawnT <= 0) {
            spawnT = 0.33 + rnd.nextDouble() * 0.25;
            Wisp w = new Wisp();
            w.x = 120 + rnd.nextDouble() * 1040;
            w.y = 150 + rnd.nextDouble() * 480;
            double a = rnd.nextDouble() * Math.PI * 2, s = 60 + rnd.nextDouble() * 90 + (timeMax - timeLeft) * 2 + (level - 1) * 18;
            w.vx = Math.cos(a) * s;
            w.vy = Math.sin(a) * s * 0.6;
            w.ph = rnd.nextDouble() * 6;
            w.max = Math.max(1.4, 2.6 - (level - 1) * 0.15) + rnd.nextDouble() * 1.6;
            w.dark = rnd.nextDouble() < 0.16 + level * 0.04 + (timeMax - timeLeft) / timeMax * 0.2;
            wisps.add(w);
        }
        for (int i = wisps.size() - 1; i >= 0; i--) {
            Wisp w = wisps.get(i);
            w.life += dt;
            w.x += w.vx * dt;
            w.y += w.vy * dt + Math.sin(w.life * 3 + w.ph) * 50 * dt;
            if (w.life > w.max || w.x < -40 || w.x > 1320 || w.y < 60 || w.y > 760) wisps.remove(i);
        }
        if (in.mouseClicked) {
            for (int i = wisps.size() - 1; i >= 0; i--) {
                Wisp w = wisps.get(i);
                if (hit(in.mouseX, in.mouseY, w.x, w.y, radius())) {
                    if (w.dark) { score = Math.max(0, score - 2); flash = 0.4; }
                    else score++;
                    for (int k = 0; k < 16; k++) {
                        double a = rnd.nextDouble() * Math.PI * 2, s = 40 + rnd.nextDouble() * 140;
                        sparks.add(new double[]{w.x, w.y, Math.cos(a) * s, Math.sin(a) * s, 0.7, w.dark ? 1 : 0});
                    }
                    wisps.remove(i);
                    break;
                }
            }
            if (score >= target()) end(true);
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            double[] s = sparks.get(i);
            s[4] -= dt;
            s[0] += s[2] * dt;
            s[1] += s[3] * dt;
            if (s[4] <= 0) sparks.remove(i);
        }
    }

    @Override
    protected void draw(Graphics2D g) {
        // Seeoberfläche
        g.setPaint(new GradientPaint(0, 560, new Color(20, 40, 70, 120), 0, 720, new Color(10, 20, 40, 220)));
        g.fillRect(0, 560, 1280, 160);
        for (int i = 0; i < 14; i++) {
            double y = 580 + i * 10, off = Math.sin(total + i) * 30;
            g.setColor(new Color(150, 190, 255, 30));
            g.fillRect((int) (200 + off + i * 37 % 300), (int) y, 180, 2);
            g.fillRect((int) (800 - off + i * 53 % 200), (int) y, 140, 2);
        }
        if (flash > 0) {
            g.setColor(new Color(120, 0, 160, (int) (flash * 250)));
            g.fillRect(0, 0, 1280, 720);
        }
        for (Wisp w : wisps) {
            double fade = Math.min(1, Math.min(w.life * 4, (w.max - w.life) * 2));
            double r = radius();
            if (w.dark) {
                Art.glow(g, w.x, w.y, r * 2.4, new Color(140, 40, 200), (int) (fade * 150));
                g.setColor(new Color(20, 0, 30, (int) (fade * 255)));
                g.fill(new Ellipse2D.Double(w.x - r * 0.55, w.y - r * 0.55, r * 1.1, r * 1.1));
                g.setColor(new Color(220, 120, 255, (int) (fade * 200)));
                g.fill(new Ellipse2D.Double(w.x - 3, w.y - 3, 6, 6));
            } else {
                Art.glow(g, w.x, w.y, r * 2.4, new Color(255, 215, 130), (int) (fade * 170));
                g.setColor(new Color(255, 245, 210, (int) (fade * 255)));
                g.fill(new Ellipse2D.Double(w.x - r * 0.4, w.y - r * 0.4, r * 0.8, r * 0.8));
            }
        }
        for (double[] s : sparks) {
            Art.dot(g, s[0], s[1], 1.5, s[5] > 0 ? Art.BLIGHT : new Color(255, 225, 150), (int) (s[4] / 0.7 * 255));
        }
        topBar(g, "Lichter: " + score + " / " + target(), "Zeit: " + (int) Math.ceil(Math.max(0, timeLeft)) + " s",
            score / (double) target(), new Color(255, 215, 130));
    }
}
