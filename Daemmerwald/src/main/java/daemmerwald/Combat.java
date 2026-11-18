package daemmerwald;

import com.jme3.anim.AnimComposer;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Kämpfe der Geschichte: Gegner mit Lebenspunkten und eigenen Schwächen, Geschosse, die man mit
 * Protego zurückwerfen kann, Felsen für Levitaris, Schockwellen zum Überspringen – und die Herzen des Spielers.
 */
public final class Combat {

    /** Was die Kämpfe von der Weltszene brauchen. */
    public interface Host {
        Vector3f player();
        float playerHeight();
        boolean playerAirborne();
        boolean shield();
        void hurt(float hearts, Vector3f from);
        void text(String s, Vector3f at, Color c);
        void sfx(String name, float pitch);
    }

    /** Zauber-Kombinationen: zwei Zauber kurz nacheinander. */
    public enum Combo {
        STERNENBLITZ("Sternenblitz", Spell.LICHT, Spell.FUNKE, "Lumen, dann Arcanum: ein Blitz aus Sternenlicht – trifft alles in der Nähe und enthüllt Unsichtbares."),
        FEUERKOMET("Feuerkomet", Spell.FLAMME, Spell.SCHWEBE, "Ignis, dann Levitaris: ein brennender Komet – verbrennt Dornenwesen ringsum."),
        SPIEGELSTOSS("Spiegelstoß", Spell.SCHILD, Spell.FUNKE, "Protego, dann Arcanum: der Schild zerspringt in eine Welle, die alle Gegner zurückwirft.");

        public final String title, text;
        public final Spell first, second;

        Combo(String title, Spell first, Spell second, String text) {
            this.title = title;
            this.first = first;
            this.second = second;
            this.text = text;
        }

        public static Combo of(Spell a, Spell b) {
            for (Combo c : values()) if (c.first == a && c.second == b) return c;
            return null;
        }
    }

    private final World3D world;
    private final Host host;
    public final Node node = new Node("combat");
    public final List<Foe> foes = new ArrayList<>();
    private final List<Orb> orbs = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    final Random rnd = new Random();

    public float hearts = 5, maxHearts = 5, regen = 0.35f;
    private float hurtCool, calmT;
    /** Bossgegner für die Lebensleiste oben. */
    public Foe boss;
    public String bossHint;

    public Combat(World3D world, Host host) {
        this.world = world;
        this.host = host;
        world.root.attachChild(node);
    }

    public boolean active() { return !foes.isEmpty(); }

    public void clear() {
        for (Foe f : foes) f.node.removeFromParent();
        foes.clear();
        for (Orb o : orbs) o.geo.removeFromParent();
        orbs.clear();
        for (Prop p : props) p.geo.removeFromParent();
        props.clear();
        for (Wave w : waves) w.geo.removeFromParent();
        waves.clear();
        boss = null;
        bossHint = null;
    }

    public <T extends Foe> T add(T f) {
        foes.add(f);
        node.attachChild(f.node);
        f.sync();
        return f;
    }

    public int alive(Class<?> k) {
        int n = 0;
        for (Foe f : foes) if (!f.dead && (k == null || k.isInstance(f))) n++;
        return n;
    }

    // ================================================================ Takt

    public void update(float dt) {
        hurtCool = Math.max(0, hurtCool - dt);
        boolean fighting = alive(null) > 0;
        calmT = fighting ? 0 : calmT + dt;
        if (calmT > 4 && hearts < maxHearts) hearts = Math.min(maxHearts, hearts + dt * regen);
        for (Iterator<Foe> it = foes.iterator(); it.hasNext(); ) {
            Foe f = it.next();
            f.t += dt;
            f.flash = Math.max(0, f.flash - dt);
            f.stun = Math.max(0, f.stun - dt);
            if (f.dead) {
                f.deadT += dt;
                f.node.setLocalScale(Math.max(0.01f, 1 - f.deadT * 2.5f) * f.scale);
                if (f.deadT > 0.4f) { f.node.removeFromParent(); it.remove(); }
                continue;
            }
            f.update(dt);
            f.sync();
        }
        updateOrbs(dt);
        updateProps(dt);
        updateWaves(dt);
    }

    /** Der Spieler wird getroffen (Protego hält alles ab). */
    boolean strike(float dmg, Vector3f from) {
        if (host.shield()) {
            world.burst(host.player().add(0, host.playerHeight() * 0.5f, 0), new ColorRGBA(0.5f, 1f, 0.85f, 1), 16);
            return false;
        }
        if (hurtCool > 0) return false;
        hurtCool = 0.9f;
        hearts = Math.max(0, hearts - dmg);
        host.hurt(dmg, from);
        return true;
    }

    // ================================================================ Zielen und Treffer

    /** Zielhilfe: der nächste Gegner vor dem Spieler (im Kegel). */
    public Foe aim(Vector3f from, float yaw, float reach) {
        Foe best = null;
        float bestScore = 1e9f;
        for (Foe f : foes) {
            if (f.dead || !f.targetable()) continue;
            Vector3f d = f.center().subtract(from).setY(0);
            float dist = d.length();
            if (dist > reach + 4 || dist < 0.01f) continue;
            float ang = FastMath.abs(FastMath.normalize(FastMath.atan2(d.x, d.z) - yaw, -FastMath.PI, FastMath.PI));
            if (ang > 0.75f) continue;
            float score = dist + ang * 8;
            if (score < bestScore) { bestScore = score; best = f; }
        }
        return best;
    }

    /** Ein Zauber schlägt ein. @return true, wenn ein Gegner oder Felsen betroffen war. */
    public boolean spellHit(Vector3f at, Spell s, Combo combo, float power, Foe homing) {
        boolean any = false;
        // Levitaris auf einen Felsen: anheben und schleudern
        if (s == Spell.SCHWEBE || combo == Combo.FEUERKOMET) {
            for (Prop p : props) {
                if (p.state == Prop.REST && p.pos.distance(at) < p.radius + 2.2f) {
                    p.state = Prop.LIFTED;
                    p.t = 0;
                    host.sfx("whoosh", 0.8f);
                    host.text("Levitaris hebt den Felsen!", p.pos.add(0, 2, 0), Spell.SCHWEBE.color);
                    return true;
                }
            }
        }
        if (s == Spell.LICHT || combo == Combo.STERNENBLITZ) {
            for (Foe f : foes) if (!f.dead && f.center().distance(at) < 8) f.lit(power);
        }
        float radius = combo == Combo.STERNENBLITZ ? 4.5f : combo == Combo.FEUERKOMET ? 5f : combo == Combo.SPIEGELSTOSS ? 7f : 1.9f;
        List<Foe> hitList = new ArrayList<>();
        if (homing != null && !homing.dead) hitList.add(homing);
        for (Foe f : foes) {
            if (f.dead || hitList.contains(f)) continue;
            if (f.center().distance(at) < f.radius + radius) hitList.add(f);
        }
        if (combo != null && homing == null) hitList.removeIf(f -> f.center().distance(at) > f.radius + radius);
        for (Foe f : hitList) {
            if (f.hit(s, combo, power, at)) any = true;
        }
        return any;
    }

    /** Spiegelstoß: Welle um den Spieler. */
    public void mirrorBlast(Vector3f at, float power) {
        Wave w = new Wave(at, 7, false, new ColorRGBA(0.5f, 1f, 0.85f, 1));
        waves.add(w);
        for (Foe f : foes) {
            if (f.dead || f.center().distance(at) > 7.5f) continue;
            f.hit(Spell.FUNKE, Combo.SPIEGELSTOSS, power, at);
            Vector3f push = f.pos.subtract(at).setY(0);
            if (push.lengthSquared() > 0.01f && f.pushable()) f.pos.addLocal(push.normalizeLocal().multLocal(3));
        }
        for (Orb o : orbs) if (!o.reflected && o.pos.distance(at) < 8) reflect(o);
    }

    // ================================================================ Geschosse

