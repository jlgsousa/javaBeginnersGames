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
        step("palace_out", 5f, () -> {
            GameState gs = app.core.state;
            gs.flags.add("intro"); gs.flags.add("home:seen"); gs.flags.add("palace:visited");
            app.core.setScene(new WorldScene(app.core));
            app.world.dayNight.time = 0.42f;
        });
        step("palace_out2", 5f, () -> teleport(Palace.PX + 14, Palace.PZ + 52, FastMath.PI + 0.25f));
        step("palace_hall", 5f, () -> { ((WorldScene) app.core.scene()).enterPalace(); });
        step("palace_hall2", 4f, () -> teleport(Palace.PX + 3, Palace.PZ + 6, FastMath.PI - 0.3f));
        step("palace_library", 4f, () -> teleport(Palace.PX - 15, Palace.PZ + 3, -FastMath.HALF_PI - 0.5f));
        step("palace_secret", 4f, () -> {
            app.core.state.flags.add("secret:regal");
            app.world.palace.applyOpen(app.core.state);
            teleport(Palace.PX - 25, Palace.PZ - 4, FastMath.PI + 0.2f);
        });
        step("palace_cellar", 4f, () -> teleport(Palace.PX + 15, Palace.PZ - 6, FastMath.HALF_PI + 0.4f));
        step("palace_night", 5f, () -> { ((WorldScene) app.core.scene()).exitPalace(); app.world.dayNight.time = 0.88f; teleport(Palace.PX + 10, Palace.PZ + 46, FastMath.PI + 0.2f); });
        step("lesson", 3f, () -> {
            GameState gs = app.core.state;
            gs.flags.add("intro"); gs.flags.add("home:seen"); gs.flags.add("palace:visited");
            app.core.setScene(new LessonGame(app.core, Spell.FLAMME));
        });
        step("lesson_play", 3f, () -> { press(java.awt.event.KeyEvent.VK_ENTER); });
        step("puzzle_vines", 5f, () -> {
            app.core.setScene(new WorldScene(app.core));
            app.core.state.spells.add(Spell.FLAMME); app.core.state.spell = Spell.FLAMME;
            app.world.dayNight.time = 0.45f;
        });
        step("puzzle_vines2", 4f, () -> teleport(24, -175.5f, FastMath.PI));
        step("puzzle_ice", 5f, () -> {
            app.core.state.flags.add("puzzle:eis1");
            app.world.puzzles.apply(app.core.state);
            teleport(-92, -6, -FastMath.HALF_PI - 0.15f);
        });
        step("puzzle_veil", 4f, () -> teleport(-118, -82, FastMath.PI));
        step("puzzle_boulder", 4f, () -> {
            app.core.state.flags.add("puzzle:fels1");
            app.world.puzzles.apply(app.core.state);
            teleport(112, -26, FastMath.PI);
        });
        step("protego", 4f, () -> {
            GameState gs = app.core.state;
            gs.flags.add("intro"); gs.flags.add("home:seen"); gs.flags.add("palace:visited");
            app.core.setScene(new WorldScene(app.core));
            gs.spells.add(Spell.SCHILD); gs.spell = Spell.SCHILD;
            app.world.dayNight.time = 0.8f;
        });
        step("protego_cast", 5f, () -> press(java.awt.event.KeyEvent.VK_Q));
        step("ch_start", 6f, () -> {
            app.core.state = new GameState();
            app.core.state.hero = CharacterDef.ALL.get(1);
            app.core.state.px = Palace.PX; app.core.state.pz = Palace.PZ + 34;
            app.core.state.dayTime = 0.3f;
            app.core.setScene(new WorldScene(app.core));
        });
        step("ch_book", 3f, () -> {
            GameState gs = app.core.state;
            ((WorldScene) app.core.scene()).dialog = null;
            gs.flags.add("spellbook"); gs.spells.add(Spell.LICHT); gs.spells.add(Spell.FLAMME); gs.spell = Spell.LICHT;
            press(java.awt.event.KeyEvent.VK_B);
        });
        step("ch_symbol", 5f, () -> {
            press(java.awt.event.KeyEvent.VK_B);
            Chapters.set(app.core.state, 1, 4);
            ((WorldScene) app.core.scene()).enterPalace();
            ((WorldScene) app.core.scene()).dialog = null;
            teleport(Palace.PX + 5.5f, Palace.PZ - 11f, FastMath.PI);
        });
        step("ch_painting", 5f, () -> {
            Chapters.set(app.core.state, 2, 1);
            app.core.state.flags.add("seal:FLAMME");
            app.world.palace.applyOpen(app.core.state);
            teleport(Palace.PX - 6f, Palace.PZ - 14f, -FastMath.HALF_PI);
        });
        step("ch_gallery", 5f, () -> {
            app.core.state.flags.add("seal:SCHWEBE"); app.core.state.flags.add("seal:LICHT"); app.core.state.flags.add("secret:gemaelde");
            Chapters.set(app.core.state, 2, 2);
            app.world.palace.applyOpen(app.core.state);
            teleport(Palace.PX - 13f, Palace.PZ - 14.5f, -FastMath.HALF_PI);
        });
        step("ch3_waves", 5f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            GameState gs = app.core.state;
            if (gs.flags.add("spellbook")) { }
            for (Spell sp : new Spell[]{Spell.LICHT, Spell.FLAMME, Spell.SCHWEBE, Spell.SCHILD}) gs.spells.add(sp);
            if (app.world.inPalace) ws.exitPalace();
            Chapters.set(gs, 3, 2);
            teleport(Saga.GROVE.x, Saga.GROVE.z + 13, FastMath.PI);
            ws.saga.startWaves();
        });
        step("ch3_fox", 5f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            Chapters.set(app.core.state, 3, 3);
            teleport(Saga.GROVE.x + 2, Saga.GROVE.z + 6, FastMath.PI);
            ws.saga.startFox();
        });
        step("ch4_village", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.saga.endEncounter();
            Chapters.set(app.core.state, 4, 1);
            app.core.state.flags.add("lantern:0");
            app.core.state.flags.add("lantern:2");
            ws.saga.apply();
            app.world.dayNight.time = 0.8f;
            teleport(-78.5f, -41f, -FastMath.HALF_PI);
        });
        step("ch4_flight", 4f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.saga.startFlight();
            ws.dialog = null;
            app.world.dayNight.time = 0.4f;
            ws.pos.set(-70, app.world.height(-70, -30) + 8, -30);
        });
        step("ch5_dark", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.saga.endEncounter();
            Chapters.set(app.core.state, 5, 1);
            ws.enterPalace();
            ws.dialog = null;
            ws.saga.apply();
            ws.teleportTo(ws.saga.dw(Depths.LIB_ARRIVE), FastMath.PI);
        });
        step("ch5_chasm", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            for (int i = 0; i < 3; i++) app.core.state.flags.add("lib:candle" + i);
            app.core.state.flags.add("lib:stack0");
            Chapters.set(app.core.state, 5, 2);
            ws.saga.apply();
            ws.teleportTo(ws.saga.dw(new Vector3f(112, Depths.LY, 9)), FastMath.PI - 0.4f);
        });
        step("ch5_runes", 5f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            for (int i = 0; i < 3; i++) app.core.state.flags.add("lib:stack" + i);
            Chapters.set(app.core.state, 5, 3);
            ws.saga.apply();
            ws.teleportTo(ws.saga.dw(new Vector3f(118, Depths.LY, -5)), FastMath.PI);
        });
        step("ch6_traps", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.saga.endEncounter();
            Chapters.set(app.core.state, 6, 1);
            ws.teleportTo(ws.saga.dw(new Vector3f(154, Depths.RY, 13)), FastMath.PI);
        });
        step("ch6_golem", 5f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            Chapters.set(app.core.state, 6, 2);
            app.core.state.flags.add("ruin:plate");
            ws.saga.apply();
            ws.teleportTo(ws.saga.dw(new Vector3f(158, Depths.RY, -68)), FastMath.PI);
            ws.saga.startGolem();
        });
        step("ch7_shade", 5f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            Chapters.set(app.core.state, 7, 1);
            ws.saga.apply();
            ws.teleportTo(ws.saga.dw(new Vector3f(210, Depths.HY, 12)), FastMath.PI);
            ws.saga.startShade();
        });
        step("scroll_open", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.saga.endEncounter();
            ws.scrollOpen = true;
        });
        step("scroll_menu", 3f, () -> { skipDialog(); ((WorldScene) app.core.scene()).clearOverlays(); press(java.awt.event.KeyEvent.VK_TAB); });
        step("scroll_bag", 2f, () -> press(java.awt.event.KeyEvent.VK_RIGHT));
        step("scroll_spells", 2f, () -> press(java.awt.event.KeyEvent.VK_RIGHT));
        step("palace_map", 4f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.enterPalace();
            skipDialog();
            ws.clearOverlays();
            for (String r : new String[]{"library", "cellar", "gallery", "deep_lib", "deep_lib2", "deep_bridge", "deep_ruin"}) app.core.state.flags.add("room:" + r);
            ws.teleportTo(ws.saga.dw(new Vector3f(118, Depths.LY, 15)), FastMath.PI);
            press(java.awt.event.KeyEvent.VK_TAB);
        });
        step("male_walk", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            skipDialog();
            ws.clearOverlays();
            app.core.state.hero = CharacterDef.ALL.get(Integer.getInteger("dw.hero", 0));
            ws.rebuildFigure();
            teleport(0, -60, FastMath.HALF_PI);
            app.world.dayNight.time = 0.5f;
            try {
                java.lang.reflect.Field z = WorldScene.class.getDeclaredField("zoom");
                z.setAccessible(true);
                z.setFloat(ws, 0.5f);
            } catch (Exception e) { }
            app.core.input.keyDown(java.awt.event.KeyEvent.VK_W);
        });
        step("male_walk2", 0.7f, () -> { });
        step("male_side", 1.5f, () -> {
            try {
                java.lang.reflect.Field f = WorldScene.class.getDeclaredField("camYaw");
                f.setAccessible(true);
                f.setFloat(app.core.scene(), FastMath.PI);
            } catch (Exception e) { }
        });
        step("male_stop", 2f, () -> app.core.input.keyUp(java.awt.event.KeyEvent.VK_W));
        step("cast_charge", 1.6f, () -> {
            app.core.input.keyUp(java.awt.event.KeyEvent.VK_W);
            try {
                java.lang.reflect.Field f = WorldScene.class.getDeclaredField("camYaw");
                f.setAccessible(true);
                f.setFloat(app.core.scene(), FastMath.HALF_PI + 1.2f);
            } catch (Exception e) { }
            app.core.input.keyDown(java.awt.event.KeyEvent.VK_Q);
        });
        step("cast_swing", 0.25f, () -> app.core.input.keyUp(java.awt.event.KeyEvent.VK_Q));
        step("cast_after", 1.5f, () -> { });
        step("palace_gate", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.enterPalace();
            skipDialog();
            ws.clearOverlays();
            ws.teleportTo(app.world.palace.toWorld(new Vector3f(0, 0, 9)), 0);
        });
        step("classroom", 3f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.enterPalace();
            skipDialog();
            ws.clearOverlays();
            ws.teleportTo(app.world.palace.toWorld(new Vector3f(14.5f, 0, 14.6f)), FastMath.HALF_PI);
        });
        step("castle_view", 4f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            skipDialog();
            ws.clearOverlays();
            app.world.dayNight.time = Float.parseFloat(System.getProperty("dw.daytime", "0.72"));
            teleport(0, -140, FastMath.PI);
            try {
                java.lang.reflect.Field z = WorldScene.class.getDeclaredField("zoom");
                z.setAccessible(true);
                z.setFloat(ws, 1.9f);
                java.lang.reflect.Field p = WorldScene.class.getDeclaredField("camPitch");
                p.setAccessible(true);
                p.setFloat(ws, -0.25f);
            } catch (Exception e) { }
        });
        step("castle_side", 4f, () -> {
            app.world.dayNight.time = Float.parseFloat(System.getProperty("dw.daytime", "0.5"));
            teleport(Float.parseFloat(System.getProperty("dw.cx", "95")), Float.parseFloat(System.getProperty("dw.cz", "-170")), Float.parseFloat(System.getProperty("dw.cyaw", "-1.88")));
            try {
                java.lang.reflect.Field z = WorldScene.class.getDeclaredField("zoom");
                z.setAccessible(true);
                z.setFloat(app.core.scene(), 1.9f);
                java.lang.reflect.Field p = WorldScene.class.getDeclaredField("camPitch");
                p.setAccessible(true);
                p.setFloat(app.core.scene(), Float.parseFloat(System.getProperty("dw.cpitch", "0.35")));
            } catch (Exception e) { }
        });
        step("voice_test", 4f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            ws.say("Ysmira Funkelstein", CharacterDef.ALL.get(9).look, new String[]{"Ah! Du musst die Neue sein – oder der Neue. Der Brief kam also wirklich an. Ich bin Ysmira Funkelstein und, nun ja, die einzige Lehrerin hier."}, null);
        });
        step("street", 4f, () -> {
            WorldScene ws = (WorldScene) app.core.scene();
            skipDialog();
            ws.clearOverlays();
            Chapters.set(app.core.state, 4, 1);
            app.world.dayNight.time = Float.parseFloat(System.getProperty("dw.daytime", "0.78"));
            teleport(-64, -95, -FastMath.HALF_PI);
        });
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
        ws.clearOverlays();
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
