package daemmerwald;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialogfenster. Zeilen-Syntax:
 *   "Text"   -> spricht die NPC
 *   ">Text"  -> spricht die Spielfigur
 *   "*Text"  -> Erzähler
 * {name} wird durch den Namen der Spielfigur ersetzt.
 */
public class DialogBox {
    private static class Line {
        String speaker;
        Look look;
        String text;
    }

    private final List<Line> lines = new ArrayList<>();
    private int idx;
    private double chars;
    public boolean done;
    public Runnable onFinish;

    public DialogBox(String npcName, Look npcLook, String[] raw, CharacterDef hero) {
        for (String s : raw) {
            Line l = new Line();
            if (s.startsWith(">")) {
                l.speaker = hero.name;
                l.look = hero.look;
                s = s.substring(1);
            } else if (s.startsWith("*")) {
                l.speaker = null;
                s = s.substring(1);
            } else {
                l.speaker = npcName;
                l.look = npcLook;
            }
            l.text = s.replace("{name}", hero.name.split(" ")[0]);
            lines.add(l);
        }
        if (lines.isEmpty()) done = true;
    }

    public void update(double dt, Input in) {
        if (done) return;
        Line l = lines.get(idx);
        chars += dt * 60;
        if (in.confirm() || in.mouseClicked) {
            if (chars < l.text.length()) {
                chars = l.text.length();
            } else {
                idx++;
                chars = 0;
                if (idx >= lines.size()) done = true;
            }
        }
    }

    public void render(Graphics2D g, double t) {
        if (done) return;
        Line l = lines.get(idx);
        double x = 120, y = 500, w = 1040, h = 196;
        Art.panel(g, x, y, w, h);
        double tx = x + 30;
        if (l.look != null && !Art.THREE_D) {
            RoundRectangle2D frame = new RoundRectangle2D.Double(x + 20, y + 20, 130, 156, 14, 14);
            g.setColor(new Color(30, 36, 52));
            g.fill(frame);
            Shape oldClip = g.getClip();
            g.clip(frame);
            Art.glow(g, x + 85, y + 100, 90, l.look.accent, 50);
            Art.drawCharacter(g, l.look, x + 85, y + 205, 2.2, t, false, 1);
            g.setClip(oldClip);
            g.setColor(Art.alpha(Art.GOLD, 120));
            g.draw(frame);
            tx = x + 175;
        }
        double ty = y + 46;
        if (l.speaker != null) {
            g.setFont(Art.H2);
            Art.shadowText(g, l.speaker, tx, ty, Art.GOLD);
            ty += 36;
        } else {
            ty += 12;
        }
        g.setFont(l.speaker == null ? Art.ITALIC : Art.BODY);
        g.setColor(l.speaker == null ? new Color(200, 205, 225) : Art.PARCH);
        String shown = l.text.substring(0, (int) Math.min(l.text.length(), chars));
        Art.drawWrapped(g, shown, tx, ty, x + w - tx - 40, 28);
        if (chars >= l.text.length() && Math.sin(t * 6) > -0.3) {
            Path2D tri = new Path2D.Double();
            double ax = x + w - 40, ay = y + h - 30;
            tri.moveTo(ax - 8, ay - 5);
            tri.lineTo(ax + 8, ay - 5);
            tri.lineTo(ax, ay + 5);
            tri.closePath();
            g.setColor(Art.GOLD);
            g.fill(tri);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(170, 170, 190));
        g.drawString((idx + 1) + "/" + lines.size(), (float) (x + w - 70), (float) (y + 26));
    }
}