    public final class Orb {
        final Geometry geo;
        final Vector3f pos, vel;
        final Foe source;
        float life = 6, dmg;
        boolean reflected, reflectable, arc;
        final ColorRGBA color;

        Orb(Foe source, Vector3f from, Vector3f vel, float dmg, ColorRGBA c, float size, boolean reflectable) {
            this.source = source;
            this.pos = from.clone();
            this.vel = vel;
            this.dmg = dmg;
            this.color = c;
            this.reflectable = reflectable;
            geo = G3.geom("orb", new Sphere(10, 10, size), G3.emissive(c));
            geo.setShadowMode(RenderQueue.ShadowMode.Off);
            geo.setLocalTranslation(pos);
            node.attachChild(geo);
        }
    }

    public Orb shoot(Foe from, Vector3f start, Vector3f target, float speed, float dmg, ColorRGBA c, float size, boolean reflectable) {
        Vector3f v = target.subtract(start).normalizeLocal().multLocal(speed);
        Orb o = new Orb(from, start, v, dmg, c, size, reflectable);
        orbs.add(o);
        return o;
    }

    private void reflect(Orb o) {
        o.reflected = true;
        Vector3f tgt = o.source != null && !o.source.dead ? o.source.center() : o.pos.subtract(o.vel);
        o.vel.set(tgt.subtract(o.pos).normalizeLocal().multLocal(o.vel.length() * 1.3f));
        o.geo.getMaterial().setColor("Color", new ColorRGBA(0.55f, 1f, 0.85f, 1));
        o.geo.getMaterial().setColor("GlowColor", new ColorRGBA(0.55f, 1f, 0.85f, 1));
        o.life = 4;
        host.sfx("chime", 1.4f);
        host.text("Zurückgeworfen!", o.pos.add(0, 0.6f, 0), new Color(140, 255, 210));
    }

