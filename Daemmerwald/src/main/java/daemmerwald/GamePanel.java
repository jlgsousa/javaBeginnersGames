package daemmerwald;

import java.awt.Graphics2D;

/**
 * Szenen-Verwaltung (früher ein Swing-Panel; der Name blieb, damit alle Szenen unverändert funktionieren).
 * Alle 2D-Oberflächen werden im virtuellen 1280x720-Raster gezeichnet und als Überlagerung über die 3D-Welt gelegt.
 */
public class GamePanel {
    public static final int W = 1280, H = 720;

    public final Input input = new Input();
    public final Main app;
    public GameState state = new GameState();
    private Scene scene;
    private Scene next;
    private double time;

    public GamePanel(Main app) {
        this.app = app;
        scene = new TitleScene(this);
        scene.onEnter();
    }

    public void setScene(Scene s) { next = s; }

    public Scene scene() { return scene; }

    public double time() { return time; }

    void tick(double dt) {
        time += dt;
        if (next != null) {
            scene = next;
            next = null;
            scene.onEnter();
        }
        scene.update(dt);
        input.endFrame();
    }

    void render(Graphics2D g) {
        scene.render(g);
    }
}
