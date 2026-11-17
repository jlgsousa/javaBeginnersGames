package daemmerwald;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Alle Zeichenroutinen – das Spiel nutzt keine Bilddateien, alles ist prozedural. */
public final class Art {
    private Art() { }

    public static final Font TITLE = new Font("Serif", Font.BOLD, 66);
    public static final Font H1 = new Font("Serif", Font.BOLD, 40);
    public static final Font H2 = new Font("Serif", Font.BOLD, 26);
    public static final Font BODY = new Font("Serif", Font.PLAIN, 21);
    public static final Font ITALIC = new Font("Serif", Font.ITALIC, 21);
    public static final Font SMALL = new Font("SansSerif", Font.PLAIN, 15);
    public static final Font UI = new Font("SansSerif", Font.BOLD, 16);
    public static final Font BIG_UI = new Font("SansSerif", Font.BOLD, 30);

    public static final Color GOLD = new Color(232, 196, 120);
    public static final Color PARCH = new Color(236, 226, 200);
    public static final Color PANEL = new Color(12, 16, 26, 222);
    public static final Color MAGIC = new Color(150, 220, 255);
    public static final Color BLIGHT = new Color(176, 70, 210);
    public static final Color DANGER = new Color(235, 90, 90);
    public static final Color GOOD = new Color(140, 230, 150);

    // ------------------------------------------------------------ Farben

