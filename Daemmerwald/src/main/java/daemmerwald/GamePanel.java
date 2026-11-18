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

    public void setScene(Scene s) { next = s; log("Szene: " + s.getClass().getSimpleName()); }

    /** Kleines Protokoll (~/.daemmerwald/protokoll.txt), damit sich Probleme beim Spielstart finden lassen. */
    public static void log(String msg) {
        try {
            java.nio.file.Path f = Mixamo.DIR.getParent().resolve("protokoll.txt");
            java.nio.file.Files.createDirectories(f.getParent());
            if (java.nio.file.Files.exists(f) && java.nio.file.Files.size(f) > 200_000) java.nio.file.Files.delete(f);
            java.nio.file.Files.writeString(f, java.time.LocalTime.now() + "  " + msg + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    public Scene scene() { return scene; }

    public double time() { return time; }

    void tick(double dt) {
        time += dt;
        long t0 = System.nanoTime();
        boolean changed = next != null;
        if (next != null) {
            scene = next;
            next = null;
            scene.onEnter();
        }
        scene.update(dt);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (changed || ms > 500) log("Bild in " + scene.getClass().getSimpleName() + ": " + ms + " ms");
        input.endFrame();
    }

    void render(Graphics2D g) {
        scene.render(g);
    }
}
