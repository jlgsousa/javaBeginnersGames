package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Tastatur: In Mottengestalt durch die Dornenschlucht fliegen. */
public class ThornFlightGame extends MiniGame {
    private static final double TOP = 80, BOTTOM = 700, PR = 13;

    private double x, y, vx, vy, scroll, spawnT, inv, elapsed, dur;
    private int hits;
    private final List<double[]> pillars = new ArrayList<>(); // x, gapY, gapH, w
    private final List<double[]> orbs = new ArrayList<>();    // x, y, phase, r
    private final List<double[]> motes = new ArrayList<>();   // x, y
    private int motesTaken;

    public ThornFlightGame(GamePanel g) { super(g); }

    @Override protected String title() { return "Flug durch die Dornenschlucht"; }

    @Override protected String subtitle() { return "Kestras Flugform trägt dich – solange du nicht zögerst."; }

    @Override protected String[] rules() {
        return new String[]{
            "Steuere die Motte mit den Pfeiltasten oder WASD.",
            "Weiche den Dornenranken und den violetten Welke-Sporen aus. Drei Treffer, und du stürzt ab.",
            "Goldene Funken heilen einen Treffer, wenn du fünf davon sammelst.",
            "Halte " + (int) duration() + " Sekunden durch."
        };
    }

    private double duration() { return 35 + level * 5; }

    @Override protected String winText() { return "Du landest auf der anderen Seite. Deine Flügel zittern noch lange nach."; }

    @Override protected String loseText() { return "Die Dornen erwischen dich. Du landest unsanft im Moos."; }

    @Override protected Color tint() { return new Color(40, 20, 50); }

    @Override
    protected void start() {
        x = 260;
        y = 390;
        hits = 0;
        dur = duration();
        scroll = 250 + (level - 1) * 25;
    }

