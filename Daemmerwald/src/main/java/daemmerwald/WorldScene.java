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

        for (int i = 0; i < 4; i++) if (in.wasPressed(KeyEvent.VK_1 + i)) drink(Potion.values()[i]);

        near = null;
        float best = 1e9f;
        for (Npc n : npcs) {
            Vector3f p = world.npcPos.get(n.id);
            if (p == null) continue;
            float r = n.id.equals("heart") ? 10 : n.id.equals("cafe") ? 4.5f : n.id.startsWith("kessel") ? 3.2f : 3.8f;
            float d = p.distance(pos);
            if (d < r && d < best) { best = d; near = n; }
        }
        nearHouse = null;
        if (near == null) {
            List<House> hs = new ArrayList<>(world.houses);
            hs.addAll(world.chunks.housesNear(pos.x, pos.z));
            for (House h : hs) if (h.door().distance(pos) < 3.2f) nearHouse = h;
        }
        if (near != null && in.wasPressed(KeyEvent.VK_E, KeyEvent.VK_ENTER) && race == null) interact(near);
        else if (nearHouse != null && in.wasPressed(KeyEvent.VK_E, KeyEvent.VK_ENTER) && race == null) {
            in.releaseAll();
            game.setScene(new HouseScene(game, this, nearHouse));
        }

        pickups();
        updateCreatures(dt);
        world.updateMarkers(st, npcs);
        autosaveT += dt;
        if (autosaveT > 180) { autosaveT = 0; saveNow(false); }
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
        if (in.isDown(KeyEvent.VK_W, KeyEvent.VK_UP)) fz += 1;
        if (in.isDown(KeyEvent.VK_S, KeyEvent.VK_DOWN)) fz -= 1;
        if (in.isDown(KeyEvent.VK_A, KeyEvent.VK_LEFT)) fx -= 1;
        if (in.isDown(KeyEvent.VK_D, KeyEvent.VK_RIGHT)) fx += 1;
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

    private void releaseSpell() {
        float reach = st.has(Ability.LANZE) ? 9 : 5;
        Vector3f fwd = new Vector3f(FastMath.sin(yaw), 0, FastMath.cos(yaw));
        Vector3f target = pos.add(fwd.mult(reach)).addLocal(0, 1.2f, 0);
        Vector3f hand = pos.add(fwd.mult(0.7f)).addLocal(0, fig.height * 0.72f, 0);
        world.burst(hand, new ColorRGBA(1f, 0.95f, 0.8f, 1), 20);
        bolts.add(new Bolt(hand, target));
        lightBoost = 3;
    }

    private void spellImpact(Vector3f target) {
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

    private void pickups() {
        float r = st.gadget(Outfit.Gadget.MAGNET) ? 6 : 1.7f;
        for (Chunks.Spot s : world.chunks.spotsNear(pos.x, pos.z, r + 1)) {
            float d = FastMath.sqrt((s.pos.x - pos.x) * (s.pos.x - pos.x) + (s.pos.z - pos.z) * (s.pos.z - pos.z));
            if (d > r || Math.abs(s.pos.y - pos.y) > 4) continue;
            s.geo.removeFromParent();
            st.picked.add(s.key);
            world.chunks.picked.add(s.key);
            int n = st.gadget(Outfit.Gadget.LUCK) && rnd.nextFloat() < 0.35f ? 2 : 1;
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
        float gh = world.height(desired.x, desired.z) + 0.6f;
        if (desired.y < gh) desired.y = gh;
        if (!camInit) { camPos.set(desired); camInit = true; }
        camPos.interpolateLocal(desired, Math.min(1, tpf * 8));
        cam.setLocation(camPos);
        cam.lookAt(target, Vector3f.UNIT_Y);
    }

    /** Wird nach dem Welt-Update aufgerufen: Figuren, Kreaturen, Begleiter. */
    public void update3D(float dt) {
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

        void build() {
            if (type == WOLF) {
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
            if (type == FOX) y += Math.abs(FastMath.sin(t * 7)) * 0.08f * Math.min(1, vel.length());
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

    private void updateCreatures(float dt) {
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

    private void interact(Npc n) {
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
                    .add("Ein andermal", () -> { }));
                return;
            }
            case "rektor" -> { sayNpc(n, st.count() >= 3 ? n.after : n.before, null); return; }
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
                        sayNpc(n, l, null);
                    });
                } else {
                    c.add("(Auftrag läuft: " + st.items(it) + "/" + q[1] + " " + it.title + ")", () -> { });
                }
            }
        }
        if (c.labels.isEmpty()) return;
        c.add("Auf Wiedersehen", () -> { });
        choice = c;
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
        if (announce) floatText("Spiel gespeichert", pos.add(0, fig.height + 1, 0), Art.GOOD);
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

    private static final String[] TABS = {"Karte", "Garderobe", "Inventar", "Missionen", "Fähigkeiten", "Tagebuch", "Spiel"};

    private void updateMenu() {
        if (in.wasPressed(KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB) || (menuTab == 0 && in.wasPressed(KeyEvent.VK_M))) { menuTab = -1; return; }
        if (in.wasPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) menuTab = (menuTab + 1) % TABS.length;
        if (in.wasPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A)) menuTab = (menuTab + TABS.length - 1) % TABS.length;
        if (in.mouseClicked) {
            for (int i = 0; i < TABS.length; i++) {
                double x = 70 + i * 162;
                if (in.mouseX > x && in.mouseX < x + 152 && in.mouseY > 40 && in.mouseY < 84) menuTab = i;
            }
        }
        if (menuTab == 1) {
            List<Outfit> owned = new ArrayList<>(st.owned);
            for (int i = 0; i < owned.size(); i++) {
                double y = 150 + i * 40;
                if (in.mouseClicked && in.mouseX > 90 && in.mouseX < 700 && in.mouseY > y - 26 && in.mouseY < y + 10) {
                    st.outfit = owned.get(i);
                    rebuildFigure();
                }
            }
        }
        if (menuTab == 2) for (int i = 0; i < 4; i++) if (in.wasPressed(KeyEvent.VK_1 + i)) drink(Potion.values()[i]);
        if (menuTab == 6) {
            if (in.wasPressed(KeyEvent.VK_S) || (in.mouseClicked && hitButton(0))) { saveNow(true); menuTab = -1; }
            else if (in.wasPressed(KeyEvent.VK_Q) || (in.mouseClicked && hitButton(1))) {
                saveNow(false);
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
        Camera cam = game.app.getCamera();
        Vector3f s = cam.getScreenCoordinates(p);
        if (s.z < 0 || s.z > 1) return null;
        Vector3f toP = p.subtract(cam.getLocation());
        if (toP.dot(cam.getDirection()) < 0) return null;
        return new double[]{s.x * SW / cam.getWidth(), SH - s.y * SH / cam.getHeight()};
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
            String label = "[E] " + (nearHouse.evil ? "Anklopfen bei " : "Besuch bei ") + nearHouse.owner;
            g.setFont(Art.UI);
            int w = g.getFontMetrics().stringWidth(label) + 30;
            g.setColor(nearHouse.evil ? new Color(40, 10, 50, 210) : new Color(40, 26, 14, 210));
            g.fill(new RoundRectangle2D.Double(s[0] - w / 2.0, s[1] - 20, w, 28, 14, 14));
            Art.shadowCentered(g, label, s[0], s[1], nearHouse.evil ? new Color(220, 150, 255) : Art.GOLD);
        }
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
        Art.panel(g, SW / 2.0 - w / 2.0, 14, w, 36);
        Art.shadowCentered(g, clock, SW / 2.0, 38, Art.PARCH);

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
        g.drawString("WASD laufen · Maus schauen · Klick/Q zaubern · Leertaste " + (st.gadget(Outfit.Gadget.FLIGHT) ? "fliegen" : st.hero.isTroll() ? "springen" : "flattern")
            + (st.has(Ability.SPRINT) ? " · Umschalt rennen" : "") + " · E sprechen · Tab Menü", 20, SH - 18);
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
        if (!st.flags.contains("cafe")) m.add(new String[]{"Ruhe", "Besuche das Café Wolkenrand", "Auf der Klippe im Süden"});
        if (!st.flags.contains("visit:knorz")) m.add(new String[]{"Besuch", "Oma Knorz wohnt in einer Hügelhöhle", "beim Steinbruch – sie hat immer Tee"});
        return m;
    }

    private String npcName(String id) {
        for (Npc n : npcs) if (n.id.equals(id)) return n.name;
        return id;
    }

    private void drawTracker(Graphics2D g) {
        List<String[]> m = missions();
        double x = SW - 330, y = 62, w = 314;
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
            double x = 70 + i * 162;
            boolean sel = i == menuTab;
            g.setColor(sel ? new Color(70, 56, 104, 230) : new Color(20, 22, 36, 220));
            g.fill(new RoundRectangle2D.Double(x, 40, 152, 44, 16, 16));
            g.setColor(sel ? Art.GOLD : Art.alpha(Art.GOLD, 90));
            g.setStroke(new BasicStroke(sel ? 2.5f : 1.2f));
            g.draw(new RoundRectangle2D.Double(x, 40, 152, 44, 16, 16));
            g.setFont(Art.UI);
            Art.shadowCentered(g, TABS[i], x + 76, 68, sel ? Color.WHITE : Art.PARCH);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(170, 170, 190));
        Art.centered(g, "←/→ oder Klick: Reiter wechseln · Esc/Tab: schließen", SW / 2.0, SH - 18);
        switch (menuTab) {
            case 0 -> drawMapTab(g);
            case 1 -> drawWardrobe(g);
            case 2 -> drawInventory(g);
            case 3 -> drawMissionsTab(g);
            case 4 -> drawAbilities(g);
            case 5 -> drawJournal(g);
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
        if (st.effect(Potion.SICHT)) {
            for (Chunks.Spot s : world.chunks.spotsNear(pos.x, pos.z, 200)) {
                double x = mx + (s.pos.x + range) / (2 * range) * ms, y = my + (s.pos.z + range) / (2 * range) * ms;
                if (x > mx && x < mx + ms && y > my && y < my + ms) Art.dot(g, x, y, 1.5, s.item.color, 255);
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
        String[] b = {"[S] Spiel speichern", "[Q] Speichern & zum Titelbild"};
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
        Art.centered(g, "Das Spiel speichert auch automatisch alle drei Minuten und wenn du im Café Platz nimmst.", 640, 420);
        Art.centered(g, "Spielstand: " + GameState.saveFile(), 640, 444);
    }
}
