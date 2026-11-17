package daemmerwald;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;

/** Zeichenfläche + Game-Loop. Rendert in eine virtuelle 1280x720-Auflösung und skaliert. */
public class GamePanel extends JPanel {
    public static final int W = 1280, H = 720;

    public final Input input = new Input();
    public GameState state = new GameState();
    private Scene scene;
    private Scene next;
    private long last;
    private double time;
    private double scale = 1, ox, oy;

    public GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.BLACK);
        setFocusable(true);
        setDoubleBuffered(true);
        addKeyListener(input);
        addMouseListener(input);
        addMouseMotionListener(input);
        scene = new TitleScene(this);
    }

    public void start() {
        requestFocusInWindow();
        last = System.nanoTime();
        scene.onEnter();
        new Timer(15, e -> tick()).start();
    }

    public void setScene(Scene s) {
        next = s;
    }

    public double time() {
        return time;
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1e9);
        last = now;
        time += dt;
        if (next != null) {
            scene = next;
            next = null;
            scene.onEnter();
        }
        scale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
        if (scale <= 0) scale = 1;
        ox = (getWidth() - W * scale) / 2;
        oy = (getHeight() - H * scale) / 2;
        input.setTransform(scale, ox, oy);
        scene.update(dt);
        input.endFrame();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(ox, oy);
        g.scale(scale, scale);
        g.setClip(0, 0, W, H);
        Art.hints(g);
        if (scene != null) scene.render(g);
        g.dispose();
        Toolkit.getDefaultToolkit().sync();
    }
}
