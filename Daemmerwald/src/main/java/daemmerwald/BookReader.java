package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Ein aufgeschlagenes Buch: Auswahl aus dem Regal, dann seitenweises Lesen. */
public class BookReader {
    private static final Font TEXT = new Font("Serif", Font.PLAIN, 19);
    private static final int LINES = 19;
    private int book = -1, page, sel;
    private final List<List<String>> pages = new ArrayList<>();

    /** @return false, wenn das Buch geschlossen wurde. */
    public boolean update(Input in) {
        if (book < 0) {
            if (in.wasPressed(KeyEvent.VK_ESCAPE)) return false;
            if (in.wasPressed(KeyEvent.VK_DOWN, KeyEvent.VK_S)) sel = (sel + 1) % Stories.BOOKS.length;
            if (in.wasPressed(KeyEvent.VK_UP, KeyEvent.VK_W)) sel = (sel + Stories.BOOKS.length - 1) % Stories.BOOKS.length;
            for (int i = 0; i < Stories.BOOKS.length; i++) {
                double y = 250 + i * 86;
                if (in.mouseX > 330 && in.mouseX < 950 && in.mouseY > y - 40 && in.mouseY < y + 30) {
                    sel = i;
                    if (in.mouseClicked) open(i);
                }
            }
            if (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_E, KeyEvent.VK_SPACE)) open(sel);
            return true;
        }
        if (in.wasPressed(KeyEvent.VK_ESCAPE, KeyEvent.VK_B)) { book = -1; Voice.stop(); return true; }
        boolean nextP = in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D, KeyEvent.VK_SPACE, KeyEvent.VK_PAGE_DOWN) || (in.mouseClicked && in.mouseX > 640);
        boolean prevP = in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A, KeyEvent.VK_PAGE_UP) || (in.mouseClicked && in.mouseX <= 640);
        if (nextP && page + 2 < pages.size()) page += 2;
        else if (nextP && page + 2 >= pages.size()) { book = -1; Voice.stop(); }
        if (prevP && page >= 2) page -= 2;
        return true;
    }

    private void open(int i) {
        book = i;
        page = 0;
        pages.clear();
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setFont(TEXT);
        List<String> lines = new ArrayList<>();
        lines.add("\u0000" + Stories.BOOKS[i][0]);
        lines.add("\u0001" + Stories.BOOKS[i][1]);
        lines.add("");
        for (String para : Stories.BOOKS[i][2].split("\n\n")) {
            lines.addAll(Art.wrap(g, para, 440));
            lines.add("");
        }
        g.dispose();
        for (int k = 0; k < lines.size(); k += LINES) pages.add(lines.subList(k, Math.min(lines.size(), k + LINES)));
        // Die Erzählerin liest das ganze Buch vor
        List<String> keys = new ArrayList<>();
        keys.add(Stories.BOOKS[i][0]);
        for (String para : Stories.BOOKS[i][2].split("\n\n")) if (!para.isBlank()) keys.add(para.trim());
        Voice.narrateAll(keys);
    }

    public void render(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        if (book < 0) {
            Art.panel(g, 300, 130, 680, 470);
            g.setFont(Art.H2);
            Art.shadowCentered(g, "Das Bücherregal des Cafés", 640, 180, Art.GOLD);
            for (int i = 0; i < Stories.BOOKS.length; i++) {
                double y = 250 + i * 86;
                if (i == sel) {
                    g.setColor(new Color(90, 60, 40, 190));
                    g.fill(new RoundRectangle2D.Double(330, y - 40, 620, 72, 14, 14));
                }
                g.setFont(new Font("Serif", Font.BOLD, 23));
                Art.shadowText(g, Stories.BOOKS[i][0], 360, y - 6, Art.PARCH);
                g.setFont(new Font("Serif", Font.ITALIC, 17));
                g.setColor(new Color(210, 195, 170));
                Art.str(g, Stories.BOOKS[i][1], 380, (float) y + 18);
            }
            g.setFont(Art.SMALL);
            g.setColor(new Color(210, 210, 220));
            Art.centered(g, "Klick oder Enter: aufschlagen · Esc: Buch zurückstellen", 640, 580);
            return;
        }
        // aufgeschlagenes Buch
        double bx = 110, by = 60, bw = 1060, bh = 600;
        g.setColor(new Color(60, 30, 20));
        g.fill(new RoundRectangle2D.Double(bx - 14, by - 12, bw + 28, bh + 24, 20, 20));
        for (int side = 0; side < 2; side++) {
            double px = bx + side * bw / 2;
            g.setPaint(new GradientPaint((float) (side == 0 ? px + bw / 2 : px), 0, new Color(200, 185, 150),
                (float) (side == 0 ? px + bw / 2 - 60 : px + 60), 0, new Color(246, 237, 214)));
            g.fill(new java.awt.Rectangle((int) px, (int) by, (int) (bw / 2), (int) bh));
        }
        g.setColor(new Color(120, 100, 70, 120));
        g.drawLine((int) (bx + bw / 2), (int) by, (int) (bx + bw / 2), (int) (by + bh));
        for (int side = 0; side < 2; side++) {
            int pi = page + side;
            if (pi >= pages.size()) continue;
            double x = bx + side * bw / 2 + 50, y = by + 62;
            for (String l : pages.get(pi)) {
                if (l.startsWith("\u0000")) {
                    g.setFont(new Font("Serif", Font.BOLD, 27));
                    g.setColor(new Color(90, 40, 30));
                    Art.drawWrapped(g, l.substring(1), x, y, 440, 30);
                    y += 34;
                    continue;
                }
                if (l.startsWith("\u0001")) {
                    g.setFont(new Font("Serif", Font.ITALIC, 18));
                    g.setColor(new Color(110, 80, 60));
                    Art.str(g, l.substring(1), (float) x, (float) y);
                    y += 26;
                    continue;
                }
                g.setFont(TEXT);
                g.setColor(new Color(50, 35, 25));
                Art.str(g, l, (float) x, (float) y);
                y += 27;
            }
            g.setFont(new Font("Serif", Font.ITALIC, 15));
            g.setColor(new Color(120, 100, 80));
            Art.centered(g, String.valueOf(pi + 1), bx + side * bw / 2 + bw / 4, by + bh - 20);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(230, 225, 210));
        Art.centered(g, "←/→ oder Klick links/rechts: umblättern · Esc: Buch schließen", 640, 700);
    }
}
