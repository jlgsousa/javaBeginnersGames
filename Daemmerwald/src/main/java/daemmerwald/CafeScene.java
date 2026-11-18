package daemmerwald;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Café Wolkenrand: Eine Fee nimmt die Bestellung auf, man setzt sich ans Fenster über der Klippe,
 * das Spiel speichert, und draußen ziehen Wetter, Vögel, Wale und Himmelsrochen vorbei.
 */
public class CafeScene implements Scene, Main.CameraDirector {
    private static final String[][] DRINKS = {
        {"Mondblütentee", "Ein heller Tee, der nach Nachtluft und Honig duftet.", "0.85,0.75,0.45"},
        {"Wolkenkakao", "Dick, süß und mit einer Haube aus Schaum, die aussieht wie ein Sturm in Zeitlupe.", "0.42,0.25,0.15"},
        {"Klippenkaffee", "Schwarz, stark, mit einer Prise Salz – wie der Wind hier oben.", "0.12,0.07,0.04"},
        {"Sternenschorle", "Prickelt in der Nase. Manchmal steigt ein winziger Funke aus dem Glas.", "0.55,0.75,1.0"},
        {"Quellwasser mit Minze", "Kalt, klar und ehrlich. Mehr braucht es manchmal nicht.", "0.7,0.85,0.8"}
    };

    private enum Stage { GREET, ORDER, SERVE, SEATED, BOOK }

    private final GamePanel game;
    private final WorldScene world;
    private final World3D w3;
    private final Input in;
    private Stage stage = Stage.GREET;
    private DialogBox dialog;
    private int drink = -1, hover = -1;
    private float t, lookYaw, lookPitch, savedT;
    private BookReader book;
    private final Vector3f cam = new Vector3f();

    public CafeScene(GamePanel game, WorldScene world) {
        this.game = game;
        this.world = world;
        this.w3 = world.world;
        this.in = game.input;
    }

    @Override
    public void onEnter() {
        game.state.flags.add("cafe");
        w3.weather.speed = 4;
        w3.vista.rate = 3;
        world.fig.node.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        String name = game.state.hero.name.split(" ")[0];
        dialog = new DialogBox("Lumi", w3.waitress.look, new String[]{
            "*Die Tür schließt sich hinter dir. Es riecht nach Zimt, Holzfeuer und Regen, der noch nicht gefallen ist.",
            "Willkommen im Café Wolkenrand! Ich bin Lumi. Setz dich, wohin du magst – der Fensterplatz ist heute besonders schön.",
            "Was darf ich dir bringen, " + name + "?"
        }, game.state.hero);
    }

    private void leave() {
        w3.weather.speed = 1;
        w3.vista.rate = 1;
        w3.showCup(null);
        world.fig.node.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
        game.input.releaseAll();
        game.setScene(world);
    }

