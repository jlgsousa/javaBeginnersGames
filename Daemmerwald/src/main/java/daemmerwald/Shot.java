package daemmerwald;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatischer Rundgang für Tests (-Ddw.tour=Ordner): stellt Szenen her, macht Bildschirmfotos und beendet sich.
 * Wird im normalen Spiel nicht benutzt.
 */
public class Shot {
    private final Main app;
    private final File dir;
    private final List<Object[]> steps = new ArrayList<>();
    private int idx = -1;
    private volatile String pending;
    private float wait;

    public Shot(Main app, String dir) {
        this.app = app;
        this.dir = new File(dir);
        this.dir.mkdirs();
        // Aufnahme erst nach der 2D-Ebene, damit HUD und Menüs mit im Bild sind
        app.getGuiViewPort().addProcessor(new com.jme3.post.SceneProcessor() {
            public void initialize(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) { }
            public void reshape(com.jme3.renderer.ViewPort vp, int w, int h) { }
            public boolean isInitialized() { return true; }
            public void preFrame(float tpf) { }
            public void postQueue(com.jme3.renderer.queue.RenderQueue rq) { }
            public void postFrame(com.jme3.texture.FrameBuffer out) {
                if (pending != null) { capture(pending, out); pending = null; }
            }
            public void cleanup() { }
            public void setProfiler(com.jme3.profile.AppProfiler p) { }
        });
        // {Name, Wartezeit, Aktion}
        step("title", 2.5f, () -> { });
        step("name", 2f, () -> { app.core.setScene(new NameScene(app.core)); for (char ch : "Liora".toCharArray()) app.core.input.typeChar(ch); });
        step("select_troll", 2f, () -> app.core.setScene(new CharacterSelectScene(app.core)));
        step("start_sunrise", 6f, () -> {
            app.core.state.hero = CharacterDef.ALL.get(1);
            app.core.state.dayTime = 0.245f;
            app.core.setScene(new WorldScene(app.core));
        });
        step("courtyard_day", 3f, () -> { skipDialog(); app.world.dayNight.time = 0.38f; });
        step("academy_noon", 3f, () -> { teleport(0, -60, FastMath.PI); app.world.dayNight.time = 0.5f; });
        step("sunrise_rays", 4f, () -> { teleport(30, -70, FastMath.HALF_PI); app.world.dayNight.time = 0.262f; });
        step("forest_detail", 4f, () -> { teleport(-60, -40, FastMath.PI * 0.8f); app.world.dayNight.time = 0.4f; });
        step("lake_day", 4f, () -> { teleport(-96, 8, -FastMath.HALF_PI); app.world.dayNight.time = 0.45f; });
        step("heart_sunset", 4f, () -> { teleport(0, 40, FastMath.PI); app.world.dayNight.time = 0.745f; });
        step("lake_night", 4f, () -> { teleport(-90, -14, -FastMath.HALF_PI); app.world.dayNight.time = 0.92f; });
        step("meadow_day", 4f, () -> { teleport(112, -80, FastMath.PI); app.world.dayNight.time = 0.42f; });
        step("shadow_grove", 4f, () -> { teleport(-118, -70, FastMath.PI); app.world.dayNight.time = 0.55f; });
        step("rain", 6f, () -> { teleport(20, -40, 0); app.world.dayNight.time = 0.45f; app.world.weather.forced = "rain"; app.world.weather.rain = 1; app.world.weather.cover = 0.95f; });
        step("rainbow", 8f, () -> { app.world.weather.forced = "clear"; app.world.weather.rain = 0; app.world.weather.cover = 0.3f; app.world.weather.rainbow = 1; app.world.dayNight.time = 0.62f; teleport(0, -20, FastMath.PI * 1.5f); });
        step("cafe_outside", 4f, () -> { app.world.weather.forced = null; teleport(34, 176, 0); app.world.dayNight.time = 0.7f; });
        step("cafe_window", 6f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            app.core.setScene(new CafeScene(app.core, ws));
        });
        step("cafe_seated", 6f, () -> { if (app.core.scene() instanceof CafeScene cs) cs.debugSeat(); app.world.dayNight.time = 0.72f; });
        step("wild_far", 6f, () -> {
            WorldScene ws = new WorldScene(app.core);
            app.core.state.flags.add("intro");
            app.core.setScene(ws);
            app.world.dayNight.time = 0.4f;
        });
        step("wild_far2", 6f, () -> teleport(-420, -380, 0.6f));
        step("menu_map", 3f, () -> { teleport(10, -50, FastMath.PI); press(java.awt.event.KeyEvent.VK_TAB); });
        step("fairy", 4f, () -> {
            app.core.state.hero = CharacterDef.ALL.get(5);
            app.core.state.outfit = Outfit.FLUEGELMANTEL;
            app.core.state.owned.add(Outfit.FLUEGELMANTEL);
            app.core.state.flags.add("intro");
            app.core.setScene(new WorldScene(app.core));
            app.world.dayNight.time = 0.3f;
        });
        step("home_out0", 4f, () -> {
            GameState gs = app.core.state;
            gs.twoPlayers = false;
            gs.hero = CharacterDef.ALL.get(5);
            gs.flags.add("intro");
            gs.flags.add("home:seen");
            gs.homeLevel = 0;
            app.core.setScene(new WorldScene(app.core));
        });
        step("home_out0b", 4f, () -> {
            Vector3f d = Home.door(0);
            teleport(d.x + FastMath.sin(Home.YAW) * 7 + 3, d.z + FastMath.cos(Home.YAW) * 7, Home.YAW + FastMath.PI + 0.35f);
            app.world.dayNight.time = 0.4f;
        });
        for (int lv = 1; lv <= 5; lv++) {
            final int L = lv;
            step("home_out" + lv, 3.5f, () -> {
                app.core.state.homeLevel = L;
                app.world.setHomeLevel(L);
                Vector3f d = Home.door(L);
                float back = L >= 2 ? 11 : 8;
                teleport(d.x + FastMath.sin(Home.YAW) * back + 3, d.z + FastMath.cos(Home.YAW) * back, Home.YAW + FastMath.PI + 0.3f);
            });
        }
        step("home_in", 4f, () -> {
            GameState gs = app.core.state;
            gs.homeLevel = 3;
            app.world.setHomeLevel(3);
            gs.furniture.addAll(java.util.List.of("teppich", "regal", "schaukelstuhl", "pflanzen", "kamin", "himmelbett", "lampe", "sofa", "gemaelde", "trankregal", "harfe", "mondlaterne"));
            WorldScene ws = (WorldScene) app.core.scene();
            app.core.setScene(new HomeScene(app.core, ws));
        });
        step("home_menu", 2.5f, () -> { if (app.core.scene() instanceof HomeScene hs) hs.debugMode("menu"); });
        step("home_bench", 2.5f, () -> {
            GameState gs = app.core.state;
            gs.addMat(Baustoff.HOLZ, 9); gs.addMat(Baustoff.STEIN, 5); gs.addMat(Baustoff.LEHM, 4); gs.addItem(Item.STERNKRISTALL, 2); gs.recipes.add("bretter");
            if (app.core.scene() instanceof HomeScene hs) hs.debugMode("bench");
        });
        step("home_build", 2.5f, () -> { if (app.core.scene() instanceof HomeScene hs) hs.debugMode("build"); });
        step("home_shop", 2.5f, () -> { if (app.core.scene() instanceof HomeScene hs) hs.debugMode("shop"); });
        step("village_day", 5f, () -> {
            app.core.state.twoPlayers = false;
            app.core.state.flags.add("intro");
            app.core.state.flags.add("home:seen");
            app.core.setScene(new WorldScene(app.core));
        });
        step("village_day2", 5f, () -> { teleport(-70, -38, -1.75f); app.world.dayNight.time = 0.62f; });
        step("village_night", 5f, () -> { teleport(-70, -38, -1.75f); app.world.dayNight.time = 0.9f; });
        step("village_bridge", 5f, () -> { teleport(-82, -37, -1.6f); app.world.dayNight.time = 0.8f; });
        step("bag", 3f, () -> {
            GameState gs = app.core.state;
            gs.flags.add("intro"); gs.flags.add("home:seen");
            app.core.setScene(new WorldScene(app.core));
            gs.addTreasure(Treasure.GOLDENE_EICHEL, 2); gs.addTreasure(Treasure.MONDPERLE, 1); gs.addTreasure(Treasure.FEENKRONE, 1);
            gs.addTreasure(Treasure.PHOENIXFEDER, 1); gs.addTreasure(Treasure.GLUEHGLAS, 3);
            gs.addItem(Item.MONDBLUME, 4); gs.addMat(Baustoff.HOLZ, 7);
            gs.friendship.put("aurin", 64); gs.friendship.put("borga", 25); gs.friendship.put("ilvy", 100); gs.friendship.put("vex", 8);
        });
        step("bag_open", 2.5f, () -> press(java.awt.event.KeyEvent.VK_I));
        step("friends_open", 2.5f, () -> press(java.awt.event.KeyEvent.VK_RIGHT));
        step("players", 2f, () -> app.core.setScene(new PlayersScene(app.core)));
        step("two_players", 6f, () -> {
            GameState gs = app.core.state;
            gs.hero = CharacterDef.ALL.get(0);
            gs.outfit = Outfit.WANDERER;
            gs.twoPlayers = true;
            gs.hero2 = CharacterDef.ALL.get(5);
            gs.playerName = "Liora";
            gs.player2Name = "Bramwen";
            gs.flags.add("intro");
            app.core.setScene(new WorldScene(app.core));
            app.world.dayNight.time = 0.4f;
        });
        step("two_players_walk", 4f, () -> { app.core.input.keyDown(java.awt.event.KeyEvent.VK_UP); app.core.input.keyDown(java.awt.event.KeyEvent.VK_LEFT); });
        step("one_player_again", 3f, () -> {
            app.core.input.keyUp(java.awt.event.KeyEvent.VK_UP); app.core.input.keyUp(java.awt.event.KeyEvent.VK_LEFT);
            app.core.state.twoPlayers = false;
            app.core.setScene(new WorldScene(app.core));
        });
        step("houses_knorz_out", 4f, () -> { House h = app.world.houses.get(1); Vector3f d = h.door(); teleport(d.x + (d.x - h.x) * 0.9f, d.z + (d.z - h.z) * 0.9f, FastMath.atan2(h.x - d.x, h.z - d.z)); app.world.dayNight.time = 0.7f; });
        step("houses_knorz_in", 4f, () -> { WorldScene ws = (WorldScene) app.core.scene(); app.core.setScene(new HouseScene(app.core, ws, app.world.houses.get(1))); });
        step("houses_evil_out", 4f, () -> { app.core.setScene(new WorldScene(app.core)); House h = app.world.houses.get(4); Vector3f d = h.door(); teleport(d.x + (d.x - h.x) * 0.9f, d.z + (d.z - h.z) * 0.9f, FastMath.atan2(h.x - d.x, h.z - d.z)); app.world.dayNight.time = 0.85f; });
        step("houses_evil_in", 4f, () -> { WorldScene ws = (WorldScene) app.core.scene(); app.core.setScene(new HouseScene(app.core, ws, app.world.houses.get(4))); });
        step("houses_mouse_out", 4f, () -> { app.core.setScene(new WorldScene(app.core)); House h = app.world.houses.get(2); Vector3f d = h.door(); teleport(d.x + (d.x - h.x) * 1.4f, d.z + (d.z - h.z) * 1.4f, FastMath.atan2(h.x - d.x, h.z - d.z)); app.world.dayNight.time = 0.4f; });
        step("end", 0.5f, () -> { });
    }

    private void step(String name, float wait, Runnable r) {
        String only = System.getProperty("dw.only");
        if (only != null && !name.equals("end") && !java.util.Arrays.asList(only.split(",")).contains(name)) return;
        steps.add(new Object[]{name, wait, r});
    }

    private void skipDialog() {
        if (app.core.scene() instanceof WorldScene ws) ws.dialog = null;
    }

    private void press(int vk) {
        app.core.input.keyDown(vk);
        app.enqueue(() -> app.core.input.keyUp(vk));
    }

    private void teleport(float x, float z, float yaw) {
        if (!(app.core.scene() instanceof WorldScene ws)) return;
        ws.dialog = null;
        ws.pos.set(x, app.world.height(x, z), z);
        try {
            java.lang.reflect.Field f = WorldScene.class.getDeclaredField("camYaw");
            f.setAccessible(true);
            f.setFloat(ws, yaw);
            java.lang.reflect.Field y = WorldScene.class.getDeclaredField("yaw");
            y.setAccessible(true);
            y.setFloat(ws, yaw);
            java.lang.reflect.Field ci = WorldScene.class.getDeclaredField("camInit");
            ci.setAccessible(true);
            ci.setBoolean(ws, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        app.world.chunks.preload(x, z);
    }

    public void update(float dt) {
        wait -= 1;
        if (wait > 0) return;
        if (pending != null) return;
        if (idx >= 0 && !captured) { pending = (String) steps.get(idx)[0]; captured = true; return; }
        captured = false;
        idx++;
        if (idx >= steps.size()) { app.stop(); return; }
        ((Runnable) steps.get(idx)[2]).run();
        wait = Math.max(3, (Float) steps.get(idx)[1] * 2);
    }

    private boolean captured;

    private void capture(String name, com.jme3.texture.FrameBuffer out) {
        int w = app.getCamera().getWidth(), h = app.getCamera().getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        app.getRenderer().readFrameBufferWithFormat(out, buf, Image.Format.RGBA8);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = ((h - 1 - y) * w + x) * 4;
            img.setRGB(x, y, ((buf.get(i) & 255) << 16) | ((buf.get(i + 1) & 255) << 8) | (buf.get(i + 2) & 255));
        }
        try {
            javax.imageio.ImageIO.write(img, "png", new File(dir, String.format("%02d_%s.png", idx, name)));
            System.out.println("SHOT " + name + " fps=" + app.getTimer().getFrameRate());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
