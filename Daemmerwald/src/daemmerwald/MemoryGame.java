package daemmerwald;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maus: Erinnerungspaare unter den Pilzkappen finden. */
public class MemoryGame extends MiniGame {
    private static final int COLS = 4, ROWS = 4;
    private static final double CW = 112, CH = 128, GAP = 16, X0 = (1280 - (COLS * CW + (COLS - 1) * GAP)) / 2, Y0 = 108;
    private static final Color[] SYM_COLORS = {new Color(255, 200, 90), new Color(130, 200, 255), new Color(220, 120, 255),
        new Color(140, 230, 140), new Color(255, 120, 120), new Color(255, 255, 255), new Color(120, 255, 220), new Color(255, 160, 210)};
    private static final String[] SYM_NAMES = {"Sonne", "Mond", "Auge", "Blatt", "Flamme", "Stern", "Welle", "Herz"};

    private final int[] sym = new int[COLS * ROWS];
    private final boolean[] up = new boolean[COLS * ROWS], done = new boolean[COLS * ROWS];
    private final double[] anim = new double[COLS * ROWS];
    private int first = -1, second = -1, pairs, misses;
    private double wait, timeLeft, timeMax;

    public MemoryGame(GamePanel g) { super(g); }

    @Override protected String title() { return "Das Gedächtnis der Wurzeln"; }

    @Override protected String subtitle() { return "Das Pilzorakel teilt, was die Erde nie vergisst."; }

    @Override protected String[] rules() {
        return new String[]{
            "Unter den Pilzkappen liegen acht Paare von Erinnerungen.",
            "Klicke zwei Karten an. Passen sie zusammen, bleiben sie offen.",
            "Finde alle Paare, bevor die Zeit abläuft. Ab Stufe 3 tauschen nach jedem Fehlgriff zwei verdeckte Karten heimlich die Plätze."
        };
    }

    @Override protected String winText() { return "Alle Erinnerungen gefunden. Unter deinen Füßen rauscht es, als würde die Erde ausatmen."; }

    @Override protected String loseText() { return "Die Erinnerungen versinken wieder im Boden."; }

    @Override protected Color tint() { return new Color(50, 20, 60); }

