package daemmerwald;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.material.TechniqueDef;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.system.AppSettings;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/** Einstiegspunkt: "Das Flüstern im Dämmerwald" in 3D (jMonkeyEngine). */
public class Main extends SimpleApplication implements RawInputListener {

    /** Szenen, die die Kamera selbst steuern (Welt, Café). */
    public interface CameraDirector {
        void directCamera(Camera cam, float tpf);

        Vector3f focus();

        default boolean indoors() { return false; }
    }

    public GamePanel core;
    public World3D world;
    public Music music;
    private Overlay overlay;
    private final Map<Integer, Integer> keyMap = new HashMap<>();
    private float cineT;
    private final Vector3f lastFocus = new Vector3f(0, 0, -40);
    private Figure showcase;
    private int showcaseIdx = -1;
    public final Vector3f showcasePos = new Vector3f();
    private Shot shot;

    public static void main(String[] args) {
        Main app = new Main();
        AppSettings s = new AppSettings(true);
        s.setTitle("Das Flüstern im Dämmerwald");
        s.setResolution(1280, 720);
        s.setSamples(World3D.lowGraphics ? 0 : 4);
        s.setVSync(true);
        s.setRenderer(AppSettings.LWJGL_OPENGL32);
        s.setGammaCorrection(false);
        if (Boolean.getBoolean("dw.noaudio")) s.setAudioRenderer(null);
        app.setSettings(s);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);
        if (inputManager.hasMapping(INPUT_MAPPING_EXIT)) inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        flyCam.setEnabled(false);
        cam.setFrustumPerspective(58, (float) cam.getWidth() / cam.getHeight(), 0.25f, 1000f);
        renderManager.setPreferredLightMode(TechniqueDef.LightMode.SinglePass);
        renderManager.setSinglePassLightBatchSize(6);
        buildKeyMap();