    private void updateOrbs(float dt) {
        Vector3f pl = host.player();
        Vector3f chest = pl.add(0, host.playerHeight() * 0.55f, 0);
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            o.life -= dt;
            if (o.arc) o.vel.y -= 9 * dt;
            if (o.reflected && o.source != null && !o.source.dead) {
                // zurückgeworfene Kugeln finden ihren Werfer
                Vector3f want = o.source.center().subtract(o.pos).normalizeLocal().multLocal(o.vel.length());
                o.vel.interpolateLocal(want, Math.min(1, dt * 6));
            }
            o.pos.addLocal(o.vel.mult(dt));
            o.geo.setLocalTranslation(o.pos);
            boolean gone = o.life <= 0 || o.pos.y < world.height(o.pos.x, o.pos.z) - 0.3f;
            if (!o.reflected && o.pos.distance(chest) < (host.shield() ? 1.7f : 0.9f)) {
                if (host.shield() && o.reflectable) { reflect(o); continue; }
                if (!host.shield()) strike(o.dmg, o.pos);
                else world.burst(o.pos, o.color, 20);
                gone = true;
            }
            if (o.reflected) {
                for (Foe f : foes) {
                    if (f.dead || f.center().distance(o.pos) > f.radius + 0.6f) continue;
                    f.reflectedHit(o);
                    gone = true;
                    break;
                }
            }
            if (gone) {
                world.burst(o.pos, o.color, 22);
                o.geo.removeFromParent();
                it.remove();
            }
        }
    }

    // ================================================================ Felsen (Levitaris)

    public final class Prop {
        static final int REST = 0, LIFTED = 1, FLYING = 2, GONE = 3;
        final Geometry geo;
        final Vector3f home, pos = new Vector3f(), vel = new Vector3f();
        final float radius;
        int state;
        float t;
        Foe target;

        Prop(Vector3f at, float radius) {
            home = at.clone();
            pos.set(at);
            this.radius = radius;
            geo = G3.geom("prop", G3.rock(rnd.nextLong(), radius), G3.ph("mossy_rock", new ColorRGBA(0.85f, 0.8f, 0.9f, 1)));
            geo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            geo.setLocalTranslation(pos);
            node.attachChild(geo);
        }
    }

    public Prop addProp(Vector3f at, float radius) {
        Prop p = new Prop(at, radius);
        props.add(p);
        return p;
    }

    private void updateProps(float dt) {
        for (Prop p : props) {
            p.t += dt;
            switch (p.state) {
                case Prop.LIFTED -> {
                    Vector3f want = p.home.add(0, 2.6f + FastMath.sin(p.t * 6) * 0.15f, 0);
                    p.pos.interpolateLocal(want, Math.min(1, dt * 4));
                    p.geo.rotate(dt * 1.5f, dt, 0);
                    if (p.t > 1.1f) {
                        // auf den nächsten Gegner schleudern
                        Foe best = null;
                        float bd = 22;
                        for (Foe f : foes) {
                            if (f.dead) continue;
                            float d = f.center().distance(p.pos);
                            if (d < bd) { bd = d; best = f; }
                        }
                        if (best == null) { p.state = Prop.REST; p.pos.set(p.home); break; }
                        p.target = best;
                        p.state = Prop.FLYING;
                        p.t = 0;
                        host.sfx("whoosh", 0.6f);
                    }
                }
                case Prop.FLYING -> {
                    Vector3f to = p.target.center();
                    Vector3f d = to.subtract(p.pos);
                    float step = 22 * dt;
                    if (d.length() < Math.max(step, p.target.radius)) {
                        world.burst(p.pos, new ColorRGBA(0.8f, 0.75f, 0.65f, 1), 60);
                        host.sfx("hit", 0.6f);
                        p.target.propHit(p);
                        p.state = Prop.GONE;
                        p.t = 0;
                        p.geo.setCullHint(Spatial.CullHint.Always);
                    } else p.pos.addLocal(d.normalizeLocal().multLocal(step));
                    if (p.t > 3) { p.state = Prop.GONE; p.t = 0; p.geo.setCullHint(Spatial.CullHint.Always); }
                }
                case Prop.GONE -> {
                    if (p.t > 5) {
                        p.state = Prop.REST;
                        p.pos.set(p.home);
                        p.geo.setCullHint(Spatial.CullHint.Inherit);
                        world.burst(p.home.add(0, 0.5f, 0), new ColorRGBA(0.75f, 0.65f, 1f, 1), 30);
                    }
                }
                default -> { }
            }
            p.geo.setLocalTranslation(p.pos);
        }
    }

    // ================================================================ Schockwellen (springen!)

    private final class Wave {
        final Geometry geo;
        final Vector3f center;
        final float maxR;
        final boolean harmful;
        float r = 0.3f, t;

        Wave(Vector3f c, float maxR, boolean harmful, ColorRGBA col) {
            center = c.clone();
            this.maxR = maxR;
            this.harmful = harmful;
            Material m = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", col);
            m.setColor("GlowColor", col);
            geo = G3.geom("wave", new Torus(48, 6, 0.12f, 1f), m);
            geo.setShadowMode(RenderQueue.ShadowMode.Off);
            geo.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
            geo.setLocalTranslation(center.add(0, 0.15f, 0));
            node.attachChild(geo);
        }
    }

    public void shockwave(Vector3f c, float maxR) {
        waves.add(new Wave(c, maxR, true, new ColorRGBA(1f, 0.55f, 0.25f, 1)));
    }

    private void updateWaves(float dt) {
        Vector3f pl = host.player();
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            float before = w.r;
            w.r += dt * 9;
            w.t += dt;
            w.geo.setLocalScale(w.r, w.r, 1 + w.t);
            if (w.harmful) {
                float d = FastMath.sqrt(FastMath.sqr(pl.x - w.center.x) + FastMath.sqr(pl.z - w.center.z));
                if (d > before - 0.5f && d < w.r + 0.5f && !host.playerAirborne()) strike(1, w.center);
            }
            if (w.r > w.maxR) { w.geo.removeFromParent(); it.remove(); }
        }
    }

    // ================================================================ Gegner

    public abstract class Foe {
        public final Node node = new Node("foe");
        public final Vector3f pos = new Vector3f(), vel = new Vector3f();
        public float hp, maxHp, t, flash, stun, revealT, deadT, radius = 0.7f, scale = 1, yaw;
        public boolean dead;
        public String name = "";
        public Runnable onDeath;

        Foe(Vector3f at, float hp) {
            pos.set(at);
            this.hp = maxHp = hp;
        }

        Vector3f center() { return pos.add(0, radius, 0); }

        boolean targetable() { return true; }

        boolean pushable() { return true; }

        void lit(float power) { }

        abstract void update(float dt);

        void sync() {
            node.setLocalTranslation(pos);
            node.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        }

        /** Grundschaden eines Zaubers (Stufe 1–3). */
        float damage(Spell s, Combo c, float power) {
            if (c != null) return 2.5f * power;
            return switch (s) {
                case FUNKE, FLAMME, FROST -> 1f * power;
                case LICHT, SCHWEBE -> 0.5f * power;
                default -> 0;
            };
        }

        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            float d = damage(s, c, power);
            if (d <= 0) return false;
            hurt(d);
            return true;
        }

        void hurt(float d) {
            if (dead) return;
            hp -= d;
            flash = 0.25f;
            host.sfx("hit", 1 + rnd.nextFloat() * 0.3f);
            world.burst(center(), new ColorRGBA(1f, 0.85f, 0.6f, 1), 18);
            if (hp <= 0) die();
        }

        void die() {
            dead = true;
            world.burst(center(), new ColorRGBA(0.7f, 0.35f, 1f, 1), 50);
            if (onDeath != null) onDeath.run();
        }

        void reflectedHit(Orb o) { hurt(1.5f); }

        void propHit(Prop p) { hurt(2); }

        Vector3f toPlayer() { return host.player().subtract(pos).setY(0); }

        float ground() { return world.height(pos.x, pos.z); }

        void face(Vector3f dir, float dt) {
            if (dir.lengthSquared() < 0.001f) return;
            float want = FastMath.atan2(dir.x, dir.z);
            yaw += FastMath.normalize(want - yaw, -FastMath.PI, FastMath.PI) * Math.min(1, dt * 6);
        }

        boolean move(Vector3f step) {
            float nx = pos.x + step.x, nz = pos.z + step.z;
            if (world.blockedIgnoringWater(nx, nz, 0.4f)) return false;
            pos.x = nx;
            pos.z = nz;
            return true;
        }
    }

    // ---------------------------------------------------------------- Schattenflatterer

    /** Fliegt Kreise um dich und stößt dann herab. Jeder Schadenszauber trifft. */
    public final class Bat extends Foe {
        private final Spatial wingL, wingR;
        private int mode;
        private float modeT, circleA = rnd.nextFloat() * 6.28f;
        private final Vector3f dive = new Vector3f();

        public Bat(Vector3f at) {
            super(at, 2);
            name = "Schattenflatterer";
            radius = 0.6f;
            Material body = G3.litGlow(new ColorRGBA(0.12f, 0.08f, 0.16f, 1), new ColorRGBA(0.08f, 0.02f, 0.12f, 1));
            Geometry b = G3.geom("batBody", new Sphere(8, 10, 0.32f), body);
            b.setLocalScale(0.8f, 0.8f, 1.2f);
            node.attachChild(b);
            for (int s = -1; s <= 1; s += 2) {
                Geometry eye = G3.geom("eye", new Sphere(5, 5, 0.05f), G3.emissive(new ColorRGBA(1f, 0.2f, 0.3f, 1)));
                eye.setLocalTranslation(s * 0.1f, 0.08f, 0.33f);
                node.attachChild(eye);
            }
            Mesh wing = wingMesh();
            Material wm = G3.lit(new ColorRGBA(0.16f, 0.1f, 0.2f, 1));
            wm.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            Node l = new Node("wl"), r = new Node("wr");
            Geometry gl = G3.geom("wingL", wing, wm);
            Geometry gr = G3.geom("wingR", wing, wm);
            gr.setLocalScale(-1, 1, 1);
            l.attachChild(gl);
            r.attachChild(gr);
            node.attachChild(l);
            node.attachChild(r);
            wingL = l;
            wingR = r;
            com.jme3.effect.ParticleEmitter smoke = G3.particles("batSmoke", 20, "Effects/Smoke/Smoke.png",
                new ColorRGBA(0.1f, 0.03f, 0.15f, 0.4f), new ColorRGBA(0, 0, 0, 0), 0.3f, 0.7f, 0.5f, 1f);
            smoke.setImagesX(15);
            smoke.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            smoke.setShape(new com.jme3.effect.shapes.EmitterSphereShape(Vector3f.ZERO, 0.2f));
            smoke.setParticlesPerSec(14);
            smoke.setInWorldSpace(true);
            node.attachChild(smoke);
            pos.y = world.height(at.x, at.z) + 3.5f;
        }

        private Mesh wingMesh() {
            Mesh m = new Mesh();
            float[] p = {0, 0, 0.15f, 0.9f, 0.15f, 0.05f, 0.75f, -0.1f, -0.25f, 0.35f, -0.05f, -0.2f, 0.05f, 0, -0.2f};
            short[] idx = {0, 1, 2, 0, 2, 3, 0, 3, 4};
            float[] n = {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0};
            m.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, p);
            m.setBuffer(com.jme3.scene.VertexBuffer.Type.Normal, 3, n);
            m.setBuffer(com.jme3.scene.VertexBuffer.Type.Index, 3, idx);
            m.updateBound();
            return m;
        }

        @Override
        void update(float dt) {
            float f = FastMath.sin(t * 18) * 0.8f;
            wingL.setLocalRotation(new Quaternion().fromAngleAxis(f, Vector3f.UNIT_Z));
            wingR.setLocalRotation(new Quaternion().fromAngleAxis(-f, Vector3f.UNIT_Z));
            Vector3f pl = host.player();
            modeT += dt;
            if (stun > 0) { pos.y = Math.max(ground() + 0.4f, pos.y - dt * 3); return; }
            switch (mode) {
                case 0 -> { // kreisen
                    circleA += dt * 1.1f;
                    Vector3f want = new Vector3f(pl.x + FastMath.cos(circleA) * 6, ground() + 3.6f + FastMath.sin(t * 2) * 0.5f, pl.z + FastMath.sin(circleA) * 6);
                    Vector3f d = want.subtract(pos);
                    pos.addLocal(d.multLocal(Math.min(1, dt * 1.6f)));
                    face(new Vector3f(-FastMath.sin(circleA), 0, FastMath.cos(circleA)), dt);
                    if (modeT > 2.5f + rnd.nextFloat() * 2) {
                        mode = 1;
                        modeT = 0;
                        dive.set(pl.x, pl.y + 1.1f, pl.z);
                        host.sfx("whoosh", 1.4f);
                    }
                }
                case 1 -> { // Sturzflug
                    Vector3f d = dive.subtract(pos);
                    face(d.clone().setY(0), dt * 3);
                    float step = 9.5f * dt;
                    if (d.length() < step) { mode = 2; modeT = 0; }
                    else pos.addLocal(d.normalizeLocal().multLocal(step));
                    if (pos.distance(pl.add(0, 1.1f, 0)) < 1.1f) {
                        strike(0.5f, pos);
                        mode = 2;
                        modeT = 0;
                    }
                    if (modeT > 2) { mode = 2; modeT = 0; }
                }
                default -> { // wieder aufsteigen
                    pos.y += dt * 3.5f;
                    if (modeT > 0.9f) { mode = 0; modeT = 0; circleA = FastMath.atan2(pos.z - pl.z, pos.x - pl.x); }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Dornenwürger

    /** Pflanzenmonster: spuckt Dornen. Nur Feuer verletzt es. */
    public final class Plant extends Foe {
        private final Node head = new Node("head");
        private float shootT = 1.5f + rnd.nextFloat();

        public Plant(Vector3f at) {
            super(at, 3);
            name = "Dornenwürger";
            radius = 1.1f;
            pos.y = world.height(at.x, at.z);
            Material leaf = G3.lit(new ColorRGBA(0.16f, 0.3f, 0.12f, 1));
            Material thorn = G3.lit(new ColorRGBA(0.35f, 0.25f, 0.15f, 1));
            for (int i = 0; i < 7; i++) {
                float a = i * FastMath.TWO_PI / 7;
                Geometry l = G3.geom("leaf", new Cylinder(2, 6, 0.28f, 0.02f, 1.6f, true, false), leaf);
                l.setLocalTranslation(FastMath.sin(a) * 0.55f, 0.35f, FastMath.cos(a) * 0.55f);
                l.setLocalRotation(new Quaternion().fromAngles(FastMath.cos(a) * 1.0f - FastMath.HALF_PI * 0, a, 0)
                    .mult(new Quaternion().fromAngleAxis(-1.1f, Vector3f.UNIT_X)));
                node.attachChild(l);
            }
            Geometry stem = G3.geom("stem", new Cylinder(2, 8, 0.16f, 0.22f, 1.6f, true, false), leaf);
            stem.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
            stem.setLocalTranslation(0, 0.8f, 0);
            node.attachChild(stem);
            Geometry bulb = G3.geom("bulb", new Sphere(12, 14, 0.55f), G3.lit(new ColorRGBA(0.4f, 0.12f, 0.2f, 1)));
            bulb.setLocalScale(1, 0.85f, 1.1f);
            head.attachChild(bulb);
            Geometry maw = G3.geom("maw", new Torus(16, 6, 0.08f, 0.3f), G3.emissive(new ColorRGBA(1f, 0.35f, 0.15f, 1)));
            maw.setLocalTranslation(0, 0, 0.5f);
            head.attachChild(maw);
            for (int i = 0; i < 8; i++) {
                float a = i * FastMath.TWO_PI / 8;
                Geometry th = G3.geom("thorn", new Cylinder(2, 4, 0.06f, 0.0f, 0.35f, true, false), thorn);
                th.setLocalTranslation(FastMath.sin(a) * 0.5f, FastMath.cos(a) * 0.45f, 0);
                th.setLocalRotation(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Z).mult(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X)));
                head.attachChild(th);
            }
            head.setLocalTranslation(0, 1.8f, 0);
            node.attachChild(head);
        }

        @Override
        boolean pushable() { return false; }

        @Override
        float damage(Spell s, Combo c, float power) {
            if (s == Spell.FLAMME || c == Combo.FEUERKOMET) return (c != null ? 3f : 1.5f) * power;
            return 0;
        }

        @Override
        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            if (damage(s, c, power) <= 0) {
                if (s != Spell.LICHT) host.text("Die Dornen schützen ihn – nur Feuer hilft: Ignis!", center().add(0, 1.6f, 0), Spell.FLAMME.color);
                stun = 0.6f;
                return true;
            }
            return super.hit(s, c, power, at);
        }

        @Override
        void update(float dt) {
            Vector3f d = toPlayer();
            face(d, dt);
            float sway = FastMath.sin(t * 1.7f) * 0.12f;
            head.setLocalRotation(new Quaternion().fromAngles(sway + (flash > 0 ? -0.4f : 0), 0, sway * 0.5f));
            if (stun > 0) return;
            shootT -= dt;
            if (d.length() < 18 && shootT <= 0) {
                shootT = 2.4f + rnd.nextFloat();
                Vector3f mouth = pos.add(0, 1.8f, 0).addLocal(FastMath.sin(yaw) * 0.6f, 0, FastMath.cos(yaw) * 0.6f);
                shoot(this, mouth, host.player().add(0, host.playerHeight() * 0.55f, 0), 10, 0.5f, new ColorRGBA(0.6f, 1f, 0.3f, 1), 0.14f, true);
                host.sfx("whoosh", 1.8f);
            }
        }

        @Override
        Vector3f center() { return pos.add(0, 1.3f, 0); }
    }

    // ---------------------------------------------------------------- Irrgeist

    /** Fast unsichtbar – erst Lumen macht ihn verwundbar. Berührung kostet ein halbes Herz. */
    public final class Ghost extends Foe {
        private final Material mat;
        private final Geometry body;

        public Ghost(Vector3f at) {
            super(at, 2);
            name = "Irrgeist";
            radius = 0.8f;
            pos.y = world.height(at.x, at.z);
            mat = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", new ColorRGBA(0.7f, 0.85f, 1f, 0.1f));
            mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            mat.getAdditionalRenderState().setDepthWrite(false);
            body = G3.transparent(G3.geom("ghostBody", new Cylinder(2, 14, 0.05f, 0.55f, 1.5f, true, false), mat));
            body.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
            body.setLocalTranslation(0, 1.0f, 0);
            node.attachChild(body);
            Geometry head = G3.transparent(G3.geom("ghostHead", new Sphere(12, 12, 0.36f), mat));
            head.setLocalTranslation(0, 1.85f, 0);
            node.attachChild(head);
            for (int s = -1; s <= 1; s += 2) {
                Geometry eye = G3.geom("eye", new Sphere(5, 5, 0.06f), G3.emissive(new ColorRGBA(0.5f, 0.9f, 1f, 1)));
                eye.setLocalTranslation(s * 0.13f, 1.9f, 0.3f);
                node.attachChild(eye);
            }
        }

        @Override
        boolean targetable() { return revealT > 0; }

        @Override
        void lit(float power) {
            boolean was = revealT > 0;
            revealT = 5 + power * 2;
            if (!was) host.text("Lumen enthüllt den Irrgeist!", center().add(0, 1.4f, 0), Spell.LICHT.color);
        }

        @Override
        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            if (s == Spell.LICHT && c == null) { hurt(0.5f); return true; }
            if (revealT <= 0 && c != Combo.STERNENBLITZ) {
                host.text("Der Zauber geht durch ihn hindurch – erst Lumen!", center().add(0, 1.4f, 0), Spell.LICHT.color);
                return true;
            }
            return super.hit(s, c, power, at);
        }

        @Override
        Vector3f center() { return pos.add(0, 1.3f, 0); }

        @Override
        void update(float dt) {
            revealT = Math.max(0, revealT - dt);
            float a = revealT > 0 ? 0.55f : 0.07f + 0.05f * FastMath.sin(t * 3);
            mat.setColor("Color", new ColorRGBA(0.7f, 0.85f, 1f, a + (flash > 0 ? 0.3f : 0)));
            Vector3f d = toPlayer();
            face(d, dt);
            float sp = stun > 0 ? 0 : revealT > 0 ? 1.4f : 2.3f;
            if (d.length() > 0.8f) {
                Vector3f step = d.normalize().multLocal(sp * dt);
                pos.x += step.x;
                pos.z += step.z;
            }
            pos.y = ground() + 0.25f + FastMath.sin(t * 2.2f) * 0.15f;
            if (d.length() < 1.0f) strike(0.5f, pos);
        }
    }

    // ---------------------------------------------------------------- Wächterauge (Schleichen)

    /** Schwebendes Auge mit Sichtkegel. Lumen blendet es für ein paar Sekunden. */
    public final class Sentry extends Foe {
        private final Vector3f[] route;
        private int leg;
        private final Geometry cone;
        private final Material coneMat;
        public float blindT;
        public Runnable onSpot;
        public float viewDist = 9, viewAngle = 0.5f;

        public Sentry(Vector3f[] route, float height) {
            super(route[0], 999);
            this.route = route;
            name = "Wächterauge";
            radius = 0.5f;
            pos.y = route[0].y + height;
            Geometry eye = G3.geom("sentry", new Sphere(14, 14, 0.45f), G3.lit(new ColorRGBA(0.85f, 0.82f, 0.75f, 1)));
            node.attachChild(eye);
            Geometry iris = G3.geom("iris", new Sphere(10, 10, 0.2f), G3.emissive(new ColorRGBA(1f, 0.6f, 0.2f, 1)));
            iris.setLocalTranslation(0, 0, 0.36f);
            iris.setLocalScale(1, 1, 0.4f);
            node.attachChild(iris);
            coneMat = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
            coneMat.setColor("Color", new ColorRGBA(1f, 0.75f, 0.35f, 0.12f));
            coneMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            coneMat.getAdditionalRenderState().setDepthWrite(false);
            coneMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            float r = FastMath.tan(viewAngle) * viewDist;
            cone = G3.transparent(G3.geom("viewCone", new Cylinder(2, 20, 0.05f, r, viewDist, false, false), coneMat));
            cone.setLocalTranslation(0, -1.0f, viewDist / 2);
            cone.setLocalRotation(new Quaternion().fromAngleAxis(0.12f, Vector3f.UNIT_X));
            node.attachChild(cone);
        }

        @Override
        boolean targetable() { return false; }

        @Override
        boolean pushable() { return false; }

        @Override
        void lit(float power) {
            blindT = 3 + power * 1.5f;
            host.text("Das Auge ist geblendet!", center().add(0, 0.8f, 0), Spell.LICHT.color);
        }

        @Override
        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            if (s != Spell.LICHT) host.text("Das Auge lässt sich nicht verletzen – blende es mit Lumen!", center().add(0, 0.8f, 0), Spell.LICHT.color);
            return true;
        }

        @Override
        void hurt(float d) { }

        @Override
        void update(float dt) {
            blindT = Math.max(0, blindT - dt);
            cone.setCullHint(blindT > 0 ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            float baseY = route[0].y;
            Vector3f target = route[leg];
            Vector3f d = target.subtract(pos).setY(0);
            if (blindT <= 0) {
                if (d.length() < 0.3f) leg = (leg + 1) % route.length;
                else {
                    face(d, dt * 0.6f);
                    pos.addLocal(d.normalize().multLocal(1.7f * dt));
                }
            }
            pos.y = baseY + 2.4f + FastMath.sin(t * 1.5f) * 0.1f;
            if (blindT > 0) return;
            Vector3f p = host.player();
            Vector3f to = p.subtract(pos).setY(0);
            float dist = to.length();
            if (dist < viewDist && dist > 0.1f) {
                float ang = FastMath.abs(FastMath.normalize(FastMath.atan2(to.x, to.z) - yaw, -FastMath.PI, FastMath.PI));
                if (ang < viewAngle && onSpot != null) {
                    coneMat.setColor("Color", new ColorRGBA(1f, 0.2f, 0.15f, 0.3f));
                    onSpot.run();
                    return;
                }
            }
            coneMat.setColor("Color", new ColorRGBA(1f, 0.75f, 0.35f, 0.12f));
        }
    }

    // ---------------------------------------------------------------- Dieb (Verfolgungsjagd)

    /** Läuft vor dir davon. Fang ihn ein oder halte ihn mit Levitaris, Arcanum oder Glacira auf. */
    public final class Thief extends Foe {
        final Figure fig;
        private final Vector3f[] route;
        private int leg = 1;
        public boolean caught;
        public float speed = 6.4f;

        public Thief(Look look, Vector3f[] route) {
            super(route[0], 1);
            this.route = route;
            name = "Der Laternendieb";
            radius = 0.9f;
            fig = new Figure(look);
            node.attachChild(fig.node);
            pos.y = ground();
        }

        @Override
        boolean pushable() { return false; }

        @Override
        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            if (s == Spell.SCHWEBE || s == Spell.FUNKE || s == Spell.FROST || c != null) {
                stun = 2.2f;
                host.text(s == Spell.SCHWEBE ? "Levitaris hebt ihn in die Luft!" : "Getroffen – er taumelt!", center().add(0, 1.5f, 0), s.color);
                return true;
            }
            return false;
        }

        @Override
        void update(float dt) {
            Vector3f p = host.player();
            float dist = p.distance(pos);
            if (dist < 1.8f) { caught = true; return; }
            float sp = 0;
            if (stun > 0) {
                pos.y = ground() + (stun > 0.3f ? 1.2f * Math.min(1, (2.2f - stun) * 3) : 0);
            } else {
                Vector3f d = route[leg].subtract(pos).setY(0);
                if (d.length() < 1.2f) leg = (leg + 1) % route.length;
                // wartet etwas, wenn du weit zurückliegst – aber nur kurz
                sp = dist > 26 ? speed * 0.45f : dist < 8 ? speed * 1.08f : speed;
                Vector3f step = d.normalize().multLocal(sp * dt);
                pos.x += step.x;
                pos.z += step.z;
                face(d, dt * 1.5f);
                pos.y = ground();
            }
            fig.update(dt, sp, false);
        }
    }

    // ---------------------------------------------------------------- Bosse: gemeinsame Hülle

    public abstract class Boss extends Foe {
        public int phase;
        public String title;
        float stateT;
        int state;

        Boss(Vector3f at, float hp) { super(at, hp); }

        void state(int s) { state = s; stateT = 0; }

        /** Anteil der Lebensleiste (0–1). */
        public float bar() { return Math.max(0, hp / maxHp); }
    }

    // ---------------------------------------------------------------- Aschfell, der Uralte (Kapitel 3)

    /**
     * Ein uralter Waldgeist in Fuchsgestalt, von Schatten besessen.
     * Phase 1: Er springt dich an (ausweichen!). Danach ist er benommen: Zerschlage die drei Schattenkristalle.
     * Phase 2: Er schleudert Schattenkugeln – wirf sie mit Protego zurück. Irrgeister helfen ihm.
     * Phase 3: Er liegt erschöpft am Boden – befreie ihn mit Lumen.
     */
    public final class AncientFox extends Boss {
        private final Spatial model;
        private AnimComposer anim;
        private String animNow = "";
        private final List<Geometry> crystals = new ArrayList<>();
        private final Vector3f charge = new Vector3f(), arena;
        private final Material crystalMat;
        private int reflected;
        public Runnable onCleansed, onPhase2;
        private float volleyT;

        public AncientFox(Vector3f at) {
            super(at, 9);
            arena = at.clone();
            title = "Aschfell, der Uralte";
            name = title;
            radius = 1.8f;
            Spatial m = G3.foxModel();
            if (m != null) {
                m.setLocalScale(0.042f);
                m.depthFirstTraversal(sp -> {
                    if (sp instanceof Geometry g) {
                        Material mm = g.getMaterial().clone();
                        mm.setColor("Diffuse", new ColorRGBA(0.42f, 0.4f, 0.48f, 1));
                        mm.setColor("Ambient", new ColorRGBA(0.2f, 0.18f, 0.25f, 1));
                        mm.setColor("GlowColor", new ColorRGBA(0.08f, 0.02f, 0.14f, 1));
                        g.setMaterial(mm);
                    }
                });
                anim = Mixamo.composer(m);
            } else {
                m = G3.geom("foxBody", new Sphere(12, 12, 1.4f), G3.lit(new ColorRGBA(0.4f, 0.38f, 0.45f, 1)));
                m.setLocalScale(0.9f, 0.9f, 1.8f);
                m.setLocalTranslation(0, 1.6f, 0);
            }
            model = m;
            node.attachChild(m);
            for (int s = -1; s <= 1; s += 2) {
                Geometry eye = G3.geom("eye", new Sphere(6, 6, 0.12f), G3.emissive(new ColorRGBA(1f, 0.2f, 0.35f, 1)));
                eye.setLocalTranslation(s * 0.24f, 2.75f, 2.75f);
                node.attachChild(eye);
            }
            crystalMat = G3.litGlow(new ColorRGBA(0.3f, 0.1f, 0.45f, 1), new ColorRGBA(0.45f, 0.1f, 0.7f, 1));
            for (int i = 0; i < 3; i++) {
                Geometry c = G3.geom("shadowCrystal", new Sphere(4, 6, 0.45f), crystalMat);
                c.setLocalScale(0.6f, 1.6f, 0.6f);
                c.setShadowMode(RenderQueue.ShadowMode.Off);
                node.attachChild(c);
                crystals.add(c);
            }
            com.jme3.effect.ParticleEmitter smoke = G3.particles("foxSmoke", 60, "Effects/Smoke/Smoke.png",
                new ColorRGBA(0.12f, 0.03f, 0.2f, 0.5f), new ColorRGBA(0, 0, 0, 0), 1.2f, 2.6f, 1f, 2f);
            smoke.setImagesX(15);
            smoke.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            smoke.setShape(new com.jme3.effect.shapes.EmitterSphereShape(new Vector3f(0, 1.5f, 0), 1.2f));
            smoke.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.6f, 0));
            smoke.setParticlesPerSec(18);
            smoke.setInWorldSpace(true);
            node.attachChild(smoke);
            pos.y = ground();
            state(0);
        }

        private void play(String a, float speed) {
            if (anim == null) return;
            if (!a.equals(animNow)) {
                animNow = a;
                try { anim.setCurrentAction(a); } catch (RuntimeException ignored) { }
            }
            anim.setGlobalSpeed(speed);
        }

        @Override
        Vector3f center() { return pos.add(0, 1.8f, 0); }

        @Override
        boolean pushable() { return false; }

        @Override
        public float bar() {
            return switch (phase) {
                case 0 -> 0.67f + crystals.size() / 9f;
                case 1 -> 0.34f + (3 - reflected) / 9f;
                default -> 0.33f;
            };
        }

        @Override
        void lit(float power) {
            if (phase == 2 && !dead) {
                dead = true;
                if (onCleansed != null) onCleansed.run();
            }
        }

        @Override
        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            if (phase == 0) {
                if (state == 3 && !crystals.isEmpty() && (damage(s, c, power) > 0)) {
                    Geometry cr = crystals.remove(crystals.size() - 1);
                    world.burst(cr.getWorldTranslation(), new ColorRGBA(0.7f, 0.3f, 1f, 1), 80);
                    cr.removeFromParent();
                    flash = 0.3f;
                    host.sfx("hit", 0.7f);
                    host.text("Ein Schattenkristall zerbricht!  (" + (3 - crystals.size()) + "/3)", center().add(0, 2.4f, 0), new Color(220, 160, 255));
                    if (crystals.isEmpty()) {
                        phase = 1;
                        state(10);
                        host.sfx("roar", 0.9f);
                        bossHint = "Er schleudert Schattenkugeln: Protego (Schild) wirft sie zurück! Irrgeister: erst Lumen.";
                        if (onPhase2 != null) onPhase2.run();
                    }
                    return true;
                }
                host.text(state == 3 ? "Ein Zauber mit Kraft: Arcanum oder Ignis!" : "Der Schattenpanzer hält – warte, bis er benommen ist!", center().add(0, 2.4f, 0), new Color(200, 180, 230));
                return true;
            }
            if (phase == 1) {
                host.text("Wirf seine Schattenkugeln mit Protego zurück!", center().add(0, 2.4f, 0), Spell.SCHILD.color);
                return true;
            }
            if (s == Spell.LICHT || c == Combo.STERNENBLITZ) { lit(power); return true; }
            host.text("Er wehrt sich nicht mehr. Befreie ihn mit Lumen!", center().add(0, 2.4f, 0), Spell.LICHT.color);
            return true;
        }

        @Override
        void reflectedHit(Orb o) {
            if (phase != 1) return;
            reflected++;
            flash = 0.4f;
            host.sfx("roar", 1.3f);
            host.text("Getroffen!  (" + reflected + "/3)", center().add(0, 2.4f, 0), new Color(140, 255, 210));
            if (reflected >= 3) {
                phase = 2;
                state(20);
                bossHint = "Aschfell liegt erschöpft am Boden. Befreie ihn mit Lumen!";
            }
        }

        @Override
        void propHit(Prop p) { }

        @Override
        void update(float dt) {
            stateT += dt;
            Vector3f pl = host.player();
            Vector3f d = toPlayer();
            for (int i = 0; i < crystals.size(); i++) {
                float a = t * (state == 3 ? 0.3f : 2.2f) + i * FastMath.TWO_PI / 3;
                crystals.get(i).setLocalTranslation(FastMath.sin(a) * 1.3f, 3.6f + FastMath.sin(t * 3 + i) * 0.2f, FastMath.cos(a) * 1.3f - 0.4f);
            }
            crystalMat.setColor("GlowColor", state == 3 ? new ColorRGBA(1f, 0.5f, 1f, 1) : new ColorRGBA(0.45f, 0.1f, 0.7f, 1));
            switch (state) {
                case 0 -> { // umkreisen
                    Vector3f side = new Vector3f(-d.z, 0, d.x).normalizeLocal();
                    Vector3f want = d.length() > 12 ? d.normalize() : d.length() < 8 ? d.normalize().negate() : side;
                    move(want.mult(3.4f * dt));
                    face(want.add(d.normalize().mult(0.6f)), dt);
                    play("Walk", 0.8f);
                    if (stateT > 2.6f) {
                        state(1);
                        host.sfx("roar", 1f);
                        host.text("Er duckt sich zum Sprung – weich aus!", center().add(0, 2.6f, 0), new Color(255, 160, 120));
                    }
                }
                case 1 -> { // Anlauf
                    face(d, dt * 2);
                    play("Survey", 1.6f);
                    if (stateT > 1.0f) {
                        charge.set(pl.x, 0, pl.z).addLocal(d.normalize().mult(5));
                        state(2);
                        host.sfx("whoosh", 0.5f);
                    }
                }
                case 2 -> { // Sprung
                    Vector3f to = charge.subtract(pos).setY(0);
                    play("Run", 1.6f);
                    float step = 17 * dt;
                    boolean blocked = false;
                    if (to.length() < step) state(3);
                    else blocked = !move(to.normalize().multLocal(step));
                    face(to, dt * 4);
                    if (pos.distance(pl) < 2.4f) {
                        if (strike(1, pos)) {
                            Vector3f push = d.normalize().multLocal(2.5f);
                            pl.addLocal(push.x, 0, push.z);
                        }
                    }
                    if (blocked || stateT > 1.6f) state(3);
                    if (state == 3) {
                        world.burst(pos.add(0, 1, 0), new ColorRGBA(0.8f, 0.75f, 0.6f, 1), 40);
                        host.text("Benommen! Zerschlag die Kristalle!", center().add(0, 2.6f, 0), new Color(255, 220, 140));
                    }
                }
                case 3 -> { // benommen
                    play("Survey", 0.4f);
                    if (stateT > 3.2f) state(0);
                }
                case 10 -> { // Phase 2: Abstand, Schattenkugeln
                    play(d.length() < 11 ? "Walk" : "Survey", 1f);
                    if (d.length() < 11) move(d.normalize().negateLocal().multLocal(2.5f * dt));
                    face(d, dt * 2);
                    volleyT -= dt;
                    if (volleyT <= 0) {
                        volleyT = 2.6f;
                        Vector3f mouth = pos.add(0, 2.6f, 0).addLocal(d.normalize().mult(2.4f));
                        for (int k = -1; k <= 1; k++) {
                            Vector3f tgt = pl.add(0, host.playerHeight() * 0.55f, 0).addLocal(new Vector3f(-d.z, 0, d.x).normalizeLocal().mult(k * 1.8f));
                            shoot(this, mouth, tgt, 9, 1, new ColorRGBA(0.6f, 0.2f, 1f, 1), 0.32f, true);
                        }
                        host.sfx("roar", 1.5f);
                    }
                }
                case 20 -> { // erschöpft
                    play("Survey", 0.2f);
                    model.setLocalRotation(new Quaternion().fromAngleAxis(Math.min(1, stateT) * 0.35f, Vector3f.UNIT_Z));
                }
                default -> { }
            }
            // in der Arena bleiben
            Vector3f off = pos.subtract(arena).setY(0);
            if (off.length() > 22) pos.set(arena.add(off.normalize().mult(22)));
            pos.y = ground();
        }
    }

    // ---------------------------------------------------------------- Der Steinwächter (Kapitel 6)

    /**
     * Ein riesiger Wächter aus Stein mit leuchtendem Runenherz.
     * Er stampft (Schockwelle – spring!) und wirft Felsen. Heb mit Levitaris einen Trümmerstein und
     * schleudere ihn auf ihn: Er taumelt, sein Runenherz öffnet sich – jetzt Arcanum oder Ignis!
     */
    public final class Golem extends Boss {
        private final Node body = new Node("golemBody");
        private final Geometry core;
        private final Material coreMat;
        private final Node armL = new Node("armL"), armR = new Node("armR");
        private float throwT = 5, speed = 2.1f;
        private int coreHits;
        public Runnable onDefeat;

        public Golem(Vector3f at) {
            super(at, 6);
            title = "Der Steinwächter";
            name = title;
            radius = 2.2f;
            Material stone = G3.ph("mossy_rock", new ColorRGBA(0.75f, 0.72f, 0.8f, 1));
            Geometry torso = G3.geom("torso", G3.rock(11, 1.6f), stone);
            torso.setLocalScale(1.3f, 1.5f, 1f);
            torso.setLocalTranslation(0, 3.6f, 0);
            body.attachChild(torso);
            Geometry head = G3.geom("head", G3.rock(12, 0.7f), stone);
            head.setLocalTranslation(0, 5.9f, 0.2f);
            body.attachChild(head);
            for (int s = -1; s <= 1; s += 2) {
                Geometry eye = G3.geom("eye", new Sphere(6, 6, 0.13f), G3.emissive(new ColorRGBA(1f, 0.7f, 0.25f, 1)));
                eye.setLocalTranslation(s * 0.28f, 6.0f, 0.82f);
                body.attachChild(eye);
                Geometry leg = G3.geom("leg", G3.rock(20 + s, 0.75f), stone);
                leg.setLocalScale(0.9f, 1.8f, 0.9f);
                leg.setLocalTranslation(s * 0.9f, 1.2f, 0);
                body.attachChild(leg);
                Node arm = s < 0 ? armL : armR;
                Geometry upper = G3.geom("arm", G3.rock(30 + s, 0.65f), stone);
                upper.setLocalScale(0.9f, 1.9f, 0.9f);
                upper.setLocalTranslation(0, -1.2f, 0);
                arm.attachChild(upper);
                Geometry fist = G3.geom("fist", G3.rock(40 + s, 0.8f), stone);
                fist.setLocalTranslation(0, -2.7f, 0.1f);
                arm.attachChild(fist);
                arm.setLocalTranslation(s * 2.1f, 4.9f, 0);
                body.attachChild(arm);
            }
            coreMat = G3.litGlow(new ColorRGBA(1f, 0.6f, 0.2f, 1), new ColorRGBA(0.4f, 0.2f, 0.05f, 1));
            core = G3.geom("core", new Sphere(12, 12, 0.5f), coreMat);
            core.setLocalTranslation(0, 3.8f, 1.35f);
            body.attachChild(core);
            node.attachChild(body);
            pos.y = ground();
            state(0);
        }

        @Override
        Vector3f center() { return pos.add(0, 3.5f, 0); }

        @Override
        boolean pushable() { return false; }

        @Override
        public float bar() { return Math.max(0, (6 - coreHits) / 6f); }

        @Override
        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            if (state == 4 && damage(s, c, power) > 0 && s != Spell.SCHWEBE) {
                coreHits += c != null ? 2 : 1;
                flash = 0.3f;
                host.sfx("hit", 0.5f);
                world.burst(pos.add(0, 3.8f, 0), new ColorRGBA(1f, 0.6f, 0.2f, 1), 60);
                host.text("Das Runenherz bebt!  (" + Math.min(6, coreHits) + "/6)", center().add(0, 3, 0), new Color(255, 200, 120));
                if (coreHits >= 6) {
                    dead = true;
                    host.sfx("roar", 0.6f);
                    if (onDefeat != null) onDefeat.run();
                } else if (coreHits % 2 == 0) {
                    state(5);
                }
                return true;
            }
            if (s != Spell.SCHWEBE) host.text(state == 4 ? "Mehr Kraft: Arcanum oder Ignis!" : "Stein ist zu hart. Heb einen Trümmerstein mit Levitaris!", center().add(0, 3, 0), new Color(220, 210, 190));
            return true;
        }

        @Override
        void propHit(Prop p) {
            if (state == 4) return;
            state(4);
            host.sfx("roar", 0.8f);
            host.text("Er taumelt! Das Runenherz liegt offen – jetzt!", center().add(0, 3, 0), new Color(255, 220, 140));
        }

        @Override
        void reflectedHit(Orb o) { }

        @Override
        void update(float dt) {
            stateT += dt;
            Vector3f d = toPlayer();
            float walk = 0;
            coreMat.setColor("GlowColor", state == 4 ? new ColorRGBA(1f, 0.55f, 0.15f, 1).mult(0.8f + 0.4f * FastMath.sin(t * 10))
                : new ColorRGBA(0.25f, 0.12f, 0.03f, 1));
            switch (state) {
                case 0 -> { // auf dich zu
                    face(d, dt * 1.5f);
                    if (d.length() > 3.6f) { move(d.normalize().multLocal(speed * dt)); walk = speed; }
                    throwT -= dt;
                    if (d.length() < 4.2f) {
                        state(1);
                        host.text("Er hebt die Fäuste – spring über die Welle!", center().add(0, 3.4f, 0), new Color(255, 170, 120));
                    } else if (throwT <= 0 && d.length() > 8) {
                        throwT = 6;
                        state(2);
                    }
                }
                case 1 -> { // Stampfen
                    float lift = Math.min(1, stateT / 0.9f);
                    armL.setLocalRotation(new Quaternion().fromAngleAxis(-2.6f * lift, Vector3f.UNIT_X));
                    armR.setLocalRotation(new Quaternion().fromAngleAxis(-2.6f * lift, Vector3f.UNIT_X));
                    if (stateT > 0.9f) {
                        armL.setLocalRotation(Quaternion.IDENTITY);
                        armR.setLocalRotation(Quaternion.IDENTITY);
                        shockwave(pos.add(new Vector3f(FastMath.sin(yaw), 0, FastMath.cos(yaw)).mult(2.5f)), 14);
                        host.sfx("hit", 0.4f);
                        world.burst(pos.add(0, 0.4f, 0), new ColorRGBA(0.75f, 0.68f, 0.55f, 1), 80);
                        state(3);
                    }
                }
                case 2 -> { // Felswurf
                    face(d, dt * 3);
                    armR.setLocalRotation(new Quaternion().fromAngleAxis(-2.8f * Math.min(1, stateT / 0.8f), Vector3f.UNIT_X));
                    if (stateT > 0.8f) {
                        armR.setLocalRotation(Quaternion.IDENTITY);
                        Vector3f hand = pos.add(0, 6.5f, 0);
                        Vector3f tgt = host.player().add(0, 1, 0);
                        float dist = tgt.distance(hand);
                        float tt = dist / 14f;
                        Vector3f v = tgt.subtract(hand).divideLocal(tt);
                        v.y += 0.5f * 9 * tt;
                        Orb o = new Orb(this, hand, v, 1, new ColorRGBA(0.75f, 0.6f, 0.45f, 1), 0.55f, false);
                        o.arc = true;
                        o.geo.setMaterial(G3.ph("mossy_rock"));
                        orbs.add(o);
                        host.sfx("whoosh", 0.5f);
                        state(0);
                    }
                }
                case 3 -> { // verschnaufen
                    if (stateT > 1.6f) state(0);
                }
                case 4 -> { // taumelt, Herz offen
                    body.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.sin(t * 6) * 0.08f - 0.2f, Vector3f.UNIT_X));
                    if (stateT > 5.5f) { body.setLocalRotation(Quaternion.IDENTITY); state(0); }
                }
                case 5 -> { // wütend: schneller
                    body.setLocalRotation(Quaternion.IDENTITY);
                    speed += 0.6f;
                    host.sfx("roar", 0.7f);
                    host.text("Der Steinwächter wird wütend – und schneller!", center().add(0, 3.4f, 0), new Color(255, 150, 110));
                    state(0);
                }
                default -> { }
            }
            if (walk > 0) {
                float sw = FastMath.sin(t * 5) * 0.4f;
                armL.setLocalRotation(new Quaternion().fromAngleAxis(sw, Vector3f.UNIT_X));
                armR.setLocalRotation(new Quaternion().fromAngleAxis(-sw, Vector3f.UNIT_X));
                body.setLocalTranslation(0, Math.abs(FastMath.sin(t * 5)) * 0.15f, 0);
            }
            pos.y = ground();
        }
    }

    // ---------------------------------------------------------------- Der Namenlose (Finale)

    /**
     * Malrik, der sechste Gründer, als Schatten.
     * Phase 1: Schattenkugeln – mit Protego zurückwerfen (3×).
     * Phase 2: Er ruft seine Diener (die Saga lässt sie erscheinen) und ist unverwundbar.
     * Phase 3: Entzünde die fünf Gründerfeuer mit Ignis, während er angreift.
     * Phase 4: Sein Schild ist gebrochen – nur der Sternenblitz (Lumen, dann Arcanum) trifft ihn (3×).
     */
    public final class Shade extends Boss {
        final Figure fig;
        private final Vector3f[] spots;
        private int spot, reflected, starHits;
        private float volleyT = 2, blinkT;
        private final Geometry barrier;
        public Runnable onSummon, onBraziers, onDefeat;
        public boolean summonDone, firesLit;

        public Shade(Look look, Vector3f[] spots) {
            super(spots[0], 12);
            this.spots = spots;
            title = "Malrik, der Namenlose";
            name = title;
            radius = 1.0f;
            fig = new Figure(look);
            fig.hover = true;
            node.attachChild(fig.node);
            Material bm = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
            bm.setColor("Color", new ColorRGBA(0.5f, 0.2f, 0.9f, 0.18f));
            bm.setColor("GlowColor", new ColorRGBA(0.25f, 0.05f, 0.45f, 1));
            bm.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            bm.getAdditionalRenderState().setDepthWrite(false);
            barrier = G3.transparent(G3.geom("barrier", new Sphere(16, 20, 1.6f), bm));
            barrier.setLocalTranslation(0, 1.2f, 0);
            node.attachChild(barrier);
            com.jme3.effect.ParticleEmitter aura = G3.particles("shadeAura", 80, "Effects/Smoke/Smoke.png",
                new ColorRGBA(0.15f, 0.02f, 0.25f, 0.55f), new ColorRGBA(0, 0, 0, 0), 0.5f, 1.4f, 0.8f, 1.6f);
            aura.setImagesX(15);
            aura.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            aura.setShape(new com.jme3.effect.shapes.EmitterSphereShape(new Vector3f(0, 0.8f, 0), 0.6f));
            aura.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.8f, 0));
            aura.setParticlesPerSec(30);
            aura.setInWorldSpace(true);
            node.attachChild(aura);
            pos.y = ground() + 0.8f;
            state(0);
        }

        @Override
        Vector3f center() { return pos.add(0, 1.2f, 0); }

        @Override
        boolean pushable() { return false; }

        @Override
        public float bar() {
            return switch (phase) {
                case 0 -> 0.75f + (3 - reflected) / 12f;
                case 1 -> 0.75f;
                case 2 -> 0.5f;
                default -> 0.25f * (3 - starHits) / 3f + 0.0001f;
            };
        }

        @Override
        boolean hit(Spell s, Combo c, float power, Vector3f at) {
            if (phase == 3 && c == Combo.STERNENBLITZ) {
                starHits++;
                flash = 0.4f;
                host.sfx("roar", 1.2f);
                world.burst(center(), new ColorRGBA(1f, 0.95f, 0.7f, 1), 90);
                host.text("Der Sternenblitz trifft!  (" + starHits + "/3)", center().add(0, 2, 0), new Color(255, 240, 180));
                teleport();
                if (starHits >= 3) {
                    dead = true;
                    if (onDefeat != null) onDefeat.run();
                }
                return true;
            }
            String why = switch (phase) {
                case 0 -> "Sein Schattenschild schluckt jeden Zauber. Wirf seine Kugeln mit Protego zurück!";
                case 1 -> "Er ist unverwundbar, solange seine Diener kämpfen!";
                case 2 -> "Entzünde die fünf Gründerfeuer mit Ignis!";
                default -> "Nur Sternenlicht: erst Lumen, dann sofort Arcanum!";
            };
            host.text(why, center().add(0, 2, 0), new Color(210, 170, 255));
            return true;
        }

        @Override
        void reflectedHit(Orb o) {
            if (phase != 0) return;
            reflected++;
            flash = 0.4f;
            host.sfx("roar", 1.4f);
            host.text("Seine eigene Magie trifft ihn!  (" + reflected + "/3)", center().add(0, 2, 0), new Color(140, 255, 210));
            teleport();
            if (reflected >= 3) {
                phase = 1;
                bossHint = "Malrik ruft seine Diener! Besiege sie – Lumen gegen Irrgeister, Ignis gegen Dornen.";
                if (onSummon != null) onSummon.run();
            }
        }

        public void summonOver() {
            if (phase != 1) return;
            phase = 2;
            bossHint = "Entzünde die fünf Gründerfeuer mit Ignis – dann bricht sein Schild!";
            if (onBraziers != null) onBraziers.run();
        }

        public void firesDone() {
            if (phase != 2) return;
            phase = 3;
            barrier.setCullHint(Spatial.CullHint.Always);
            host.sfx("roar", 0.8f);
            bossHint = "Sein Schild ist gebrochen! Sternenblitz: Lumen, dann sofort Arcanum (3×).";
        }

        @Override
        void propHit(Prop p) { }

        private void teleport() {
            world.burst(center(), new ColorRGBA(0.5f, 0.2f, 0.9f, 1), 60);
            spot = (spot + 1 + rnd.nextInt(spots.length - 1)) % spots.length;
            pos.set(spots[spot]);
            blinkT = 0.5f;
            world.burst(center(), new ColorRGBA(0.5f, 0.2f, 0.9f, 1), 60);
        }

        @Override
        void update(float dt) {
            blinkT = Math.max(0, blinkT - dt);
            Vector3f d = toPlayer();
            face(d, dt * 3);
            pos.y = ground() + 0.8f + FastMath.sin(t * 1.6f) * 0.25f;
            fig.update(dt, 0, false);
            barrier.setLocalScale(1 + FastMath.sin(t * 3) * 0.04f);
            if (phase == 1) return;
            volleyT -= dt;
            if (volleyT <= 0 && blinkT <= 0) {
                volleyT = phase == 0 ? 2.4f : phase == 2 ? 3.2f : 2.8f;
                int n = phase == 0 ? 1 : phase == 2 ? 3 : 2;
                Vector3f hand = center().add(d.normalize().mult(1.2f)).addLocal(0, 0.4f, 0);
                for (int k = 0; k < n; k++) {
                    float off = (k - (n - 1) / 2f) * 1.6f;
                    Vector3f tgt = host.player().add(0, host.playerHeight() * 0.55f, 0).addLocal(new Vector3f(-d.z, 0, d.x).normalizeLocal().mult(off));
                    shoot(this, hand, tgt, phase == 0 ? 8.5f : 10, 1, new ColorRGBA(0.6f, 0.15f, 1f, 1), 0.3f, phase == 0);
                }
                fig.cast();
                host.sfx("spell", 0.55f);
            }
            if (phase == 3 && t % 7 < dt) teleport();
        }
    }
}