    @Override
    public void update(double dtd) {
        float dt = (float) dtd;
        t += dt;
        game.state.playTime += dt;
        w3.waitress.update(dt, 0, false);
        w3.animateCafe(t);
        savedT = Math.max(0, savedT - dt);
        switch (stage) {
            case GREET -> {
                dialog.update(dt, in);
                if (dialog.done) stage = Stage.ORDER;
                if (in.wasPressed(KeyEvent.VK_ESCAPE)) leave();
            }
            case ORDER -> {
                hover = -1;
                for (int i = 0; i < DRINKS.length; i++) {
                    double y = 260 + i * 64;
                    if (in.mouseX > 330 && in.mouseX < 950 && in.mouseY > y - 34 && in.mouseY < y + 20) hover = i;
                    if (in.wasPressed(KeyEvent.VK_1 + i)) choose(i);
                }
                if (in.mouseClicked && hover >= 0) choose(hover);
                if (in.wasPressed(KeyEvent.VK_ESCAPE)) leave();
            }
            case SERVE -> {
                dialog.update(dt, in);
                if (dialog.done) {
                    stage = Stage.SEATED;
                    world.saveNow(false);
                    savedT = 4;
                }
            }
            case SEATED -> {
                lookYaw = FastMath.clamp(lookYaw - (float) in.lookDX * 0.0015f, -0.7f, 0.7f);
                lookPitch = FastMath.clamp(lookPitch - (float) in.lookDY * 0.0015f, -0.35f, 0.3f);
                if (in.isDown(KeyEvent.VK_LEFT, KeyEvent.VK_A)) lookYaw = Math.min(0.7f, lookYaw + dt * 0.6f);
                if (in.isDown(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) lookYaw = Math.max(-0.7f, lookYaw - dt * 0.6f);
                if (in.isDown(KeyEvent.VK_UP, KeyEvent.VK_W)) lookPitch = Math.min(0.3f, lookPitch + dt * 0.4f);
                if (in.isDown(KeyEvent.VK_DOWN, KeyEvent.VK_S)) lookPitch = Math.max(-0.35f, lookPitch - dt * 0.4f);
                if (in.wasPressed(KeyEvent.VK_B)) { book = new BookReader(); stage = Stage.BOOK; }
                if (in.wasPressed(KeyEvent.VK_R)) { drink = -1; w3.showCup(null); stage = Stage.ORDER; }
                if (in.wasPressed(KeyEvent.VK_1)) w3.weather.forced = "clear";
                if (in.wasPressed(KeyEvent.VK_2)) w3.weather.forced = "rain";
                if (in.wasPressed(KeyEvent.VK_3)) w3.weather.forced = null;
                if (in.wasPressed(KeyEvent.VK_ESCAPE)) { w3.weather.forced = null; leave(); }
            }
            case BOOK -> {
                if (!book.update(in)) stage = Stage.SEATED;
            }
        }
    }

    /** Für automatische Tests: direkt Platz nehmen. */
    void debugSeat() {
        choose(1);
        stage = Stage.SEATED;
    }

    private void choose(int i) {
        drink = i;
        String[] c = DRINKS[i][2].split(",");
        w3.showCup(new ColorRGBA(Float.parseFloat(c[0]), Float.parseFloat(c[1]), Float.parseFloat(c[2]), 1));
        dialog = new DialogBox("Lumi", w3.waitress.look, new String[]{
            "Ein " + DRINKS[i][0] + ". " + DRINKS[i][1],
            "Bitte schön. Lass dir Zeit – hier oben hat es niemand eilig. Wenn du magst, nimm dir ein Buch aus dem Regal.",
            "*Du setzt dich ans Fenster. Unter dir fällt die Klippe ins Meer, und der Horizont ist so weit, dass man das Atmen fast vergisst. (Dein Spiel wurde gespeichert.)"
        }, game.state.hero);
        stage = Stage.SERVE;
    }

    @Override
    public Vector3f focus() { return w3.cafeSeat; }

    @Override
    public boolean indoors() { return true; }

    @Override
    public void directCamera(Camera c, float tpf) {
        Vector3f target;
        Vector3f eye;
        if (stage == Stage.GREET || stage == Stage.ORDER) {
            Vector3f wp = w3.waitress.node.getLocalTranslation();
            eye = w3.cafeSeat.add(0.4f, 0.25f, -0.4f);
            target = wp.add(0, 1.35f, 0);
        } else {
            eye = w3.cafeSeat.clone();
            eye.y += FastMath.sin(t * 0.4f) * 0.01f;
            Vector3f dir = w3.cafeLook.subtract(w3.cafeSeat).normalizeLocal();
            float yaw = FastMath.atan2(dir.x, dir.z) + lookYaw;
            float pitch = FastMath.asin(dir.y) + lookPitch;
            target = eye.add(new Vector3f(FastMath.sin(yaw) * FastMath.cos(pitch), FastMath.sin(pitch), FastMath.cos(yaw) * FastMath.cos(pitch)));
        }
        cam.interpolateLocal(eye, cam.lengthSquared() == 0 ? 1 : Math.min(1, tpf * 3));
        c.setLocation(cam);
        c.lookAt(target, Vector3f.UNIT_Y);
    }

    @Override
    public void render(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(640f, 360f, 900f, new float[]{0.5f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(20, 8, 0, 130)}));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        switch (stage) {
            case GREET, SERVE -> dialog.render(g, t);
            case ORDER -> {
                Art.panel(g, 300, 150, 680, 420);
                g.setFont(Art.H2);
                Art.shadowCentered(g, "Karte des Café Wolkenrand", 640, 200, Art.GOLD);
                for (int i = 0; i < DRINKS.length; i++) {
                    double y = 260 + i * 64;
                    if (i == hover) {
                        g.setColor(new Color(90, 60, 40, 180));
                        g.fill(new RoundRectangle2D.Double(330, y - 34, 620, 54, 14, 14));
                    }
                    g.setFont(Art.UI);
                    Art.shadowText(g, (i + 1) + "   " + DRINKS[i][0], 350, y - 8, Art.PARCH);
                    g.setFont(Art.SMALL);
                    g.setColor(new Color(200, 190, 175));
                    Art.str(g, DRINKS[i][1], 375, (float) y + 11);
                }
                g.setFont(Art.SMALL);
                g.setColor(new Color(200, 200, 210));
                Art.centered(g, "Klick oder Taste 1–5 · Esc: doch wieder gehen", 640, 548);
            }
            case SEATED -> {
                g.setFont(Art.SMALL);
                String hint = "[B] Buch lesen   ·   Maus/Pfeile: umsehen   ·   Wetter: [1] Sonne [2] Regen [3] wie es kommt   ·   [R] nachbestellen   ·   [Esc] Café verlassen";
                int w = g.getFontMetrics().stringWidth(hint) + 30;
                g.setColor(new Color(10, 8, 6, 150));
                g.fill(new RoundRectangle2D.Double(640 - w / 2.0, 676, w, 30, 14, 14));
                g.setColor(new Color(235, 225, 205));
                Art.centered(g, hint, 640, 696);
                String info = w3.dayNight.clock() + "  ·  " + w3.weather.describe();
                g.setFont(Art.UI);
                Art.shadowCentered(g, info, 640, 34, new Color(250, 240, 220, 220));
                if (savedT > 0) {
                    g.setFont(Art.UI);
                    Art.shadowCentered(g, "✓ Spiel gespeichert", 640, 64, Art.alpha(Art.GOOD, (int) (Math.min(1, savedT) * 255)));
                }
            }
            case BOOK -> book.render(g);
        }
    }
}
