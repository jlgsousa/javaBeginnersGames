package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/** Gedächtnis: Zutaten in der gezeigten Reihenfolge in den Kessel geben. */
public class PotionGame extends MiniGame {
    private static final String[] NAMES = {"Mondtau", "Krötenmoos", "Aschwurz", "Nachtschatten", "Bernsteinharz", "Eisenkraut"};
    private static final Color[] COLORS = {new Color(120, 180, 255), new Color(110, 200, 90), new Color(255, 140, 60),
        new Color(170, 90, 230), new Color(255, 205, 80), new Color(210, 220, 230)};
    private static final double CX = 640, CY = 410, R = 230;
    private int finalLen() { return Math.min(14, 6 + level); }

    private enum S { PAUSE, SHOW, INPUT }

    private S s;
    private final List<Integer> seq = new ArrayList<>();
    private int showIdx, inputIdx, lives, lit = -1;
    private double timer, litT, shake, msgT;
    private String msg = "";
    private Color brew = new Color(80, 160, 90);

    public PotionGame(GamePanel g) { super(g); }

    @Override protected String title() { return "Der Trank gegen die Welke"; }

    @Override protected String subtitle() { return "Ysolde rührt im Kessel und summt ein schiefes Lied."; }

    @Override protected String[] rules() {
        return new String[]{
            "Ysolde zeigt dir eine Folge von Zutaten – sie leuchten nacheinander auf.",
            "Gib sie in derselben Reihenfolge in den Kessel: Klick auf die Zutat oder Tasten 1–6.",
            "Jede Runde kommt eine Zutat dazu. Schaffe eine Folge von " + finalLen() + " Zutaten.",
            "Drei Fehler, und der Kessel kocht über."
        };
    }

    @Override protected String winText() { return "Der Trank schimmert grün wie junges Laub. Das Moor atmet ein wenig leichter."; }

    @Override protected String loseText() { return "Der Kessel kocht über. Es riecht genau so schlimm, wie Ysolde versprochen hat."; }

    @Override protected Color tint() { return new Color(30, 50, 30); }

    @Override
    protected void start() {
        seq.clear();
        for (int i = 0; i < 3; i++) seq.add(rnd.nextInt(6));
        lives = 3;
        s = S.PAUSE;
        timer = 1.0;
    }

    private double ix(int i) { return CX + Math.cos(-Math.PI / 2 + i * Math.PI / 3) * R; }

    private double iy(int i) { return CY + Math.sin(-Math.PI / 2 + i * Math.PI / 3) * R * 0.82; }

    private void light(int i) {
        lit = i;
        litT = 0.42;
    }

    @Override
    protected void play(double dt) {
        litT = Math.max(0, litT - dt);
        shake = Math.max(0, shake - dt);
        msgT = Math.max(0, msgT - dt);
        if (litT <= 0) lit = -1;
        switch (s) {
            case PAUSE -> {
                timer -= dt;
                if (timer <= 0) { s = S.SHOW; showIdx = 0; timer = 0.2; }
            }
            case SHOW -> {
                timer -= dt;
                if (timer <= 0) {
                    if (showIdx < seq.size()) {
                        light(seq.get(showIdx));
                        showIdx++;
                        timer = Math.max(0.32, 0.75 - seq.size() * 0.03 - (level - 1) * 0.03);
                    } else {
                        s = S.INPUT;
                        inputIdx = 0;
                    }
                }
            }
            case INPUT -> {
                int choice = -1;
                for (int k = 0; k < 6; k++) {
                    if (in.wasPressed(KeyEvent.VK_1 + k, KeyEvent.VK_NUMPAD1 + k)) choice = k;
                }
                if (in.mouseClicked) {
                    for (int k = 0; k < 6; k++) if (hit(in.mouseX, in.mouseY, ix(k), iy(k), 58)) choice = k;
                }
                if (choice >= 0) {
                    light(choice);
                    brew = Art.mix(brew, COLORS[choice], 0.5);
                    if (choice == seq.get(inputIdx)) {
                        inputIdx++;
                        if (inputIdx == seq.size()) {
                            if (seq.size() >= finalLen()) { end(true); return; }
                            seq.add(rnd.nextInt(6));
                            msg = "Gut! Noch eine Zutat mehr …";
                            msgT = 1.2;
                            s = S.PAUSE;
                            timer = 1.2;
                        }
                    } else {
                        lives--;
                        shake = 0.5;
                        brew = new Color(120, 60, 40);
                        if (lives <= 0) { end(false); return; }
                        msg = "Falsch! Ysolde zeigt es dir noch einmal.";
                        msgT = 1.4;
                        s = S.PAUSE;
                        timer = 1.4;
                    }
                }
            }
        }
    }

