package daemmerwald;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Maus: Runen in einem Zug in der richtigen Reihenfolge nachziehen. */
public class RuneTraceGame extends MiniGame {
    private static final double[][][] PTS = {
        // Pentagramm
        {{0, -0.95}, {0.9, -0.29}, {0.56, 0.81}, {-0.56, 0.81}, {-0.9, -0.29}},
        // Blitz
        {{-0.8, -0.85}, {0.25, -0.85}, {-0.3, -0.05}, {0.6, -0.05}, {-0.1, 0.85}, {0.8, 0.85}},
        // Auge
        {{0, -0.9}, {0.85, 0}, {0, 0.9}, {-0.85, 0}, {0, 0}},
        // Spirale
        {{-0.85, -0.85}, {0.85, -0.85}, {0.85, 0.85}, {-0.6, 0.85}, {-0.6, -0.4}, {0.4, -0.4}, {0.4, 0.4}, {-0.15, 0.4}},
        // Baum
        {{0, 0.9}, {0, 0.1}, {-0.75, -0.4}, {0, -0.92}, {0.75, -0.4}},
        // Sechsstern
        {{0, -0.9}, {0.78, -0.45}, {0.78, 0.45}, {0, 0.9}, {-0.78, 0.45}, {-0.78, -0.45}}
    };
    private static final int[][] ORD = {
        {0, 2, 4, 1, 3, 0},
        {0, 1, 2, 3, 4, 5},
        {0, 1, 2, 3, 0, 4},
        {0, 1, 2, 3, 4, 5, 6, 7},
        {0, 1, 2, 3, 4, 1},
        {0, 3, 1, 4, 2, 5}
    };
    private static final String[] NAMES = {"Ansur – Rune der Bindung", "Thurak – Rune des Bruchs", "Ithel – Rune des Sehens",
        "Morvanh – Rune der Rückkehr", "Eldrun – Rune der Wurzel", "Sêlis – Rune des Gleichgewichts"};

    private static final double CX = 640, CY = 395, SC = 215;

    private int[] chosen = new int[3];
    private int count = 3;
    private double timeMax = 60, previewMax = 2.6;
    private int rune, prog;
    private boolean[] visited;
    private double preview, timeLeft, flash, doneAnim;
    private boolean needRelease;
    private final List<double[]> trail = new ArrayList<>();
    private final List<double[]> sparks = new ArrayList<>();

    public RuneTraceGame(GamePanel g) { super(g); }

    @Override protected String title() { return "Die alten Runen"; }

    @Override protected String subtitle() { return "Magistra Velmara prüft deine Handschrift."; }

    @Override protected String[] rules() {
        return new String[]{
            "Halte die linke Maustaste gedrückt und ziehe die Rune in einem Zug nach.",
            level <= 2 ? "Die Reihenfolge wird kurz gezeigt. Bei der ersten Rune bleiben die Zahlen sichtbar – danach musst du sie dir merken." : "Die Reihenfolge wird nur kurz gezeigt. Ab dieser Stufe musst du sie dir immer merken.",
            "Berührst du einen falschen Knoten, beginnt die Rune von vorn und du verlierst 3 Sekunden.",
            count() + " Runen, bevor die Kerze heruntergebrannt ist."
        };
    }

    @Override protected String winText() { return count + " Runen, sauber gezogen. Velmara nickt – das ist bei ihr ein Lob."; }

    @Override protected String loseText() { return "Die Kerze ist heruntergebrannt."; }