    @Override
    protected void play(double dt) {
        elapsed += dt;
        inv = Math.max(0, inv - dt);
        if (elapsed >= dur) { end(true); return; }
        double ax = 0, ay = 0;
        if (in.isDown(KeyEvent.VK_LEFT, KeyEvent.VK_A)) ax -= 1;
        if (in.isDown(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) ax += 1;
        if (in.isDown(KeyEvent.VK_UP, KeyEvent.VK_W)) ay -= 1;
        if (in.isDown(KeyEvent.VK_DOWN, KeyEvent.VK_S)) ay += 1;
        double sp = 330 + hero.geschick * 16;
        vx += (ax * sp - vx) * Math.min(1, dt * 10);
        vy += (ay * sp - vy) * Math.min(1, dt * 10);
        x = Math.max(40, Math.min(900, x + vx * dt));
        y = Math.max(TOP + PR, Math.min(BOTTOM - PR, y + vy * dt));

        double speed = scroll + elapsed * 4;
        spawnT -= dt;
        if (spawnT <= 0) {
            double gapH = Math.max(150, 230 - elapsed * 1.5 - level * 6);
            double gy = TOP + gapH / 2 + 20 + rnd.nextDouble() * (BOTTOM - TOP - gapH - 40);
            pillars.add(new double[]{1320, gy, gapH, 46});
            if (rnd.nextDouble() < 0.6) motes.add(new double[]{1343, gy});
            if (rnd.nextDouble() < 0.25 + level * 0.08) orbs.add(new double[]{1500, TOP + 60 + rnd.nextDouble() * 500, rnd.nextDouble() * 6, 15});
            spawnT = Math.max(0.95, 1.7 - elapsed * 0.012 - level * 0.05);
        }
        for (int i = pillars.size() - 1; i >= 0; i--) {
            double[] p = pillars.get(i);
            p[0] -= speed * dt;
            if (p[0] < -80) { pillars.remove(i); continue; }
            double top = p[1] - p[2] / 2, bot = p[1] + p[2] / 2;
            boolean inX = x + PR > p[0] && x - PR < p[0] + p[3];
            if (inX && (y - PR < top || y + PR > bot)) damage();
        }
        for (int i = orbs.size() - 1; i >= 0; i--) {
            double[] o = orbs.get(i);
            o[0] -= speed * 1.25 * dt;
            o[2] += dt;
            o[1] += Math.sin(o[2] * 2.5) * 120 * dt;
            if (o[0] < -40) { orbs.remove(i); continue; }
            if (hit(x, y, o[0], o[1], PR + o[3])) damage();
        }
        for (int i = motes.size() - 1; i >= 0; i--) {
            double[] m = motes.get(i);
            m[0] -= speed * dt;
            if (m[0] < -20) { motes.remove(i); continue; }
            if (hit(x, y, m[0], m[1], PR + 12)) {
                motes.remove(i);
                motesTaken++;
                if (motesTaken % 5 == 0 && hits > 0) hits--;
            }
        }
    }

    private void damage() {
        if (inv > 0) return;
        hits++;
        inv = 1.3;
        if (hits >= 3) end(false);
    }

    @Override
    protected void draw(Graphics2D g) {
        // Schluchtwände
        g.setColor(new Color(24, 16, 28));
        g.fillRect(0, 0, 1280, (int) TOP);
        g.fillRect(0, (int) BOTTOM, 1280, 40);
        for (double[] p : pillars) {
            double top = p[1] - p[2] / 2, bot = p[1] + p[2] / 2;
            vine(g, p[0], TOP, p[3], top - TOP, true);
            vine(g, p[0], bot, p[3], BOTTOM - bot, false);
        }
        for (double[] m : motes) Art.dot(g, m[0], m[1], 3, new Color(255, 220, 130), 230);
        for (double[] o : orbs) {
            Art.glow(g, o[0], o[1], o[3] * 3, Art.BLIGHT, 160);
            g.setColor(new Color(40, 10, 50));
            g.fill(new Ellipse2D.Double(o[0] - o[3] * 0.7, o[1] - o[3] * 0.7, o[3] * 1.4, o[3] * 1.4));
        }
        // Motte
        if (inv <= 0 || Math.sin(total * 30) > 0) {
            Color c = hero.look.accent;
            Art.glow(g, x, y, 50, c, 120);
            double flap = 0.5 + 0.5 * Math.abs(Math.sin(total * 18));
            g.setColor(Art.alpha(Art.mix(c, Color.WHITE, 0.3), 200));
            g.fill(new Ellipse2D.Double(x - 22, y - 18 * flap - 4, 22, 20 * flap + 4));
            g.fill(new Ellipse2D.Double(x, y - 18 * flap - 4, 22, 20 * flap + 4));
            g.setColor(Art.alpha(c, 220));
            g.fill(new Ellipse2D.Double(x - 16, y, 14, 12 * flap + 2));
            g.fill(new Ellipse2D.Double(x + 2, y, 14, 12 * flap + 2));
            g.setColor(new Color(40, 30, 30));
            g.fill(new Ellipse2D.Double(x - 3, y - 10, 6, 20));
        }
        String hp = "◆".repeat(Math.max(0, 3 - hits)) + "◇".repeat(Math.min(3, hits));
        topBar(g, "Leben: " + hp + "   Funken: " + motesTaken, "Noch " + (int) Math.ceil(Math.max(0, dur - elapsed)) + " s",
            elapsed / dur, new Color(200, 255, 170));
    }

    private void vine(Graphics2D g, double x, double y, double w, double h, boolean fromTop) {
        if (h <= 0) return;
        g.setPaint(new GradientPaint((float) x, 0, new Color(50, 70, 40), (float) (x + w), 0, new Color(26, 36, 24)));
        g.fill(new Rectangle2D.Double(x + w * 0.2, y, w * 0.6, h));
        g.setColor(new Color(120, 60, 140));
        for (double yy = y + 8; yy < y + h - 4; yy += 22) {
            Path2D t = new Path2D.Double();
            t.moveTo(x + w * 0.2, yy); t.lineTo(x - 4, yy + 6); t.lineTo(x + w * 0.2, yy + 12);
            t.moveTo(x + w * 0.8, yy + 8); t.lineTo(x + w + 4, yy + 14); t.lineTo(x + w * 0.8, yy + 20);
            g.fill(t);
        }
        double tipY = fromTop ? y + h : y;
        Path2D tip = new Path2D.Double();
        tip.moveTo(x + w * 0.2, tipY);
        tip.lineTo(x + w / 2, tipY + (fromTop ? 16 : -16));
        tip.lineTo(x + w * 0.8, tipY);
        g.fill(tip);
    }
}
