package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;

/** Die Entscheidung am Herzbaum und der Epilog. Danach geht es endlos weiter. */
public class EndingScene implements Scene {
    private final GamePanel game;
    private final WorldScene world;
    private final Input in;
    private DialogBox dialog;
    private int stage; // 0 Dialog, 1 Wahl, 2 Epilog
    private int choice;
    private double t, total;

    public EndingScene(GamePanel game, WorldScene world) {
        this.game = game;
        this.world = world;
        this.in = game.input;
        dialog = new DialogBox("Corvina", Story.CORVINA, Story.ENDING_TALK, game.state.hero);
    }

    @Override
    public void update(double dt) {
        t += dt;
        total += dt;
        if (stage == 0) {
            dialog.update(dt, in);
            if (dialog.done) { stage = 1; t = 0; }
        } else if (stage == 1) {
            if (t < 0.4) return;
            int c = -1;
            if (in.wasPressed(KeyEvent.VK_1)) c = 1;
            if (in.wasPressed(KeyEvent.VK_2)) c = 2;
            if (in.mouseClicked) {
                if (in.mouseX > 160 && in.mouseX < 620 && in.mouseY > 300 && in.mouseY < 520) c = 1;
                if (in.mouseX > 660 && in.mouseX < 1120 && in.mouseY > 300 && in.mouseY < 520) c = 2;
            }
            if (c > 0) { choice = c; stage = 2; t = 0; }
        } else if (t > 1.5 && (in.wasPressed(KeyEvent.VK_ENTER) || in.mouseClicked)) {
            game.setScene(world);
            world.afterEnding(choice == 2);
        }
    }

    @Override
    public void render(Graphics2D g) {
        Art.backdrop(g, total);
        g.setColor(new Color(20, 0, 30, 140));
        g.fillRect(0, 0, 1280, 720);
        if (stage == 0) {
            Art.glow(g, 640, 280, 260, Story.CORVINA.accent, 80);
            Art.drawCharacter(g, Story.CORVINA, 640, 430, 3.4, total, false, 1);
            dialog.render(g, total);
        } else if (stage == 1) {
            g.setFont(Art.H1);
            Art.shadowCentered(g, "Deine Entscheidung", 640, 200, Art.GOLD);
            option(g, 160, "1", "Den Pakt erneuern", "Frieden, Licht und Schweigen. Corvina schläft weiter.", Art.GOLD);
            option(g, 660, "2", "Den Pakt brechen", "Wahrheit und Freiheit. Die Akademie verliert ihr Licht.", Art.MAGIC);
        } else {
            int a = (int) (Math.min(1, t) * 255);
            g.setFont(Art.H1);
            Art.shadowCentered(g, choice == 1 ? "Der erneuerte Pakt" : "Der gebrochene Pakt", 640, 120, Art.alpha(Art.GOLD, a));
            g.setFont(new Font("Serif", Font.PLAIN, 23));
            g.setColor(Art.alpha(Art.PARCH, a));
            double y = 190;
            for (String l : Art.wrap(g, choice == 1 ? Story.ENDING_RENEW : Story.ENDING_BREAK, 900)) {
                Art.centered(g, l, 640, y);
                y += 32;
            }
            GameState st = game.state;
            g.setFont(Art.SMALL);
            g.setColor(new Color(190, 190, 215, a));
            Art.centered(g, "Spielzeit: " + (int) (st.playTime / 60) + " min   ·   Stufe " + st.level
                + "   ·   Schattenwesen vertrieben: " + st.banished, 640, 610);
            if (t > 1.5) {
                g.setFont(Art.UI);
                Art.shadowCentered(g, "[Enter] Weiterspielen – der Dämmerwald wartet", 640, 660, Art.GOLD);
            }
        }
    }

    private void option(Graphics2D g, double x, String key, String title, String desc, Color c) {
        Art.panel(g, x, 300, 460, 220);
        Art.glow(g, x + 230, 370, 120, c, 60);
        g.setFont(Art.BIG_UI);
        Art.shadowCentered(g, "[" + key + "]", x + 230, 360, c);
        g.setFont(Art.H2);
        Art.shadowCentered(g, title, x + 230, 410, Color.WHITE);
        g.setFont(Art.BODY);
        g.setColor(Art.PARCH);
        double y = 450;
        for (String l : Art.wrap(g, desc, 400)) {
            Art.centered(g, l, x + 230, y);
            y += 26;
        }
    }
}