    @Override
    protected void start() {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < PTS.length; i++) pool.add(i);
        java.util.Collections.shuffle(pool, rnd);
        count = count();
        chosen = new int[count];
        for (int i = 0; i < count; i++) chosen[i] = pool.get(i);
        rune = 0;
        timeMax = timeLeft = Math.max(11.0 * count, 20.0 * count - (level - 1) * 3) * timeFactor();
        previewMax = Math.max(1.3, 2.6 - (level - 1) * 0.2);
        setupRune();
    }

    private int count() { return Math.min(5, 3 + (level - 1) / 2); }

    private void setupRune() {
        prog = 0;
        visited = new boolean[pts().length];
        preview = previewMax;
        trail.clear();
    }

    private double[][] pts() { return PTS[chosen[rune]]; }

    private int[] ord() { return ORD[chosen[rune]]; }

    private double sx(int i) { return CX + pts()[i][0] * SC; }

    private double sy(int i) { return CY + pts()[i][1] * SC; }

    private void resetStroke() {
        prog = 0;
        visited = new boolean[pts().length];
        trail.clear();
    }

    @Override
    protected void play(double dt) {
        timeLeft -= dt;
        flash = Math.max(0, flash - dt);
        for (int i = sparks.size() - 1; i >= 0; i--) {
            double[] s = sparks.get(i);
            s[4] -= dt;
            s[0] += s[2] * dt;
            s[1] += s[3] * dt;
            if (s[4] <= 0) sparks.remove(i);
        }
        if (timeLeft <= 0) { end(false); return; }
        if (doneAnim > 0) {
            doneAnim -= dt;
            if (doneAnim <= 0) { rune++; setupRune(); }
            return;
        }
        if (preview > 0) { preview -= dt; return; }

        if (!in.mouseDown) {
            needRelease = false;
            if (prog > 0 && prog < ord().length) resetStroke();
            trail.clear();
            return;
        }
        if (needRelease) return;
        trail.add(new double[]{in.mouseX, in.mouseY});
        if (trail.size() > 70) trail.remove(0);

        int target = ord()[prog];
        if (hit(in.mouseX, in.mouseY, sx(target), sy(target), 30)) {
            visited[target] = true;
            prog++;
            for (int k = 0; k < 14; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, s = 40 + rnd.nextDouble() * 120;
                sparks.add(new double[]{sx(target), sy(target), Math.cos(a) * s, Math.sin(a) * s, 0.6});
            }
            if (prog == ord().length) {
                if (rune == count - 1) end(true);
                else doneAnim = 1.0;
            }
            return;
        }
        for (int i = 0; i < pts().length; i++) {
            if (!visited[i] && i != target && hit(in.mouseX, in.mouseY, sx(i), sy(i), 17)) {
                flash = 0.5;
                timeLeft -= 3;
                needRelease = true;
                resetStroke();
                break;
            }
        }
    }

    @Override
    protected void draw(Graphics2D g) {
        topBar(g, "Rune " + Math.min(count, rune + 1) + " / " + count, "Zeit: " + (int) Math.ceil(Math.max(0, timeLeft)) + " s",
            timeLeft / timeMax, Art.GOLD);
        // Zauberkreis
        AffineTransform old = g.getTransform();
        g.translate(CX, CY);
        g.rotate(total * 0.08);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(150, 180, 255, 50));
        g.draw(new Ellipse2D.Double(-280, -280, 560, 560));
        g.draw(new Ellipse2D.Double(-262, -262, 524, 524));
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            g.draw(new Line2D.Double(Math.cos(a) * 262, Math.sin(a) * 262, Math.cos(a) * 280, Math.sin(a) * 280));
        }
        g.setTransform(old);

        if (flash > 0) {
            g.setColor(new Color(255, 60, 60, (int) (flash * 160)));
            g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        }

        g.setFont(Art.ITALIC);
        g.setColor(new Color(200, 205, 230));
        Art.centered(g, NAMES[chosen[Math.min(count - 1, rune)]], CX, 108);

        int[] o = ord();
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Vorschau
        if (preview > 0) {
            double f = Art.clamp01((previewMax - preview) / (previewMax * 0.8)) * (o.length - 1);
            g.setColor(new Color(255, 230, 160, 200));
            for (int i = 0; i < o.length - 1; i++) {
                if (i >= f) break;
                double seg = Math.min(1, f - i);
                double ax = sx(o[i]), ay = sy(o[i]), bx = sx(o[i + 1]), by = sy(o[i + 1]);
                g.draw(new Line2D.Double(ax, ay, ax + (bx - ax) * seg, ay + (by - ay) * seg));
            }
            g.setFont(Art.UI);
            Art.shadowCentered(g, "Präge dir die Reihenfolge ein …", CX, 670, Art.GOLD);
        }
        // Erledigte Segmente
        g.setColor(new Color(140, 210, 255, 230));
        for (int i = 0; i < prog - 1; i++) {
            g.draw(new Line2D.Double(sx(o[i]), sy(o[i]), sx(o[i + 1]), sy(o[i + 1])));
        }
        if (doneAnim > 0) {
            Art.glow(g, CX, CY, 300 * (1 - doneAnim) + 50, Art.MAGIC, (int) (doneAnim * 160));
            g.setFont(Art.H2);
            Art.shadowCentered(g, "Rune vollendet", CX, 670, Art.GOOD);
        }
        // Knoten
        for (int i = 0; i < pts().length; i++) {
            double x = sx(i), y = sy(i);
            boolean v = visited[i];
            Art.glow(g, x, y, 34, v ? Art.MAGIC : Art.GOLD, v ? 140 : 70);
            g.setColor(v ? new Color(200, 240, 255) : new Color(60, 54, 70));
            g.fill(new Ellipse2D.Double(x - 13, y - 13, 26, 26));
            g.setStroke(new BasicStroke(2f));
            g.setColor(Art.GOLD);
            g.draw(new Ellipse2D.Double(x - 13, y - 13, 26, 26));
        }
        // Nummern
        if ((rune == 0 && level <= 2) || preview > 0) {
            g.setFont(Art.UI);
            for (int k = 0; k < o.length; k++) {
                int i = o[k];
                double x = sx(i), y = sy(i);
                double ox = (x - CX) * 0.14, oy = (y - CY) * 0.14;
                if (Math.hypot(ox, oy) < 5) oy = -30;
                String label = String.valueOf(k + 1);
                boolean dup = false;
                for (int j = 0; j < k; j++) if (o[j] == i) dup = true;
                if (dup) { label = "→" + (k + 1); oy += 18; }
                Art.shadowCentered(g, label, x + ox, y + oy + 6, Art.PARCH);
            }
        }
        // Spur
        if (trail.size() > 1) {
            Path2D p = new Path2D.Double();
            p.moveTo(trail.get(0)[0], trail.get(0)[1]);
            for (double[] q : trail) p.lineTo(q[0], q[1]);
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(150, 220, 255, 60));
            g.draw(p);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(220, 245, 255, 220));
            g.draw(p);
        }
        for (double[] s : sparks) Art.dot(g, s[0], s[1], 1.6, Art.MAGIC, (int) (s[4] / 0.6 * 255));
    }
}
