package daemmerwald;

import com.jme3.effect.ParticleEmitter;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Zauberrätsel in der Welt: Jedes Rätsel braucht einen bestimmten Zauber aus dem Unterricht.
 * Dornenranken (Flamme), Felsbrocken (Schwebe), Schattenschleier (Licht), Eisweg über den Mondsee (Frost),
 * Feuerschalen (Flamme) und verschlossene Truhen (Öffnen) mit Belohnungen.
 */
public final class SpellPuzzles {
    public enum Kind { VINES, BOULDER, VEIL, ICE, BRAZIER, CHEST }

    public static final class Puzzle {
        public final String id, name, hint;
        public final Kind kind;
        public final Spell spell;
        public final Vector3f pos;
        public final float radius;
        /** Muss vorher gelöst sein (z. B. Ranken vor der Truhe). */
        public final String[] requires;
        Node node;
        Spatial movable;
        float anim;
        Puzzle(String id, String name, String hint, Kind kind, Spell spell, Vector3f pos, float radius, String... requires) {
            this.id = id; this.name = name; this.hint = hint; this.kind = kind; this.spell = spell; this.pos = pos; this.radius = radius; this.requires = requires;
        }
    }

    public final Node root = new Node("spellPuzzles");
    public final List<Puzzle> all = new ArrayList<>();
    public final List<PointLight> lights = new ArrayList<>();
    private final Random rnd = new Random(12);
    private Material wood, gold, vineM, thornM, rockM, stoneM, iceM;
    private Node icePath;
    private float t;

    /** Mondsee-Insel und Eisweg. */
    public static final float LX = WorldGen.LAKE[0], LZ = WorldGen.LAKE[1], ISLAND_R = 3.4f, ISLAND_Y = 0.75f, ICE_X0 = -96.5f, ICE_X1 = LX + ISLAND_R - 0.5f;

    public SpellPuzzles() {
        wood = G3.ph("brown_planks_05");
        gold = G3.lit(new ColorRGBA(0.95f, 0.75f, 0.3f, 1));
        gold.setColor("Specular", ColorRGBA.White);
        gold.setFloat("Shininess", 60);
        vineM = G3.lit(new ColorRGBA(0.12f, 0.2f, 0.08f, 1));
        thornM = G3.lit(new ColorRGBA(0.35f, 0.28f, 0.2f, 1));
        rockM = G3.ph("mossy_rock");
        stoneM = G3.ph("mossy_stone_wall");
        iceM = G3.lit(new ColorRGBA(0.75f, 0.92f, 1f, 0.75f));
        iceM.setColor("Specular", ColorRGBA.White);
        iceM.setFloat("Shininess", 90);
        iceM.setColor("GlowColor", new ColorRGBA(0.05f, 0.12f, 0.18f, 1));
        iceM.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        // 1) Dornenranken + Truhe beim Palast
        Vector3f c1 = ground(24, -181);
        add(new Puzzle("ranken1", "Dornenranken am Palast", "Dichte Dornenranken umschlingen eine Truhe.", Kind.VINES, Spell.FLAMME, c1, 2.4f), this::vines);
        add(new Puzzle("truhe1", "Truhe hinter den Ranken", "Eine verschlossene Truhe mit Messingschloss.", Kind.CHEST, Spell.OEFFNEN, c1, 1.6f, "ranken1"), this::chest);
        // 2) Felsbrocken im Steinbruch
        Vector3f c2 = ground(112, -33);
        add(new Puzzle("truhe2", "Truhe unter dem Felsen", "Unter dem Felsen schaut eine Truhenecke hervor.", Kind.CHEST, Spell.OEFFNEN, c2, 1.6f, "fels1"), this::chest);
        add(new Puzzle("fels1", "Schwerer Felsbrocken", "Ein riesiger Felsbrocken. Unter ihm glitzert etwas.", Kind.BOULDER, Spell.SCHWEBE, c2, 2.4f), this::boulder);
        // 3) Schattenschleier im Schattenhain
        Vector3f c3 = ground(-118, -88);
        add(new Puzzle("schleier1", "Schattenschleier", "Ein wabernder Schattenschleier versperrt einen Steinbogen.", Kind.VEIL, Spell.LICHT, c3, 2.4f), this::veil);
        add(new Puzzle("truhe3", "Truhe im Steinbogen", "Hinter dem Bogen steht eine alte Truhe.", Kind.CHEST, Spell.OEFFNEN, c3.add(0, 0, -2.2f), 1.6f, "schleier1"), this::chest);
        // 4) Eisweg zur Mondsee-Insel
        Vector3f c4 = ground(-95, LZ);
        add(new Puzzle("eis1", "Frostkristall am Mondsee", "Ein Frostkristall am Ufer. Draußen auf der Insel glänzt eine Truhe.", Kind.ICE, Spell.FROST, c4, 3.0f), this::frostMarker);
        add(new Puzzle("truhe4", "Truhe auf der Mondsee-Insel", "Die Truhe auf der kleinen Insel.", Kind.CHEST, Spell.OEFFNEN, new Vector3f(LX, ISLAND_Y, LZ), 1.6f, "eis1"), this::chest);
        // 5) Drei Feuerschalen auf der Sonnenwiese
        float mx = WorldGen.MEADOW[0], mz = WorldGen.MEADOW[1];
        for (int i = 0; i < 3; i++) {
            float a = i * FastMath.TWO_PI / 3 + 0.3f;
            add(new Puzzle("schale" + (i + 1), "Kalte Feuerschale", "Eine kalte, steinerne Feuerschale.", Kind.BRAZIER, Spell.FLAMME, ground(mx + FastMath.sin(a) * 5.5f, mz + FastMath.cos(a) * 5.5f), 1.8f), this::brazier);
        }
        add(new Puzzle("truhe5", "Truhe der drei Feuer", "Wenn alle drei Schalen brennen, steigt etwas aus der Erde.", Kind.CHEST, Spell.OEFFNEN, ground(mx, mz), 1.6f, "schale1", "schale2", "schale3"), this::chest);
        island();
        G3.tangents(root);
        root.setShadowMode(ShadowMode.CastAndReceive);
    }

