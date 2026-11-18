package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Zauberunterricht im Palast: Die Professorin zeigt die Zauberbewegung als Pfeilfolge.
 * Runde 1 mit Vorlage, Runde 2 aus dem Gedächtnis, Runde 3 doppelt so lang und unter Zeitdruck.
 */
public class LessonGame extends MiniGame {
    private static final String TEACHER = "Professorin Ysmira Funkelstein";
    private final Spell spell;
    private int round, prog, lives = 3;
    private int[] seq;
    private double roundTime, roundMax, showT, flash, shake;
    private final List<double[]> sparks = new ArrayList<>();

    public LessonGame(GamePanel g, Spell spell) {
        super(g);
        this.spell = spell;
    }

    @Override protected String title() { return "Zauberunterricht: " + spell.word; }

    @Override protected String subtitle() { return TEACHER + " lehrt den " + spell.title + "."; }

    @Override protected String[] rules() {
        return new String[]{
            spell.text,
            "Führe die Zauberbewegung mit den Pfeiltasten (oder W A S D) aus – genau in der gezeigten Reihenfolge.",
            "Runde 1: mit Vorlage. Runde 2: aus dem Gedächtnis. Runde 3: doppelt und gegen die Zeit.",
            "Drei Fehler, und die Stunde ist vorbei. Du kannst jederzeit wiederkommen."
        };
    }

    @Override protected Color tint() { return spell.color.darker().darker(); }

    @Override protected String winText() { return "„" + spell.word + "!“ – Der " + spell.title + " gehört jetzt dir. Mit R wählst du ihn, mit Q oder Klick zauberst du."; }

    @Override protected String loseText() { return TEACHER + ": „Noch nicht ganz. Übung macht den Zauberer – komm einfach wieder.“"; }

    @Override
    protected void start() {
        round = 0;
        nextRound();
    }

    private void nextRound() {
        round++;
        prog = 0;
        int[] p = spell.pattern;
        if (round < 3) seq = p.clone();
        else {
            seq = new int[p.length * 2];
            for (int i = 0; i < seq.length; i++) seq[i] = p[i % p.length];
        }
        roundMax = (round == 3 ? 1.1 : 2.2) * seq.length * timeFactor() + 2;
        roundTime = roundMax;
        showT = round == 2 ? 2.4 : 0;
    }

    private int pressedDir() {
        if (in.wasPressed(KeyEvent.VK_UP, KeyEvent.VK_W)) return 0;
        if (in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) return 1;
        if (in.wasPressed(KeyEvent.VK_DOWN, KeyEvent.VK_S)) return 2;
        if (in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A)) return 3;
        return -1;
    }

    @Override
    protected void play(double dt) {
        flash = Math.max(0, flash - dt * 2);
        shake = Math.max(0, shake - dt * 3);
        for (int i = sparks.size() - 1; i >= 0; i--) {
            double[] s = sparks.get(i);
            s[0] += s[2] * dt; s[1] += s[3] * dt; s[3] += 120 * dt; s[4] -= dt;
            if (s[4] <= 0) sparks.remove(i);
        }
        if (showT > 0) { showT -= dt; return; }
        roundTime -= dt;
        if (roundTime <= 0) { miss(); return; }
        int d = pressedDir();
        if (d < 0) return;
        if (d == seq[prog]) {
            prog++;
            for (int i = 0; i < 14; i++) sparks.add(new double[]{arrowX(prog - 1), 560, (rnd.nextDouble() - 0.5) * 260, -120 - rnd.nextDouble() * 200, 0.8});
            if (prog >= seq.length) {
                flash = 1;
                for (int i = 0; i < 70; i++) sparks.add(new double[]{640, 300, (rnd.nextDouble() - 0.5) * 600, (rnd.nextDouble() - 0.8) * 500, 1.3});
                if (round >= 3) end(true);
                else nextRound();
            }
        } else miss();
    }

    private void miss() {
        lives--;
        shake = 1;
        prog = 0;
        roundTime = roundMax;
        if (lives <= 0) end(false);
    }

    private double arrowX(int i) {
        double w = Math.min(96, 900.0 / seq.length);
        return 640 - (seq.length - 1) * w / 2 + i * w;
    }

    @Override
    protected void draw(Graphics2D g) {
        topBar(g, "Runde " + round + "/3  ·  " + "♥".repeat(Math.max(0, lives)), spell.word, roundTime / roundMax, new Color(spell.color.getRed(), spell.color.getGreen(), spell.color.getBlue()));
        // magischer Kreis
        double cx = 640, cy = 300, r = 150;
        Color c = spell.color;
        Art.glow(g, cx, cy, r * 1.6, c, (int) (60 + flash * 150));
        g.setStroke(new BasicStroke(3f));
        g.setColor(Art.alpha(c, 200));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Ellipse2D.Double(cx - r * 0.78, cy - r * 0.78, r * 1.56, r * 1.56));
        int k = 7;
        for (int i = 0; i < k; i++) {
            double a1 = total * 0.4 + i * Math.PI * 2 / k, a2 = total * 0.4 + (i + 3) * Math.PI * 2 / k;
            g.draw(new Line2D.Double(cx + Math.cos(a1) * r * 0.78, cy + Math.sin(a1) * r * 0.78, cx + Math.cos(a2) * r * 0.78, cy + Math.sin(a2) * r * 0.78));
            Art.star(g, cx + Math.cos(-total * 0.6 + i * Math.PI * 2 / k) * r, cy + Math.sin(-total * 0.6 + i * Math.PI * 2 / k) * r, 6, c);
        }
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 56));
        Art.shadowCentered(g, spell.word, cx, cy + 18, flash > 0 ? Color.WHITE : c);
        g.setFont(Art.ITALIC);
        String tip = showT > 0 ? "Präg dir die Bewegung ein …" : round == 1 ? "Folge der Vorlage." : round == 2 ? "Jetzt aus dem Gedächtnis!" : "Zweimal hintereinander – schnell!";
        Art.shadowCentered(g, tip, cx, 495, Art.PARCH);
        // Pfeile
        double sx = shake > 0 ? Math.sin(total * 70) * shake * 12 : 0;
        boolean hidden = round >= 2 && showT <= 0;
        for (int i = 0; i < seq.length; i++) {
            double x = arrowX(i) + sx, y = 560;
            boolean done = i < prog;
            boolean visible = !hidden || done;
            RoundRectangle2D box = new RoundRectangle2D.Double(x - 36, y - 36, 72, 72, 18, 18);
            g.setColor(done ? Art.alpha(c, 200) : i == prog ? new Color(70, 56, 104, 230) : new Color(20, 18, 34, 220));
            g.fill(box);
            g.setColor(Art.alpha(c, i == prog ? 255 : 120));
            g.setStroke(new BasicStroke(i == prog ? 3f : 1.4f));
            g.draw(box);
            if (visible) arrow(g, x, y, seq[i], done ? new Color(30, 20, 10) : Color.WHITE);
            else {
                g.setFont(Art.H2);
                Art.shadowCentered(g, "?", x, y + 10, new Color(180, 170, 210));
            }
        }
        for (double[] s : sparks) Art.dot(g, s[0], s[1], 3, c, (int) (Math.min(1, s[4]) * 255));
    }

    private void arrow(Graphics2D g, double x, double y, int dir, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(0, -24); p.lineTo(20, 2); p.lineTo(8, 2); p.lineTo(8, 22); p.lineTo(-8, 22); p.lineTo(-8, 2); p.lineTo(-20, 2); p.closePath();
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.rotate(dir * Math.PI / 2);
        g.setColor(c);
        g.fill(at.createTransformedShape(p));
    }
}
