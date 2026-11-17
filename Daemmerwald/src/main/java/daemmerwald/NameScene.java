package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/** Zu Beginn eines neuen Spiels: Wie möchtest du heißen? */
public class NameScene implements Scene {
    private static final String[] SUGGEST = {"Liora", "Bramwen", "Tilda Moosmond", "Fennick", "Ysella", "Grimmo", "Nuala", "Pip Dornwisper",
        "Maelis", "Orrik", "Saphira Tau", "Wendel", "Elowen", "Brakk", "Ivy Sternfeld", "Juna"};
    private final GamePanel game;
    private final Input in;
    private final StringBuilder name = new StringBuilder();
    private final Random rnd = new Random();
    private double t, shake;
    /** 1 oder 2 – für wen wird gerade der Name eingetragen? */
    private final int player;

    public NameScene(GamePanel game) { this(game, 1); }

    public NameScene(GamePanel game, int player) {
        this.game = game;
        this.in = game.input;
        this.player = player;
    }

    @Override
    public void update(double dt) {
        t += dt;
        shake = Math.max(0, shake - dt);
        for (int i = 0; i < in.typed.length(); i++) if (name.length() < 18) name.append(in.typed.charAt(i));
        if (in.wasPressed(KeyEvent.VK_BACK_SPACE) && name.length() > 0) name.setLength(name.length() - 1);
        if (in.mouseClicked && hit(dice())) { name.setLength(0); name.append(SUGGEST[rnd.nextInt(SUGGEST.length)]); }
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { game.setScene(player == 2 ? new NameScene(game, 1) : new PlayersScene(game)); return; }
        boolean ok = in.wasPressed(KeyEvent.VK_ENTER) || (in.mouseClicked && hit(okButton()));
        if (ok) {
            String n = name.toString().trim();
            if (n.isEmpty()) { shake = 0.4; return; }
            if (player == 2) {
                game.state.player2Name = n;
                game.setScene(new IntroScene(game));
                return;
            }
            game.state.playerName = n;
            DialogBox.playerName = n;
            game.setScene(game.state.twoPlayers ? new NameScene(game, 2) : new IntroScene(game));
        }
    }

    private RoundRectangle2D okButton() { return new RoundRectangle2D.Double(540, 470, 200, 50, 26, 26); }

    private RoundRectangle2D dice() { return new RoundRectangle2D.Double(790, 372, 150, 40, 20, 20); }

    private boolean hit(RoundRectangle2D r) { return r.contains(in.mouseX, in.mouseY); }

    @Override
    public void render(Graphics2D g) {
        Art.backdrop(g, t);
        Art.panel(g, 300, 170, 680, 390);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 40));
        if (game.state.twoPlayers) {
            g.setFont(Art.UI);
            Art.shadowCentered(g, player == 1 ? "✦ Spieler 1 ✦" : "✦ Spieler 2 ✦", 640, 205, player == 1 ? new Color(150, 200, 255) : new Color(255, 190, 120));
            g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 40));
        }
        Art.shadowCentered(g, "Wie möchtest du heißen?", 640, 248, Art.GOLD);
        Art.ornament(g, 640, 272, 360, t);
        g.setFont(Art.ITALIC);
        g.setColor(new Color(220, 215, 235));
        Art.centered(g, "Unter diesem Namen wird man dich im Dämmerwald kennen.", 640, 310);
        double sx = shake > 0 ? Math.sin(t * 60) * shake * 18 : 0;
        RoundRectangle2D field = new RoundRectangle2D.Double(400 + sx, 360, 380, 60, 24, 24);
        g.setColor(new Color(20, 14, 34, 230));
        g.fill(field);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(shake > 0 ? Art.DANGER : Art.GOLD);
        g.draw(field);
        g.setFont(new Font("Serif", Font.BOLD, 32));
        String shown = name + (Math.sin(t * 6) > 0 ? "|" : " ");
        Art.shadowCentered(g, name.length() == 0 && Math.sin(t * 6) <= 0 ? "Dein Name …" : shown, 590 + sx, 402,
            name.length() == 0 ? new Color(160, 150, 180) : Color.WHITE);
        RoundRectangle2D d = dice();
        g.setColor(hit(d) ? new Color(80, 60, 120, 230) : new Color(40, 30, 64, 220));
        g.fill(d);
        g.setColor(Art.alpha(Art.GOLD, 160));
        g.draw(d);
        g.setFont(Art.UI);
        Art.shadowCentered(g, "✧ Zufallsname", d.getCenterX(), d.getCenterY() + 6, Art.PARCH);
        RoundRectangle2D b = okButton();
        g.setColor(hit(b) ? new Color(110, 80, 160) : new Color(80, 58, 124));
        g.fill(b);
        g.setColor(Art.GOLD);
        g.draw(b);
        g.setFont(Art.UI);
        Art.shadowCentered(g, "So soll es sein  [Enter]", 640, 501, Color.WHITE);
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 185, 210));
        Art.centered(g, "Tippe deinen Namen · Rücktaste löscht · Esc zurück", 640, 545);
    }
}