    @Override
    protected void start() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < 8; i++) { l.add(i); l.add(i); }
        Collections.shuffle(l, rnd);
        for (int i = 0; i < sym.length; i++) sym[i] = l.get(i);
        timeMax = timeLeft = Math.max(32, 80 - (level - 1) * 7) * timeFactor();
    }

    private double cx(int i) { return X0 + (i % COLS) * (CW + GAP) + CW / 2; }

    private double cy(int i) { return Y0 + (i / COLS) * (CH + GAP) + CH / 2; }

    @Override
    protected void play(double dt) {
        timeLeft -= dt;
        if (timeLeft <= 0) { end(false); return; }
        for (int i = 0; i < anim.length; i++) {
            double target = (up[i] || done[i]) ? 1 : 0;
            anim[i] += Math.signum(target - anim[i]) * Math.min(Math.abs(target - anim[i]), dt * 6);
        }
        if (wait > 0) {
            wait -= dt;
            if (wait <= 0) {
                up[first] = false;
                up[second] = false;
                first = second = -1;
            }
            return;
        }
        if (!in.mouseClicked) return;
        for (int i = 0; i < sym.length; i++) {
            if (Math.abs(in.mouseX - cx(i)) < CW / 2 && Math.abs(in.mouseY - cy(i)) < CH / 2) {
                if (done[i] || up[i]) return;
                up[i] = true;
                if (first < 0) {
                    first = i;
                } else {
                    second = i;
                    if (sym[first] == sym[second]) {
                        done[first] = done[second] = true;
                        up[first] = up[second] = false;
                        first = second = -1;
                        pairs++;
                        if (pairs == 8) end(true);
                    } else {
                        misses++;
                        wait = 0.85;
                        if (level >= 3) swapHidden();
                    }
                }
                return;
            }
        }
    }

    /** Tauscht zwei noch verdeckte Karten (ab Stufe 3). */
    private void swapHidden() {
        List<Integer> hidden = new ArrayList<>();
        for (int i = 0; i < sym.length; i++) if (!done[i] && i != first && i != second) hidden.add(i);
        if (hidden.size() < 2) return;
        Collections.shuffle(hidden, rnd);
        int a = hidden.get(0), b = hidden.get(1);
        int tmp = sym[a];
        sym[a] = sym[b];
        sym[b] = tmp;
    }

    @Override
    protected void draw(Graphics2D g) {
        for (int i = 0; i < sym.length; i++) {
            double a = anim[i];
            double w = CW * Math.abs(1 - 2 * a);
            double x = cx(i), y = cy(i);
            boolean front = a > 0.5;
            RoundRectangle2D r = new RoundRectangle2D.Double(x - w / 2, y - CH / 2, Math.max(1, w), CH, 14, 14);
            if (front) {
                Color c = SYM_COLORS[sym[i]];
                if (done[i]) Art.glow(g, x, y, 90, c, 70);
                g.setPaint(new GradientPaint(0, (float) (y - CH / 2), new Color(40, 34, 56), 0, (float) (y + CH / 2), new Color(20, 16, 30)));
                g.fill(r);
                g.setColor(Art.alpha(c, 200));
                g.setStroke(new BasicStroke(2f));
                g.draw(r);
                if (w > 20) {
                    AffineTransform old = g.getTransform();
                    g.translate(x, y - 8);
                    g.scale(w / CW, 1);
                    symbol(g, sym[i], c);
                    g.setTransform(old);
                    if (w > 90) {
                        g.setFont(Art.SMALL);
                        g.setColor(Art.alpha(c, 220));
                        Art.centered(g, SYM_NAMES[sym[i]], x, y + CH / 2 - 12);
                    }
                }
            } else {
                g.setPaint(new GradientPaint(0, (float) (y - CH / 2), new Color(150, 56, 46), 0, (float) (y + CH / 2), new Color(70, 26, 26)));
                g.fill(r);
                g.setColor(new Color(255, 236, 200, 170));
                if (w > 30) {
                    double sc = w / CW;
                    g.fill(new Ellipse2D.Double(x - 30 * sc, y - 34, 16 * sc, 12));
                    g.fill(new Ellipse2D.Double(x + 8 * sc, y - 18, 20 * sc, 14));
                    g.fill(new Ellipse2D.Double(x - 12 * sc, y + 14, 14 * sc, 10));
                    g.fill(new Ellipse2D.Double(x + 14 * sc, y + 32, 10 * sc, 8));
                }
                g.setColor(new Color(255, 220, 180, 90));
                g.setStroke(new BasicStroke(2f));
                g.draw(r);
            }
        }
        topBar(g, "Paare " + pairs + "/8 · Fehler " + misses, "Zeit: " + (int) Math.ceil(Math.max(0, timeLeft)) + " s",
            timeLeft / timeMax, new Color(220, 140, 255));
    }

    private void symbol(Graphics2D g, int s, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (s) {
            case 0 -> {
                g.fill(new Ellipse2D.Double(-14, -14, 28, 28));
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    g.draw(new Line2D.Double(Math.cos(a) * 20, Math.sin(a) * 20, Math.cos(a) * 30, Math.sin(a) * 30));
                }
            }
            case 1 -> {
                Area m = new Area(new Ellipse2D.Double(-24, -24, 48, 48));
                m.subtract(new Area(new Ellipse2D.Double(-12, -30, 48, 48)));
                g.fill(m);
            }
            case 2 -> {
                Path2D p = new Path2D.Double();
                p.moveTo(-30, 0); p.quadTo(0, -26, 30, 0); p.quadTo(0, 26, -30, 0);
                g.draw(p);
                g.fill(new Ellipse2D.Double(-9, -9, 18, 18));
            }
            case 3 -> {
                Path2D p = new Path2D.Double();
                p.moveTo(-20, 24); p.curveTo(-24, -8, 4, -26, 24, -26); p.curveTo(24, 4, 4, 24, -20, 24);
                g.fill(p);
            }
            case 4 -> {
                Path2D p = new Path2D.Double();
                p.moveTo(0, -30); p.curveTo(20, -8, 20, 22, 0, 26); p.curveTo(-20, 22, -20, -8, 0, -30);
                g.fill(p);
                g.setColor(new Color(255, 230, 150));
                g.fill(new Ellipse2D.Double(-6, 4, 12, 18));
            }
            case 5 -> {
                Path2D p = new Path2D.Double();
                for (int k = 0; k < 10; k++) {
                    double a = -Math.PI / 2 + k * Math.PI / 5, r = k % 2 == 0 ? 28 : 12;
                    if (k == 0) p.moveTo(Math.cos(a) * r, Math.sin(a) * r); else p.lineTo(Math.cos(a) * r, Math.sin(a) * r);
                }
                p.closePath();
                g.fill(p);
            }
            case 6 -> {
                for (int k = 0; k < 3; k++) {
                    Path2D p = new Path2D.Double();
                    double y = -14 + k * 14;
                    p.moveTo(-28, y);
                    p.curveTo(-14, y - 10, -6, y + 10, 0, y);
                    p.curveTo(6, y - 10, 14, y + 10, 28, y);
                    g.draw(p);
                }
            }
            default -> {
                Path2D p = new Path2D.Double();
                p.moveTo(0, 24);
                p.curveTo(-34, 0, -18, -30, 0, -12);
                p.curveTo(18, -30, 34, 0, 0, 24);
                g.fill(p);
            }
        }
    }
}