    public static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
    }

    public static Color mix(Color a, Color b, double t) {
        t = clamp01(t);
        return new Color(
            clamp((int) (a.getRed() + (b.getRed() - a.getRed()) * t)),
            clamp((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t)),
            clamp((int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)),
            clamp((int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)));
    }

    public static Color shade(Color c, double f) {
        return new Color(clamp((int) (c.getRed() * f)), clamp((int) (c.getGreen() * f)),
            clamp((int) (c.getBlue() * f)), c.getAlpha());
    }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    // ------------------------------------------------------------ Grundformen

    public static void glow(Graphics2D g, double x, double y, double r, Color c, int a) {
        if (r < 1 || a <= 0) return;
        RadialGradientPaint p = new RadialGradientPaint(new Point2D.Double(x, y), (float) r,
            new float[]{0f, 0.35f, 1f},
            new Color[]{alpha(c, a), alpha(c, a / 3), alpha(c, 0)});
        Paint old = g.getPaint();
        g.setPaint(p);
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setPaint(old);
    }

    /** Günstiges Glühen ohne Verlauf (für viele kleine Lichter). */
    public static void dot(Graphics2D g, double x, double y, double r, Color c, int a) {
        g.setColor(alpha(c, a / 5));
        g.fill(new Ellipse2D.Double(x - r * 3, y - r * 3, r * 6, r * 6));
        g.setColor(alpha(c, a / 2));
        g.fill(new Ellipse2D.Double(x - r * 1.6, y - r * 1.6, r * 3.2, r * 3.2));
        g.setColor(alpha(mix(c, Color.WHITE, 0.6), a));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    public static void panel(Graphics2D g, double x, double y, double w, double h) {
        RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, h, 18, 18);
        g.setColor(PANEL);
        g.fill(r);
        g.setStroke(new BasicStroke(2f));
        g.setColor(alpha(GOLD, 150));
        g.draw(r);
        g.setStroke(new BasicStroke(1f));
        g.setColor(alpha(GOLD, 45));
        g.draw(new RoundRectangle2D.Double(x + 5, y + 5, w - 10, h - 10, 14, 14));
    }

    public static void centered(Graphics2D g, String s, double cx, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) y);
    }

    public static void shadowCentered(Graphics2D g, String s, double cx, double y, Color c) {
        g.setColor(new Color(0, 0, 0, 170));
        centered(g, s, cx + 2, y + 2);
        g.setColor(c);
        centered(g, s, cx, y);
    }

    public static void shadowText(Graphics2D g, String s, double x, double y, Color c) {
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(s, (float) x + 2, (float) y + 2);
        g.setColor(c);
        g.drawString(s, (float) x, (float) y);
    }

    public static List<String> wrap(Graphics2D g, String text, double width) {
        List<String> out = new ArrayList<>();
        FontMetrics fm = g.getFontMetrics();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String w : para.split(" ")) {
                String test = line.length() == 0 ? w : line + " " + w;
                if (fm.stringWidth(test) > width && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(test);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    public static double drawWrapped(Graphics2D g, String text, double x, double y, double width, double lh) {
        for (String l : wrap(g, text, width)) {
            g.drawString(l, (float) x, (float) y);
            y += lh;
        }
        return y;
    }

    public static void bar(Graphics2D g, double x, double y, double w, double h, double frac, Color c) {
        frac = clamp01(frac);
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        if (frac > 0) {
            g.setPaint(new GradientPaint((float) x, (float) y, mix(c, Color.WHITE, 0.35), (float) x, (float) (y + h), shade(c, 0.7)));
            g.fill(new RoundRectangle2D.Double(x, y, w * frac, h, h, h));
        }
        g.setColor(alpha(Color.WHITE, 60));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, h, h));
    }

    public static void shard(Graphics2D g, double x, double y, double r, Color c, boolean filled, double t) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y - r);
        p.lineTo(x + r * 0.55, y - r * 0.2);
        p.lineTo(x + r * 0.32, y + r);
        p.lineTo(x - r * 0.32, y + r);
        p.lineTo(x - r * 0.55, y - r * 0.2);
        p.closePath();
        if (filled) {
            glow(g, x, y, r * 2.4, c, (int) (110 + 40 * Math.sin(t * 3)));
            g.setPaint(new GradientPaint((float) x, (float) (y - r), Color.WHITE, (float) x, (float) (y + r), c));
            g.fill(p);
        } else {
            g.setColor(new Color(255, 255, 255, 22));
            g.fill(p);
        }
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(filled ? new Color(255, 255, 255, 220) : new Color(255, 255, 255, 80));
        g.draw(p);
        g.setColor(filled ? alpha(Color.WHITE, 120) : new Color(255, 255, 255, 30));
        g.draw(new Line2D.Double(x, y - r, x, y + r));
    }

    /** Pfeil-Symbol, dir: 0=hoch 1=rechts 2=runter 3=links. */
    public static void arrow(Graphics2D g, double cx, double cy, double s, int dir, Color c) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate(dir * Math.PI / 2);
        Path2D p = new Path2D.Double();
        p.moveTo(0, -s);
        p.lineTo(s * 0.8, 0);
        p.lineTo(s * 0.3, 0);
        p.lineTo(s * 0.3, s);
        p.lineTo(-s * 0.3, s);
        p.lineTo(-s * 0.3, 0);
        p.lineTo(-s * 0.8, 0);
        p.closePath();
        g.setColor(c);
        g.fill(p);
        g.setTransform(old);
    }

    // ------------------------------------------------------------ Kulisse für Menüs & Minispiele

    private static BufferedImage backdrop;

    public static void backdrop(Graphics2D g, double t) {
        if (backdrop == null) backdrop = makeBackdrop();
        g.drawImage(backdrop, 0, 0, null);
        for (int i = 0; i < 3; i++) {
            double y = 470 + i * 75;
            double off = (t * 14 * (i + 1)) % 1600;
            g.setColor(new Color(150, 170, 210, 12));
            for (int k = -1; k < 2; k++)
                g.fill(new Ellipse2D.Double(k * 1600 - off + i * 300, y, 1500, 100));
        }
        for (int i = 0; i < 45; i++) {
            double fx = ((i * 137.5 + t * (10 + i % 7)) % 1320) - 20;
            double fy = 360 + (i * 53 % 320) + Math.sin(t * 1.3 + i) * 22;
            double a = 0.5 + 0.5 * Math.sin(t * 2 + i * 1.7);
            dot(g, fx, fy, 1.6, new Color(255, 225, 140), (int) (a * 200));
        }
    }

    private static BufferedImage makeBackdrop() {
        BufferedImage img = new BufferedImage(GamePanel.W, GamePanel.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setPaint(new GradientPaint(0, 0, new Color(8, 10, 28), 0, 620, new Color(46, 32, 66)));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        Random r = new Random(7);
        for (int i = 0; i < 260; i++) {
            g.setColor(new Color(255, 255, 255, 30 + r.nextInt(170)));
            double s = r.nextDouble() * 2 + 0.5;
            g.fill(new Ellipse2D.Double(r.nextInt(1280), r.nextInt(430), s, s));
        }
        glow(g, 1010, 150, 260, new Color(190, 200, 255), 80);
        g.setColor(new Color(236, 232, 214));
        g.fill(new Ellipse2D.Double(955, 95, 110, 110));
        g.setColor(new Color(212, 206, 190));
        g.fill(new Ellipse2D.Double(980, 120, 22, 18));
        g.fill(new Ellipse2D.Double(1022, 160, 16, 14));
        g.fill(new Ellipse2D.Double(1000, 170, 10, 9));

        Color[] layers = {new Color(30, 30, 56), new Color(20, 22, 40), new Color(11, 14, 24)};
        for (int layer = 0; layer < 3; layer++) {
            double base = 440 + layer * 85;
            g.setColor(layers[layer]);
            g.fillRect(0, (int) base + 50, 1280, 720);
            for (double x = -40; x < 1320; x += 26 + r.nextInt(40)) {
                double h = 110 + r.nextInt(150) + layer * 40;
                pine(g, x, base + 70, 36 + r.nextInt(40) + layer * 12, h);
            }
            if (layer == 0) academySilhouette(g, 300, base + 70);
        }
        g.dispose();
        return img;
    }

    private static void pine(Graphics2D g, double x, double baseY, double w, double h) {
        for (int i = 0; i < 3; i++) {
            double top = baseY - h + i * h * 0.22;
            double bw = w * (0.55 + i * 0.25);
            Path2D p = new Path2D.Double();
            p.moveTo(x, top);
            p.lineTo(x + bw / 2, top + h * 0.45);
            p.lineTo(x - bw / 2, top + h * 0.45);
            p.closePath();
            g.fill(p);
        }
        g.fill(new Rectangle2D.Double(x - w * 0.06, baseY - h * 0.25, w * 0.12, h * 0.25));
    }

    private static void academySilhouette(Graphics2D g, double x, double baseY) {
        Color c = new Color(26, 26, 50);
        g.setColor(c);
        Path2D trunk = new Path2D.Double();
        trunk.moveTo(x - 120, baseY);
        trunk.curveTo(x - 50, baseY - 60, x - 60, baseY - 200, x - 40, baseY - 300);
        trunk.lineTo(x + 40, baseY - 300);
        trunk.curveTo(x + 60, baseY - 200, x + 50, baseY - 60, x + 120, baseY);
        trunk.closePath();
        g.fill(trunk);
        Random r = new Random(3);
        for (int i = 0; i < 14; i++) {
            double cx = x + r.nextGaussian() * 110, cy = baseY - 320 + r.nextGaussian() * 50;
            double rad = 60 + r.nextInt(50);
            g.fill(new Ellipse2D.Double(cx - rad, cy - rad * 0.7, rad * 2, rad * 1.4));
        }
        for (int i = 0; i < 16; i++) {
            double wx = x - 35 + r.nextInt(70), wy = baseY - 40 - r.nextInt(240);
            glow(g, wx + 3, wy + 5, 16, new Color(255, 200, 120), 90);
            g.setColor(new Color(255, 210, 130));
            g.fill(new RoundRectangle2D.Double(wx, wy, 6, 10, 4, 4));
            g.setColor(c);
        }
    }

    // ------------------------------------------------------------ Figuren

    /** Zeichnet eine Figur mit den Füßen bei (x,y). */
    public static void drawCharacter(Graphics2D g, Look l, double x, double y, double s, double t, boolean moving, int flip) {
        if (l == null) return;
        AffineTransform old = g.getTransform();
        Stroke os = g.getStroke();
        g.translate(x, y);
        g.scale(s * (flip < 0 ? -1 : 1), s);
        switch (l.kind) {
            case Look.TROLL -> troll(g, l, t, moving);
            case Look.FAIRY -> fairy(g, l, t, moving);
            case Look.SHROOM -> shroom(g, l, t);
            default -> wraith(g, l, t);
        }
        g.setTransform(old);
        g.setStroke(os);
    }

    private static void troll(Graphics2D g, Look l, double t, boolean moving) {
        double sw = moving ? Math.sin(t * 10) * 4 : 0;
        double bob = moving ? -Math.abs(Math.sin(t * 10)) * 2 : Math.sin(t * 2) * 0.8;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(-21, -6, 42, 11));
        g.setColor(shade(l.cloth, 0.6));
        g.fill(new RoundRectangle2D.Double(-14 + sw * 0.5, -20, 11, 20, 6, 6));
        g.fill(new RoundRectangle2D.Double(3 - sw * 0.5, -20, 11, 20, 6, 6));
        g.translate(0, bob);
        g.setColor(shade(l.skin, 0.78));
        g.fill(new Ellipse2D.Double(11, -44 + sw, 12, 26));
        g.setPaint(new GradientPaint(0, -50, shade(l.cloth, 1.2), 0, -14, shade(l.cloth, 0.75)));
        g.fill(new Ellipse2D.Double(-20, -52, 40, 38));
        g.setColor(l.accent);
        g.fill(new Rectangle2D.Double(-18, -27, 36, 4));
        g.setColor(shade(l.accent, 0.7));
        g.fill(new Rectangle2D.Double(-3, -28, 6, 6));
        g.setColor(l.skin);
        g.fill(new Ellipse2D.Double(-24, -46 - sw, 12, 26));
        // Kopf
        Path2D earL = new Path2D.Double();
        earL.moveTo(-12, -62); earL.lineTo(-23, -68); earL.lineTo(-11, -55); earL.closePath();
        Path2D earR = new Path2D.Double();
        earR.moveTo(12, -62); earR.lineTo(23, -68); earR.lineTo(11, -55); earR.closePath();
        g.setColor(shade(l.skin, 0.85));
        g.fill(earL);
        g.fill(earR);
        g.setPaint(new GradientPaint(0, -72, shade(l.skin, 1.12), 0, -46, shade(l.skin, 0.85)));
        g.fill(new Ellipse2D.Double(-14, -72, 28, 26));
        g.setColor(l.hair);
        g.fill(new Ellipse2D.Double(-11, -76, 22, 9));
        g.setColor(shade(l.skin, 0.62));
        g.fill(new RoundRectangle2D.Double(-11, -64, 22, 4, 3, 3));
        g.setColor(l.accent);
        g.fill(new Ellipse2D.Double(-8, -61, 5, 3.5));
        g.fill(new Ellipse2D.Double(3, -61, 5, 3.5));
        g.setColor(shade(l.skin, 0.72));
        g.fill(new Ellipse2D.Double(-3, -59, 6, 7));
        g.setColor(new Color(240, 234, 210));
        Path2D tusk = new Path2D.Double();
        tusk.moveTo(-9, -50); tusk.lineTo(-7, -56); tusk.lineTo(-5, -50); tusk.closePath();
        tusk.moveTo(9, -50); tusk.lineTo(7, -56); tusk.lineTo(5, -50); tusk.closePath();
        g.fill(tusk);
        if (l.horns) {
            g.setColor(new Color(214, 204, 180));
            g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new QuadCurve2D.Double(-9, -70, -22, -78, -18, -88));
            g.draw(new QuadCurve2D.Double(9, -70, 22, -78, 18, -88));
        }
    }

    private static void fairy(Graphics2D g, Look l, double t, boolean moving) {
        double hover = Math.sin(t * 3) * 3 - 7;
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(-12, -4, 24, 8));
        g.translate(0, hover);
        glow(g, 0, -34, 42, l.accent, 45);
        double flap = 0.55 + 0.45 * Math.abs(Math.sin(t * (moving ? 16 : 6)));
        for (int side = -1; side <= 1; side += 2) {
            AffineTransform o = g.getTransform();
            g.translate(side * 2, -38);
            g.scale(side * flap, 1);
            g.setColor(alpha(l.wing, 110));
            g.fill(new Ellipse2D.Double(0, -24, 24, 30));
            g.fill(new Ellipse2D.Double(0, -2, 17, 20));
            g.setColor(alpha(Color.WHITE, 90));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Ellipse2D.Double(0, -24, 24, 30));
            g.setTransform(o);
        }
        double sw = moving ? Math.sin(t * 10) * 2 : 0;
        g.setColor(shade(l.skin, 0.8));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(-4, -10, -4 + sw, 0));
        g.draw(new Line2D.Double(4, -10, 4 - sw, 0));
        Path2D dress = new Path2D.Double();
        dress.moveTo(-6, -40); dress.lineTo(6, -40); dress.lineTo(13, -9); dress.lineTo(-13, -9); dress.closePath();
        g.setPaint(new GradientPaint(0, -40, shade(l.cloth, 1.25), 0, -9, shade(l.cloth, 0.7)));
        g.fill(dress);
        g.setColor(l.accent);
        g.fill(new Rectangle2D.Double(-6, -31, 12, 2.5));
        g.setColor(l.skin);
        g.draw(new Line2D.Double(-6, -37, -11, -24 + sw));
        g.draw(new Line2D.Double(6, -37, 11, -24 - sw));
        // Kopf
        g.setColor(l.hair);
        g.fill(new RoundRectangle2D.Double(-9, -56, 18, 26, 10, 10));
        Path2D earL = new Path2D.Double();
        earL.moveTo(-7, -49); earL.lineTo(-15, -55); earL.lineTo(-7, -45); earL.closePath();
        Path2D earR = new Path2D.Double();
        earR.moveTo(7, -49); earR.lineTo(15, -55); earR.lineTo(7, -45); earR.closePath();
        g.setColor(l.skin);
        g.fill(earL);
        g.fill(earR);
        g.fill(new Ellipse2D.Double(-7.5, -56, 15, 16));
        g.setColor(l.hair);
        g.fill(new Arc2D.Double(-9, -60, 18, 14, 0, 180, Arc2D.CHORD));
        g.setColor(shade(l.accent, 0.9));
        g.fill(new Ellipse2D.Double(-4.5, -49, 3, 3));
        g.fill(new Ellipse2D.Double(1.5, -49, 3, 3));
    }

    private static void shroom(Graphics2D g, Look l, double t) {
        double sway = Math.sin(t * 1.4) * 2;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(-24, -6, 48, 12));
        g.setColor(new Color(220, 210, 185));
        g.fill(new RoundRectangle2D.Double(-11, -42, 22, 42, 12, 12));
        g.setColor(new Color(40, 30, 30));
        g.fill(new Ellipse2D.Double(-7, -30, 4, 6));
        g.fill(new Ellipse2D.Double(3, -30, 4, 6));
        g.translate(sway, 0);
        glow(g, 0, -50, 50, l.accent, 50);
        g.setPaint(new GradientPaint(0, -70, shade(l.cloth, 1.3), 0, -38, shade(l.cloth, 0.7)));
        g.fill(new Arc2D.Double(-32, -74, 64, 60, 0, 180, Arc2D.CHORD));
        g.setColor(new Color(255, 245, 220, 200));
        g.fill(new Ellipse2D.Double(-18, -64, 8, 6));
        g.fill(new Ellipse2D.Double(6, -68, 10, 7));
        g.fill(new Ellipse2D.Double(-4, -56, 6, 5));
        g.fill(new Ellipse2D.Double(18, -54, 6, 4));
        g.translate(-sway, 0);
        for (int i = 0; i < 3; i++) {
            double a = 0.5 + 0.5 * Math.sin(t * 2 + i * 2);
            dot(g, -26 + i * 26, -82 - i % 2 * 8 + Math.sin(t + i) * 4, 1.6, l.accent, (int) (a * 200));
        }
    }

    private static void wraith(Graphics2D g, Look l, double t) {
        glow(g, 0, -40, 70, l.accent, 60);
        Path2D robe = new Path2D.Double();
        robe.moveTo(-14, -58);
        robe.curveTo(-26, -30, -30, -10, -26, 0);
        for (int i = 0; i <= 6; i++) {
            double x = -26 + i * (52 / 6.0);
            double y = (i % 2 == 0 ? 0 : -8) + Math.sin(t * 4 + i) * 3;
            robe.lineTo(x, y);
        }
        robe.curveTo(30, -10, 26, -30, 14, -58);
        robe.closePath();
        g.setPaint(new GradientPaint(0, -60, shade(l.cloth, 1.6), 0, 0, alpha(l.cloth, 120)));
        g.fill(robe);
        g.setColor(alpha(l.accent, 120));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            double sx = -14 + i * 9;
            g.draw(new QuadCurve2D.Double(sx, -20, sx + Math.sin(t * 3 + i) * 12, -6, sx + Math.sin(t * 2 + i) * 8, 8));
        }
        g.setColor(shade(l.cloth, 0.6));
        g.fill(new Ellipse2D.Double(-15, -78, 30, 32));
        g.setColor(new Color(10, 5, 15));
        g.fill(new Ellipse2D.Double(-10, -70, 20, 22));
        dot(g, -4, -60, 1.8, l.accent, 255);
        dot(g, 4, -60, 1.8, l.accent, 255);
    }

    // ------------------------------------------------------------ Welt-Sprites

    public static BufferedImage makeTree(int v) {
        BufferedImage img = new BufferedImage(84, 112, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        Random r = new Random(v * 31L + 5);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(20, 98, 44, 12));
        g.setPaint(new GradientPaint(36, 0, new Color(62, 46, 36), 48, 0, new Color(36, 26, 22)));
        g.fill(new RoundRectangle2D.Double(36, 60, 12, 46, 4, 4));
        g.fill(new Ellipse2D.Double(30, 98, 24, 9));
        Color[] bases = {new Color(24, 62, 52), new Color(30, 68, 44), new Color(20, 54, 58), new Color(38, 62, 42)};
        Color base = bases[v % bases.length];
        double[][] blobs = new double[7][3];
        for (int i = 0; i < blobs.length; i++) {
            blobs[i][0] = 42 + r.nextGaussian() * 11;
            blobs[i][1] = 44 + r.nextGaussian() * 9 - (i % 3) * 4;
            blobs[i][2] = 17 + r.nextInt(9);
        }
        for (double[] b : blobs) {
            g.setColor(shade(base, 0.6));
            g.fill(new Ellipse2D.Double(b[0] - b[2] + 2, b[1] - b[2] + 4, b[2] * 2, b[2] * 2));
        }
        for (double[] b : blobs) {
            g.setColor(base);
            g.fill(new Ellipse2D.Double(b[0] - b[2], b[1] - b[2], b[2] * 2, b[2] * 2));
        }
        for (double[] b : blobs) {
            g.setColor(alpha(shade(base, 1.4), 150));
            g.fill(new Ellipse2D.Double(b[0] - b[2] * 0.7, b[1] - b[2] * 0.85, b[2] * 1.0, b[2] * 0.8));
        }
        g.dispose();
        return img;
    }

    public static BufferedImage makeDeadTree() {
        BufferedImage img = new BufferedImage(84, 112, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(22, 98, 40, 11));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(46, 36, 52));
        g.draw(new QuadCurve2D.Double(42, 104, 38, 70, 44, 40));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(42, 62, 24, 52, 16, 30));
        g.draw(new QuadCurve2D.Double(43, 54, 62, 44, 70, 22));
        g.draw(new QuadCurve2D.Double(44, 42, 40, 26, 48, 12));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(20, 38, 10, 34));
        g.draw(new Line2D.Double(64, 32, 76, 30));
        g.setColor(new Color(200, 100, 255, 200));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new QuadCurve2D.Double(41, 100, 44, 80, 40, 66));
        g.draw(new QuadCurve2D.Double(42, 60, 30, 50, 22, 38));
        g.dispose();
        return img;
    }

    public static BufferedImage makeRock(int v) {
        BufferedImage img = new BufferedImage(48, 44, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        Random r = new Random(v * 17L + 2);
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(4, 34, 40, 9));
        Path2D p = new Path2D.Double();
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double rad = 15 + r.nextInt(6);
            double x = 24 + Math.cos(a) * rad * 1.15, y = 24 + Math.sin(a) * rad * 0.9;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        g.setPaint(new GradientPaint(10, 8, new Color(130, 128, 136), 36, 40, new Color(56, 56, 64)));
        g.fill(p);
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new Ellipse2D.Double(12, 12, 14, 7));
        g.setColor(new Color(30, 30, 36));
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
        g.dispose();
        return img;
    }

    public static BufferedImage makeShelf() {
        BufferedImage img = new BufferedImage(32, 52, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(70, 44, 30));
        g.fillRect(0, 0, 32, 52);
        g.setColor(new Color(48, 30, 20));
        g.fillRect(2, 2, 28, 48);
        Random r = new Random(11);
        Color[] books = {new Color(120, 40, 40), new Color(40, 70, 110), new Color(60, 100, 60), new Color(150, 120, 60), new Color(90, 50, 110)};
        for (int row = 0; row < 3; row++) {
            int y = 4 + row * 16;
            g.setColor(new Color(70, 44, 30));
            g.fillRect(0, y + 13, 32, 3);
            int x = 3;
            while (x < 28) {
                int w = 3 + r.nextInt(3);
                g.setColor(books[r.nextInt(books.length)]);
                g.fillRect(x, y + 1 + r.nextInt(3), w, 12);
                x += w + 1;
            }
        }
        g.dispose();
        return img;
    }
}