        world = new World3D(this);
        world.chunks.preload(0, -40);
        overlay = new Overlay(this);
        core = new GamePanel(this);
        music = new Music(this);
        inputManager.addRawInputListener(this);
        String tour = System.getProperty("dw.tour");
        if (tour != null) shot = new Shot(this, tour);
    }

    @Override
    public void simpleUpdate(float tpf) {
        float dt = Math.min(tpf, 0.1f);
        Scene s = core.scene();
        boolean capture = s instanceof WorldScene ws && ws.wantsMouse();
        if (inputManager.isCursorVisible() == capture) inputManager.setCursorVisible(!capture);

        core.tick(dt);
        s = core.scene();

        Vector3f focus;
        boolean indoors = false;
        if (s instanceof CameraDirector d) {
            d.directCamera(cam, dt);
            focus = d.focus();
            indoors = d.indoors();
            lastFocus.set(focus);
        } else if (s instanceof CharacterSelectScene cs) {
            focus = showcase(cs.selected(), dt);
        } else if (s instanceof TitleScene || s instanceof IntroScene) {
            cineT += dt;
            float a = cineT * 0.035f;
            Vector3f c = new Vector3f(0, 0, -40);
            Vector3f p = new Vector3f(c.x + FastMath.sin(a) * 75, 0, c.z + FastMath.cos(a) * 75);
            p.y = world.height(p.x, p.z) + 16;
            cam.setLocation(p);
            cam.lookAt(new Vector3f(0, world.height(0, -100) + 22, -100), Vector3f.UNIT_Y);
            focus = c;
        } else {
            focus = lastFocus;
        }
        world.update(dt, focus, core.state, indoors);
        if (core.scene() instanceof WorldScene ws) ws.update3D(dt);
        overlay.render(core);
        if (music != null) music.update(dt, core, world);
        if (shot != null) shot.update(dt);
    }

    private Vector3f showcase(int idx, float dt) {
        if (idx != showcaseIdx) {
            if (showcase != null) showcase.node.removeFromParent();
            showcase = new Figure(CharacterDef.ALL.get(idx).look);
            showcasePos.set(6, 0, -48);
            showcasePos.y = world.height(showcasePos.x, showcasePos.z);
            showcase.node.setLocalTranslation(showcasePos);
            world.root.attachChild(showcase.node);
            showcaseIdx = idx;
        }
        cineT += dt;
        showcase.node.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(0.5f + FastMath.sin(cineT * 0.5f) * 0.6f, Vector3f.UNIT_Y));
        showcase.update(dt, 0, false);
        float h = showcase.height;
        Vector3f eye = showcasePos.add(-1.2f, h * 0.75f, h * 1.9f + 1.5f);
        cam.setLocation(eye);
        cam.lookAt(showcasePos.add(-1.9f, h * 0.55f, 0), Vector3f.UNIT_Y);
        return showcasePos;
    }

    public void removeShowcase() {
        if (showcase != null) showcase.node.removeFromParent();
        showcase = null;
        showcaseIdx = -1;
    }

    // ---------------------------------------------------------------- Eingabe

    private void buildKeyMap() {
        int[][] m = {
            {KeyInput.KEY_W, KeyEvent.VK_W}, {KeyInput.KEY_A, KeyEvent.VK_A}, {KeyInput.KEY_S, KeyEvent.VK_S}, {KeyInput.KEY_D, KeyEvent.VK_D},
            {KeyInput.KEY_E, KeyEvent.VK_E}, {KeyInput.KEY_Q, KeyEvent.VK_Q}, {KeyInput.KEY_M, KeyEvent.VK_M}, {KeyInput.KEY_F, KeyEvent.VK_F},
            {KeyInput.KEY_B, KeyEvent.VK_B}, {KeyInput.KEY_R, KeyEvent.VK_R}, {KeyInput.KEY_TAB, KeyEvent.VK_TAB},
            {KeyInput.KEY_UP, KeyEvent.VK_UP}, {KeyInput.KEY_DOWN, KeyEvent.VK_DOWN}, {KeyInput.KEY_LEFT, KeyEvent.VK_LEFT}, {KeyInput.KEY_RIGHT, KeyEvent.VK_RIGHT},
            {KeyInput.KEY_SPACE, KeyEvent.VK_SPACE}, {KeyInput.KEY_RETURN, KeyEvent.VK_ENTER}, {KeyInput.KEY_NUMPADENTER, KeyEvent.VK_ENTER},
            {KeyInput.KEY_ESCAPE, KeyEvent.VK_ESCAPE}, {KeyInput.KEY_LSHIFT, KeyEvent.VK_SHIFT}, {KeyInput.KEY_RSHIFT, KeyEvent.VK_SHIFT},
            {KeyInput.KEY_1, KeyEvent.VK_1}, {KeyInput.KEY_2, KeyEvent.VK_2}, {KeyInput.KEY_3, KeyEvent.VK_3},
            {KeyInput.KEY_4, KeyEvent.VK_4}, {KeyInput.KEY_5, KeyEvent.VK_5}, {KeyInput.KEY_6, KeyEvent.VK_6},
            {KeyInput.KEY_NUMPAD1, KeyEvent.VK_NUMPAD1}, {KeyInput.KEY_NUMPAD2, KeyEvent.VK_NUMPAD2}, {KeyInput.KEY_NUMPAD3, KeyEvent.VK_NUMPAD3},
            {KeyInput.KEY_NUMPAD4, KeyEvent.VK_NUMPAD4}, {KeyInput.KEY_NUMPAD5, KeyEvent.VK_NUMPAD5}, {KeyInput.KEY_NUMPAD6, KeyEvent.VK_NUMPAD6},
            {KeyInput.KEY_PGUP, KeyEvent.VK_PAGE_UP}, {KeyInput.KEY_PGDN, KeyEvent.VK_PAGE_DOWN}
        };
        for (int[] p : m) keyMap.put(p[0], p[1]);
    }

    @Override public void onKeyEvent(KeyInputEvent e) {
        Integer vk = keyMap.get(e.getKeyCode());
        if (vk == null || e.isRepeating()) return;
        if (e.isPressed()) core.input.keyDown(vk);
        else core.input.keyUp(vk);
    }

    @Override public void onMouseMotionEvent(MouseMotionEvent e) {
        Input in = core.input;
        in.mouseX = e.getX() * (double) GamePanel.W / cam.getWidth();
        in.mouseY = (cam.getHeight() - e.getY()) * (double) GamePanel.H / cam.getHeight();
        in.lookDX += e.getDX();
        in.lookDY += e.getDY();
        in.wheel += e.getDeltaWheel();
    }

    @Override public void onMouseButtonEvent(MouseButtonEvent e) {
        if (e.getButtonIndex() == 0) core.input.mouseButton(e.isPressed());
        if (e.getButtonIndex() == 1) core.input.rightDown = e.isPressed();
    }

    @Override public void beginInput() { }
    @Override public void endInput() { }
    @Override public void onJoyAxisEvent(JoyAxisEvent e) { }
    @Override public void onJoyButtonEvent(JoyButtonEvent e) { }
    @Override public void onTouchEvent(TouchEvent e) { }
}