    @Override
    protected void draw(Graphics2D g) {
        double sx = shake > 0 ? Math.sin(total * 70) * shake * 14 : 0;
        AffineTransform old = g.getTransform();
        g.translate(sx, 0);
        // Kessel
        Art.glow(g, CX, CY - 10, 170, brew, 90);
        g.setColor(new Color(255, 140, 60, 140));
        for (int i = 0; i < 5; i++) {
            double fx = CX - 60 + i * 30, h = 26 + Math.sin(total * 10 + i) * 8;
            g.fill(new Ellipse2D.Double(fx - 9, CY + 86 - h, 18, h));
        }
        g.setPaint(new GradientPaint((float) CX - 100, 0, new Color(60, 60, 66), (float) CX + 100, 0, new Color(20, 20, 24)));
        g.fill(new Ellipse2D.Double(CX - 105, CY - 50, 210, 150));
        g.setColor(new Color(40, 40, 46));
        g.fill(new RoundRectangle2D.Double(CX - 112, CY - 52, 224, 22, 12, 12));
        g.setColor(brew);
        g.fill(new Ellipse2D.Double(CX - 96, CY - 50, 192, 22));
        for (int i = 0; i < 6; i++) {
            double bx = CX - 70 + (i * 29) % 140, ph = (total * 1.5 + i * 0.37) % 1;
            g.setColor(Art.alpha(Art.mix(brew, Color.WHITE, 0.4), (int) ((1 - ph) * 200)));
            double r = 4 + ph * 6;
            g.fill(new Ellipse2D.Double(bx - r, CY - 44 - ph * 50 - r, r * 2, r * 2));
        }
        // Zutaten
        for (int i = 0; i < 6; i++) {
            double x = ix(i), y = iy(i);
            boolean on = lit == i;
            Art.glow(g, x, y, on ? 110 : 60, COLORS[i], on ? 220 : 60);
            g.setColor(new Color(30, 30, 40, 220));
            g.fill(new Ellipse2D.Double(x - 46, y - 46, 92, 92));
            g.setStroke(new BasicStroke(on ? 4f : 2f));
            g.setColor(on ? Color.WHITE : Art.alpha(COLORS[i], 200));
            g.draw(new Ellipse2D.Double(x - 46, y - 46, 92, 92));
            ingredient(g, i, x, y, on);
            g.setFont(Art.SMALL);
            Art.shadowCentered(g, (i + 1) + "  " + NAMES[i], x, y + 68, on ? Color.WHITE : Art.PARCH);
        }
        g.setTransform(old);

        String status = switch (s) {
            case PAUSE -> "Ysolde greift nach den Zutaten …";
            case SHOW -> "Merke dir die Reihenfolge!";
            case INPUT -> "Deine Reihenfolge: " + inputIdx + " / " + seq.size();
        };
        g.setFont(Art.H2);
        Art.shadowCentered(g, status, CX, 128, s == S.INPUT ? Art.GOLD : Art.PARCH);
        if (msgT > 0) {
            g.setFont(Art.UI);
            Art.shadowCentered(g, msg, CX, 700, Art.alpha(Art.PARCH, (int) (Math.min(1, msgT) * 255)));
        }
        topBar(g, "Folge: " + seq.size() + " / " + finalLen(), "Kessel: " + "♥".repeat(Math.max(0, lives)),
            (seq.size() - 3) / (double) (finalLen() - 3), new Color(110, 200, 90));
    }

    private void ingredient(Graphics2D g, int i, double x, double y, boolean on) {
        Color c = on ? Art.mix(COLORS[i], Color.WHITE, 0.4) : COLORS[i];
        g.setColor(c);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (i) {
            case 0 -> { // Tropfen
                Path2D p = new Path2D.Double();
                p.moveTo(x, y - 24);
                p.curveTo(x + 18, y, x + 16, y + 20, x, y + 20);
                p.curveTo(x - 16, y + 20, x - 18, y, x, y - 24);
                g.fill(p);
            }
            case 1 -> { // Moos
                for (int k = 0; k < 5; k++) g.fill(new Ellipse2D.Double(x - 22 + k * 9, y - 6 - (k % 2) * 8, 14, 14));
                g.fill(new RoundRectangle2D.Double(x - 22, y, 44, 12, 8, 8));
            }
            case 2 -> { // Wurzel
                g.draw(new QuadCurve2D.Double(x, y - 22, x - 6, y, x + 4, y + 22));
                g.draw(new QuadCurve2D.Double(x - 2, y, x - 16, y + 6, x - 18, y + 18));
                g.draw(new QuadCurve2D.Double(x + 1, y - 6, x + 14, y, x + 18, y + 12));
            }
            case 3 -> { // Beeren
                g.draw(new Line2D.Double(x, y - 22, x, y - 8));
                g.fill(new Ellipse2D.Double(x - 16, y - 10, 16, 16));
                g.fill(new Ellipse2D.Double(x, y - 10, 16, 16));
                g.fill(new Ellipse2D.Double(x - 8, y + 3, 16, 16));
            }
            case 4 -> { // Harz
                Path2D p = new Path2D.Double();
                p.moveTo(x, y - 22); p.lineTo(x + 18, y - 4); p.lineTo(x + 10, y + 20); p.lineTo(x - 10, y + 20); p.lineTo(x - 18, y - 4);
                p.closePath();
                g.fill(p);
            }
            default -> { // Blatt
                Path2D p = new Path2D.Double();
                p.moveTo(x - 18, y + 18);
                p.curveTo(x - 18, y - 10, x + 6, y - 22, x + 20, y - 22);
                p.curveTo(x + 20, y, x + 4, y + 18, x - 18, y + 18);
                g.fill(p);
                g.setColor(new Color(60, 70, 80));
                g.draw(new Line2D.Double(x - 14, y + 14, x + 14, y - 16));
            }
        }
    }
}