    private static Vector3f ground(float x, float z) { return new Vector3f(x, WorldGen.height(x, z), z); }

    private void add(Puzzle p, java.util.function.Consumer<Puzzle> builder) {
        p.node = new Node(p.id);
        p.node.setLocalTranslation(p.pos);
        builder.accept(p);
        root.attachChild(p.node);
        all.add(p);
    }

    // ================================================================ Modelle

    private Geometry geo(Node n, com.jme3.scene.Mesh mesh, Material m, float x, float y, float z) {
        Geometry g = new Geometry("g", mesh);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        n.attachChild(g);
        return g;
    }

    private void chest(Puzzle p) {
        Node n = p.node;
        geo(n, new Box(0.55f, 0.3f, 0.36f), wood, 0, 0.3f, 0);
        for (int s = -1; s <= 1; s += 2) geo(n, new Box(0.04f, 0.31f, 0.37f), gold, s * 0.4f, 0.3f, 0);
        Node lid = new Node("lid");
        Geometry top = geo(lid, new Cylinder(2, 14, 0.36f, 1.1f, true), wood, 0, 0, 0);
        top.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
        top.setLocalScale(1, 0.55f, 1);
        lid.setLocalTranslation(0, 0.62f, 0);
        n.attachChild(lid);
        geo(n, new Box(0.08f, 0.1f, 0.03f), gold, 0, 0.5f, 0.38f);
        p.movable = lid;
        if (p.id.equals("truhe5")) n.move(0, -1.4f, 0); // steigt erst aus der Erde
        if (p.id.equals("truhe2")) n.setLocalRotation(new Quaternion().fromAngleAxis(0.4f, Vector3f.UNIT_Y));
    }

