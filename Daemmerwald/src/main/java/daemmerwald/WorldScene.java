package daemmerwald;

import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Die erkundbare 3D-Welt in Third-Person-Perspektive. */
public class WorldScene implements Scene, Main.CameraDirector {
    private static final int SW = GamePanel.W, SH = GamePanel.H;

    final GamePanel game;
    final Input in;
    final GameState st;
    final World3D world;
    final List<Npc> npcs = Story.createNpcs();
    private final Random rnd = new Random();

    // Spielfigur
    Figure fig;
    final Vector3f pos = new Vector3f();
    /** Spieler 2 (nur im 2-Spieler-Modus, sonst null). */
    Mate p2;
    private Npc nearP2;
    private House nearHouseP2;
    private boolean nearHome, nearHomeP2;
    /** Palast: was ist in der Nähe? (Text für den Hinweis, Aktion für E) */
    private String palaceLabel;
    private Runnable palaceAction;
    private float velY, yaw, camYaw, camPitch = 0.28f, zoom = 1, moveSpeed;
    private boolean onGround = true, flyingNow;
    float mana;
    private float castT, lightBoost, hurtT, invT, foxT, companionT, autosaveT;
    private final Vector3f camPos = new Vector3f();
    private boolean camInit;

    // Oberflächen
    DialogBox dialog;
    private Npc dialogNpc;
    private Choice choice;
    private int menuTab = -1;
    private boolean shop, brew;
    private int shopSel;
    private final List<FloatText> texts = new ArrayList<>();
    private BufferedImage mapImg;
    private Npc near;
    private House nearHouse;

    // Kreaturen
    private final List<Creature> creatures = new ArrayList<>();
    private float spawnT;
    private Geometry companion;
    private com.jme3.effect.ParticleEmitter dust;
    private boolean wasAir;

    // Rennen
    private Race race;

    private static class FloatText {
        String s;
        Vector3f p;
        float life;
        Color c;
    }

    /** Auswahlmenü in Dialogen. */
    static class Choice {
        final String title;
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        Choice(String title) { this.title = title; }
        Choice add(String l, Runnable r) { labels.add(l); actions.add(r); return this; }
    }

