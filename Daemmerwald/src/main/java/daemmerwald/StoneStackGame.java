package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Timing: Runensteine stapeln, Überhänge brechen weg. */
public class StoneStackGame extends MiniGame {
    private int goal() { return 12 + (level - 1) * 2; }
    private static final double BH = 34, GROUND = 650;

    private static class Block {
        double x, w;
        int level;
        Block(double x, double w, int level) { this.x = x; this.w = w; this.level = level; }
    }

    private final List<Block> stack = new ArrayList<>();
    private final List<double[]> falling = new ArrayList<>(); // x, y, w, vy, rot
    private double mx, mw, dir, speed, cam, msgT;
    private String msg = "";

    public StoneStackGame(GamePanel g) { super(g); }

    @Override protected String title() { return "Der Turm der Clans"; }

    @Override protected String subtitle() { return "Clanälteste Grumhild verschränkt die Arme."; }

    @Override protected String[] rules() {
        return new String[]{
            "Ein Runenstein gleitet über dem Turm hin und her.",
            "Drücke [Leertaste] oder klicke, um ihn fallen zu lassen. Was übersteht, bricht ab – der Turm wird schmaler.",
            "Triffst du fast exakt, rastet der Stein perfekt ein.",
            "Baue " + goal() + " Lagen. Fällt ein Stein ins Leere, ist die Prüfung vorbei."
        };
    }

    @Override protected String winText() { return goal() + " Lagen. Grumhild klopft zweimal auf den Turm – Trollsprache für Respekt."; }

    @Override protected String loseText() { return "Der Stein fällt ins Leere. Unten lacht jemand. Nicht böse – aber laut."; }

    @Override protected Color tint() { return new Color(60, 40, 20); }

    @Override
    protected void start() {
        stack.clear();
        stack.add(new Block(640 - 140, 280, 0));
        dir = 1;
        newMover();
    }

    private void newMover() {
        Block top = stack.get(stack.size() - 1);
        mw = top.w;
        dir = -dir;
        mx = dir > 0 ? 140 : 1140 - mw;
        speed = 230 + stack.size() * 22 + (level - 1) * 30 - hero.fokus * 8;
    }

    private double screenY(int level) { return GROUND - (level + 1) * BH + cam; }

    @Override
    protected void play(double dt) {
        msgT = Math.max(0, msgT - dt);
        mx += dir * speed * dt;
        if (mx < 120) { mx = 120; dir = 1; }
        if (mx + mw > 1160) { mx = 1160 - mw; dir = -1; }
        double targetCam = Math.max(0, (stack.size() - 8) * BH);
        cam += (targetCam - cam) * Math.min(1, dt * 5);
        for (int i = falling.size() - 1; i >= 0; i--) {
            double[] f = falling.get(i);
            f[3] += 1100 * dt;
            f[1] += f[3] * dt;
            f[4] += dt * 2;
            if (f[1] > 900) falling.remove(i);
        }
        if (in.wasPressed(KeyEvent.VK_SPACE, KeyEvent.VK_ENTER) || in.mouseClicked) drop();
    }

    private void drop() {
        Block top = stack.get(stack.size() - 1);
        int level = stack.size();
        double left = Math.max(mx, top.x), right = Math.min(mx + mw, top.x + top.w);
        double ov = right - left;
        if (ov <= 0) {
            falling.add(new double[]{mx, screenY(level) - cam, mw, 0, 0});
            end(false);
            return;
        }
        if (Math.abs(mx - top.x) < 7) {
            left = top.x;
            ov = top.w;
            msg = "Perfekt!";
            msgT = 1;
        } else {
            if (mx < top.x) falling.add(new double[]{mx, screenY(level) - cam, top.x - mx, 0, 0});
            else falling.add(new double[]{top.x + top.w, screenY(level) - cam, mx + mw - (top.x + top.w), 0, 0});
        }
        stack.add(new Block(left, ov, level));
        if (stack.size() - 1 >= goal()) end(true);
        else newMover();
    }

    private void stone(Graphics2D g, double x, double y, double w, int seed) {
        RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, BH - 2, 6, 6);
        g.setPaint(new GradientPaint(0, (float) y, new Color(150, 140, 130), 0, (float) (y + BH), new Color(80, 74, 70)));
        g.fill(r);
        g.setColor(new Color(40, 36, 34));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(r);
        if (w > 30) {
            g.setColor(new Color(255, 170, 80, 150));
            double cx = x + w / 2;
            int k = seed % 3;
            if (k == 0) g.draw(new Line2D.Double(cx - 6, y + 8, cx + 6, y + BH - 10));
            if (k == 1) g.draw(new Ellipse2D.Double(cx - 6, y + 9, 12, 12));
            if (k == 2) { g.draw(new Line2D.Double(cx, y + 7, cx, y + BH - 9)); g.draw(new Line2D.Double(cx - 6, y + 14, cx + 6, y + 14)); }
        }
    }

    @Override
    protected void draw(Graphics2D g) {
        g.setColor(new Color(50, 40, 34));
        g.fillRect(0, (int) (GROUND + cam), 1280, 400);
        g.setColor(new Color(70, 58, 48));
        g.fillRect(0, (int) (GROUND + cam), 1280, 6);
        for (Block b : stack) stone(g, b.x, screenY(b.level), b.w, b.level);
        for (double[] f : falling) {
            AffineTransform old = g.getTransform();
            g.rotate(f[4] * 0.3, f[0] + f[2] / 2, f[1] + cam + BH / 2);
            stone(g, f[0], f[1] + cam, f[2], 1);
            g.setTransform(old);
        }
        if (phase == Phase.PLAY) {
            double y = screenY(stack.size());
            Art.glow(g, mx + mw / 2, y + BH / 2, mw * 0.6, new Color(255, 170, 80), 60);
            stone(g, mx, y, mw, stack.size());
        }
        if (msgT > 0) {
            g.setFont(Art.H2);
            Art.shadowCentered(g, msg, 640, 150, Art.alpha(Art.GOOD, (int) (msgT * 255)));
        }
        topBar(g, "Lagen: " + (stack.size() - 1) + " / " + goal(), "Breite: " + (int) stack.get(stack.size() - 1).w,
            (stack.size() - 1) / (double) goal(), new Color(255, 170, 80));
    }
}