    private void vines(Puzzle p) {
        Node n = new Node("vineCage");
        for (int i = 0; i < 14; i++) {
            float a = i * FastMath.TWO_PI / 14 + rnd.nextFloat() * 0.2f;
            float r = 1.15f + rnd.nextFloat() * 0.3f;
            Geometry v = geo(n, new Cylinder(2, 6, 0.07f, 0.03f, 1.9f + rnd.nextFloat() * 0.8f, true, false), vineM, FastMath.sin(a) * r, 0.9f, FastMath.cos(a) * r);
            v.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI + (rnd.nextFloat() - 0.5f) * 0.6f, a, (rnd.nextFloat() - 0.5f) * 0.6f));
            for (int k = 0; k < 4; k++) {
                Geometry th = geo(n, new Cylinder(2, 4, 0.025f, 0.001f, 0.14f, true, false), thornM,
                    FastMath.sin(a) * (r + 0.06f), 0.3f + k * 0.45f, FastMath.cos(a) * (r + 0.06f));
                th.setLocalRotation(new Quaternion().fromAngles(0, a, 0));
            }
        }
        for (int i = 0; i < 5; i++) {
            Geometry ring = geo(n, new com.jme3.scene.shape.Torus(16, 6, 0.06f, 1.25f), vineM, 0, 0.3f + i * 0.45f, 0);
            ring.setLocalRotation(new Quaternion().fromAngles(FastMath.HALF_PI + (rnd.nextFloat() - 0.5f) * 0.4f, 0, (rnd.nextFloat() - 0.5f) * 0.4f));
        }
        p.node.attachChild(n);
        p.movable = n;
    }

    private void boulder(Puzzle p) {
        Geometry b = new Geometry("boulder", G3.rock(77, 1.8f));
        b.setMaterial(rockM);
        b.setLocalTranslation(0, 0.9f, 0);
        b.setLocalScale(1, 0.8f, 1);
        p.node.attachChild(b);
        p.movable = b;
    }

    private void veil(Puzzle p) {
        Node n = p.node;
        for (int s = -1; s <= 1; s += 2) geo(n, new Box(0.35f, 1.9f, 0.35f), stoneM, s * 1.5f, 1.9f, -0.8f);
        geo(n, new Box(1.9f, 0.3f, 0.4f), stoneM, 0, 3.95f, -0.8f);
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random r = new Random(5);
        for (int i = 0; i < 160; i++) {
            int a = 30 + r.nextInt(60);
            g.setColor(new Color(20 + r.nextInt(30), 0, 40 + r.nextInt(40), a));
            int s = 30 + r.nextInt(90);
            g.fillOval(r.nextInt(256) - s / 2, r.nextInt(256) - s / 2, s, s);
        }
        g.dispose();
        Material vm = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        Texture tx = G3.tex(img);
        tx.setWrap(Texture.WrapMode.Repeat);
        vm.setTexture("ColorMap", tx);
        vm.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        vm.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        Node veil = new Node("veil");
        for (int k = 0; k < 3; k++) {
            Geometry q = geo(veil, new Quad(2.7f, 3.6f), vm, -1.35f, 0.05f, -0.8f + k * 0.08f);
            q.setQueueBucket(RenderQueue.Bucket.Transparent);
            q.setShadowMode(ShadowMode.Off);
        }
        n.attachChild(veil);
        p.movable = veil;
    }

    private void frostMarker(Puzzle p) {
        Geometry c = geo(p.node, new Sphere(4, 6, 0.5f), G3.litGlow(new ColorRGBA(0.6f, 0.9f, 1f, 1), new ColorRGBA(0.3f, 0.6f, 0.8f, 1)), 0, 1.6f, 0);
        c.setLocalScale(1, 2.4f, 1);
        geo(p.node, new Cylinder(2, 10, 0.5f, 0.6f, 0.6f, true, false), stoneM, 0, 0.3f, 0).setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        p.movable = c;
        icePath = new Node("icePath");
        for (float x = ICE_X0; x > ICE_X1; x -= 1.15f) {
            Geometry tile = geo(icePath, new Box(0.6f, 0.12f, 1.25f + rnd.nextFloat() * 0.2f), iceM, x - p.pos.x, WorldGen.WATER + 0.12f - p.pos.y, (rnd.nextFloat() - 0.5f) * 0.3f);
            tile.setLocalRotation(new Quaternion().fromAngleAxis((rnd.nextFloat() - 0.5f) * 0.12f, Vector3f.UNIT_Y));
            tile.setQueueBucket(RenderQueue.Bucket.Transparent);
        }
        icePath.setCullHint(Spatial.CullHint.Always);
        p.node.attachChild(icePath);
    }

    private void brazier(Puzzle p) {
        com.jme3.scene.Mesh bowl = Village.lathe(new float[]{0.2f, 0.25f, 0.2f, 0.7f, 0.75f}, new float[]{0, 0.1f, 0.9f, 1.15f, 1.3f}, 16, 2);
        geo(p.node, bowl, stoneM, 0, 0, 0);
        geo(p.node, new Cylinder(2, 14, 0.65f, 0.04f, true), G3.lit(new ColorRGBA(0.1f, 0.08f, 0.07f, 1)), 0, 1.24f, 0).setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
    }

    private void island() {
        Node n = new Node("island");
        Geometry base = geo(n, new Cylinder(2, 24, ISLAND_R, ISLAND_R + 0.6f, 6f, true, false), rockM, 0, ISLAND_Y - 3f, 0);
        base.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        Geometry grass = geo(n, new Cylinder(2, 24, ISLAND_R - 0.05f, ISLAND_R - 0.05f, 0.1f, true, false), G3.lit(new ColorRGBA(0.3f, 0.48f, 0.2f, 1)), 0, ISLAND_Y, 0);
        grass.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        for (int i = 0; i < 6; i++) {
            Spatial f = G3.model("fern_02");
            float a = i * 1.1f;
            f.setLocalTranslation(FastMath.sin(a) * 2.3f, ISLAND_Y, FastMath.cos(a) * 2.3f);
            f.setLocalScale(0.8f);
            n.attachChild(f);
        }
        n.setLocalTranslation(LX, 0, LZ);
        root.attachChild(n);
    }

    // ================================================================ Zustand

    public static boolean solved(GameState st, String id) { return st.flags.contains("puzzle:" + id); }

    public boolean ready(GameState st, Puzzle p) {
        for (String r : p.requires) if (!solved(st, r)) return false;
        return true;
    }

    /** Sichtbarer Zustand nach dem Laden (ohne Animation). */
    public void apply(GameState st) {
        for (Puzzle p : all) if (solved(st, p.id)) show(p, true);
        for (Puzzle p : all) if (p.kind == Kind.CHEST && ready(st, p) && p.id.equals("truhe5")) p.node.setLocalTranslation(p.pos);
    }

    private void show(Puzzle p, boolean instant) {
        switch (p.kind) {
            case VINES, VEIL -> { if (p.movable != null) p.movable.removeFromParent(); }
            case BOULDER -> { p.anim = instant ? 1 : 0.001f; if (instant) p.movable.setLocalTranslation(0, 4.8f, 0); }
            case ICE -> icePath.setCullHint(Spatial.CullHint.Inherit);
            case BRAZIER -> {
                ParticleEmitter fire = G3.particles("brazierFire", 40, "Effects/Explosion/flame.png", new ColorRGBA(1f, 0.6f, 0.2f, 1),
                    new ColorRGBA(1f, 0.15f, 0.05f, 0), 0.5f, 0.1f, 0.4f, 0.9f);
                fire.setImagesX(2);
                fire.setImagesY(2);
                fire.setSelectRandomImage(true);
                fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 1.6f, 0));
                fire.getParticleInfluencer().setVelocityVariation(0.25f);
                fire.setParticlesPerSec(40);
                fire.setLocalTranslation(0, 1.3f, 0);
                p.node.attachChild(fire);
                PointLight l = new PointLight(p.pos.add(0, 2.2f, 0), new ColorRGBA(1f, 0.6f, 0.3f, 1).mult(1.4f), 9);
                lights.add(l);
                root.addLight(l);
            }
            case CHEST -> {
                p.movable.setLocalRotation(new Quaternion().fromAngleAxis(-1.9f, Vector3f.UNIT_X));
                p.movable.setLocalTranslation(0, 0.62f, -0.36f);
            }
        }
    }

    /** Ergebnis eines Zaubers auf ein Rätsel. */
    public record Cast(Puzzle puzzle, boolean wrongSpell, boolean notReady) { }

    /** Zauber trifft (oder steht nahe) – löst ein Rätsel, wenn Zauber und Reihenfolge stimmen. */
    public Cast cast(GameState st, Spell s, Vector3f target, Vector3f player) {
        Puzzle best = null;
        float bd = 1e9f;
        for (Puzzle p : all) {
            if (solved(st, p.id)) continue;
            float d = Math.min(flat(p.pos, target), flat(p.pos, player) - 1.2f);
            if (d < p.radius + 1.2f && d < bd) { bd = d; best = p; }
        }
        if (best == null) return null;
        if (best.spell != s) {
            // gibt es ein anderes ungelöstes Rätsel hier, das genau diesen Zauber braucht?
            for (Puzzle p : all) if (!solved(st, p.id) && p.spell == s && flat(p.pos, best.pos) < 0.5f) { best = p; break; }
            if (best.spell != s) return new Cast(best, true, false);
        }
        if (!ready(st, best)) return new Cast(best, false, true);
        st.flags.add("puzzle:" + best.id);
        show(best, false);
        // Truhe der drei Feuer steigt auf, wenn alle Schalen brennen
        for (Puzzle p : all) if (p.id.equals("truhe5") && ready(st, p)) p.anim = 0.001f;
        return new Cast(best, false, false);
    }

    private static float flat(Vector3f a, Vector3f b) { return FastMath.sqrt(FastMath.sqr(a.x - b.x) + FastMath.sqr(a.z - b.z)); }

    /** Nächstes Rätsel in der Nähe (für Hinweise). */
    public Puzzle near(GameState st, Vector3f pos, float r) {
        Puzzle best = null;
        float bd = r;
        for (Puzzle p : all) {
            if (solved(st, p.id)) continue;
            if (!ready(st, p) && p.kind == Kind.CHEST) continue;
            float d = flat(p.pos, pos) - p.radius;
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** Hindernisse: ungelöste Ranken, Felsen, Schleier; Truhen und Schalen immer. */
    public boolean blocked(GameState st, float x, float z, float r) {
        for (Puzzle p : all) {
            float rad = switch (p.kind) {
                case VINES, VEIL -> solved(st, p.id) ? 0 : 1.45f;
                case BOULDER -> solved(st, p.id) ? 0 : 1.9f;
                case CHEST -> 0.6f;
                case BRAZIER -> 0.8f;
                case ICE -> 0.6f;
            };
            if (rad <= 0) continue;
            if (FastMath.sqr(x - p.pos.x) + FastMath.sqr(z - p.pos.z) < FastMath.sqr(rad + r)) return true;
        }
        return false;
    }

    /** Eisweg und Insel: begehbare Höhe über dem Wasser (oder NaN). */
    public float walkHeight(GameState st, float x, float z) {
        if (FastMath.sqr(x - LX) + FastMath.sqr(z - LZ) < ISLAND_R * ISLAND_R) return ISLAND_Y;
        if (solved(st, "eis1") && Math.abs(z - LZ) < 1.4f && x < ICE_X0 + 0.6f && x > ICE_X1 - 0.6f) return WorldGen.WATER + 0.24f;
        return Float.NaN;
    }

    public void update(float dt) {
        t += dt;
        for (Puzzle p : all) {
            if (p.kind == Kind.BOULDER && p.anim > 0 && p.anim < 1) {
                p.anim = Math.min(1, p.anim + dt * 0.5f);
                p.movable.setLocalTranslation(0, 0.9f + 3.9f * G3.smooth(0, 1, p.anim), 0);
            }
            if (p.kind == Kind.BOULDER && p.anim >= 1) {
                p.movable.setLocalTranslation(0, 4.8f + FastMath.sin(t * 1.2f) * 0.25f, 0);
                p.movable.setLocalRotation(new Quaternion().fromAngleAxis(t * 0.3f, Vector3f.UNIT_Y));
            }
            if (p.kind == Kind.VEIL && p.movable != null && p.movable.getParent() != null) {
                p.movable.setLocalScale(1 + FastMath.sin(t * 1.7f) * 0.03f, 1 + FastMath.sin(t * 1.3f + 1) * 0.04f, 1);
            }
            if (p.kind == Kind.ICE && p.movable != null) p.movable.setLocalRotation(new Quaternion().fromAngleAxis(t * 0.8f, Vector3f.UNIT_Y));
            if (p.id.equals("truhe5") && p.anim > 0 && p.anim < 1) {
                p.anim = Math.min(1, p.anim + dt * 0.4f);
                p.node.setLocalTranslation(p.pos.x, p.pos.y - 1.4f + 1.4f * G3.smooth(0, 1, p.anim), p.pos.z);
            }
        }
    }
}