    public WorldScene(GamePanel game) {
        this.game = game;
        this.in = game.input;
        this.st = game.state;
        this.world = game.app.world;
        game.app.removeShowcase();
        pos.set(st.px, 0, st.pz);
        pos.y = world.height(pos.x, pos.z);
        yaw = FastMath.PI;
        camYaw = yaw;
        world.dayNight.time = st.dayTime;
        world.chunks.picked.addAll(st.picked);
        world.chunks.preload(pos.x, pos.z);
        if (st.flags.contains("blight")) world.setBlightCleared();
        rebuildFigure();
        world.setHomeLevel(st.homeLevel);
        if (st.twoPlayers) p2 = new Mate(st, world, pos, yaw);
        else if (world.mate != null) { world.mate.remove(); world.mate = null; }
        mana = (float) st.hero.manaMax();
        Material cm = G3.emissive(new ColorRGBA(1f, 0.9f, 0.6f, 1));
        companion = G3.geom("companion", new Sphere(10, 10, 0.12f), cm);
        companion.setCullHint(Spatial.CullHint.Always);
        world.root.attachChild(companion);
        new Thread(this::buildMap, "map").start();
        dust = G3.particles("dust", 40, "Effects/Smoke/Smoke.png", new ColorRGBA(0.55f, 0.48f, 0.38f, 0.28f), new ColorRGBA(0.5f, 0.45f, 0.38f, 0), 0.25f, 0.9f, 0.7f, 1.2f);
        dust.setImagesX(15);
        dust.getMaterial().getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        dust.setShape(new com.jme3.effect.shapes.EmitterSphereShape(Vector3f.ZERO, 0.25f));
        dust.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.4f, 0));
        dust.getParticleInfluencer().setVelocityVariation(1f);
        dust.setParticlesPerSec(0);
        dust.setInWorldSpace(true);
        world.root.attachChild(dust);
    }

    void rebuildFigure() {
        if (fig != null) fig.node.removeFromParent();
        fig = new Figure(st.outfit.apply(st.hero.look), st.outfit.accessory);
        fig.node.setLocalTranslation(pos);
        world.root.attachChild(fig.node);
    }

    @Override
    public void onEnter() {
        if (!st.flags.contains("intro")) {
            st.flags.add("intro");
            say(null, null, new String[]{
                "*Der Morgen dämmert über Wurzelhain. Hinter dir ragt die Akademie auf, ein Baum so alt wie der Pakt selbst.",
                "*Steuerung: WASD laufen · Maus umsehen · Linksklick oder Q zaubern · Leertaste springen · E sprechen · Tab Menü.",
                "*Rechts siehst du immer deine aktuellen Missionen. Sprich zuerst mit Borga – sie steht links vor dir auf dem Hof."
            }, null);
        }
    }

    public boolean racing() { return race != null; }

    /** Ein dunkles Wesen ist ganz in der Nähe – die Musik wird spannend. */
    public boolean danger() {
        for (Creature c : creatures) if (c.dark() && c.pos.distance(pos) < 16) return true;
        return false;
    }

    public boolean wantsMouse() {
        return dialog == null && choice == null && menuTab < 0 && !shop && !brew;
    }

    void say(String name, Look look, String[] lines, Runnable after) {
        dialog = new DialogBox(name, look, lines, st.hero);
        dialog.onFinish = after;
    }

    void sayNpc(Npc n, String[] lines, Runnable after) {
        dialogNpc = n;
        say(n.name, n.look, lines, after);
    }

    // ================================================================ Update (Logik)

    @Override
    public void update(double dtd) {
        float dt = (float) dtd;
        st.playTime += dt;
        st.dayTime = world.dayNight.time;
        for (Potion p : Potion.values()) {
            float r = st.effects.getOrDefault(p, 0f);
            if (r > 0) st.effects.put(p, Math.max(0, r - dt));
        }
        updateTexts(dt);
        if (p2 != null) {
            boolean frozen = dialog != null || choice != null || shop || brew || menuTab >= 0 || (race != null && race.countdown > 0);
            p2.update(dt, in, frozen, pos);
        }
        if (dialog != null) {
            dialog.update(dt, in);
            if (dialog.done) {
                Runnable r = dialog.onFinish;
                dialog = null;
                if (r == null) dialogNpc = null;
                if (r != null) r.run();
            }
            idle(dt);
            return;
        }
        if (choice != null) { updateChoice(); idle(dt); return; }
        if (shop) { updateShop(); idle(dt); return; }
        if (brew) { updateBrew(); idle(dt); return; }
        if (menuTab >= 0) { updateMenu(); idle(dt); return; }
        dialogNpc = null;
        if (in.wasPressed(KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB)) { menuTab = 0; return; }
        if (in.wasPressed(KeyEvent.VK_M)) { menuTab = 0; return; }
        if (in.wasPressed(KeyEvent.VK_I)) { menuTab = 1; return; }

        if (race != null && race.countdown > 0) {
            race.countdown -= dt;
            idle(dt);
        } else {
            move(dt);
        }
        if (race != null) updateRace(dt);

        float regen = (4 + st.hero.macht * 1.2f) * (st.has(Ability.MOND) ? 2 : 1) * (st.gadget(Outfit.Gadget.MANA) ? 2 : 1)
            * (st.effect(Potion.MANA) ? 2 : 1);
        if (!flyingNow) mana = Math.min(maxMana(), mana + regen * dt);
        lightBoost = Math.max(0, lightBoost - dt);

        if (castT > 0) {
            castT -= dt;
            if (castT <= 0) releaseSpell();
        }
        updateBolts(dt);
        if ((in.mouseClicked || in.wasPressed(KeyEvent.VK_Q, KeyEvent.VK_F)) && castT <= 0 && !fig.casting()) castSpell();
        if (p2 != null && p2.castNow) {
            if (p2.mana < 16) floatText("Zu wenig Mana", p2.pos.add(0, p2.fig.height + 0.8f, 0), Art.DANGER);
            else {
                p2.mana -= 16;
                p2.fig.cast();
                if (game.app.music != null) game.app.music.sfx("spell");
                releaseSpell(p2.pos, p2.yaw, p2.fig.height);
            }
        }

        for (int i = 0; i < 4; i++) if (in.wasPressed(KeyEvent.VK_1 + i)) drink(Potion.values()[i]);

        near = nearNpc(pos);
        nearHouse = near == null ? nearHouseAt(pos) : null;
        boolean use1 = p2 == null ? in.wasPressed(KeyEvent.VK_E, KeyEvent.VK_ENTER) : in.wasPressed(KeyEvent.VK_E);
        Vector3f homeDoor = Home.door(st.homeLevel);
        nearHome = near == null && nearHouse == null && homeDoor.distance(pos) < 3.4f;
        Npc useNpc = null;
        House useHouse = null;
        boolean useHome = false;
        if (use1) { useNpc = near; useHouse = nearHouse; useHome = nearHome; }
        if (p2 != null) {
            nearP2 = nearNpc(p2.pos);
            nearHouseP2 = nearP2 == null ? nearHouseAt(p2.pos) : null;
            nearHomeP2 = nearP2 == null && nearHouseP2 == null && homeDoor.distance(p2.pos) < 3.4f;
            if (p2.useNow && useNpc == null && useHouse == null && !useHome) { useNpc = nearP2; useHouse = nearHouseP2; useHome = nearHomeP2; }
        }
        if (!st.flags.contains("home:seen") && FastMath.sqr(pos.x - Home.X) + FastMath.sqr(pos.z - Home.Z) < 15 * 15) {
            st.flags.add("home:seen");
            say(null, null, new String[]{
                "*Da steht sie: deine kleine Holzhütte. Schief, zugig – aber deine.",
                "*Sammle in der Welt Holz, Stein und Lehm. An der Werkbank drinnen kombinierst du sie zu Brettern, Ziegeln und Kristallglas.",
                "*Damit baust du dein Zuhause Stufe um Stufe aus – bis zur Baumvilla. Möbel kannst du dir auch kaufen. Drück E an der Tür."
            }, null);
            return;
        }
        updatePalace(use1 || (p2 != null && p2.useNow));
        if (palaceAction != null && (use1 || (p2 != null && p2.useNow)) && race == null) {
            Runnable a = palaceAction;
            palaceAction = null;
            a.run();
        } else if (useHome && race == null) {
            in.releaseAll();
            game.setScene(new HomeScene(game, this));
        } else if (useNpc != null && race == null) interact(useNpc);
        else if (useHouse != null && race == null) {
            in.releaseAll();
            game.setScene(new HouseScene(game, this, useHouse));
        }

        pickups(pos);
        if (p2 != null) pickups(p2.pos);
        updateCreatures(dt);
        world.updateMarkers(st, npcs);

    }

    private Npc nearNpc(Vector3f at) {
        Npc found = null;
        float best = 1e9f;
        for (Npc n : npcs) {
            Vector3f p = world.npcPos.get(n.id);
            if (p == null) continue;
            float r = n.id.equals("heart") ? 10 : n.id.equals("cafe") ? 4.5f : n.id.startsWith("kessel") ? 3.2f : 3.8f;
            float d = p.distance(at);
            if (d < r && d < best) { best = d; found = n; }
        }
        return found;
    }

    private House nearHouseAt(Vector3f at) {
        List<House> hs = new ArrayList<>(world.houses);
        hs.addAll(world.chunks.housesNear(at.x, at.z));
        House found = null;
        for (House h : hs) if (h.door().distance(at) < 3.2f) found = h;
        return found;
    }

    private void idle(float dt) {
        moveSpeed = 0;
        velY = Math.max(velY - 12 * dt, -20);
        pos.y = Math.max(world.height(pos.x, pos.z), pos.y + velY * dt);
        if (pos.y <= world.height(pos.x, pos.z) + 0.01f) { velY = 0; onGround = true; }
        flyingNow = false;
    }

    private float maxMana() { return (float) st.hero.manaMax() + (st.effect(Potion.MANA) ? 30 : 0); }

    private void move(float dt) {
        if (!in.rightDown || true) {
            camYaw -= (float) in.lookDX * 0.0042f;
            camPitch = FastMath.clamp(camPitch - (float) in.lookDY * 0.0032f, -0.45f, 1.15f);
        }
        if (in.wheel != 0) zoom = FastMath.clamp(zoom - (float) in.wheel * 0.0012f, 0.45f, 1.9f);
        float fx = 0, fz = 0;
        // Im 2-Spieler-Modus gehören die Pfeiltasten Spieler 2
        boolean arrows = p2 == null;
        if (in.isDown(KeyEvent.VK_W) || (arrows && in.isDown(KeyEvent.VK_UP))) fz += 1;
        if (in.isDown(KeyEvent.VK_S) || (arrows && in.isDown(KeyEvent.VK_DOWN))) fz -= 1;
        if (in.isDown(KeyEvent.VK_A) || (arrows && in.isDown(KeyEvent.VK_LEFT))) fx -= 1;
        if (in.isDown(KeyEvent.VK_D) || (arrows && in.isDown(KeyEvent.VK_RIGHT))) fx += 1;
        Vector3f fwd = new Vector3f(FastMath.sin(camYaw), 0, FastMath.cos(camYaw));
        Vector3f right = new Vector3f(-fwd.z, 0, fwd.x);
        Vector3f dir = fwd.mult(fz).addLocal(right.mult(fx));
        boolean troll = st.hero.isTroll();
        boolean canFly = st.gadget(Outfit.Gadget.FLIGHT);
        float ground = world.height(pos.x, pos.z);
        boolean swim = st.gadget(Outfit.Gadget.SWIM);
        if (swim) ground = Math.max(ground, WorldGen.WATER - 1.1f);
        else ground = Math.max(ground, ground);

        float sp = (float) st.hero.moveSpeed() / 36f;
        if (st.gadget(Outfit.Gadget.SPEED)) sp *= 1.3f;
        if (st.effect(Potion.WIND)) sp *= 1.4f;
        if (st.has(Ability.SPRINT) && in.isDown(KeyEvent.VK_SHIFT)) sp *= 1.55f;
        if (world.height(pos.x, pos.z) < WorldGen.WATER + 0.1f && onGround) sp *= 0.6f;
        if (flyingNow) sp *= 1.6f;

        moveSpeed = 0;
        if (dir.lengthSquared() > 0) {
            dir.normalizeLocal();
            yaw = FastMath.atan2(dir.x, dir.z);
            float step = sp * dt;
            float nx = pos.x + dir.x * step, nz = pos.z + dir.z * step;
            boolean air = !onGround && pos.y > ground + 0.6f;
            if (canGo(nx, pos.z, air, swim, step)) pos.x = nx;
            if (canGo(pos.x, nz, air, swim, step)) pos.z = nz;
            moveSpeed = sp;
        }
        // Springen, Flattern, Fliegen
        boolean space = in.isDown(KeyEvent.VK_SPACE);
        flyingNow = false;
        if (canFly && space && mana > 1) {
            velY = Math.min(velY + 18 * dt, 5.5f);
            mana -= 7 * dt;
            flyingNow = true;
        } else if (!troll && space && pos.y < ground + 3.2f) {
            velY = Math.min(velY + 14 * dt, 3.2f);
        } else if (in.wasPressed(KeyEvent.VK_SPACE) && onGround) {
            velY = (troll ? 6.2f : 5f) * (st.gadget(Outfit.Gadget.JUMP) ? 1.75f : 1);
            onGround = false;
        }
        float grav = troll ? 18 : 9;
        if (canFly && !onGround && velY < 0 && space) grav = 3;
        else if (canFly && !onGround && velY < -2.5f) grav = 4;
        velY -= grav * dt;
        pos.y += velY * dt;
        ground = world.height(pos.x, pos.z);
        if (swim) ground = Math.max(ground, WorldGen.WATER - 1.1f);
        if (pos.y <= ground) {
            pos.y = ground;
            velY = 0;
            onGround = true;
        } else {
            onGround = pos.y < ground + 0.05f;
        }
        if (pos.y > 220) { pos.y = 220; velY = Math.min(velY, 0); }
    }

    private boolean canGo(float x, float z, boolean air, boolean swim, float step) {
        if (air) {
            return !world.chunks.blocked(x, z, 0.4f) || pos.y > world.height(x, z) + 8;
        }
        float hNew = world.height(x, z), hCur = world.height(pos.x, pos.z);
        if ((hNew - hCur) / Math.max(0.001f, step) > 1.5f && hNew > hCur + 0.05f) return false;
        if (!swim && hNew < WorldGen.WATER - 1.3f) return false;
        return !world.blockedIgnoringWater(x, z, 0.45f);
    }

    // ---------------------------------------------------------------- Zauber

    private void castSpell() {
        float cost = 16;
        if (mana < cost) { floatText("Zu wenig Mana", pos.add(0, fig.height + 0.8f, 0), Art.DANGER); return; }
        mana -= cost;
        fig.cast();
        if (game.app.music != null) game.app.music.sfx("spell");
        castT = 0.32f;
    }

    /** Fliegendes Lichtgeschoss mit Leuchtspur und eigenem Licht. */
    private class Bolt {
        final Node node = new Node("bolt");
        final com.jme3.light.PointLight light = new com.jme3.light.PointLight();
        final com.jme3.effect.ParticleEmitter trail;
        final Vector3f p, target;
        float trailLife = -1;

        Bolt(Vector3f from, Vector3f to) {
            p = from.clone();
            target = to.clone();
            node.attachChild(G3.geom("boltCore", new Sphere(10, 10, 0.16f), G3.emissive(new ColorRGBA(0.85f, 0.95f, 1f, 1))));
            trail = G3.particles("boltTrail", 60, null, new ColorRGBA(0.6f, 0.85f, 1f, 1), new ColorRGBA(0.3f, 0.5f, 1f, 0), 0.22f, 0.02f, 0.3f, 0.6f);
            trail.setParticlesPerSec(90);
            trail.setShape(new com.jme3.effect.shapes.EmitterSphereShape(Vector3f.ZERO, 0.06f));
            trail.getParticleInfluencer().setVelocityVariation(0.3f);
            trail.setInWorldSpace(true);
            node.attachChild(trail);
            light.setColor(new ColorRGBA(0.6f, 0.85f, 1f, 1).multLocal(2.5f));
            light.setRadius(9);
            world.root.addLight(light);
            world.root.attachChild(node);
        }

        /** @return true, wenn das Ziel erreicht ist. */
        boolean fly(float dt) {
            if (trailLife >= 0) {
                trailLife -= dt;
                if (trailLife < 0) node.removeFromParent();
                return false;
            }
            Vector3f d = target.subtract(p);
            float step = 24 * dt;
            if (d.length() <= step) {
                p.set(target);
                node.getChild("boltCore").removeFromParent();
                trail.setParticlesPerSec(0);
                world.root.removeLight(light);
                trailLife = 0.7f;
                return true;
            }
            p.addLocal(d.normalizeLocal().multLocal(step));
            node.setLocalTranslation(p);
            light.setPosition(p);
            return false;
        }
    }

    private final List<Bolt> bolts = new ArrayList<>();

    private void updateBolts(float dt) {
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (b.fly(dt)) spellImpact(b.target);
            if (b.trailLife < 0 && b.node.getParent() == null) it.remove();
        }
    }

    private void releaseSpell() { releaseSpell(pos, yaw, fig.height); }

    private void releaseSpell(Vector3f pos, float yaw, float figH) {
        float reach = st.has(Ability.LANZE) ? 9 : 5;
        Vector3f fwd = new Vector3f(FastMath.sin(yaw), 0, FastMath.cos(yaw));
        Vector3f target = pos.add(fwd.mult(reach)).addLocal(0, 1.2f, 0);
        Vector3f hand = pos.add(fwd.mult(0.7f)).addLocal(0, figH * 0.72f, 0);
        world.burst(hand, new ColorRGBA(1f, 0.95f, 0.8f, 1), 20);
        bolts.add(new Bolt(hand, target));
        lightBoost = 3;
    }

    private void spellImpact(Vector3f target) {
        if (palaceSpell(target)) return;
        world.burst(target, new ColorRGBA(0.6f, 0.9f, 1f, 1), 70);
        float r = st.has(Ability.LANZE) ? 16 : 10;
        for (Iterator<Creature> it = creatures.iterator(); it.hasNext(); ) {
            Creature c = it.next();
            if (c.dark() && (c.pos.distance(target) < r || c.pos.distance(pos) < r * 0.7f)) {
                banish(c);
                it.remove();
            }
        }
        if (world.clearBlight(target, r)) {
            st.flags.add("blight");
            floatText("Die Welke zerbricht!", target.add(0, 2, 0), Art.MAGIC);
        }
    }

    private void drink(Potion p) {
        int n = st.potions.getOrDefault(p, 0);
        if (n <= 0) { floatText("Kein " + p.title + " im Gepäck", pos.add(0, fig.height + 0.7f, 0), new Color(200, 200, 210)); return; }
        st.potions.put(p, n - 1);
        st.effects.put(p, p.seconds);
        if (p == Potion.MANA) mana = maxMana();
        world.burst(pos.add(0, fig.height * 0.6f, 0), G3.rgb(p.color), 40);
        floatText(p.title + " getrunken", pos.add(0, fig.height + 0.8f, 0), p.color);
    }

    // ---------------------------------------------------------------- Sammeln

    private void pickups(Vector3f pos) {
        float r = st.gadget(Outfit.Gadget.MAGNET) ? 6 : 1.7f;
        for (Chunks.Spot s : world.chunks.spotsNear(pos.x, pos.z, r + 1)) {
            float d = FastMath.sqrt((s.pos.x - pos.x) * (s.pos.x - pos.x) + (s.pos.z - pos.z) * (s.pos.z - pos.z));
            if (d > r || Math.abs(s.pos.y - pos.y) > 4) continue;
            s.geo.removeFromParent();
            st.picked.add(s.key);
            world.chunks.picked.add(s.key);
            int n = st.gadget(Outfit.Gadget.LUCK) && rnd.nextFloat() < 0.35f ? 2 : 1;
            if (s.treasure != null) {
                if (!treasureVisible(s.treasure)) continue;
                st.addTreasure(s.treasure, 1);
                if (game.app.music != null) game.app.music.sfx("chime");
                world.burst(s.pos, G3.rgb(s.treasure.glow()), 90);
                floatText("✦ RARITÄT: " + s.treasure.title + " ✦", s.pos.add(0, 1.6f, 0), s.treasure.color());
                floatText(Treasure.RARITY[s.treasure.rarity] + " · " + st.treasureKinds() + "/" + Treasure.values().length + " in deiner Sammlung", s.pos.add(0, 1.0f, 0), Art.PARCH);
                saveNow(false);
                continue;
            }
            if (s.mat != null) {
                n += s.mat == Baustoff.HOLZ ? rnd.nextInt(2) : 0;
                st.addMat(s.mat, n);
                if (game.app.music != null) game.app.music.sfx("chime");
                world.burst(s.pos, G3.rgb(s.mat.color), 16);
                floatText("+" + n + " " + s.mat.title, s.pos.add(0, 1.0f, 0), s.mat.color);
                continue;
            }
            st.addItem(s.item, n);
            st.collected += n;
            int c = st.earn(2);
            if (game.app.music != null) game.app.music.sfx("chime");
            world.burst(s.pos, G3.rgb(s.item.color), 20);
            floatText("+" + n + " " + s.item.title + "  +" + c + " Taler", s.pos.add(0, 1.2f, 0), s.item.color);
        }
    }

    // ---------------------------------------------------------------- Texte

    void floatText(String s, Vector3f p, Color c) {
        FloatText f = new FloatText();
        f.s = s;
        f.p = p.clone();
        f.life = 2.4f;
        f.c = c;
        texts.add(f);
    }

    private void updateTexts(float dt) {
        for (Iterator<FloatText> it = texts.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.life -= dt;
            f.p.y += dt * 0.7f;
            if (f.life <= 0) it.remove();
        }
    }

    // ================================================================ Kamera & 3D

    @Override
    public Vector3f focus() { return pos; }

    @Override
    public boolean indoors() { return world.inPalace; }

    @Override
    public void directCamera(Camera cam, float tpf) {
        Vector3f target;
        Vector3f desired;
        Vector3f npcP = dialogNpc != null ? world.npcPos.get(dialogNpc.id) : null;
        if (npcP != null && dialog != null) {
            Figure nf = world.figures.get(dialogNpc.id);
            float nh = nf != null ? nf.height : 4;
            target = npcP.add(0, nh * 0.82f, 0);
            Vector3f toNpc = npcP.subtract(pos).setY(0);
            if (toNpc.lengthSquared() < 0.01f) toNpc.set(0, 0, 1);
            toNpc.normalizeLocal();
            Vector3f side = new Vector3f(-toNpc.z, 0, toNpc.x);
            desired = pos.add(toNpc.mult(-2.2f)).addLocal(side.mult(1.3f)).addLocal(0, fig.height * 0.95f + 0.4f, 0);
            yaw = FastMath.atan2(toNpc.x, toNpc.z);
        } else {
            target = pos.add(0, fig.height * 0.85f, 0);
            float dist = (st.hero.isTroll() ? 6.8f : 4.8f) * zoom;
            Vector3f back = new Vector3f(-FastMath.sin(camYaw) * FastMath.cos(camPitch), FastMath.sin(camPitch), -FastMath.cos(camYaw) * FastMath.cos(camPitch));
            desired = target.add(back.mult(dist));
        }
        if (world.inPalace) {
            // Kamera nicht durch Palastwände und -decken
            Vector3f d = desired.subtract(target);
            float len = d.length();
            if (len > 0.01f) {
                d.divideLocal(len);
                float hit = world.palace.rayDistance(target, d, len);
                if (hit < len) desired = target.add(d.mult(Math.max(0.6f, hit - 0.35f)));
            }
        }
        float gh = world.height(desired.x, desired.z) + 0.6f;
        if (desired.y < gh) desired.y = gh;
        if (!camInit) { camPos.set(desired); camInit = true; }
        camPos.interpolateLocal(desired, Math.min(1, tpf * 8));
        cam.setLocation(camPos);
        cam.lookAt(target, Vector3f.UNIT_Y);
    }

    /** Wird nach dem Welt-Update aufgerufen: Figuren, Kreaturen, Begleiter. */
    private float treasureT;

    /** Manche Raritäten zeigen sich nur nachts, bei Sonnenaufgang oder nach dem Regen. */
    boolean treasureVisible(Treasure t) {
        float night = world.dayNight.night, time = world.dayNight.time;
        return switch (t) {
            case GLUEHGLAS, MONDMOOS, MONDPERLE, FEENKRONE -> night > 0.5f;
            case EINHORNHAAR -> time > 0.2f && time < 0.34f;
            case REGENSCHUPPE -> world.weather.rainbow > 0.15f || world.weather.rain > 0.2f;
            default -> true;
        };
    }

    public void update3D(float dt) {
        if (p2 != null) p2.update3D(dt);
        treasureT += dt;
        for (Chunks.Spot sp : world.chunks.spotsNear(pos.x, pos.z, 90)) {
            if (sp.treasure == null || sp.geo == null) continue;
            boolean vis = treasureVisible(sp.treasure);
            sp.geo.setCullHint(vis ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            if (vis) {
                sp.geo.setLocalRotation(new Quaternion().fromAngleAxis(treasureT * 1.6f, Vector3f.UNIT_Y));
                sp.geo.setLocalTranslation(sp.pos.x, sp.pos.y + FastMath.sin(treasureT * 2.2f) * 0.15f, sp.pos.z);
                if (FastMath.nextRandomFloat() < dt * 1.5f) world.burst(sp.pos, G3.rgb(sp.treasure.glow()), 6);
            }
        }
        fig.node.setLocalTranslation(pos);
        fig.face(yaw, dt);
        fig.flying = flyingNow;
        fig.update(dt, moveSpeed, !onGround);
        dust.setLocalTranslation(pos.add(0, 0.1f, 0));
        boolean wet = world.height(pos.x, pos.z) < WorldGen.WATER + 0.2f;
        dust.setParticlesPerSec(onGround && !wet && st.hero.isTroll() ? moveSpeed * 2.2f : onGround && !wet ? moveSpeed * 0.8f : 0);
        if (wasAir && onGround && !wet) dust.emitParticles(st.hero.isTroll() ? 14 : 6);
        wasAir = !onGround;
        for (Map.Entry<String, Figure> e : world.figures.entrySet()) {
            Figure f = e.getValue();
            if (race != null && e.getKey().equals("brumm")) continue;
            Vector3f p = f.node.getLocalTranslation();
            Vector3f home = world.npcPos.get(e.getKey());
            if (home != null && p.distanceSquared(home) > 0.01f) f.node.setLocalTranslation(home);
            if (p.distance(pos) < 14) {
                Vector3f d = pos.subtract(p);
                f.face(FastMath.atan2(d.x, d.z), dt);
            }
            f.update(dt, 0, false);
        }
        world.corvina.update(dt, 0, false);
        world.waitress.update(dt, 0, false);
        for (Creature c : creatures) c.sync(dt);
        boolean comp = st.has(Ability.BEGLEITER);
        companion.setCullHint(comp ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (comp) {
            float t = (float) game.time();
            Vector3f tp = pos.add(FastMath.cos(t * 1.3f) * 1.1f, fig.height + 0.4f + FastMath.sin(t * 2) * 0.2f, FastMath.sin(t * 1.3f) * 1.1f);
            companion.setLocalTranslation(companion.getLocalTranslation().interpolateLocal(tp, Math.min(1, dt * 5)));
        }
        float lightMul = (st.gadget(Outfit.Gadget.LIGHT) ? 2f : 1f) * (1 + lightBoost * 0.4f);
        world.lantern.setRadius(14 * lightMul);
    }

    // ================================================================ Kreaturen

    private class Creature {
        static final int WOLF = 0, MOTH = 1, FOX = 2, RABBIT = 3;
        final int type;
        final Vector3f pos = new Vector3f(), vel = new Vector3f();
        final Node node = new Node("creature");
        float t;
        Spatial wingL, wingR;

        Creature(int type, Vector3f p) {
            this.type = type;
            pos.set(p);
            build();
            world.root.attachChild(node);
        }

        boolean dark() { return type == WOLF || type == MOTH; }

        com.jme3.anim.AnimComposer anim;
        String animNow = "";

        /** Echtes, animiertes Fuchsmodell (Khronos glTF-Beispiel, CC-BY 4.0) – als Fuchs oder dunkel getönt als Schattenwolf. */
        private boolean realAnimal(boolean wolf) {
            Spatial m = G3.foxModel();
            if (m == null) return false;
            float sc = wolf ? 0.0125f : 0.0072f;
            m.setLocalScale(sc);
            if (wolf) {
                m.depthFirstTraversal(sp -> {
                    if (sp instanceof Geometry g) {
                        com.jme3.material.Material mm = g.getMaterial().clone();
                        mm.setColor("Diffuse", new ColorRGBA(0.2f, 0.17f, 0.24f, 1));
                        mm.setColor("Ambient", new ColorRGBA(0.12f, 0.1f, 0.16f, 1));
                        mm.setColor("GlowColor", new ColorRGBA(0.04f, 0f, 0.07f, 1));
                        g.setMaterial(mm);
                    }
                });
                for (int s = -1; s <= 1; s += 2) {
                    Geometry eye = G3.geom("eye", new Sphere(6, 6, 0.035f), G3.emissive(new ColorRGBA(1f, 0.15f, 0.25f, 1)));
                    eye.setLocalTranslation(s * 0.065f, 0.8f, 0.8f);
                    node.attachChild(eye);
                }
            }
            node.attachChild(m);
            anim = Mixamo.composer(m);
            return true;
        }

        void build() {
            if (type == WOLF && realAnimal(true)) {
                com.jme3.effect.ParticleEmitter smoke = G3.particles("wolfSmoke", 30, "Effects/Smoke/Smoke.png",
                    new ColorRGBA(0.06f, 0.02f, 0.1f, 0.45f), new ColorRGBA(0, 0, 0, 0), 0.5f, 1.2f, 0.8f, 1.6f);
                smoke.setImagesX(15);
                smoke.getMaterial().getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
                smoke.setShape(new com.jme3.effect.shapes.EmitterSphereShape(new Vector3f(0, 0.6f, -0.5f), 0.4f));
                smoke.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.4f, 0));
                smoke.setParticlesPerSec(12);
                smoke.setInWorldSpace(true);
                node.attachChild(smoke);
            } else if (type == FOX && realAnimal(false)) {
                // fertig
            } else if (type == WOLF) {
                Material fur = G3.litGlow(new ColorRGBA(0.04f, 0.03f, 0.06f, 1), new ColorRGBA(0.05f, 0, 0.08f, 1));
                Geometry body = G3.geom("wolfBody", new Sphere(10, 12, 1), fur);
                body.setLocalScale(0.32f, 0.36f, 0.85f);
                body.setLocalTranslation(0, 0.75f, 0);
                node.attachChild(body);
                Geometry head = G3.geom("wolfHead", new Sphere(10, 12, 0.28f), fur);
                head.setLocalScale(0.9f, 0.85f, 1.3f);
                head.setLocalTranslation(0, 1.0f, 0.85f);
                node.attachChild(head);
                for (int s = -1; s <= 1; s += 2) {
                    Geometry ear = G3.geom("ear", new com.jme3.scene.shape.Cylinder(2, 4, 0.08f, 0.01f, 0.25f, true, false), fur);
                    ear.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
                    ear.setLocalTranslation(s * 0.12f, 1.27f, 0.8f);
                    node.attachChild(ear);
                    Geometry eye = G3.geom("eye", new Sphere(6, 6, 0.045f), G3.emissive(new ColorRGBA(1f, 0.15f, 0.25f, 1)));
                    eye.setLocalTranslation(s * 0.11f, 1.05f, 1.15f);
                    node.attachChild(eye);
                    for (int f = -1; f <= 1; f += 2) {
                        Geometry leg = G3.geom("leg", new com.jme3.scene.shape.Box(0.05f, 0.3f, 0.05f), fur);
                        leg.setLocalTranslation(s * 0.16f, 0.3f, f * 0.5f);
                        node.attachChild(leg);
                    }
                }
                com.jme3.effect.ParticleEmitter smoke = G3.particles("wolfSmoke", 30, "Effects/Smoke/Smoke.png",
                    new ColorRGBA(0.06f, 0.02f, 0.1f, 0.5f), new ColorRGBA(0, 0, 0, 0), 0.5f, 1.2f, 0.8f, 1.6f);
                smoke.setImagesX(15);
                smoke.getMaterial().getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
                smoke.setShape(new com.jme3.effect.shapes.EmitterSphereShape(new Vector3f(0, 0.7f, -0.6f), 0.4f));
                smoke.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.4f, 0));
                smoke.setParticlesPerSec(14);
                smoke.setInWorldSpace(true);
                node.attachChild(smoke);
            } else if (type == MOTH) {
                Material wm = G3.litGlow(new ColorRGBA(0.2f, 0.06f, 0.26f, 1), new ColorRGBA(0.35f, 0.08f, 0.5f, 1));
                wm.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
                Node l = new Node("l"), r = new Node("r");
                Geometry wl = G3.geom("wing", new com.jme3.scene.shape.Box(0.45f, 0.01f, 0.32f), wm);
                wl.setLocalTranslation(-0.45f, 0, 0);
                l.attachChild(wl);
                Geometry wr = G3.geom("wing", new com.jme3.scene.shape.Box(0.45f, 0.01f, 0.32f), wm);
                wr.setLocalTranslation(0.45f, 0, 0);
                r.attachChild(wr);
                node.attachChild(l);
                node.attachChild(r);
                wingL = l;
                wingR = r;
                Geometry core = G3.geom("core", new Sphere(8, 8, 0.1f), G3.emissive(new ColorRGBA(0.85f, 0.4f, 1f, 1)));
                node.attachChild(core);
            } else if (type == RABBIT) {
                Material fur = G3.lit(new ColorRGBA(0.55f, 0.45f, 0.36f, 1));
                Geometry body = G3.geom("rBody", new Sphere(10, 12, 1), fur);
                body.setLocalScale(0.14f, 0.13f, 0.2f);
                body.setLocalTranslation(0, 0.14f, 0);
                node.attachChild(body);
                Geometry head = G3.geom("rHead", new Sphere(10, 12, 0.09f), fur);
                head.setLocalTranslation(0, 0.24f, 0.17f);
                node.attachChild(head);
                for (int sd = -1; sd <= 1; sd += 2) {
                    Geometry ear = G3.geom("rEar", new Sphere(6, 8, 1), fur);
                    ear.setLocalScale(0.025f, 0.09f, 0.015f);
                    ear.setLocalTranslation(sd * 0.04f, 0.36f, 0.14f);
                    ear.setLocalRotation(new Quaternion().fromAngleAxis(sd * 0.15f, Vector3f.UNIT_Z));
                    node.attachChild(ear);
                    Geometry eye = G3.geom("rEye", new Sphere(5, 5, 0.012f), G3.lit(new ColorRGBA(0.02f, 0.02f, 0.02f, 1)));
                    eye.setLocalTranslation(sd * 0.055f, 0.26f, 0.23f);
                    node.attachChild(eye);
                }
                Geometry tail = G3.geom("rTail", new Sphere(6, 6, 0.045f), G3.lit(new ColorRGBA(0.95f, 0.93f, 0.9f, 1)));
                tail.setLocalTranslation(0, 0.17f, -0.2f);
                node.attachChild(tail);
            } else {
                Material furM = G3.litGlow(new ColorRGBA(0.95f, 0.55f, 0.2f, 1), new ColorRGBA(0.45f, 0.22f, 0.05f, 1));
                Geometry body = G3.geom("foxBody", new Sphere(10, 12, 1), furM);
                body.setLocalScale(0.18f, 0.2f, 0.4f);
                body.setLocalTranslation(0, 0.35f, 0);
                node.attachChild(body);
                Geometry head = G3.geom("foxHead", new Sphere(10, 12, 0.15f), furM);
                head.setLocalScale(1, 0.9f, 1.3f);
                head.setLocalTranslation(0, 0.52f, 0.42f);
                node.attachChild(head);
                Geometry tail = G3.geom("foxTail", new Sphere(8, 10, 1), furM);
                tail.setLocalScale(0.12f, 0.12f, 0.35f);
                tail.setLocalTranslation(0, 0.5f, -0.6f);
                tail.setLocalRotation(new Quaternion().fromAngleAxis(-0.6f, Vector3f.UNIT_X));
                node.attachChild(tail);
                Geometry tip = G3.geom("tip", new Sphere(6, 6, 0.08f), G3.emissive(new ColorRGBA(1f, 0.95f, 0.85f, 1)));
                tip.setLocalTranslation(0, 0.72f, -0.86f);
                node.attachChild(tip);
                for (int s = -1; s <= 1; s += 2) {
                    Geometry ear = G3.geom("ear", new com.jme3.scene.shape.Cylinder(2, 4, 0.05f, 0.005f, 0.15f, true, false), furM);
                    ear.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
                    ear.setLocalTranslation(s * 0.07f, 0.68f, 0.4f);
                    node.attachChild(ear);
                }
            }
            node.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Cast);
        }

        void sync(float dt) {
            t += dt;
            float y = world.height(pos.x, pos.z);
            if (type == MOTH) y += 1.8f + FastMath.sin(t * 2) * 0.4f;
            if (type == FOX && anim == null) y += Math.abs(FastMath.sin(t * 7)) * 0.08f * Math.min(1, vel.length());
            if (anim != null) {
                float sp = vel.length();
                String want = sp > 3.2f ? "Run" : sp > 0.25f ? "Walk" : "Survey";
                if (!want.equals(animNow)) {
                    animNow = want;
                    try { anim.setCurrentAction(want); } catch (RuntimeException ignored) { }
                }
                anim.setGlobalSpeed(want.equals("Walk") ? Math.max(0.6f, sp / (type == WOLF ? 1.6f : 1.1f)) : 1f);
            }
            if (type == RABBIT) y += Math.abs(FastMath.sin(t * 9)) * 0.22f * Math.min(1, vel.length());
            node.setLocalTranslation(pos.x, Math.max(y, WorldGen.WATER), pos.z);
            if (vel.lengthSquared() > 0.01f) node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.atan2(vel.x, vel.z), Vector3f.UNIT_Y));
            if (wingL != null) {
                float f = FastMath.sin(t * 16) * 0.7f;
                wingL.setLocalRotation(new Quaternion().fromAngleAxis(f, Vector3f.UNIT_Z));
                wingR.setLocalRotation(new Quaternion().fromAngleAxis(-f, Vector3f.UNIT_Z));
            }
        }

        void remove() { node.removeFromParent(); }
    }

    // ================================================================ Zauberpalast

    private void updatePalace(boolean pressed) {
        palaceLabel = null;
        palaceAction = null;
        Palace pal = world.palace;
        if (!world.inPalace) {
            if (pal.gateOutside().distance(pos) < 4.5f) {
                palaceLabel = "[E] Zauberpalast Sternenhall betreten";
                palaceAction = this::enterPalace;
            }
            return;
        }
        // Rarität im Geheimraum aufheben
        Palace.Secret ts = pal.treasureNear(pos, 1.8f);
        if (ts != null && Palace.isOpen(st, ts.id) && !st.flags.contains("secretTreasure:" + ts.id)) {
            st.flags.add("secretTreasure:" + ts.id);
            st.addTreasure(ts.treasure, 1);
            pal.applyOpen(st);
            if (game.app.music != null) game.app.music.sfx("chime");
            world.burst(pal.toWorld(ts.treasurePos), G3.rgb(ts.treasure.glow()), 120);
            List<String> l = new ArrayList<>(List.of("*Auf dem goldenen Sockel liegt: " + ts.treasure.title + " (" + Treasure.RARITY[ts.treasure.rarity] + ")!",
                "*" + ts.treasure.text + "  –  " + st.treasureKinds() + "/" + Treasure.values().length + " Raritäten gesammelt."));
            l.addAll(st.addXp(40));
            say(null, null, l.toArray(new String[0]), null);
            saveNow(false);
            return;
        }
        if (pal.exitInside().distance(pos) < 2.6f) {
            palaceLabel = "[E] Palast verlassen";
            palaceAction = this::exitPalace;
            return;
        }
        Palace.Portal po = pal.portalNear(pos, 1.7f);
        if (po != null && Palace.isOpen(st, po.secret())) {
            palaceLabel = "[E] " + po.label();
            palaceAction = () -> {
                pos.set(pal.toWorld(po.to()));
                yaw = camYaw = po.yawTo();
                camInit = false;
                if (p2 != null) p2.pos.set(pos).addLocal(1, 0, 0.5f);
            };
            return;
        }
        Palace.Secret sc = pal.secretNear(pos, 3.6f);
        if (sc != null && !Palace.isOpen(st, sc.id)) {
            boolean knows = st.flags.contains("hint:" + sc.id);
            palaceLabel = "✦ " + sc.hint + (knows ? "  Zauber wirken (Q)!" : "");
        }
    }

    void enterPalace() {
        Palace pal = world.palace;
        world.setPalace(true);
        pal.applyOpen(st);
        for (Creature c : creatures) c.remove();
        creatures.clear();
        pos.set(pal.entryInside());
        yaw = camYaw = FastMath.PI;
        camInit = false;
        if (p2 != null) { p2.pos.set(pos).addLocal(1.5f, 0, 0); p2.yaw = FastMath.PI; }
        if (!st.flags.contains("palace:visited")) {
            st.flags.add("palace:visited");
            say(null, null, new String[]{
                "*Die Flügeltüren schwingen auf. Über dir schweben hunderte Kerzen unter einem Himmel aus Sternen – eine verzauberte Decke.",
                "*Das ist Sternenhall, der Zauberpalast der Akademie. Links die Bibliothek, rechts der Trankkeller, vorn der große Kamin.",
                "*Man erzählt sich von vier Geheimgängen. Wo etwas seltsam schimmert, hilft oft ein Zauber (Q). Und Freunde verraten Geheimnisse …"
            }, null);
        }
    }

    void exitPalace() {
        Palace pal = world.palace;
        world.setPalace(false);
        pos.set(pal.gateOutside()).addLocal(0, 0, 2.5f);
        pos.y = world.height(pos.x, pos.z);
        yaw = camYaw = 0;
        camInit = false;
        if (p2 != null) { p2.pos.set(pos).addLocal(1.5f, 0, 0); p2.pos.y = world.height(p2.pos.x, p2.pos.z); }
    }

    /** Zauber im Palast: öffnet einen Geheimgang, wenn man an der richtigen Stelle zaubert. */
    private boolean palaceSpell(Vector3f target) {
        if (!world.inPalace) return false;
        Palace pal = world.palace;
        Palace.Secret sc = pal.secretNear(target, 3.4f);
        if (sc == null) sc = pal.secretNear(pos, 3.6f);
        if (sc == null || Palace.isOpen(st, sc.id)) return false;
        st.flags.add("secret:" + sc.id);
        pal.applyOpen(st);
        world.burst(pal.toWorld(sc.spot), new ColorRGBA(0.6f, 0.8f, 1f, 1), 120);
        if (game.app.music != null) game.app.music.sfx("chime");
        int found = 0;
        for (Palace.Secret s2 : pal.secrets) if (Palace.isOpen(st, s2.id)) found++;
        List<String> l = new ArrayList<>(List.of("*Es knirscht und rumpelt – " + sc.title + " gibt einen geheimen Gang frei!",
            "*Geheimgänge entdeckt: " + found + " von " + pal.secrets.size() + ". Dahinter wartet etwas Seltenes …"));
        l.addAll(st.addXp(25));
        say(null, null, l.toArray(new String[0]), null);
        saveNow(false);
        return true;
    }

    private void updateCreatures(float dt) {
        if (world.inPalace) return;
        invT = Math.max(0, invT - dt);
        hurtT = Math.max(0, hurtT - dt);
        foxT = Math.max(0, foxT - dt);
        spawnT -= dt;
        int darkN = 0, foxN = 0, rabbitN = 0;
        for (Creature c : creatures) { if (c.dark()) darkN++; else if (c.type == Creature.RABBIT) rabbitN++; else foxN++; }
        float night = world.dayNight.night;
        int darkCap = (int) Math.min(14, 3 + st.level / 2 + st.count() + night * 4);
        if (spawnT <= 0) {
            spawnT = 0.9f;
            for (int tries = 0; tries < 5; tries++) {
                float a = rnd.nextFloat() * FastMath.TWO_PI, d = 32 + rnd.nextFloat() * 26;
                float x = pos.x + FastMath.cos(a) * d, z = pos.z + FastMath.sin(a) * d;
                float h = world.height(x, z);
                if (h < WorldGen.WATER + 0.3f || world.blocked(x, z, 0.6f)) continue;
                float dk = WorldGen.darkness(x, z), br = WorldGen.brightness(x, z);
                if (st.flags.contains("freed")) dk *= 0.6f;
                boolean nightForest = night > 0.6f && rnd.nextFloat() < 0.25f && br < 0.3f;
                if ((dk > 0.45f || nightForest) && darkN < darkCap) {
                    creatures.add(new Creature(rnd.nextFloat() < 0.55f ? Creature.WOLF : Creature.MOTH, new Vector3f(x, h, z)));
                    break;
                } else if (night < 0.5f && dk < 0.2f && rabbitN < 6 && (br > 0.2f || G3.fbm(x * 0.03 + 7, z * 0.03, 3) > 0.5)
                    && WorldGen.forest(x, z, h, 0) < 0.4f && rnd.nextFloat() < 0.6f) {
                    creatures.add(new Creature(Creature.RABBIT, new Vector3f(x, h, z)));
                    break;
                } else if (br > 0.4f && foxN < (st.gadget(Outfit.Gadget.FRIENDS) ? 8 : 4)) {
                    creatures.add(new Creature(Creature.FOX, new Vector3f(x, h, z)));
                    break;
                }
            }
        }
        // Irrlichtbegleiter
        if (st.has(Ability.BEGLEITER)) {
            companionT -= dt;
            if (companionT <= 0) {
                Creature target = null;
                float best = 18;
                for (Creature c : creatures) {
                    float d = c.pos.distance(pos);
                    if (c.dark() && d < best) { best = d; target = c; }
                }
                if (target != null) {
                    world.burst(target.pos.add(0, 1, 0), new ColorRGBA(1f, 0.9f, 0.6f, 1), 30);
                    banish(target);
                    target.remove();
                    creatures.remove(target);
                    companionT = 4.5f;
                } else companionT = 0.5f;
            }
        }
        float chaseR = st.gadget(Outfit.Gadget.STEALTH) ? 7 : 22;
        for (Iterator<Creature> it = creatures.iterator(); it.hasNext(); ) {
            Creature c = it.next();
            Vector3f d = pos.subtract(c.pos).setY(0);
            float dist = d.length();
            if (dist > 90) { c.remove(); it.remove(); continue; }
            if (c.type == Creature.WOLF) {
                float sp = dist < chaseR ? 3.9f + st.level * 0.12f : 1.4f;
                if (dist < chaseR && dist > 0.1f) c.vel.set(d.normalize().multLocal(sp));
                else if (rnd.nextFloat() < dt * 0.8f) { float a = rnd.nextFloat() * 6.28f; c.vel.set(FastMath.cos(a) * sp, 0, FastMath.sin(a) * sp); }
            } else if (c.type == Creature.MOTH) {
                Vector3f tgt = dist < chaseR * 1.3f ? d.normalize().multLocal(2.4f) : Vector3f.ZERO;
                c.vel.set(tgt.x + FastMath.cos(c.t * 2.3f) * 2.5f, 0, tgt.z + FastMath.sin(c.t * 3.1f) * 2);
            } else if (c.type == Creature.RABBIT) {
                if (dist < 7 && dist > 0.1f) c.vel.set(d.normalize().multLocal(-5.5f));
                else if (rnd.nextFloat() < dt * 0.9f) {
                    if (rnd.nextFloat() < 0.5f) c.vel.set(0, 0, 0);
                    else { float a = rnd.nextFloat() * 6.28f; c.vel.set(FastMath.cos(a) * 1.6f, 0, FastMath.sin(a) * 1.6f); }
                }
            } else {
                boolean follow = st.gadget(Outfit.Gadget.FRIENDS) && dist < 25;
                if (follow && dist > 3) c.vel.set(d.normalize().multLocal(4.5f));
                else if (rnd.nextFloat() < dt * 0.7f) { float a = rnd.nextFloat() * 6.28f; c.vel.set(FastMath.cos(a) * 1.8f, 0, FastMath.sin(a) * 1.8f); }
                if (!follow && WorldGen.brightness(c.pos.x + c.vel.x, c.pos.z + c.vel.z) < 0.3f) c.vel.negateLocal();
            }
            float nx = c.pos.x + c.vel.x * dt, nz = c.pos.z + c.vel.z * dt;
            if (c.type == Creature.MOTH || !world.blocked(nx, nz, 0.4f)) { c.pos.x = nx; c.pos.z = nz; }
            else c.vel.negateLocal();
            if (dist < 1.4f) {
                if (c.dark() && invT <= 0) {
                    if (st.effect(Potion.SCHATTEN)) {
                        floatText("Der Schattenschutz hält!", pos.add(0, fig.height + 0.6f, 0), new Color(200, 140, 255));
                        invT = 1.5f;
                    } else if (st.gadget(Outfit.Gadget.THORNS)) {
                        banish(c);
                        c.remove();
                        it.remove();
                        continue;
                    } else {
                        float loss = 16 * (st.has(Ability.SCHILD) ? 0.5f : 1) * (st.gadget(Outfit.Gadget.DEFENSE) ? 0.4f : 1);
                        mana = Math.max(0, mana - loss);
                        invT = 1.2f;
                        hurtT = 0.5f;
                        floatText("-" + (int) loss + " Mana", pos.add(0, fig.height + 0.6f, 0), new Color(200, 120, 255));
                        Vector3f push = d.normalize().multLocal(2.2f);
                        if (!world.blocked(pos.x + push.x, pos.z + push.z, 0.4f)) { pos.x += push.x; pos.z += push.z; }
                    }
                } else if (c.type == Creature.FOX && foxT <= 0) {
                    foxT = 6;
                    float gain = st.gadget(Outfit.Gadget.FRIENDS) ? 40 : 20;
                    mana = Math.min(maxMana(), mana + gain);
                    floatText("Der Lichtfuchs schmiegt sich an dich: +" + (int) gain + " Mana", pos.add(0, fig.height + 0.6f, 0), new Color(255, 210, 130));
                    world.burst(c.pos.add(0, 0.6f, 0), new ColorRGBA(1f, 0.8f, 0.5f, 1), 20);
                }
            }
        }
    }

    private void banish(Creature c) {
        st.banished++;
        world.burst(c.pos.add(0, 0.9f, 0), new ColorRGBA(0.7f, 0.3f, 1f, 1), 45);
        c.remove();
        List<String> msgs = st.addXp(8);
        int coins = st.earn(3);
        floatText("Vertrieben  +8 EP  +" + coins + " Taler", c.pos.add(0, 2, 0), Art.MAGIC);
        if (!msgs.isEmpty() && dialog == null) say(null, null, msgs.toArray(new String[0]), null);
    }

    // ================================================================ Gespräche & Aufträge

    private static final Map<String, Item[]> GIVERS = Map.of(
        "ysolde", new Item[]{Item.KROETENMOOS, Item.ASCHWURZ},
        "grumhild", new Item[]{Item.STERNKRISTALL},
        "oberion", new Item[]{Item.SEEROSE, Item.MONDBLUME},
        "aurin", new Item[]{Item.MONDBLUME, Item.FEDER},
        "borga", new Item[]{Item.KROETENMOOS, Item.STERNKRISTALL, Item.MONDBLUME});

    private int today() { return (int) (st.playTime / DayNight.DAY_SECONDS); }

    /** Tägliches Plaudern stärkt die Freundschaft. */
    private void interact(Npc n) {
        if (Friends.get(n.id) != null && st.stage("talkday:" + n.id) != today() + 2) {
            st.stages.put("talkday:" + n.id, today() + 2);
            List<String> up = Friends.add(st, n.id, 4);
            Vector3f np = world.npcPos.get(n.id);
            if (np != null) floatText("♥ Freundschaft +", np.add(0, 3.2f, 0), new Color(255, 140, 180));
            if (!up.isEmpty()) { sayNpc(n, up.toArray(new String[0]), () -> interactInner(n)); return; }
        }
        interactInner(n);
    }

    private void interactInner(Npc n) {
        switch (n.id) {
            case "heart" -> {
                if (!st.allShards()) say(null, null, Story.heartNotReady(st.count()), null);
                else if (!st.flags.contains("ended")) sayNpc(n, Story.HEART_READY, () -> startMinigame(n));
                else sayNpc(n, st.flags.contains("freed") ? Story.HEART_FREED : Story.HEART_RENEWED, () -> startMinigame(n));
                return;
            }
            case "cafe" -> { game.setScene(new CafeScene(game, this)); return; }
            case "kessel1", "kessel2" -> { brew = true; shopSel = 0; return; }
            case "pimpernell" -> {
                sayNpc(n, st.flags.contains("shopVisited") ? new String[]{"Ah, du schon wieder! Ich habe frisch gebügelt."} : n.before, () -> { shop = true; shopSel = 0; });
                st.flags.add("shopVisited");
                return;
            }
            case "brumm" -> {
                sayNpc(n, st.stage("brumm") > 1 ? n.after : n.before, () -> choice = new Choice("Brumm der Läufer")
                    .add("Rennen starten (Stufe " + st.stage("brumm") + ")", () -> startRace(n))
                    .add("Ein Geschenk überreichen (" + hearts(n.id) + ")", () -> openGift(n))
                    .add("Ein andermal", () -> { }));
                return;
            }
            case "rektor" -> { sayNpc(n, st.count() >= 3 ? n.after : n.before, () -> openChoice(n)); return; }
            default -> { }
        }
        boolean shardOpen = n.shard != null && !st.has(n.shard);
        if (shardOpen) { sayNpc(n, n.before, () -> startMinigame(n)); return; }
        String[] lines = n.game == null ? n.before : (n.shard != null || st.stage(n.id) > 1 ? n.after : n.before);
        sayNpc(n, lines, () -> openChoice(n));
    }

    private void openChoice(Npc n) {
        Choice c = new Choice(n.name);
        if (n.game != null) c.add("Prüfung ablegen (Stufe " + st.stage(n.id) + ", +" + (30 + 20 * st.stage(n.id)) + " EP)", () -> startMinigame(n));
        Item[] wants = GIVERS.get(n.id);
        if (wants != null) {
            int[] q = st.quests.get(n.id);
            if (q == null) {
                c.add("Nach einem Auftrag fragen", () -> {
                    Item it = wants[rnd.nextInt(wants.length)];
                    int need = 3 + rnd.nextInt(3) + st.level / 3;
                    int reward = 20 + need * 9;
                    st.quests.put(n.id, new int[]{it.ordinal(), need, reward});
                    sayNpc(n, new String[]{"Bring mir " + need + "× " + it.title + ". Findest du an Orten wie: " + it.where + ". Ich zahle " + reward + " Glimmertaler."}, null);
                });
            } else {
                Item it = Item.values()[q[0]];
                if (st.items(it) >= q[1]) {
                    c.add("Auftrag abgeben: " + q[1] + "× " + it.title, () -> {
                        st.addItem(it, -q[1]);
                        st.quests.remove(n.id);
                        int coins = st.earn(q[2]);
                        List<String> m = st.addXp(15 + q[1] * 6);
                        world.burst(pos.add(0, 2, 0), new ColorRGBA(1f, 0.85f, 0.4f, 1), 50);
                        String[] l = {"Wunderbar! Genau, was ich brauchte. Hier, " + coins + " Glimmertaler."};
                        for (String s : m) l = append(l, s);
                        for (String s : Friends.add(st, n.id, 12)) l = append(l, s);
                        sayNpc(n, l, null);
                    });
                } else {
                    c.add("(Auftrag läuft: " + st.items(it) + "/" + q[1] + " " + it.title + ")", () -> { });
                }
            }
        }
        if (Friends.get(n.id) != null) c.add("Ein Geschenk überreichen (" + hearts(n.id) + ")", () -> openGift(n));
        if (c.labels.isEmpty()) return;
        c.add("Auf Wiedersehen", () -> { });
        choice = c;
    }

    private String hearts(String id) {
        int h = Friends.hearts(st, id);
        return "♥".repeat(h) + "♡".repeat(Friends.MAX_HEARTS - h);
    }

    /** Geschenk aus der Tasche aussuchen. */
    private void openGift(Npc n) {
        Friends.Friend f = Friends.get(n.id);
        if (f == null) return;
        if (st.stage("giftday:" + n.id) == today() + 2) {
            sayNpc(n, new String[]{"Du hast mir heute schon etwas geschenkt. Morgen freue ich mich wieder!"}, null);
            return;
        }
        boolean knows = Friends.hearts(st, n.id) >= 1;
        Choice c = new Choice("Geschenk für " + f.name());
        List<String> keys = new ArrayList<>();
        for (Treasure t : Treasure.values()) if (st.treasure(t) > 0) keys.add("T:" + t.name());
        for (Item it : Item.values()) if (st.items(it) > 0) keys.add("I:" + it.name());
        // Lieblingsgeschenke zuerst (wenn bekannt)
        if (knows) keys.sort(Comparator.comparingInt(k -> -Friends.giftValue(f, k)));
        int shown = 0;
        for (String k : keys) {
            if (shown++ >= 7) break;
            boolean t = k.startsWith("T:");
            String name = t ? Treasure.valueOf(k.substring(2)).title : Item.valueOf(k.substring(2)).title;
            int have = t ? st.treasure(Treasure.valueOf(k.substring(2))) : st.items(Item.valueOf(k.substring(2)));
            boolean fav = knows && Friends.giftValue(f, k) >= 12 && (t ? Friends.giftValue(f, k) >= 40 : true);
            c.add((fav ? "♥ " : "") + name + "  (×" + have + ")" + (t ? "  – Rarität" : ""), () -> giveGift(n, f, k));
        }
        if (keys.isEmpty()) { sayNpc(n, new String[]{"*Deine Tasche ist leer. Sammle Zutaten oder Raritäten, um sie zu verschenken."}, null); return; }
        c.add("Doch nicht", () -> { });
        choice = c;
    }

    private void giveGift(Npc n, Friends.Friend f, String key) {
        if (key.startsWith("T:")) st.treasures.merge(Treasure.valueOf(key.substring(2)), -1, Integer::sum);
        else st.addItem(Item.valueOf(key.substring(2)), -1);
        st.treasures.values().removeIf(v -> v <= 0);
        st.stages.put("giftday:" + n.id, today() + 2);
        int v = Friends.giftValue(f, key);
        List<String> lines = new ArrayList<>();
        lines.add(Friends.reaction(f, v));
        lines.addAll(Friends.add(st, n.id, v));
        Vector3f np = world.npcPos.get(n.id);
        if (np != null) world.burst(np.add(0, 2.2f, 0), new ColorRGBA(1f, 0.5f, 0.75f, 1), 40);
        if (game.app.music != null) game.app.music.sfx("chime");
        sayNpc(n, lines.toArray(new String[0]), null);
    }

    /** Zufällige Rarität als Siegespreis (nicht die geheimen aus dem Palast oder von Freunden). */
    private Treasure randomPrize() {
        List<Treasure> pool = new ArrayList<>();
        for (Treasure t : Treasure.values()) {
            if (t.rarity == 2 || t == Treasure.STERNENSTAUB || t == Treasure.ZEITSAND || t == Treasure.KRISTALLROSE) continue;
            for (int i = 0; i < (t.rarity == 0 ? 4 : 1); i++) pool.add(t);
        }
        return pool.get(rnd.nextInt(pool.size()));
    }

    private void updateChoice() {
        int pick = -1;
        for (int i = 0; i < choice.labels.size() && i < 9; i++) if (in.wasPressed(KeyEvent.VK_1 + i)) pick = i;
        if (in.mouseClicked) {
            for (int i = 0; i < choice.labels.size(); i++) {
                double y = choiceY(i);
                if (in.mouseX > 360 && in.mouseX < 920 && in.mouseY > y - 26 && in.mouseY < y + 10) pick = i;
            }
        }
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) pick = choice.labels.size() - 1;
        if (pick >= 0) {
            Runnable r = choice.actions.get(pick);
            choice = null;
            r.run();
        }
    }

    private double choiceY(int i) {
        return SH / 2.0 - choice.labels.size() * 22 + 30 + i * 46;
    }

    static String[] append(String[] a, String s) {
        String[] r = Arrays.copyOf(a, a.length + 1);
        r[a.length] = s;
        return r;
    }

    // ---------------------------------------------------------------- Prüfungen

    void startMinigame(Npc n) {
        MiniGame mg = n.game.apply(game);
        mg.level = st.stage(n.id);
        if (n.id.equals("aurin")) mg.xpMult = 1.5;
        in.releaseAll();
        if (mg instanceof DuelGame) {
            Figure enemy = n.id.equals("heart") ? world.corvina : world.figures.get(n.id);
            Vector3f ep = enemy.node.getLocalTranslation().clone();
            Vector3f d = ep.subtract(pos).setY(0);
            if (d.length() < 4) {
                Vector3f dn = d.lengthSquared() < 0.01f ? new Vector3f(0, 0, 1) : d.normalize();
                pos.set(ep.subtract(dn.mult(6)));
                pos.y = world.height(pos.x, pos.z);
                d = ep.subtract(pos).setY(0);
            }
            yaw = FastMath.atan2(d.x, d.z);
            fig.node.setLocalTranslation(pos);
            fig.node.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
            Vector3f dn = d.normalize();
            enemy.node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.atan2(-dn.x, -dn.z), Vector3f.UNIT_Y));
            Vector3f mid = pos.add(ep).multLocal(0.5f);
            Vector3f side = new Vector3f(-dn.z, 0, dn.x);
            Camera cam = game.app.getCamera();
            Vector3f cp = mid.add(side.mult(9)).addLocal(0, 2.4f, 0);
            cp.y = Math.max(cp.y, world.height(cp.x, cp.z) + 1.5f);
            cam.setLocation(cp);
            cam.lookAt(mid.add(0, 1.4f, 0), Vector3f.UNIT_Y);
            DuelGame.heroWorld = pos.add(0, fig.height * 0.75f, 0).addLocal(dn.mult(0.5f));
            DuelGame.enemyWorld = ep.add(0, enemy.height * 0.75f, 0).subtractLocal(dn.mult(0.4f));
        } else {
            DuelGame.heroWorld = null;
        }
        mg.onDone = r -> {
            game.setScene(this);
            in.releaseAll();
            if (r == MiniGame.Result.WIN) {
                int lvl = mg.level;
                st.stages.put(n.id, lvl + 1);
                st.gamesWon++;
                int coins = st.earn(15 + 10 * lvl);
                List<String> lvlMsgs = st.addXp(mg.xpReward());
                if (!(n.id.equals("heart") && !st.flags.contains("ended"))) saveNow(true);
                floatText("+" + mg.xpReward() + " EP  +" + coins + " Taler", pos.add(0, fig.height + 1, 0), Art.GOLD);
                if (n.id.equals("heart") && !st.flags.contains("ended")) {
                    pendingLines = lvlMsgs;
                    game.setScene(new EndingScene(game, this));
                    return;
                }
                boolean firstShard = n.shard != null && !st.has(n.shard);
                world.burst(pos.add(0, 1.5f, 0), n.shard != null ? G3.rgb(n.shard.color) : new ColorRGBA(1f, 0.85f, 0.4f, 1), 80);
                String[] lines;
                if (firstShard) {
                    st.give(n.shard);
                    if (n.note != null) st.notes.add(n.note);
                    floatText(n.shard.title + " erhalten!", pos.add(0, fig.height + 1.6f, 0), n.shard.color);
                    lines = n.win;
                    if (st.allShards()) lines = append(lines, "*Alle sechs Splitter sind dein. Sie ziehen dich zum Herzbaum, in die Mitte des Waldes.");
                } else if (n.shard != null) {
                    lines = new String[]{"Stufe " + lvl + " gemeistert. Die nächste wird härter. Komm wieder, wann immer du willst."};
                } else {
                    lines = n.win;
                }
                for (String m : lvlMsgs) lines = append(lines, m);
                for (String m : Friends.add(st, n.id, 8)) lines = append(lines, m);
                if (rnd.nextFloat() < 0.12f) {
                    Treasure t = randomPrize();
                    st.addTreasure(t, 1);
                    lines = append(lines, "*Im Siegerkranz glitzert etwas: " + t.title + " (" + Treasure.RARITY[t.rarity] + ")! Es liegt jetzt in deiner Tasche.");
                }
                sayNpc(n, lines, null);
            } else if (r == MiniGame.Result.LOSE) {
                sayNpc(n, n.lose, null);
            }
        };
        game.setScene(mg);
    }

    private List<String> pendingLines;

    /** Nach dem Ende geht das Spiel endlos weiter. */
    public void afterEnding(boolean freed) {
        st.flags.add("ended");
        if (freed) st.flags.add("freed");
        String[] l = {
            "*Die Geschichte des Paktes ist erzählt – aber der Dämmerwald schläft nie, und die Welt reicht weiter, als irgendein Auge sieht.",
            "*Alle Prüfungen, Rennen, Rätsel und Aufträge kannst du immer wieder angehen – jedes Mal eine Stufe schwerer.",
            "*Und wenn du Ruhe brauchst: Das Café Wolkenrand auf der Klippe im Süden hat immer einen Fensterplatz frei."
        };
        if (pendingLines != null) for (String m : pendingLines) l = append(l, m);
        pendingLines = null;
        say(null, null, l, null);
    }

    void saveNow(boolean announce) {
        st.px = pos.x;
        st.pz = pos.z;
        st.dayTime = world.dayNight.time;
        st.save();
        if (announce) floatText("✓ Automatisch gespeichert", pos.add(0, fig.height + 1.3f, 0), Art.GOOD);
    }

    // ================================================================ Rennen

    private static final float[][][] ROUTES = {
        {{40, -52}, {66, -50}, {92, -44}, {80, -22}, {55, -5}, {28, 6}, {6, -18}, {4, -44}, {22, -56}},
        {{30, -74}, {55, -82}, {80, -92}, {104, -102}, {110, -86}, {92, -72}, {66, -60}, {40, -60}},
        {{10, -40}, {4, -14}, {-16, 6}, {-42, 6}, {-66, 2}, {-44, -12}, {-16, -26}, {6, -50}}
    };

    private class Race {
        final Npc rival;
        final Figure rivalFig;
        final List<Vector3f> cps = new ArrayList<>();
        final List<Geometry> rings = new ArrayList<>();
        int next;
        float countdown = 3.5f, rivalS, rivalSpeed, time;
        final Vector3f start;

        Race(Npc rival) {
            this.rival = rival;
            this.rivalFig = world.figures.get(rival.id);
            start = world.npcPos.get(rival.id).clone();
            float[][] route = ROUTES[(st.stage("brumm") - 1) % ROUTES.length];
            Material on = G3.emissive(new ColorRGBA(1f, 0.85f, 0.35f, 1));
            for (float[] p : route) {
                Vector3f v = world.ground(p[0], p[1]);
                cps.add(v);
                Geometry ring = G3.geom("raceRing", new Torus(32, 8, 0.18f, 2.8f), on.clone());
                ring.setLocalTranslation(v.add(0, 2.6f, 0));
                world.root.attachChild(ring);
                rings.add(ring);
            }
            for (int i = 0; i < rings.size(); i++) {
                Vector3f a = i == 0 ? start : cps.get(i - 1), b = cps.get(i);
                Vector3f d = b.subtract(a).setY(0).normalizeLocal();
                rings.get(i).setLocalRotation(new Quaternion().fromAngleAxis(FastMath.atan2(d.x, d.z), Vector3f.UNIT_Y));
            }
            rivalSpeed = 4.1f + st.stage("brumm") * 0.32f;
        }

        Vector3f rivalAt(float s) {
            Vector3f prev = start;
            for (Vector3f c : cps) {
                float seg = prev.distance(c);
                if (s <= seg) return prev.add(c.subtract(prev).multLocal(s / seg));
                s -= seg;
                prev = c;
            }
            return cps.get(cps.size() - 1).clone();
        }

        float length() {
            float l = 0;
            Vector3f prev = start;
            for (Vector3f c : cps) { l += prev.distance(c); prev = c; }
            return l;
        }

        void cleanup() {
            for (Geometry g : rings) g.removeFromParent();
        }
    }

    private void startRace(Npc n) {
        race = new Race(n);
        Vector3f s = race.start;
        Vector3f d = race.cps.get(0).subtract(s).setY(0).normalizeLocal();
        Vector3f side = new Vector3f(-d.z, 0, d.x);
        pos.set(s.add(side.mult(2.2f)));
        pos.y = world.height(pos.x, pos.z);
        yaw = camYaw = FastMath.atan2(d.x, d.z);
    }

    private void updateRace(float dt) {
        Race r = race;
        for (int i = 0; i < r.rings.size(); i++) {
            float a = i == r.next ? 1f : i > r.next ? 0.25f : 0f;
            r.rings.get(i).getMaterial().setColor("Color", new ColorRGBA(1f, 0.85f, 0.35f, 1).multLocal(a));
            r.rings.get(i).getMaterial().setColor("GlowColor", new ColorRGBA(1f, 0.8f, 0.3f, 1).multLocal(a));
            r.rings.get(i).setCullHint(i < r.next ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        }
        if (r.countdown > 0) {
            r.rivalFig.node.setLocalTranslation(r.start);
            return;
        }
        r.time += dt;
        r.rivalS += r.rivalSpeed * dt * (0.95f + 0.1f * FastMath.sin(r.time * 0.7f));
        Vector3f rp = r.rivalAt(r.rivalS);
        rp.y = world.height(rp.x, rp.z);
        Vector3f dir = r.rivalAt(r.rivalS + 1).subtract(rp).setY(0);
        r.rivalFig.node.setLocalTranslation(rp);
        if (dir.lengthSquared() > 0.0001f) r.rivalFig.face(FastMath.atan2(dir.x, dir.z), dt);
        r.rivalFig.update(dt, r.rivalSpeed, false);
        Vector3f cp = r.cps.get(r.next);
        if (FastMath.sqr(cp.x - pos.x) + FastMath.sqr(cp.z - pos.z) < 3.6f * 3.6f) {
            world.burst(cp.add(0, 2.6f, 0), new ColorRGBA(1f, 0.85f, 0.4f, 1), 30);
            r.next++;
            if (r.next >= r.cps.size()) { finishRace(true); return; }
        }
        if (r.rivalS >= r.length()) finishRace(false);
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) finishRace(false);
    }

    private void finishRace(boolean won) {
        Race r = race;
        race = null;
        r.cleanup();
        r.rivalFig.node.setLocalTranslation(r.start);
        if (won) {
            int lvl = st.stage("brumm");
            st.stages.put("brumm", lvl + 1);
            st.racesWon++;
            int coins = st.earn(30 + 15 * lvl);
            List<String> m = st.addXp(40 + 20 * lvl);
            saveNow(true);
            String[] l = append(r.rival.win, "*Zeit: " + String.format("%.1f", r.time) + " s.  +" + (40 + 20 * lvl) + " EP, +" + coins + " Glimmertaler.");
            for (String s : m) l = append(l, s);
            sayNpc(r.rival, l, null);
        } else {
            sayNpc(r.rival, r.rival.lose, null);
        }
    }

    // ================================================================ Laden & Kessel

    private void updateShop() {
        Outfit[] all = Outfit.values();
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { shop = false; return; }
        if (in.wasPressed(KeyEvent.VK_DOWN, KeyEvent.VK_S)) shopSel = (shopSel + 1) % all.length;
        if (in.wasPressed(KeyEvent.VK_UP, KeyEvent.VK_W)) shopSel = (shopSel + all.length - 1) % all.length;
        boolean act = in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_E);
        if (in.mouseClicked) {
            for (int i = 0; i < all.length; i++) {
                double y = 150 + i * 28;
                if (in.mouseX > 80 && in.mouseX < 620 && in.mouseY > y - 20 && in.mouseY < y + 6) {
                    if (shopSel == i) act = true;
                    shopSel = i;
                }
            }
        }
        if (act) {
            Outfit o = all[shopSel];
            if (st.owned.contains(o)) {
                st.outfit = o;
                rebuildFigure();
            } else if (st.coins >= o.price) {
                st.coins -= o.price;
                st.owned.add(o);
                st.outfit = o;
                rebuildFigure();
                world.burst(pos.add(0, 1.5f, 0), G3.rgb(o.color == null ? Color.WHITE : o.color), 60);
            }
        }
    }

    private void updateBrew() {
        Potion[] all = Potion.values();
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { brew = false; return; }
        if (in.wasPressed(KeyEvent.VK_DOWN, KeyEvent.VK_S)) shopSel = (shopSel + 1) % all.length;
        if (in.wasPressed(KeyEvent.VK_UP, KeyEvent.VK_W)) shopSel = (shopSel + all.length - 1) % all.length;
        boolean act = in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_E);
        if (in.mouseClicked) {
            for (int i = 0; i < all.length; i++) {
                double y = 220 + i * 90;
                if (in.mouseX > 240 && in.mouseX < 1040 && in.mouseY > y - 30 && in.mouseY < y + 50) {
                    if (shopSel == i) act = true;
                    shopSel = i;
                }
            }
        }
        if (act) {
            Potion p = all[shopSel];
            if (!p.canBrew(st)) return;
            for (Map.Entry<Item, Integer> e : p.needs.entrySet()) st.addItem(e.getKey(), -e.getValue());
            brew = false;
            PotionGame pg = new PotionGame(game);
            pg.level = 1 + st.brewed / 3;
            pg.onDone = r -> {
                game.setScene(this);
                in.releaseAll();
                if (r == MiniGame.Result.WIN) {
                    st.potions.merge(p, 1, Integer::sum);
                    st.brewed++;
                    saveNow(true);
                    List<String> m = st.addXp(25);
                    String[] l = {"*Der Kessel zischt und färbt sich. Du füllst einen " + p.title + " ab. (Trinken mit Taste " + (p.ordinal() + 1) + ")"};
                    for (String s : m) l = append(l, s);
                    say(null, null, l, null);
                } else {
                    for (Map.Entry<Item, Integer> e : p.needs.entrySet()) st.addItem(e.getKey(), e.getValue() / 2 + (e.getValue() % 2));
                    say(null, null, new String[]{"*Der Trank misslingt. Ein Teil der Zutaten lässt sich noch retten."}, null);
                }
            };
            game.setScene(pg);
        }
    }

    // ================================================================ Menü

    private static final String[] TABS = {"Karte", "Tasche", "Freunde", "Garderobe", "Missionen", "Fähigkeiten", "Tagebuch", "Spiel"};

    private void updateMenu() {
        if (in.wasPressed(KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB) || (menuTab == 0 && in.wasPressed(KeyEvent.VK_M))) { menuTab = -1; return; }
        if (in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) menuTab = (menuTab + 1) % TABS.length;
        if (in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A)) menuTab = (menuTab + TABS.length - 1) % TABS.length;
        if (in.mouseClicked) {
            for (int i = 0; i < TABS.length; i++) {
                double x = 40 + i * 152;
                if (in.mouseX > x && in.mouseX < x + 144 && in.mouseY > 40 && in.mouseY < 84) menuTab = i;
            }
        }
        if (menuTab == 3) {
            List<Outfit> owned = new ArrayList<>(st.owned);
            for (int i = 0; i < owned.size(); i++) {
                double y = 150 + i * 40;
                if (in.mouseClicked && in.mouseX > 90 && in.mouseX < 700 && in.mouseY > y - 26 && in.mouseY < y + 10) {
                    st.outfit = owned.get(i);
                    rebuildFigure();
                }
            }
        }
        if (menuTab == 1) for (int i = 0; i < 4; i++) if (in.wasPressed(KeyEvent.VK_1 + i)) drink(Potion.values()[i]);
        if (menuTab == 7) {
            if (in.wasPressed(KeyEvent.VK_Q) || (in.mouseClicked && hitButton(1))) {
                fig.node.removeFromParent();
                for (Creature c : creatures) c.remove();
                game.state = new GameState();
                game.setScene(new TitleScene(game));
            }
        }
    }

    private boolean hitButton(int i) {
        double y = 220 + i * 80;
        return in.mouseX > 440 && in.mouseX < 840 && in.mouseY > y && in.mouseY < y + 56;
    }

    private void buildMap() {
        int n = 420;
        float range = 330;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float x = -range + 2 * range * i / n, z = -range + 2 * range * j / n;
                float h = WorldGen.height(x, z);
                Color c;
                if (h < WorldGen.WATER) c = Art.mix(new Color(30, 70, 110), new Color(10, 30, 60), Math.min(1, -h / 14));
                else {
                    float slope = Math.abs(WorldGen.height(x + 1.5f, z) - h);
                    float f = WorldGen.forest(x, z, h, slope);
                    c = Art.mix(new Color(96, 120, 70), new Color(34, 64, 40), f);
                    if (WorldGen.pathDist(x, z) < 2.5) c = new Color(150, 128, 92);
                    if (slope > 1.2f) c = new Color(110, 108, 104);
                    c = Art.mix(c, new Color(110, 60, 140), WorldGen.darkness(x, z) * 0.5f);
                    c = Art.shade(c, 0.85 + Math.min(0.3, h / 80.0));
                }
                img.setRGB(i, j, c.getRGB());
            }
        }
        mapImg = img;
    }

    // ================================================================ Darstellung

    private double[] screen(Vector3f p) {
        double[] a = screen(p, game.app.getCamera());
        if (!world.split() || world.cam2 == null) return a;
        if (a != null && a[0] <= SW / 2.0) return a;
        a = screen(p, world.cam2);
        return a != null && a[0] >= SW / 2.0 ? a : null;
    }

    private double[] screen(Vector3f p, Camera cam) {
        if (cam == null) return null;
        Vector3f s = cam.getScreenCoordinates(p);
        if (s.z < 0 || s.z > 1) return null;
        Vector3f toP = p.subtract(cam.getLocation());
        if (toP.dot(cam.getDirection()) < 0) return null;
        return new double[]{s.x * SW / cam.getWidth(), SH - s.y * SH / cam.getHeight()};
    }

    private boolean calm() { return dialog == null && choice == null && menuTab < 0 && !shop && !brew && race == null; }

    /** Trennlinie, Statusleiste und Hinweise für Spieler 2. */
    private void drawSplit(Graphics2D g) {
        if (p2 == null || !world.split()) return;
        int mx = SW / 2;
        g.setColor(new Color(8, 6, 14));
        g.fillRect(mx - 3, 0, 6, SH);
        g.setColor(Art.alpha(Art.GOLD, 200));
        g.setStroke(new BasicStroke(1.4f));
        g.drawLine(mx - 3, 0, mx - 3, SH);
        g.drawLine(mx + 3, 0, mx + 3, SH);
        for (int y = 90; y < SH; y += 180) Art.star(g, mx, y, 7, Art.GOLD);

        double x = mx + 16;
        Art.panel(g, x, 16, 280, 92);
        g.setFont(Art.UI);
        Art.shadowText(g, st.displayName2(), x + 16, 42, new Color(255, 200, 130));
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 190, 210));
        g.drawString(st.hero2.name.split(" ")[0] + ", " + st.hero2.epithet, (float) x + 16, 61);
        Art.bar(g, x + 16, 70, 248, 14, p2.mana / p2.maxMana(), Art.MAGIC);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("Mana " + (int) p2.mana, (float) x + 22, 81);

        if (nearP2 != null && calm()) {
            Vector3f p = world.npcPos.get(nearP2.id);
            Figure nf = world.figures.get(nearP2.id);
            double[] s = screen(p.add(0, (nf != null ? nf.height : 2.5f) + 0.5f, 0), world.cam2);
            if (s == null) s = new double[]{mx + SW / 4.0, SH - 140};
            label(g, "[Enter] " + nearP2.name, s, Art.GOLD, new Color(10, 12, 20, 200));
        } else if (nearHouseP2 != null && calm()) {
            double[] s = screen(nearHouseP2.door().add(0, 2.4f, 0), world.cam2);
            if (s == null) s = new double[]{mx + SW / 4.0, SH - 140};
            label(g, "[Enter] " + (nearHouseP2.cafe ? nearHouseP2.title + " betreten" : (nearHouseP2.evil ? "Anklopfen bei " : "Besuch bei ") + nearHouseP2.owner),
                s, Art.GOLD, new Color(40, 26, 14, 210));
        }
        if (nearHomeP2 && calm()) {
            double[] sc = screen(Home.door(st.homeLevel).add(0, 2.6f, 0), world.cam2);
            if (sc == null) sc = new double[]{mx + SW / 4.0, SH - 140};
            label(g, "[Enter] Nach Hause", sc, new Color(255, 215, 150), new Color(40, 26, 14, 210));
        }
        if (p2.leashHint > 0) {
            g.setFont(Art.UI);
            Art.shadowCentered(g, "Bleib in der Nähe von " + st.displayName() + "!", mx + SW / 4.0, SH / 2.0 - 120,
                Art.alpha(new Color(255, 200, 130), (int) (Math.min(1, p2.leashHint) * 255)));
        }
    }

    private void label(Graphics2D g, String text, double[] s, Color fg, Color bg) {
        g.setFont(Art.UI);
        int w = g.getFontMetrics().stringWidth(text) + 26;
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Double(s[0] - w / 2.0, s[1] - 20, w, 28, 14, 14));
        Art.shadowCentered(g, text, s[0], s[1], fg);
    }

    @Override
    public void render(Graphics2D g) {
        // Vignette
        RadialGradientPaint vig = new RadialGradientPaint(SW / 2f, SH / 2f, SW * 0.72f, new float[]{0.55f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 120)});
        g.setPaint(vig);
        g.fillRect(0, 0, SW, SH);
        if (hurtT > 0) {
            g.setColor(new Color(120, 30, 160, (int) (hurtT * 200)));
            g.fillRect(0, 0, SW, SH);
        }
        for (FloatText f : texts) {
            double[] s = screen(f.p);
            if (s == null) continue;
            g.setFont(Art.UI);
            Art.shadowCentered(g, f.s, s[0], s[1], Art.alpha(f.c, (int) (Math.min(1, f.life) * 255)));
        }
        if (near != null && dialog == null && choice == null && menuTab < 0 && !shop && !brew && race == null) {
            Vector3f p = world.npcPos.get(near.id);
            Figure nf = world.figures.get(near.id);
            double[] s = screen(p.add(0, (nf != null ? nf.height : 2.5f) + 0.5f, 0));
            if (s == null) s = new double[]{SW / 2.0, SH - 140};
            String label = "[E] " + near.name;
            g.setFont(Art.UI);
            int w = g.getFontMetrics().stringWidth(label) + 24;
            g.setColor(new Color(10, 12, 20, 200));
            g.fill(new RoundRectangle2D.Double(s[0] - w / 2.0, s[1] - 20, w, 28, 14, 14));
            Art.shadowCentered(g, label, s[0], s[1], Art.GOLD);
        }
        if (nearHouse != null && dialog == null && choice == null && menuTab < 0 && !shop && !brew && race == null) {
            double[] s = screen(nearHouse.door().add(0, 2.4f, 0));
            if (s == null) s = new double[]{SW / 2.0, SH - 140};
            String label = "[E] " + (nearHouse.cafe ? nearHouse.title + " betreten" : (nearHouse.evil ? "Anklopfen bei " : "Besuch bei ") + nearHouse.owner);
            g.setFont(Art.UI);
            int w = g.getFontMetrics().stringWidth(label) + 30;
            g.setColor(nearHouse.evil ? new Color(40, 10, 50, 210) : new Color(40, 26, 14, 210));
            g.fill(new RoundRectangle2D.Double(s[0] - w / 2.0, s[1] - 20, w, 28, 14, 14));
            Art.shadowCentered(g, label, s[0], s[1], nearHouse.evil ? new Color(220, 150, 255) : Art.GOLD);
        }
        if (nearHome && calm()) {
            double[] sc = screen(Home.door(st.homeLevel).add(0, 2.6f, 0));
            if (sc == null) sc = new double[]{SW / 2.0, SH - 140};
            label(g, "[E] Nach Hause", sc, new Color(255, 215, 150), new Color(40, 26, 14, 210));
        }
        if (palaceLabel != null && calm()) label(g, palaceLabel, new double[]{SW / 2.0, SH - 150}, new Color(255, 220, 150), new Color(30, 20, 50, 215));
        if (world.inPalace && calm()) {
            Palace.Room rm = world.palace.roomAt(pos.x, pos.z);
            if (rm != null) {
                g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 24));
                Art.shadowCentered(g, "Sternenhall · " + rm.name(), SW / 2.0, 80, new Color(230, 210, 255));
            }
        }
        drawSplit(g);
        drawHud(g);
        if (race != null) drawRace(g);
        if (dialog == null && menuTab < 0 && !shop && !brew) drawTracker(g);
        if (choice != null) drawChoice(g);
        if (shop) drawShop(g);
        if (brew) drawBrew(g);
        if (menuTab >= 0) drawMenu(g);
        if (dialog != null) dialog.render(g, game.time());
    }

    private void drawHud(Graphics2D g) {
        Art.panel(g, 16, 16, 330, 112);
        g.setFont(Art.UI);
        Art.shadowText(g, st.displayName(), 32, 42, Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 190, 210));
        g.drawString(st.hero.name.split(" ")[0] + ", " + st.hero.epithet + "  ·  " + st.outfit.title, 32, 61);
        Art.bar(g, 32, 70, 296, 14, mana / maxMana(), Art.MAGIC);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("Mana " + (int) mana, 38, 81);
        Art.bar(g, 32, 90, 296, 9, st.xp / (double) st.xpNext(), Art.GOLD);
        g.setFont(Art.SMALL);
        Art.shadowText(g, "Stufe " + st.level + "   ·   " + st.coins + " Glimmertaler", 32, 119, Art.GOLD);

        // Uhrzeit & Wetter
        String clock = world.dayNight.clock() + "  ·  " + world.dayNight.phaseName() + "  ·  " + world.weather.describe();
        g.setFont(Art.UI);
        int w = g.getFontMetrics().stringWidth(clock) + 40;
        double cy = p2 != null ? SH - 104 : 14;   // geteilter Bildschirm: Uhr unten in der Mitte
        Art.panel(g, SW / 2.0 - w / 2.0, cy, w, 36);
        Art.shadowCentered(g, clock, SW / 2.0, cy + 24, Art.PARCH);

        // Splitter
        Shard[] all = Shard.values();
        for (int i = 0; i < all.length; i++) Art.shard(g, SW - 262 + i * 42, 34, 12, all[i].color, st.has(all[i]), game.time() + i);

        // Aktive Trank-Effekte & Tränke
        double y = 150;
        for (Potion p : Potion.values()) {
            float r = st.effects.getOrDefault(p, 0f);
            if (r <= 0) continue;
            g.setFont(Art.SMALL);
            Art.shadowText(g, "✦ " + p.title + "  " + (int) r + " s", 24, y, p.color);
            y += 20;
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(200, 200, 220, 200));
        StringBuilder pot = new StringBuilder();
        for (Potion p : Potion.values()) pot.append("[").append(p.ordinal() + 1).append("] ").append(p.title).append(" ×").append(st.potions.getOrDefault(p, 0)).append("   ");
        g.drawString(pot.toString(), 20, SH - 40);
        if (p2 != null) {
            g.drawString("WASD · Maus · Klick/Q zaubern · Leertaste " + (st.hero.isTroll() ? "springen" : "fliegen") + " · E sprechen · Tab Menü", 20, SH - 18);
            g.drawString("Pfeile laufen · R-Strg " + (st.hero2.isTroll() ? "springen" : "fliegen") + " · R-Umschalt zaubern · Enter sprechen",
                SW / 2 + 20, SH - 18);
            g.drawString("oder Gamepad: Stick · A " + (st.hero2.isTroll() ? "springen" : "fliegen") + " · B zaubern · X sprechen", SW / 2 + 20, SH - 40);
            return;
        }
        g.drawString("WASD laufen · Maus schauen · Klick/Q zaubern · Leertaste " + (st.gadget(Outfit.Gadget.FLIGHT) ? "fliegen" : st.hero.isTroll() ? "springen" : "flattern")
            + (st.has(Ability.SPRINT) ? " · Umschalt rennen" : "") + " · E sprechen · I Tasche · Tab Menü", 20, SH - 18);
    }

    /** Missionsliste am rechten Rand. */
    private List<String[]> missions() {
        List<String[]> m = new ArrayList<>();
        if (!st.allShards()) {
            Shard next = null;
            for (Shard s : Shard.values()) if (!st.has(s)) { next = s; break; }
            m.add(new String[]{"Hauptquest", "Splitter des Paktsteins: " + st.count() + "/6", "Nächster: " + next.where});
            if (!st.has(Shard.WIND) && !world.blightCleared() && st.count() >= 3)
                m.add(new String[]{"Hauptquest", "Brenne die Welke vor der Dornenschlucht weg", "Zauber (Klick/Q) direkt davor"});
        } else if (!st.flags.contains("ended")) {
            m.add(new String[]{"Hauptquest", "Bringe die Splitter zum Herzbaum", "Mitte des Waldes"});
        } else {
            m.add(new String[]{"Endlos", "Werde stärker: Stufe " + st.level, st.gamesWon + " Prüfungen · " + st.racesWon + " Rennen · " + st.brewed + " Tränke"});
        }
        for (Map.Entry<String, int[]> e : st.quests.entrySet()) {
            Item it = Item.values()[e.getValue()[0]];
            String who = npcName(e.getKey());
            boolean done = st.items(it) >= e.getValue()[1];
            m.add(new String[]{"Auftrag", who + ": " + Math.min(st.items(it), e.getValue()[1]) + "/" + e.getValue()[1] + " " + it.title,
                done ? "Fertig – zurück zu " + who.split(" ")[0] : "Zu finden: " + it.where});
        }
        if (st.quests.size() < 2) m.add(new String[]{"Tipp", "Frag Borga, Ysolde, Grumhild, Oberion oder Aurin", "nach Sammelaufträgen (Glimmertaler)"});
        m.add(new String[]{"Optional", "Rennen gegen Brumm (Stufe " + st.stage("brumm") + ")", "Akademiehof, rechts vom Weg"});
        m.add(new String[]{"Optional", "Rätsel bei Steinwächter Grol (Stufe " + st.stage("grol") + ")", "Weg zum Schattenhain"});
        Outfit cheapest = null;
        for (Outfit o : Outfit.values()) if (!st.owned.contains(o) && (cheapest == null || o.price < cheapest.price)) cheapest = o;
        if (cheapest != null) m.add(new String[]{"Laden", "Nächstes Gewand: " + cheapest.title, cheapest.price + " Taler bei Pimpernell (Hof)"});
        if (!st.flags.contains("palace:visited")) m.add(new String[]{"Palast", "Betritt den Zauberpalast Sternenhall", "Nördlich hinter dem Akademiebaum"});
        else {
            int open = 0, taken = 0;
            for (Palace.Secret sc : world.palace.secrets) {
                if (Palace.isOpen(st, sc.id)) open++;
                if (st.flags.contains("secretTreasure:" + sc.id)) taken++;
            }
            if (taken < 4) {
                int hints = 0;
                for (Palace.Secret sc : world.palace.secrets) if (st.flags.contains("hint:" + sc.id) && !Palace.isOpen(st, sc.id)) hints++;
                m.add(new String[]{"Palast", "Geheimgänge in Sternenhall: " + open + "/4 · Schätze " + taken + "/4",
                    hints > 0 ? hints + " Geheimnis(se) von Freunden bekannt – zaubern!" : "Freunde (3 ♥) verraten, wo man zaubern muss"});
            }
        }
        m.add(new String[]{"Sammeln", "Raritäten: " + st.treasureKinds() + "/" + Treasure.values().length, "Taste I zeigt deine Tasche und Gerüchte"});
        if (st.homeLevel + 1 < Home.LEVELS.size()) {
            Home.Level nx = Home.LEVELS.get(st.homeLevel + 1);
            StringBuilder need = new StringBuilder();
            for (Map.Entry<String, Integer> e : nx.needs().entrySet())
                need.append(Math.min(Home.have(st, e.getKey()), e.getValue())).append("/").append(e.getValue()).append(" ").append(Home.title(e.getKey())).append(" · ");
            need.append(nx.coins()).append(" Taler");
            m.add(new String[]{"Zuhause", "Ausbau: " + nx.title(), need.toString()});
        }
        m.add(new String[]{"Speichern", "Nächstes Café: " + nearestCafeText(), "Im Café ins Gästebuch eintragen"});
        if (!st.flags.contains("cafe")) m.add(new String[]{"Ruhe", "Besuche das Café Wolkenrand", "Auf der Klippe im Süden"});
        if (!st.flags.contains("visit:knorz")) m.add(new String[]{"Besuch", "Oma Knorz wohnt in einer Hügelhöhle", "beim Steinbruch – sie hat immer Tee"});
        return m;
    }

    /** Entfernung und Richtung zum nächsten bekannten Café. */
    String nearestCafeText() {
        List<float[]> cafes = new ArrayList<>();
        cafes.add(new float[]{world.cafeDoor.x, world.cafeDoor.z});
        for (House h : world.houses) if (h.cafe) cafes.add(new float[]{h.x, h.z});
        for (House h : world.chunks.housesNear(pos.x, pos.z)) if (h.cafe) cafes.add(new float[]{h.x, h.z});
        float best = Float.MAX_VALUE, bx = 0, bz = 0;
        for (float[] c : cafes) {
            float d = FastMath.sqrt(FastMath.sqr(c[0] - pos.x) + FastMath.sqr(c[1] - pos.z));
            if (d < best) { best = d; bx = c[0]; bz = c[1]; }
        }
        String[] dirs = {"Süden", "Südosten", "Osten", "Nordosten", "Norden", "Nordwesten", "Westen", "Südwesten"};
        float ang = FastMath.atan2(bx - pos.x, bz - pos.z);
        int idx = Math.round(ang / (FastMath.PI / 4));
        idx = ((idx % 8) + 8) % 8;
        return (int) best + " m Richtung " + dirs[idx];
    }

    private String npcName(String id) {
        for (Npc n : npcs) if (n.id.equals(id)) return n.name;
        return id;
    }

    private void drawTracker(Graphics2D g) {
        List<String[]> m = missions();
        double x = SW - 330, y = 62, w = 314;
        if (p2 != null) {
            // geteilter Bildschirm: nur die wichtigsten Missionen, unter der Leiste von Spieler 2
            if (m.size() > 3) m = new ArrayList<>(m.subList(0, 3));
            y = 122;
        }
        double h = 34 + m.size() * 50;
        g.setColor(new Color(8, 10, 18, 150));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 16, 16));
        g.setFont(Art.UI);
        Art.shadowText(g, "Missionen", x + 14, y + 24, Art.GOLD);
        double yy = y + 48;
        for (String[] e : m) {
            Color tag = switch (e[0]) {
                case "Hauptquest" -> Art.GOLD;
                case "Auftrag" -> new Color(140, 220, 150);
                case "Endlos" -> Art.MAGIC;
                default -> new Color(170, 170, 195);
            };
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            Art.shadowText(g, e[1], x + 14, yy, tag);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(190, 190, 210));
            g.drawString(e[2], (float) x + 24, (float) yy + 17);
            yy += 50;
        }
    }

    private void drawRace(Graphics2D g) {
        g.setFont(Art.BIG_UI);
        if (race.countdown > 0) {
            String s = race.countdown > 0.5f ? String.valueOf((int) Math.ceil(race.countdown - 0.5f)) : "LOS!";
            Art.shadowCentered(g, s, SW / 2.0, SH / 2.0 - 60, Art.GOLD);
        }
        g.setFont(Art.UI);
        Art.panel(g, SW / 2.0 - 200, 58, 400, 34);
        Art.shadowCentered(g, "Rennen · Ring " + Math.min(race.cps.size(), race.next + 1) + "/" + race.cps.size() + " · " + String.format("%.1f s", race.time) + " · [Esc] aufgeben",
            SW / 2.0, 81, Art.PARCH);
        double[] s = screen(race.cps.get(Math.min(race.next, race.cps.size() - 1)).add(0, 2.6f, 0));
        if (s != null) Art.shard(g, s[0], s[1] - 40, 10, new Color(255, 220, 120), true, game.time());
    }

    private void drawChoice(Graphics2D g) {
        double h = choice.labels.size() * 46 + 70;
        Art.panel(g, 340, SH / 2.0 - h / 2, 600, h);
        g.setFont(Art.H2);
        Art.shadowCentered(g, choice.title, SW / 2.0, SH / 2.0 - h / 2 + 40, Art.GOLD);
        for (int i = 0; i < choice.labels.size(); i++) {
            double y = choiceY(i);
            boolean hover = in.mouseX > 360 && in.mouseX < 920 && in.mouseY > y - 26 && in.mouseY < y + 10;
            if (hover) {
                g.setColor(new Color(60, 50, 90, 200));
                g.fill(new RoundRectangle2D.Double(360, y - 26, 560, 36, 12, 12));
            }
            g.setFont(Art.BODY);
            Art.shadowText(g, (i + 1) + "   " + choice.labels.get(i), 380, y, hover ? Color.WHITE : Art.PARCH);
        }
    }

    private void drawShop(Graphics2D g) {
        Outfit[] all = Outfit.values();
        Art.panel(g, 60, 40, 600, 640);
        g.setFont(Art.H2);
        Art.shadowText(g, "Pimpernells Fadenwerk", 84, 86, Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(new Color(200, 200, 220));
        g.drawString("Du hast " + st.coins + " Glimmertaler.   ↑↓ wählen · Enter kaufen/anziehen · Esc", 84, 112);
        for (int i = 0; i < all.length; i++) {
            Outfit o = all[i];
            double y = 150 + i * 28;
            if (i == shopSel) {
                g.setColor(new Color(70, 55, 100, 210));
                g.fill(new RoundRectangle2D.Double(76, y - 19, 568, 26, 10, 10));
            }
            Color c = o.color == null ? new Color(140, 120, 100) : o.color;
            g.setColor(c);
            g.fill(new Ellipse2D.Double(88, y - 14, 14, 14));
            g.setFont(Art.UI);
            Art.shadowText(g, o.title, 112, y, st.outfit == o ? Art.GOOD : Art.PARCH);
            String right = st.outfit == o ? "getragen" : st.owned.contains(o) ? "im Schrank" : o.price + " Taler";
            g.setColor(!st.owned.contains(o) && st.coins < o.price ? new Color(200, 120, 120) : new Color(220, 220, 230));
            g.drawString(right, 540, (float) y);
        }
        Outfit o = all[shopSel];
        Art.panel(g, 680, 480, 560, 200);
        g.setFont(Art.H2);
        Art.shadowText(g, o.title, 704, 520, Art.GOLD);
        g.setFont(Art.BODY);
        g.setColor(Art.PARCH);
        Art.drawWrapped(g, o.desc, 704, 556, 510, 26);
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 190, 210));
        g.drawString(st.owned.contains(o) ? "Enter: anziehen" : (st.coins >= o.price ? "Enter: kaufen & anziehen" : "Noch " + (o.price - st.coins) + " Taler sparen"), 704, 664);
    }

    private void drawBrew(Graphics2D g) {
        Potion[] all = Potion.values();
        Art.panel(g, 220, 90, 840, 520);
        g.setFont(Art.H2);
        Art.shadowCentered(g, "Am Braukessel", SW / 2.0, 140, Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(new Color(200, 200, 220));
        Art.centered(g, "↑↓ wählen · Enter brauen (Gedächtnis-Prüfung) · Esc", SW / 2.0, 166);
        for (int i = 0; i < all.length; i++) {
            Potion p = all[i];
            double y = 220 + i * 90;
            if (i == shopSel) {
                g.setColor(new Color(60, 50, 90, 200));
                g.fill(new RoundRectangle2D.Double(240, y - 30, 800, 80, 14, 14));
            }
            Art.shard(g, 270, y, 14, p.color, p.canBrew(st), game.time());
            g.setFont(Art.UI);
            Art.shadowText(g, p.title + "   (im Gepäck: " + st.potions.getOrDefault(p, 0) + ")", 300, y - 4, p.canBrew(st) ? Color.WHITE : new Color(160, 160, 175));
            g.setFont(Art.SMALL);
            g.setColor(new Color(200, 200, 215));
            g.drawString(p.recipe() + "      " + p.desc, 300, (float) y + 18);
            StringBuilder have = new StringBuilder("Vorrat: ");
            for (Item it : p.needs.keySet()) have.append(it.title).append(" ").append(st.items(it)).append("   ");
            g.setColor(new Color(160, 200, 160));
            g.drawString(have.toString(), 300, (float) y + 36);
        }
    }

    private void drawMenu(Graphics2D g) {
        g.setColor(new Color(4, 6, 12, 200));
        g.fillRect(0, 0, SW, SH);
        for (int i = 0; i < TABS.length; i++) {
            double x = 40 + i * 152;
            boolean sel = i == menuTab;
            g.setColor(sel ? new Color(70, 56, 104, 230) : new Color(20, 22, 36, 220));
            g.fill(new RoundRectangle2D.Double(x, 40, 144, 44, 16, 16));
            g.setColor(sel ? Art.GOLD : Art.alpha(Art.GOLD, 90));
            g.setStroke(new BasicStroke(sel ? 2.5f : 1.2f));
            g.draw(new RoundRectangle2D.Double(x, 40, 144, 44, 16, 16));
            g.setFont(Art.UI);
            Art.shadowCentered(g, TABS[i], x + 72, 68, sel ? Color.WHITE : Art.PARCH);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(170, 170, 190));
        Art.centered(g, "←/→ oder Klick: Reiter wechseln · Esc/Tab: schließen", SW / 2.0, SH - 18);
        switch (menuTab) {
            case 0 -> drawMapTab(g);
            case 1 -> drawBag(g);
            case 2 -> drawFriends(g);
            case 3 -> drawWardrobe(g);
            case 4 -> drawMissionsTab(g);
            case 5 -> drawAbilities(g);
            case 6 -> drawJournal(g);
            default -> drawGameTab(g);
        }
    }

    private void drawMapTab(Graphics2D g) {
        double mx = 330, my = 104, ms = 560;
        if (mapImg == null) {
            g.setFont(Art.H2);
            Art.shadowCentered(g, "Die Karte wird gezeichnet …", SW / 2.0, SH / 2.0, Art.PARCH);
            return;
        }
        g.drawImage(mapImg, (int) mx, (int) my, (int) ms, (int) ms, null);
        g.setColor(Art.alpha(Art.GOLD, 160));
        g.draw(new java.awt.Rectangle((int) mx, (int) my, (int) ms, (int) ms));
        float range = 330;
        String[][] labels = {{"Akademie", "0", "-112"}, {"Herzbaum", "0", "12"}, {"Mondsee", "-124", "-4"}, {"Steinbruch", "120", "-36"},
            {"Moor", "0", "92"}, {"Pilzkreis", "-120", "92"}, {"Dornenschlucht", "128", "84"}, {"Schattenhain", "-124", "-92"},
            {"Lichtwiese", "116", "-104"}, {"Café Wolkenrand", "34", "194"}, {"Meer", "0", "300"}};
        g.setFont(Art.SMALL);
        for (String[] l : labels) {
            double x = mx + (Float.parseFloat(l[1]) + range) / (2 * range) * ms, y = my + (Float.parseFloat(l[2]) + range) / (2 * range) * ms;
            Art.shadowCentered(g, l[0], x, y, Art.PARCH);
        }
        for (Npc n : npcs) {
            boolean open = n.shard != null && !st.has(n.shard);
            Vector3f p = world.npcPos.get(n.id);
            if (p == null || !(open || n.id.equals("brumm") || n.id.equals("grol") || n.id.equals("pimpernell") || n.id.startsWith("kessel"))) continue;
            double x = mx + (p.x + range) / (2 * range) * ms, y = my + (p.z + range) / (2 * range) * ms;
            if (open) Art.shard(g, x, y - 10, 7, n.shard.color, true, game.time());
            else Art.dot(g, x, y, 3, n.id.startsWith("kessel") ? new Color(120, 255, 140) : new Color(255, 220, 140), 255);
        }
        {
            // Eigenes Haus
            double hx = mx + (Home.X + range) / (2 * range) * ms, hy = my + (Home.Z + range) / (2 * range) * ms;
            java.awt.geom.Path2D hp = new java.awt.geom.Path2D.Double();
            hp.moveTo(hx - 7, hy + 6); hp.lineTo(hx - 7, hy - 1); hp.lineTo(hx, hy - 8); hp.lineTo(hx + 7, hy - 1); hp.lineTo(hx + 7, hy + 6); hp.closePath();
            g.setColor(new Color(255, 210, 140));
            g.fill(hp);
            g.setColor(new Color(60, 30, 10));
            g.draw(hp);
            g.setFont(Art.SMALL);
            Art.shadowText(g, "Zuhause", hx + 10, hy + 4, new Color(255, 220, 160));
            double px = mx + (Palace.PX + range) / (2 * range) * ms, py = my + (Palace.PZ + range) / (2 * range) * ms;
            if (px > mx && px < mx + ms && py > my && py < my + ms) {
                g.setColor(new Color(190, 170, 255));
                for (int k = -1; k <= 1; k++) g.fill(new java.awt.geom.Rectangle2D.Double(px + k * 6 - 2, py - 10 - (k == 0 ? 4 : 0), 4, 12 + (k == 0 ? 4 : 0)));
                g.fill(new java.awt.geom.Rectangle2D.Double(px - 8, py, 16, 6));
                Art.shadowText(g, "Zauberpalast", px + 12, py + 4, new Color(210, 195, 255));
            }
        }
        if (st.effect(Potion.SICHT)) {
            for (Chunks.Spot s : world.chunks.spotsNear(pos.x, pos.z, 200)) {
                double x = mx + (s.pos.x + range) / (2 * range) * ms, y = my + (s.pos.z + range) / (2 * range) * ms;
                if (x > mx && x < mx + ms && y > my && y < my + ms) Art.dot(g, x, y, 1.5, (s.item != null ? s.item.color : s.mat != null ? s.mat.color : s.treasure.color()), 255);
            }
        }
        double px = mx + (pos.x + range) / (2 * range) * ms, py = my + (pos.z + range) / (2 * range) * ms;
        boolean inside = px > mx && px < mx + ms && py > my && py < my + ms;
        if (inside) {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(px - 6, py - 6, 12, 12));
            g.setColor(Color.RED);
            g.fill(new Ellipse2D.Double(px - 4, py - 4, 8, 8));
            g.setColor(Color.WHITE);
            g.drawLine((int) px, (int) py, (int) (px + Math.sin(yaw) * 16), (int) (py + Math.cos(yaw) * 16));
        }
        g.setFont(Art.UI);
        float d = (float) Math.hypot(pos.x, pos.z);
        Art.shadowText(g, inside ? "Du bist hier (rot)." : "Du bist " + (int) d + " m vom Herzbaum entfernt – weit draußen in der Wildnis.", 330, 692, Art.PARCH);
        g.setFont(Art.SMALL);
        g.setColor(new Color(180, 180, 200));
        g.drawString("Die Welt endet nie: Hinter der Karte warten Wälder, Berge, Seen und Steinkreise.", 920, 140);
    }

    private void drawWardrobe(Graphics2D g) {
        Art.panel(g, 70, 104, 640, 560);
        g.setFont(Art.H2);
        Art.shadowText(g, "Dein Kleiderschrank – klicke zum Anziehen", 90, 136, Art.GOLD);
        List<Outfit> owned = new ArrayList<>(st.owned);
        for (int i = 0; i < owned.size(); i++) {
            Outfit o = owned.get(i);
            double y = 150 + i * 40 + 30;
            g.setFont(Art.UI);
            Art.shadowText(g, (st.outfit == o ? "✦ " : "   ") + o.title, 96, y - 24 + 20, st.outfit == o ? Art.GOOD : Art.PARCH);
        }
        Outfit o = st.outfit;
        Art.panel(g, 740, 104, 480, 300);
        g.setFont(Art.H2);
        Art.shadowText(g, "Getragen: " + o.title, 764, 144, Art.GOLD);
        g.setFont(Art.BODY);
        g.setColor(Art.PARCH);
        Art.drawWrapped(g, o.desc, 764, 184, 430, 27);
        g.setFont(Art.SMALL);
        g.setColor(new Color(180, 180, 200));
        g.drawString("Weitere Gewänder kauft man bei Mäusewirtin Pimpernell im Akademiehof.", 764, 384);
    }

    private void drawInventory(Graphics2D g) {
        Art.panel(g, 70, 104, 560, 560);
        g.setFont(Art.H2);
        Art.shadowText(g, "Zutaten", 94, 140, Art.GOLD);
        double y = 180;
        for (Item it : Item.values()) {
            Art.dot(g, 104, y - 5, 4, it.color, 255);
            g.setFont(Art.UI);
            Art.shadowText(g, it.title + "   ×" + st.items(it), 124, y, Art.PARCH);
            g.setFont(Art.SMALL);
            g.setColor(new Color(170, 170, 190));
            g.drawString(it.where, 124, (float) y + 18);
            y += 52;
        }
        g.setFont(Art.H2);
        Art.shadowText(g, "Baustoffe fürs Haus", 94, 524, Art.GOLD);
        Baustoff[] bs = Baustoff.values();
        for (int i = 0; i < bs.length; i++) {
            double bx = 104 + (i % 2) * 260, by = 560 + (i / 2) * 32;
            Art.dot(g, bx, by - 5, 4, bs[i].color, 255);
            g.setFont(Art.UI);
            Art.shadowText(g, bs[i].title + "   ×" + st.mats(bs[i]), bx + 18, by, bs[i].raw ? Art.PARCH : new Color(200, 230, 255));
        }
        Art.panel(g, 660, 104, 560, 560);
        g.setFont(Art.H2);
        Art.shadowText(g, "Tränke (Taste 1–4 trinkt)", 684, 140, Art.GOLD);
        y = 180;
        for (Potion p : Potion.values()) {
            Art.shard(g, 696, y - 6, 9, p.color, true, game.time());
            g.setFont(Art.UI);
            Art.shadowText(g, "[" + (p.ordinal() + 1) + "] " + p.title + "   ×" + st.potions.getOrDefault(p, 0), 716, y, Art.PARCH);
            g.setFont(Art.SMALL);
            g.setColor(new Color(170, 170, 190));
            g.drawString(p.desc, 716, (float) y + 18);
            g.drawString("Rezept: " + p.recipe(), 716, (float) y + 35);
            y += 70;
        }
        g.setFont(Art.UI);
        Art.shadowText(g, st.coins + " Glimmertaler", 684, 640, Art.GOLD);
    }

    // ---------------------------------------------------------------- Tasche

    private static final java.awt.geom.Path2D GEM = gemShape();

    private static java.awt.geom.Path2D gemShape() {
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        p.moveTo(0, -1); p.lineTo(0.8, -0.35); p.lineTo(0.55, 0.75); p.lineTo(0, 1.05); p.lineTo(-0.55, 0.75); p.lineTo(-0.8, -0.35); p.closePath();
        return p;
    }

    private void gem(Graphics2D g, double x, double y, double r, Color c, boolean found) {
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
        at.translate(x, y);
        at.scale(r, r);
        Shape sh = at.createTransformedShape(GEM);
        if (found) {
            Art.glow(g, x, y, r * 2.4, c, 90);
            g.setPaint(new GradientPaint((float) (x - r), (float) (y - r), c.brighter(), (float) (x + r), (float) (y + r), c.darker()));
            g.fill(sh);
            g.setColor(new Color(255, 255, 255, 150));
            g.fill(new java.awt.geom.Ellipse2D.Double(x - r * 0.35, y - r * 0.55, r * 0.35, r * 0.25));
        } else {
            g.setColor(new Color(30, 28, 44, 230));
            g.fill(sh);
        }
        g.setColor(found ? Color.WHITE : Art.alpha(c, 140));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(sh);
    }

    private void drawBag(Graphics2D g) {
        // Links: alles Gesammelte
        Art.panel(g, 30, 100, 450, 570);
        g.setFont(Art.H2);
        Art.shadowText(g, "Deine Tasche", 54, 138, Art.GOLD);
        g.setFont(Art.UI);
        Art.shadowText(g, st.coins + " Glimmertaler", 300, 138, new Color(255, 215, 120));
        double y = 168;
        g.setFont(Art.SMALL);
        Art.shadowText(g, "ZUTATEN", 54, y, new Color(180, 175, 200));
        Item[] its = Item.values();
        for (int i = 0; i < its.length; i++) {
            double x = 58 + (i % 2) * 210, yy = y + 26 + (i / 2) * 28;
            Art.dot(g, x + 6, yy - 5, 6, its[i].color, st.items(its[i]) > 0 ? 255 : 80);
            g.setFont(Art.UI);
            Art.shadowText(g, its[i].title + "  ×" + st.items(its[i]), x + 18, yy, st.items(its[i]) > 0 ? Art.PARCH : new Color(120, 115, 135));
        }
        y += 26 + 3 * 28 + 14;
        g.setFont(Art.SMALL);
        Art.shadowText(g, "BAUSTOFFE FÜRS HAUS", 54, y, new Color(180, 175, 200));
        Baustoff[] bs = Baustoff.values();
        for (int i = 0; i < bs.length; i++) {
            double x = 58 + (i % 2) * 210, yy = y + 26 + (i / 2) * 28;
            Art.dot(g, x + 6, yy - 5, 6, bs[i].color, st.mats(bs[i]) > 0 ? 255 : 80);
            g.setFont(Art.UI);
            Art.shadowText(g, bs[i].title + "  ×" + st.mats(bs[i]), x + 18, yy, st.mats(bs[i]) > 0 ? Art.PARCH : new Color(120, 115, 135));
        }
        y += 26 + 3 * 28 + 14;
        g.setFont(Art.SMALL);
        Art.shadowText(g, "TRÄNKE (Taste 1–4 trinkt)", 54, y, new Color(180, 175, 200));
        Potion[] ps = Potion.values();
        for (int i = 0; i < ps.length; i++) {
            double x = 58 + (i % 2) * 210, yy = y + 26 + (i / 2) * 28;
            Art.shard(g, x + 6, yy - 6, 7, ps[i].color, true, game.time());
            g.setFont(Art.UI);
            Art.shadowText(g, "[" + (i + 1) + "] " + ps[i].title + " ×" + st.potions.getOrDefault(ps[i], 0), x + 18, yy, Art.PARCH);
        }
        // Rechts: Raritäten-Sammlung
        Art.panel(g, 500, 100, 750, 570);
        g.setFont(Art.H2);
        Treasure[] all = Treasure.values();
        Art.shadowText(g, "Raritäten-Sammlung  " + st.treasureKinds() + " / " + all.length, 524, 138, Art.GOLD);
        g.setFont(Art.SMALL);
        for (int r = 0; r < 3; r++) {
            Treasure probe = r == 0 ? Treasure.GOLDENE_EICHEL : r == 1 ? Treasure.MONDPERLE : Treasure.PHOENIXFEDER;
            Art.dot(g, 975 + r * 90, 132, 5, probe.color(), 255);
            Art.shadowText(g, Treasure.RARITY[r], 984 + r * 90, 137, probe.color());
        }
        Treasure hover = null;
        for (int i = 0; i < all.length; i++) {
            Treasure t = all[i];
            double cx = 524 + (i % 5) * 144, cy = 158 + (i / 5) * 112;
            boolean found = st.treasure(t) > 0;
            RoundRectangle2D card = new RoundRectangle2D.Double(cx, cy, 134, 102, 14, 14);
            boolean hov = card.contains(in.mouseX, in.mouseY);
            if (hov) hover = t;
            g.setColor(hov ? new Color(60, 48, 92, 235) : found ? new Color(34, 28, 54, 220) : new Color(16, 14, 26, 210));
            g.fill(card);
            g.setColor(Art.alpha(t.color(), found ? 220 : 90));
            g.setStroke(new BasicStroke(found ? 2f : 1.2f));
            g.draw(card);
            gem(g, cx + 67, cy + 38, 20, t.glow(), found);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            String name = found ? t.title : "???";
            Art.shadowCentered(g, name.length() > 22 ? name.substring(0, 21) + "…" : name, cx + 67, cy + 80, found ? Color.WHITE : new Color(130, 125, 150));
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            Art.shadowCentered(g, found ? "×" + st.treasure(t) + " · " + Treasure.RARITY[t.rarity] : Treasure.RARITY[t.rarity], cx + 67, cy + 95, Art.alpha(t.color(), 220));
        }
        g.setFont(Art.SMALL);
        if (hover != null) {
            boolean found = st.treasure(hover) > 0;
            Art.shadowText(g, found ? hover.title + ": " + hover.text : "Unentdeckt. Gerücht: " + hover.where, 524, 630, found ? Art.PARCH : new Color(200, 190, 230));
            if (found) Art.shadowText(g, "Fundort: " + hover.where, 524, 650, new Color(180, 175, 200));
        } else {
            Art.shadowText(g, "Maus über eine Rarität halten. Manche zeigen sich nur nachts, bei Sonnenaufgang oder nach Regen.", 524, 640, new Color(180, 175, 200));
        }
    }

    // ---------------------------------------------------------------- Freunde

    private void drawFriends(Graphics2D g) {
        Art.panel(g, 30, 100, 1220, 570);
        g.setFont(Art.H2);
        Art.shadowText(g, "Freundschaften", 54, 138, Art.GOLD);
        g.setFont(Art.SMALL);
        Art.shadowText(g, "Täglich plaudern, Lieblingsgeschenke, Aufträge und gemeinsame Spiele bringen Herzen.", 310, 136, new Color(190, 185, 210));
        List<Friends.Friend> all = Friends.ALL;
        for (int i = 0; i < all.size(); i++) {
            Friends.Friend f = all.get(i);
            double x = 54 + (i % 2) * 600, y = 168 + (i / 2) * 70;
            int h = Friends.hearts(st, f.id()), pts = Friends.points(st, f.id());
            g.setColor(new Color(24, 20, 38, 200));
            g.fill(new RoundRectangle2D.Double(x, y - 6, 580, 62, 14, 14));
            g.setFont(Art.UI);
            Art.shadowText(g, f.name(), x + 14, y + 18, h >= 5 ? new Color(255, 200, 120) : Art.PARCH);
            for (int k = 0; k < 5; k++) Art.shadowText(g, k < h ? "♥" : "♡", x + 300 + k * 22, y + 18, k < h ? new Color(255, 110, 160) : new Color(120, 100, 130));
            Art.bar(g, x + 420, y + 6, 140, 8, h >= 5 ? 1 : (pts % Friends.PER_HEART) / (double) Friends.PER_HEART, new Color(255, 120, 170));
            g.setFont(Art.SMALL);
            String likes;
            if (h >= 1) {
                StringBuilder sb = new StringBuilder("mag: ");
                for (Item it : f.likes()) sb.append(it.title).append(", ");
                for (Treasure t : f.loves()) sb.append(t.title).append(", ");
                likes = sb.substring(0, sb.length() - 2);
            } else likes = "mag: ??? (ab 1 Herz)";
            String next = switch (h) {
                case 0, 1 -> "bei 2 ♥: ein Geschenk";
                case 2 -> "bei 3 ♥: ein Geheimnis";
                case 3 -> "bei 4 ♥: Taler & Trank";
                case 4 -> "bei 5 ♥: Legendäres";
                default -> "beste Freundschaft ✦";
            };
            Art.shadowText(g, likes.length() > 52 ? likes.substring(0, 51) + "…" : likes, x + 14, y + 40, new Color(190, 185, 210));
            int nw = g.getFontMetrics().stringWidth(next);
            Art.shadowText(g, next, x + 566 - nw, y + 40, new Color(255, 200, 220));
        }
    }

    private void drawMissionsTab(Graphics2D g) {
        Art.panel(g, 240, 104, 800, 560);
        g.setFont(Art.H2);
        Art.shadowText(g, "Missionen", 270, 144, Art.GOLD);
        double y = 190;
        for (String[] m : missions()) {
            g.setFont(Art.SMALL);
            g.setColor(new Color(170, 170, 195));
            g.drawString(m[0].toUpperCase(), 270, (float) y);
            g.setFont(Art.UI);
            Art.shadowText(g, m[1], 380, y, Art.PARCH);
            g.setFont(Art.SMALL);
            g.setColor(new Color(190, 190, 210));
            g.drawString(m[2], 380, (float) y + 18);
            y += 48;
        }
    }

    private void drawAbilities(Graphics2D g) {
        Art.panel(g, 240, 104, 800, 560);
        g.setFont(Art.H2);
        Art.shadowText(g, "Fähigkeiten – Stufe " + st.level + " (" + st.xp + "/" + st.xpNext() + " EP)", 270, 144, Art.GOLD);
        double y = 190;
        for (Ability a : Ability.values()) {
            boolean ok = st.has(a);
            g.setFont(Art.UI);
            Art.shadowText(g, (ok ? "✦ " : "Stufe " + a.level + ":  ") + a.title, 270, y, ok ? Art.GOOD : new Color(150, 150, 170));
            g.setFont(Art.SMALL);
            g.setColor(ok ? new Color(210, 210, 225) : new Color(130, 130, 150));
            g.drawString(a.desc, 290, (float) y + 19);
            y += 54;
        }
    }

    private void drawJournal(Graphics2D g) {
        Art.panel(g, 140, 104, 1000, 560);
        g.setFont(Art.H2);
        Art.shadowText(g, "Tagebuch – Erkenntnisse", 170, 144, Art.GOLD);
        g.setFont(Art.BODY);
        g.setColor(Art.PARCH);
        double y = 186;
        if (st.notes.isEmpty()) Art.drawWrapped(g, "Noch nichts. Sprich mit den Hüterinnen und Hütern der Splitter.", 170, y, 940, 26);
        for (String n : st.notes) y = Art.drawWrapped(g, "• " + n, 170, y, 940, 26) + 8;
        g.setFont(Art.SMALL);
        g.setColor(new Color(170, 170, 190));
        g.drawString("Spielzeit " + (int) (st.playTime / 60) + " min · vertriebene Schattenwesen " + st.banished + " · gesammelte Zutaten " + st.collected
            + " · gelöste Rätsel " + st.riddlesSolved, 170, 640);
    }

    private void drawGameTab(Graphics2D g) {
        String[] b = {"Speichern nur im Café", "[Q] Zum Titelbild"};
        for (int i = 0; i < b.length; i++) {
            double y = 220 + i * 80;
            boolean hover = hitButton(i);
            g.setColor(hover ? new Color(70, 56, 104, 230) : new Color(20, 22, 36, 220));
            g.fill(new RoundRectangle2D.Double(440, y, 400, 56, 20, 20));
            g.setColor(Art.GOLD);
            g.draw(new RoundRectangle2D.Double(440, y, 400, 56, 20, 20));
            g.setFont(Art.H2);
            Art.shadowCentered(g, b[i], 640, y + 37, Color.WHITE);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(180, 180, 200));
        Art.centered(g, "Gespeichert wird automatisch nach jedem gewonnenen Spiel – und von Hand in jedem Café (Gästebuch).", 640, 420);
        Art.centered(g, "Zum Titelbild: Fortschritt seit dem letzten Speichern geht verloren. " + nearestCafeText(), 640, 466);
        Art.centered(g, "Spielstand: " + GameState.saveFile(), 640, 444);
    }
}
