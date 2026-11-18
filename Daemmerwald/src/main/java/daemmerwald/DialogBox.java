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

    /** Name, mit dem die Figuren die Spielerin oder den Spieler ansprechen. */
    public static String playerName = "";
    private final List<Line> lines = new ArrayList<>();
    private int idx;
    private double chars;
    public boolean done;
    public Runnable onFinish;

    public DialogBox(String npcName, Look npcLook, String[] raw, CharacterDef hero) {
        for (String s : raw) {
            s = L.t(s);
            Line l = new Line();
            if (s.startsWith(">") || s.startsWith("> ")) {
                l.speaker = playerName.isBlank() ? hero.name : playerName;
                l.look = hero.look;
                s = s.substring(1);
            } else if (s.startsWith("*")) {
                l.speaker = null;
                s = s.substring(1);
            } else {
                l.speaker = npcName;
                l.look = npcLook;
            }
            l.text = s.replace("{name}", playerName.isBlank() ? hero.name.split(" ")[0] : playerName);
            lines.add(l);
        }
        if (lines.isEmpty()) done = true;
    }

    private int spoken = -1;

    /** Spricht die aktuelle Zeile laut, wenn eine Figur redet (Erzähltext bleibt stumm). */
    private void voice() {
        if (spoken == idx || done) return;
        spoken = idx;
        Line l = lines.get(idx);
        String who = l.speaker, text = l.text;
        boolean female = l.look != null && l.look.female;
        if (who == null) {
            // „Aschfell: …“ – Name vor dem Doppelpunkt
            int c = text.indexOf(": ");
            if (c > 0 && c < 28 && Character.isUpperCase(text.charAt(0))) {
                who = text.substring(0, c);
                text = text.substring(c + 2);
                female = who.startsWith("Hedda") || who.startsWith("Die Fünf") || who.startsWith("Professorin");
            } else {
                Voice.stop();
                return;
            }
        }
        Voice.say(text, female, who);
    }

    public void update(double dt, Input in) {
        if (done) return;
        voice();
        Line l = lines.get(idx);
        chars += dt * 60;
        if (in.confirm() || in.mouseClicked) {
            if (chars < l.text.length()) {
                chars = l.text.length();
            } else {
                idx++;
                chars = 0;
                if (idx >= lines.size()) { done = true; Voice.stop(); }
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
            int nw = g.getFontMetrics().stringWidth(l.speaker) + 44;
            java.awt.geom.Path2D rib = new java.awt.geom.Path2D.Double();
            rib.moveTo(tx - 22, y - 16); rib.lineTo(tx - 22 + nw, y - 16); rib.lineTo(tx - 22 + nw + 14, y + 2);
            rib.lineTo(tx - 22 + nw, y + 20); rib.lineTo(tx - 22, y + 20); rib.lineTo(tx - 8, y + 2); rib.closePath();
            g.setPaint(new GradientPaint((float) tx, (float) y - 16, new Color(120, 70, 160), (float) tx, (float) y + 20, new Color(70, 36, 100)));
            g.fill(rib);
            g.setColor(Art.alpha(Art.GOLD, 220));
            g.draw(rib);
            Art.shadowText(g, l.speaker, tx, y + 11, Art.GOLD);
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
        Art.str(g, (idx + 1) + "/" + lines.size(), (float) (x + w - 70), (float) (y + 26));
    }
}
