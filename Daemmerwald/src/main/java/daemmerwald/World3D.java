package daemmerwald;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.texture.Texture;
import com.jme3.water.WaterFilter;

import java.awt.image.BufferedImage;
import java.util.*;

/** Die 3D-Welt: Gelände, Himmel, Wetter, Wasser, Bauwerke, Figuren und Lichter. */
public class World3D {
    public final Node root = new Node("world");
    public final DayNight dayNight;
    public final Weather weather;
    public final Chunks chunks;
    public final Vista vista;
    public final Ambience ambience;
    private com.jme3.post.filters.LightScatteringFilter sunRays;
    public final Map<String, Figure> figures = new HashMap<>();
    public final Map<String, Vector3f> npcPos = new HashMap<>();
    public Figure corvina, waitress;
    /** Begehbare Häuser der Story-Region. */
    public final List<House> houses = Chunks.STORY_HOUSES;
    public final PointLight lantern = new PointLight();

    private final Main app;
    private final List<float[]> statics = new ArrayList<>();
    private final List<Spatial> blightWall = new ArrayList<>();
    private final List<float[]> blightObst = new ArrayList<>();
    private boolean blightCleared;
    private final Map<String, Geometry> markers = new HashMap<>();
    private final List<PointLight> nightLights = new ArrayList<>();
    private final List<ColorRGBA> nightLightBase = new ArrayList<>();
    private ParticleEmitter fireflies;
    private final List<ParticleEmitter> bursts = new ArrayList<>();
    private SkyFogFilter fog;
    private DirectionalLightShadowFilter shadows;
    private WaterFilter water;
    // Zweite Kamera für den geteilten Bildschirm (Spieler 2)
    public Camera cam2;
    /** Die aktuelle Figur von Spieler 2 (damit beim Neuladen keine alte Figur stehen bleibt). */
    Mate mate;
    private com.jme3.renderer.ViewPort vp2;
    private WaterFilter water2;
    private SkyFogFilter fog2;
    private DirectionalLightShadowFilter shadows2;
    private boolean split;
    private FilterPostProcessor fpp1, fpp2;
    private GradeFilter grade, grade2;
    private Material heartLeaves, heartVeins;
    private PointLight heartLight;
    private Geometry crystal;
    private float t;
    private float zoneDark, zoneBright;

    // Café
    public final Vector3f cafeSeat = new Vector3f(), cafeLook = new Vector3f(), cafeDoor = new Vector3f();
    private Node cafeCup;
    private Geometry cafeCat;
    private PointLight cafeLight;

    public static boolean lowGraphics = Boolean.getBoolean("dw.low");

    public World3D(Main app) {
        this.app = app;
        G3.am = app.getAssetManager();
        app.getRootNode().attachChild(root);
        dayNight = new DayNight(root);
        weather = new Weather(root);
        chunks = new Chunks(root);
        vista = new Vista(root);
        ambience = new Ambience(root);
        lantern.setColor(new ColorRGBA(1f, 0.78f, 0.5f, 1));
        lantern.setRadius(14);
        root.addLight(lantern);

        buildAcademy();
        buildHeart();
        buildQuarry();
        buildSwampHut();
        buildMushroomRing();
        buildBlightWall();
        buildShadowGrove();
        buildMeadow();
        buildLakeCourt();
        buildCafe();
        buildShop();
        for (House h : houses) {
            root.attachChild(h.buildExterior());
            solid(h.x, h.z, h.radius());
        }
        setHomeLevel(0);
        palace = new Palace();
        root.attachChild(palace.exterior);
        root.attachChild(palace.exteriorExtras);
        // das große Schloss: Hauptbau, Flügel, Türme, Bergfried
        float[][] cs = {{0, -1, 14}, {-10, 0, 10}, {10, 0, 10}, {-25, -2, 8.5f}, {25, -2, 8.5f}, {-32, -2, 4.4f}, {32, -2, 4.4f},
            {-18, 13, 4.8f}, {18, 13, 4.8f}, {-18, -13, 4.8f}, {18, -13, 4.8f}, {-7.5f, 13.5f, 3.2f}, {7.5f, 13.5f, 3.2f},
            {0, -21, 12.5f}, {-12, -30, 4.6f}, {12, -30, 4.6f}};
        for (float[] c : cs) solid(Palace.PX + c[0], Palace.PZ + c[1], c[2]);
        puzzles = new SpellPuzzles();
        root.attachChild(puzzles.root);
        street = new MarketStreet();
        root.attachChild(street.root);
        for (float[] v : street.solids) solid(v[0], v[1], v[2]);
        for (com.jme3.light.PointLight l : street.lights) root.addLight(l);
        houses.addAll(street.shops);
        village = new Village();
        root.attachChild(village.root);
        for (float[] v : village.solids) solid(v[0], v[1], v[2]);
        for (com.jme3.light.PointLight l : village.lights) root.addLight(l);
        G3.tangents(root);
        buildNpcs();
        buildParticles();
        setupFilters();
    }

    // ================================================================ Hilfen

    public Village village;
    public MarketStreet street;
    public Palace palace;
    public SpellPuzzles puzzles;
    public final Cards cards = new Cards();
    /** Aktueller Spielstand (für Rätsel-Zustände), wird von der Weltszene gesetzt. */
    public GameState state;
    /** Spieler ist im Inneren des Zauberpalasts. */
    public boolean inPalace;
    private float fallT;

    public void setPalace(boolean inside) {
        if (inside == inPalace) return;
        inPalace = inside;
        if (inside) root.attachChild(palace.interior);
        else palace.interior.removeFromParent();
    }

    /** Bodenhöhe für Figuren: Gelände – oder die Brücke im Feendorf. */
    public float height(float x, float z) {
        if (inPalace) {
            float f = palace.floorAt(x, z);
            if (!Float.isNaN(f)) return f;
        }
        float hg = WorldGen.height(x, z);
        if (puzzles != null && state != null) {
            float w = puzzles.walkHeight(state, x, z);
            if (!Float.isNaN(w) && w > hg) return w;
        }
        if (village != null) {
            float d = village.deckHeight(x, z);
            if (!Float.isNaN(d) && d > hg) return d;
        }
        return hg;
    }

    public Vector3f ground(float x, float z) { return new Vector3f(x, height(x, z), z); }

    private void solid(float x, float z, float r) { statics.add(new float[]{x, z, r}); }

    /** Hindernis für die Geschichte (Steine im Hain, Laternen im Dorf). */
    public void addSolid(float x, float z, float r) { solid(x, z, r); }

    // ---------------------------------------------------------------- Eigenes Haus

    private com.jme3.scene.Node homeNode;
    private int homeLevel = -1;
    private final float[] homeSolid = new float[]{Home.X, Home.Z, 0};

    /** Zeigt das eigene Haus in der passenden Ausbaustufe (baut es bei Änderung neu). */
    public void setHomeLevel(int level) {
        if (level == homeLevel) return;
        homeLevel = level;
        if (homeNode != null) homeNode.removeFromParent();
        homeNode = Home.build(level);
        root.attachChild(homeNode);
        homeSolid[2] = Home.radius(level);
        if (!statics.contains(homeSolid)) statics.add(homeSolid);
    }

    public int homeLevel() { return homeLevel; }

    public boolean blocked(float x, float z, float r) {
        return height(x, z) < WorldGen.WATER - 1.3f || blockedIgnoringWater(x, z, r);
    }

    public boolean blockedIgnoringWater(float x, float z, float r) {
        if (inPalace) return palace.blocked(x, z, r);
        if (puzzles != null && state != null && puzzles.blocked(state, x, z, r)) return true;
        for (float[] o : statics) {
            float dx = x - o[0], dz = z - o[1], rr = r + o[2];
            if (dx * dx + dz * dz < rr * rr) return true;
        }
        if (!blightCleared) {
            for (float[] o : blightObst) {
                float dx = x - o[0], dz = z - o[1], rr = r + o[2];
                if (dx * dx + dz * dz < rr * rr) return true;
            }
        }
        return chunks.blocked(x, z, r);
    }

    private PointLight light(Vector3f pos, ColorRGBA c, float radius, boolean nightOnly) {
        PointLight l = new PointLight(pos, c, radius);
        root.addLight(l);
        if (nightOnly) {
            nightLights.add(l);
            nightLightBase.add(c.clone());
        }
        return l;
    }

    private Geometry add(Node parent, Geometry g, float x, float y, float z) {
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    private static Texture2DRepeat repeat(String path) { return new Texture2DRepeat(path); }

    /** Kleiner Wrapper, damit Texturen wiederholt werden. */
    private static final class Texture2DRepeat {
        final Texture tex;
        Texture2DRepeat(String path) {
            tex = G3.am.loadTexture(path);
            tex.setWrap(Texture.WrapMode.Repeat);
        }
    }

    private static Material texMat(String path, ColorRGBA tint, Vector2f scaleHint) {
        return G3.texture(path, tint);
    }

    private static Mesh scaled(Mesh m, float u, float v) {
        m.scaleTextureCoordinates(new Vector2f(u, v));
        return m;
    }

    private static Geometry cylY(String name, float r1, float r2, float h, int sides, Material m) {
        Geometry g = G3.geom(name, new Cylinder(2, sides, r1, r2, h, true, false), m);
        g.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        return g;
    }

    // ================================================================ Bauwerke

    private void buildAcademy() {
        Node n = new Node("academy");
        float x = WorldGen.ACADEMY[0], z = WorldGen.ACADEMY[1], y = height(x, z);
        Material bark = texMat("Models/Tree/BarkColor.jpg", new ColorRGBA(0.62f, 0.52f, 0.46f, 1), null);
        Texture bn = G3.am.loadTexture("Models/Tree/BarkNormal.jpg");
        bn.setWrap(Texture.WrapMode.Repeat);
        bark.setTexture("NormalMap", bn);
        Material stone = G3.litTex("Textures/Terrain/BrickWall/BrickWall.jpg", new ColorRGBA(0.75f, 0.72f, 0.7f, 1), "Textures/Terrain/BrickWall/BrickWall_normal.jpg");
        Geometry trunk = G3.geom("academyTrunk", scaled(new Cylinder(2, 40, 15, 10, 70, false, false), 26, 12), bark);
        trunk.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        add(n, trunk, x, y + 35, z);
        Geometry base = G3.geom("academyBase", scaled(new Cylinder(2, 40, 18.5f, 18, 7, true, false), 16, 1), stone);
        base.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        add(n, base, x, y + 3.4f, z);
        // Wurzeln
        for (int i = 0; i < 9; i++) {
            float a = i * FastMath.TWO_PI / 9 + 0.2f;
            Geometry root = G3.geom("root", new Cylinder(2, 10, 2.6f, 0.6f, 16, true, false), bark);
            root.setLocalTranslation(x + FastMath.cos(a) * 19, y + 1.2f, z + FastMath.sin(a) * 19);
            root.setLocalRotation(new Quaternion().fromAngles(0, -a + FastMath.HALF_PI, 0).mult(new Quaternion().fromAngles(0.15f, 0, 0)));
            n.attachChild(root);
        }
        // Fenster
        Material win = G3.emissive(new ColorRGBA(1f, 0.72f, 0.38f, 1));
        Random r = new Random(4);
        for (int i = 0; i < 46; i++) {
            float a = r.nextFloat() * FastMath.TWO_PI, wy = 10 + r.nextFloat() * 52;
            float rad = 15 - (wy - 0) / 70 * 5 + 0.05f;
            Geometry w = G3.geom("window", new Box(0.55f, 0.95f, 0.08f), win);
            w.setLocalTranslation(x + FastMath.cos(a) * rad, y + wy, z + FastMath.sin(a) * rad);
            w.setLocalRotation(new Quaternion().fromAngleAxis(-a + FastMath.HALF_PI, Vector3f.UNIT_Y));
            n.attachChild(w);
        }
        // Tor
        Material door = G3.lit(new ColorRGBA(0.18f, 0.11f, 0.07f, 1));
        add(n, G3.geom("door", new Box(2.4f, 3.6f, 0.4f), door), x, y + 3.6f, z + 18.6f);
        add(n, G3.geom("doorGlow", new Box(1.8f, 2.9f, 0.05f), G3.emissive(new ColorRGBA(0.9f, 0.6f, 0.3f, 1))), x, y + 3.0f, z + 19.05f);
        // Krone
        Spatial crown = G3.am.loadModel("Models/Tree/Tree.mesh.j3o");
        crown.setLocalScale(15);
        crown.setLocalTranslation(x, y + 40, z);
        n.attachChild(crown);
        // Terrasse mit Lesepulten
        Geometry terrace = G3.geom("terrace", scaled(new Cylinder(2, 40, 13, 13, 0.4f, true, false), 10, 1), stone);
        terrace.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        add(n, terrace, x, height(x, z + 26) + 0.05f, z + 24);
        Material wood = texMat("Models/Tree/BarkColor.jpg", new ColorRGBA(0.6f, 0.45f, 0.35f, 1), null);
        for (int side = -1; side <= 1; side += 2) {
            float lx = x + side * 6, lz = z + 22;
            float ly = height(lx, lz);
            add(n, G3.geom("lectern", new Box(0.5f, 0.6f, 0.4f), wood), lx, ly + 0.6f, lz);
            add(n, G3.geom("book", new Box(0.35f, 0.05f, 0.25f), G3.litGlow(new ColorRGBA(0.6f, 0.15f, 0.12f, 1), new ColorRGBA(0.2f, 0.1f, 0.02f, 1))), lx, ly + 1.25f, lz);
            lampPost(n, x + side * 4.5f, z + 30);
        }
        light(new Vector3f(x, y + 5, z + 24), new ColorRGBA(1f, 0.7f, 0.4f, 1).multLocal(1.4f), 26, true);
        n.setShadowMode(ShadowMode.CastAndReceive);
        tangents(n);
        root.attachChild(n);
        solid(x, z, 19.5f);
        for (int i = 0; i < 9; i++) {
            float a = i * FastMath.TWO_PI / 9 + 0.2f;
            solid(x + FastMath.cos(a) * 21, z + FastMath.sin(a) * 21, 2.2f);
        }
        // Laternen entlang des Weges
        for (int i = 0; i < 6; i++) lampPost(root, (i % 2 == 0 ? -3.5f : 3.5f), -80 + i * 13);
    }

    /** Tangenten für Normal-Maps (nur für statische Geometrie ohne Partikel). */
    private static void tangents(Spatial s) {
        s.depthFirstTraversal(sp -> {
            if (sp instanceof Geometry g && !(g instanceof com.jme3.effect.ParticleEmitter) && g.getMesh().getBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord) != null
                && g.getMesh().getBuffer(com.jme3.scene.VertexBuffer.Type.Tangent) == null) {
                com.jme3.util.mikktspace.MikktspaceTangentGenerator.generate(g);
            }
        });
    }

    private void lampPost(Node parent, float x, float z) {
        float y = height(x, z);
        Material iron = G3.lit(new ColorRGBA(0.12f, 0.12f, 0.13f, 1));
        Geometry post = cylY("post", 0.08f, 0.08f, 3.2f, 8, iron);
        post.setLocalTranslation(x, y + 1.6f, z);
        parent.attachChild(post);
        Geometry glass = G3.geom("lamp", new Sphere(8, 10, 0.25f), G3.emissive(new ColorRGBA(1f, 0.78f, 0.45f, 1)));
        glass.setLocalTranslation(x, y + 3.3f, z);
        parent.attachChild(glass);
        solid(x, z, 0.3f);
    }

    private void buildHeart() {
        float x = WorldGen.HEART[0], z = WorldGen.HEART[1], y = height(x, z);
        Spatial tree = G3.am.loadModel("Models/Tree/Tree.mesh.j3o");
        tree.depthFirstTraversal(s -> {
            if (s instanceof Geometry g) {
                Material m = g.getMaterial().clone();
                if (String.valueOf(m.getParam("DiffuseMap")).contains("Leaves")) {
                    m.setBoolean("UseMaterialColors", true);
                    heartLeaves = m;
                    m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
                } else {
                    m.setBoolean("UseMaterialColors", true);
                    m.setColor("Diffuse", new ColorRGBA(0.75f, 0.62f, 0.7f, 1));
                    m.setColor("Ambient", new ColorRGBA(0.75f, 0.62f, 0.7f, 1));
                    heartVeins = m;
                }
                g.setMaterial(m);
            }
        });
        tree.setLocalScale(10.5f);
        tree.setLocalTranslation(x, y - 0.5f, z);
        tree.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(tree);
        heartLight = light(new Vector3f(x, y + 6, z + 6), new ColorRGBA(0.7f, 0.3f, 1f, 1), 34, false);
        solid(x, z, 4.2f);
        ParticleEmitter motes = G3.particles("heartMotes", 120, null, new ColorRGBA(0.8f, 0.4f, 1f, 0.9f), new ColorRGBA(0.6f, 0.2f, 1f, 0), 0.18f, 0.05f, 3f, 6f);
        motes.setShape(new EmitterSphereShape(Vector3f.ZERO, 9f));
        motes.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.8f, 0));
        motes.getParticleInfluencer().setVelocityVariation(0.4f);
        motes.setParticlesPerSec(18);
        motes.setLocalTranslation(x, y + 4, z);
        motes.setName("heartMotes");
        root.attachChild(motes);
        corvina = new Figure(Story.CORVINA);
        corvina.node.setLocalTranslation(x, y + 0.2f, z + 7);
        corvina.node.setCullHint(Spatial.CullHint.Always);
        root.attachChild(corvina.node);
    }

    private void buildQuarry() {
        float x = WorldGen.QUARRY[0], z = WorldGen.QUARRY[1];
        Material rock = G3.litTex("Textures/Terrain/PBR/Rock035_1K_Color.jpg", new ColorRGBA(0.9f, 0.88f, 0.85f, 1), null);
        Random r = new Random(8);
        for (int i = 0; i < 14; i++) {
            float a = r.nextFloat() * FastMath.TWO_PI, d = 8 + r.nextFloat() * 14;
            if (Math.abs(Math.abs(a - FastMath.PI)) < 0.5f) continue;
            float rx = x + FastMath.cos(a) * d, rz = z + FastMath.sin(a) * d, s = 1.2f + r.nextFloat() * 2.4f;
            Geometry g = G3.geom("qrock", G3.rock(100 + i, 1), rock);
            g.setLocalScale(s);
            g.setLocalTranslation(rx, height(rx, rz) - 0.2f * s, rz);
            g.setShadowMode(ShadowMode.CastAndReceive);
            root.attachChild(g);
            solid(rx, rz, s * 0.95f);
        }
        // gestapelte Blöcke
        Material block = G3.litTex("Textures/Terrain/Rocky/RockyTexture.jpg", new ColorRGBA(0.85f, 0.82f, 0.78f, 1), "Textures/Terrain/Rocky/RockyNormals.jpg");
        for (int i = 0; i < 6; i++) {
            float bx = x + 6 + (i % 3) * 2.3f, bz = z - 8, by = height(bx, bz) + 0.8f + (i / 3) * 1.6f;
            Geometry g = G3.geom("block", new Box(1.1f, 0.8f, 0.9f), block);
            tangents(g);
            g.setLocalTranslation(bx, by, bz);
            g.setShadowMode(ShadowMode.CastAndReceive);
            root.attachChild(g);
        }
        solid(x + 8.3f, z - 8, 3.6f);
        torch(x - 3, z + 4);
        torch(x + 3, z - 3);
    }

    private void torch(float x, float z) {
        float y = height(x, z);
        Geometry stick = cylY("torch", 0.07f, 0.05f, 1.8f, 6, G3.lit(new ColorRGBA(0.25f, 0.16f, 0.1f, 1)));
        stick.setLocalTranslation(x, y + 0.9f, z);
        root.attachChild(stick);
        ParticleEmitter fire = G3.particles("fire", 30, "Effects/Explosion/flame.png", new ColorRGBA(1f, 0.6f, 0.2f, 1), new ColorRGBA(1f, 0.15f, 0.05f, 0), 0.35f, 0.08f, 0.4f, 0.8f);
        fire.setImagesX(2);
        fire.setImagesY(2);
        fire.setSelectRandomImage(true);
        fire.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.08f));
        fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 1.2f, 0));
        fire.getParticleInfluencer().setVelocityVariation(0.25f);
        fire.setParticlesPerSec(30);
        fire.setLocalTranslation(x, y + 1.9f, z);
        root.attachChild(fire);
        light(new Vector3f(x, y + 2.3f, z), new ColorRGBA(1f, 0.55f, 0.25f, 1).multLocal(1.3f), 13, true);
        solid(x, z, 0.25f);
    }

    private void buildSwampHut() {
        float x = WorldGen.SWAMP[0] + 12, z = WorldGen.SWAMP[1] + 4, y = Math.max(height(x, z), 0.2f);
        Material bark = texMat("Models/Tree/BarkColor.jpg", new ColorRGBA(0.55f, 0.45f, 0.38f, 1), null);
        Geometry walls = G3.geom("hut", scaled(new Cylinder(2, 18, 3.2f, 3.2f, 3.4f, true, false), 6, 1), bark);
        walls.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        add(root, walls, x, y + 1.7f, z).setShadowMode(ShadowMode.CastAndReceive);
        Material thatch = G3.phFresh("thatch_roof_angled", new ColorRGBA(0.95f, 0.85f, 0.7f, 1));
        Geometry roof = cylY("roof", 4.2f, 0.05f, 3.6f, 18, thatch);
        roof.setLocalTranslation(x, y + 5.2f, z);
        roof.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(roof);
        Geometry win = G3.geom("hutWindow", new Box(0.5f, 0.45f, 0.05f), G3.emissive(new ColorRGBA(0.6f, 1f, 0.45f, 1)));
        win.setLocalTranslation(x - 1.2f, y + 2.0f, z - 2.98f);
        win.setLocalRotation(new Quaternion().fromAngleAxis(0.38f, Vector3f.UNIT_Y));
        root.attachChild(win);
        solid(x, z, 3.5f);
        // Kessel
        float cx = WorldGen.SWAMP[0] + 6, cz = WorldGen.SWAMP[1] - 2, cy = Math.max(height(cx, cz), 0.1f);
        Geometry pot = G3.geom("cauldron", new Sphere(12, 16, 0.9f), G3.lit(new ColorRGBA(0.1f, 0.1f, 0.11f, 1)));
        pot.setLocalScale(1, 0.75f, 1);
        add(root, pot, cx, cy + 0.65f, cz);
        Geometry brew = G3.geom("brew", new Cylinder(2, 16, 0.78f, 0.78f, 0.02f, true, false), G3.emissive(new ColorRGBA(0.35f, 0.9f, 0.3f, 1)));
        brew.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        add(root, brew, cx, cy + 1.15f, cz);
        ParticleEmitter steam = G3.particles("brewSteam", 30, "Effects/Smoke/Smoke.png", new ColorRGBA(0.4f, 1f, 0.4f, 0.35f), new ColorRGBA(0.3f, 0.6f, 0.3f, 0), 0.4f, 1.4f, 1.5f, 3f);
        steam.setImagesX(15);
        steam.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        steam.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.4f));
        steam.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.8f, 0));
        steam.setParticlesPerSec(8);
        steam.setLocalTranslation(cx, cy + 1.3f, cz);
        root.attachChild(steam);
        light(new Vector3f(cx, cy + 2f, cz), new ColorRGBA(0.4f, 1f, 0.35f, 1), 12, false);
        solid(cx, cz, 1f);
        // Nebelschwaden
        ParticleEmitter mist = G3.particles("mist", 60, "Effects/Smoke/Smoke.png", new ColorRGBA(0.6f, 0.65f, 0.6f, 0.12f), new ColorRGBA(0.5f, 0.55f, 0.5f, 0), 4f, 9f, 8f, 14f);
        mist.setImagesX(15);
        mist.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mist.setShape(new EmitterBoxShape(new Vector3f(-26, 0, -26), new Vector3f(26, 1, 26)));
        mist.getParticleInfluencer().setInitialVelocity(new Vector3f(0.3f, 0.05f, 0));
        mist.getParticleInfluencer().setVelocityVariation(1f);
        mist.setParticlesPerSec(5);
        mist.setLocalTranslation(WorldGen.SWAMP[0], 0.6f, WorldGen.SWAMP[1]);
        root.attachChild(mist);
    }

    private void buildMushroomRing() {
        float x = WorldGen.MUSHROOMS[0], z = WorldGen.MUSHROOMS[1];
        Material stem = G3.lit(new ColorRGBA(0.88f, 0.84f, 0.74f, 1));
        ColorRGBA[] caps = {new ColorRGBA(0.75f, 0.22f, 0.16f, 1), new ColorRGBA(0.5f, 0.3f, 0.75f, 1), new ColorRGBA(0.3f, 0.55f, 0.75f, 1)};
        for (int i = 0; i < 11; i++) {
            float a = i * FastMath.TWO_PI / 11, d = 13 + (i % 2) * 2, s = 0.8f + (i * 37 % 10) / 10f * 0.9f;
            float mx = x + FastMath.cos(a) * d, mz = z + FastMath.sin(a) * d, my = height(mx, mz);
            Geometry st = cylY("mstem", 0.35f * s, 0.5f * s, 2.6f * s, 12, stem);
            st.setLocalTranslation(mx, my + 1.3f * s, mz);
            root.attachChild(st);
            ColorRGBA cc = caps[i % caps.length];
            Geometry cap = G3.geom("mcap", new Dome(Vector3f.ZERO, 8, 20, 1.6f * s, false), G3.litGlow(cc, cc.mult(0.45f)));
            cap.setLocalScale(1, 0.55f, 1);
            cap.setLocalTranslation(mx, my + 2.5f * s, mz);
            cap.setShadowMode(ShadowMode.Cast);
            root.attachChild(cap);
            solid(mx, mz, 0.6f * s);
        }
        light(new Vector3f(x, height(x, z) + 4, z), new ColorRGBA(0.6f, 0.45f, 1f, 1), 22, true);
    }

    private void buildBlightWall() {
        float x = WorldGen.RAVINE[0] - 27, z = WorldGen.RAVINE[1];
        Material crystalMat = G3.litGlow(new ColorRGBA(0.12f, 0.05f, 0.16f, 1), new ColorRGBA(0.5f, 0.12f, 0.75f, 1));
        crystalMat.setColor("Specular", new ColorRGBA(0.8f, 0.6f, 1f, 1));
        crystalMat.setFloat("Shininess", 64);
        Random r = new Random(13);
        for (int i = 0; i < 46; i++) {
            float sz = z - 9 + r.nextFloat() * 18, sx = x - 2 + r.nextFloat() * 4;
            float h = 2.5f + r.nextFloat() * 5, rad = 0.35f + r.nextFloat() * 0.5f;
            Geometry spike = G3.geom("blightSpike", new Cylinder(2, 5, rad, 0.01f, h, true, false), crystalMat);
            Quaternion q = new Quaternion().fromAngles(-FastMath.HALF_PI + (r.nextFloat() - 0.5f) * 0.7f, r.nextFloat() * 3, (r.nextFloat() - 0.5f) * 0.6f);
            spike.setLocalRotation(q);
            spike.setLocalTranslation(sx, height(sx, sz) + h * 0.4f, sz);
            spike.setShadowMode(ShadowMode.CastAndReceive);
            root.attachChild(spike);
            blightWall.add(spike);
        }
        for (float zz = z - 10; zz <= z + 10; zz += 2.5f) blightObst.add(new float[]{x, zz, 2.2f});
        ParticleEmitter p = G3.particles("blightMotes", 60, null, new ColorRGBA(0.7f, 0.25f, 1f, 0.9f), new ColorRGBA(0.4f, 0.1f, 0.7f, 0), 0.25f, 0.05f, 2f, 4f);
        p.setShape(new EmitterBoxShape(new Vector3f(-3, 0, -10), new Vector3f(3, 5, 10)));
        p.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.5f, 0));
        p.getParticleInfluencer().setVelocityVariation(0.6f);
        p.setParticlesPerSec(14);
        p.setLocalTranslation(x, height(x, z), z);
        root.attachChild(p);
        blightWall.add(p);
        PointLight l = light(new Vector3f(x - 4, height(x, z) + 4, z), new ColorRGBA(0.6f, 0.2f, 1f, 1), 20, false);
        blightWall.add(null);
        blightLight = l;
    }

    private PointLight blightLight;

    public Vector3f blightWallPos() {
        float x = WorldGen.RAVINE[0] - 27, z = WorldGen.RAVINE[1];
        return new Vector3f(x, height(x, z), z);
    }

    public boolean blightCleared() { return blightCleared; }

    /** Verbrennt die Welke-Barriere, wenn der Zauber nah genug ist. */
    public boolean clearBlight(Vector3f at, float radius) {
        if (blightCleared) return false;
        if (at.distance(blightWallPos()) > radius + 10) return false;
        blightCleared = true;
        for (Spatial s : blightWall) if (s != null) s.removeFromParent();
        root.removeLight(blightLight);
        for (int i = 0; i < 4; i++) burst(blightWallPos().add(0, 2, -9 + i * 6), new ColorRGBA(0.7f, 0.3f, 1f, 1), 60);
        return true;
    }

    public void setBlightCleared() {
        if (!blightCleared) clearBlight(blightWallPos(), 1);
    }

    private void buildShadowGrove() {
        float x = WorldGen.SHADOW[0], z = WorldGen.SHADOW[1], y = height(x, z);
        Material obs = G3.litGlow(new ColorRGBA(0.07f, 0.06f, 0.09f, 1), new ColorRGBA(0.04f, 0f, 0.06f, 1));
        obs.setColor("Specular", new ColorRGBA(0.4f, 0.3f, 0.5f, 1));
        obs.setFloat("Shininess", 40);
        Geometry ob = cylY("obelisk", 1.5f, 0.55f, 10, 4, obs);
        ob.setLocalTranslation(x, y + 5, z);
        ob.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(ob);
        Material rune = G3.emissive(new ColorRGBA(0.85f, 0.3f, 1f, 1));
        for (int i = 0; i < 4; i++) {
            float a = i * FastMath.HALF_PI + FastMath.QUARTER_PI;
            Geometry g = G3.geom("rune", new Box(0.08f, 1.6f, 0.02f), rune);
            g.setLocalTranslation(x + FastMath.cos(a) * 1.0f, y + 5, z + FastMath.sin(a) * 1.0f);
            g.setLocalRotation(new Quaternion().fromAngleAxis(-a + FastMath.HALF_PI, Vector3f.UNIT_Y));
            root.attachChild(g);
        }
        solid(x, z, 1.8f);
        light(new Vector3f(x, y + 3, z + 3), new ColorRGBA(0.7f, 0.2f, 1f, 1), 18, false);
        ParticleEmitter mist = G3.particles("darkMist", 50, "Effects/Smoke/Smoke.png", new ColorRGBA(0.15f, 0.05f, 0.22f, 0.3f), new ColorRGBA(0.05f, 0, 0.1f, 0), 3f, 7f, 6f, 10f);
        mist.setImagesX(15);
        mist.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mist.setShape(new EmitterBoxShape(new Vector3f(-22, 0, -22), new Vector3f(22, 1, 22)));
        mist.getParticleInfluencer().setInitialVelocity(new Vector3f(0.2f, 0.1f, 0));
        mist.getParticleInfluencer().setVelocityVariation(1f);
        mist.setParticlesPerSec(5);
        mist.setLocalTranslation(x, y + 0.5f, z);
        root.attachChild(mist);
    }

    private void buildMeadow() {
        float x = WorldGen.MEADOW[0], z = WorldGen.MEADOW[1] - 4, y = height(x, z);
        Material cm = G3.litGlow(new ColorRGBA(1f, 0.9f, 0.6f, 1), new ColorRGBA(1f, 0.75f, 0.35f, 1));
        crystal = G3.geom("crystal", new Sphere(3, 4, 1.6f), cm);
        crystal.setLocalScale(0.9f, 2.4f, 0.9f);
        crystal.setLocalTranslation(x, y + 5, z);
        root.attachChild(crystal);
        light(new Vector3f(x, y + 5, z), new ColorRGBA(1f, 0.8f, 0.45f, 1).multLocal(1.4f), 34, false);
        solid(x, z, 1.4f);
        ParticleEmitter p = G3.particles("goldMotes", 80, null, new ColorRGBA(1f, 0.85f, 0.4f, 1), new ColorRGBA(1f, 0.7f, 0.3f, 0), 0.14f, 0.04f, 3f, 6f);
        p.setShape(new EmitterBoxShape(new Vector3f(-26, 0, -26), new Vector3f(26, 3, 26)));
        p.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.3f, 0));
        p.getParticleInfluencer().setVelocityVariation(1f);
        p.setParticlesPerSec(14);
        p.setLocalTranslation(x, y + 0.5f, z);
        root.attachChild(p);
    }

    private void buildLakeCourt() {
        Material orb = G3.emissive(new ColorRGBA(0.75f, 0.9f, 1f, 1));
        Random r = new Random(21);
        for (int i = 0; i < 12; i++) {
            float a = 2.2f + r.nextFloat() * 2f, d = 18 + r.nextFloat() * 14;
            float ox = WorldGen.LAKE[0] + FastMath.cos(a) * d, oz = WorldGen.LAKE[1] + FastMath.sin(a) * d;
            Geometry g = G3.geom("lakeOrb", new Sphere(8, 10, 0.22f), orb);
            g.setLocalTranslation(ox, 1.5f + r.nextFloat() * 2.5f, oz);
            g.setUserData("bob", r.nextFloat() * 6);
            root.attachChild(g);
            floating.add(g);
        }
        light(new Vector3f(WorldGen.LAKE[0] + 30, 3, WorldGen.LAKE[1] - 12), new ColorRGBA(0.6f, 0.75f, 1f, 1), 24, true);
    }

    private final List<Geometry> floating = new ArrayList<>();

    // ================================================================ Laden & Kessel

    private void buildShop() {
        float x = 18, z = -37, y = height(x, z);
        Material wood = texMat("Models/Tree/BarkColor.jpg", new ColorRGBA(0.75f, 0.55f, 0.4f, 1), null);
        Node n = new Node("shop");
        box(n, wood, x, y + 0.55f, z, 2.6f, 0.55f, 0.5f);
        for (int side = -1; side <= 1; side += 2) {
            Geometry post = cylY("post", 0.09f, 0.09f, 3f, 8, wood);
            post.setLocalTranslation(x + side * 2.5f, y + 1.5f, z + 0.3f);
            n.attachChild(post);
            Geometry post2 = cylY("post", 0.09f, 0.09f, 3f, 8, wood);
            post2.setLocalTranslation(x + side * 2.5f, y + 1.5f, z + 3f);
            n.attachChild(post2);
        }
        for (int i = 0; i < 6; i++) {
            Geometry stripe = G3.geom("awning", new Box(0.45f, 0.04f, 1.7f), G3.lit(i % 2 == 0 ? new ColorRGBA(0.75f, 0.2f, 0.25f, 1) : new ColorRGBA(0.92f, 0.88f, 0.8f, 1)));
            stripe.setLocalTranslation(x - 2.25f + i * 0.9f, y + 3.05f, z + 1.6f);
            stripe.setLocalRotation(new Quaternion().fromAngleAxis(-0.12f, Vector3f.UNIT_X));
            n.attachChild(stripe);
        }
        // Kleiderstangen mit Gewändern
        Outfit[] o = Outfit.values();
        for (int i = 1; i < o.length; i++) {
            float ox = x - 2.2f + (i % 9) * 0.55f, oz = z + 1.6f + (i / 9) * 1.1f;
            Geometry cloth = G3.geom("garment", new Box(0.2f, 0.42f, 0.05f), G3.lit(G3.rgb(o[i].color)));
            cloth.setLocalTranslation(ox, y + 2.0f, oz);
            n.attachChild(cloth);
        }
        box(n, wood, x, y + 2.5f, z + 1.6f, 2.5f, 0.03f, 0.03f);
        box(n, wood, x, y + 2.5f, z + 2.7f, 2.5f, 0.03f, 0.03f);
        Geometry sign = G3.geom("shopSign", new Box(1.3f, 0.3f, 0.04f), G3.litGlow(new ColorRGBA(0.9f, 0.8f, 0.5f, 1), new ColorRGBA(0.3f, 0.25f, 0.1f, 1)));
        sign.setLocalTranslation(x, y + 3.4f, z - 0.2f);
        n.attachChild(sign);
        n.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(n);
        solid(x, z + 1.5f, 2.7f);
        light(new Vector3f(x, y + 2.6f, z - 1.5f), new ColorRGBA(1f, 0.8f, 0.55f, 1), 10, true);
        // Kessel der Akademie
        float kx = -16, kz = -44, ky = height(kx, kz);
        Geometry pot = G3.geom("cauldron", new Sphere(12, 16, 0.8f), G3.lit(new ColorRGBA(0.1f, 0.1f, 0.11f, 1)));
        pot.setLocalScale(1, 0.75f, 1);
        add(root, pot, kx, ky + 0.6f, kz);
        Geometry brew = G3.geom("brew", new Cylinder(2, 16, 0.7f, 0.7f, 0.02f, true, false), G3.emissive(new ColorRGBA(0.4f, 0.6f, 1f, 1)));
        brew.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        add(root, brew, kx, ky + 1.05f, kz);
        solid(kx, kz, 0.9f);
    }

    // ================================================================ Café

    private void buildCafe() {
        float cx = WorldGen.CAFE[0], cz = WorldGen.CAFE[1];
        float fy = Math.max(height(cx - 7, cz - 5), Math.max(height(cx + 7, cz + 5), height(cx, cz))) + 0.35f;
        Node n = new Node("cafe");
        float w = 8, d = 5.5f, wh = 4.2f;
        Material wall = G3.lit(new ColorRGBA(0.95f, 0.88f, 0.76f, 1));
        wall.setTexture("DiffuseMap", plasterTex());
        Material stone = G3.litTex("Textures/Terrain/BrickWall/BrickWall.jpg", new ColorRGBA(0.8f, 0.76f, 0.72f, 1), "Textures/Terrain/BrickWall/BrickWall_normal.jpg");
        Material planks = G3.lit(new ColorRGBA(1, 1, 1, 1));
        planks.setTexture("DiffuseMap", plankTex());
        Material dark = G3.lit(new ColorRGBA(0.3f, 0.2f, 0.13f, 1));
        Material roofM = G3.lit(new ColorRGBA(0.35f, 0.18f, 0.14f, 1));
        // Sockel & Boden
        add(n, G3.geom("cafeBase", scaled(new Box(w + 0.6f, 1.6f, d + 0.6f), 4, 1), stone), cx, fy - 1.6f, cz);
        add(n, G3.geom("cafeFloor", scaled(new Box(w, 0.05f, d), 1, 1), planks), cx, fy, cz);
        // Wände: Nord mit Tür, Süd mit großem Fenster
        box(n, wall, cx - (w + 1.2f) / 2 - 0.6f, fy + wh / 2, cz - d, (w - 1.2f) / 2, wh / 2, 0.15f);
        box(n, wall, cx + (w + 1.2f) / 2 + 0.6f, fy + wh / 2, cz - d, (w - 1.2f) / 2, wh / 2, 0.15f);
        box(n, wall, cx, fy + wh - 0.6f, cz - d, 1.2f, 0.6f, 0.15f);
        box(n, wall, cx, fy + 0.33f, cz + d, w, 0.33f, 0.15f);
        box(n, wall, cx, fy + wh - 0.4f, cz + d, w, 0.4f, 0.15f);
        box(n, wall, cx - w, fy + wh / 2, cz, 0.15f, wh / 2, d);
        box(n, wall, cx + w, fy + wh / 2, cz, 0.15f, wh / 2, d);
        for (float mx = -w; mx <= w; mx += 4) box(n, dark, cx + mx, fy + 2.2f, cz + d, 0.1f, 1.5f, 0.2f);
        box(n, dark, cx, fy + 0.7f, cz + d - 0.2f, w, 0.05f, 0.35f);
        // Dach
        for (int side = -1; side <= 1; side += 2) {
            Geometry rf = G3.geom("roof", new Box(w + 0.8f, 0.12f, d * 0.62f), roofM);
            rf.setLocalTranslation(cx, fy + wh + 1.2f, cz + side * d * 0.5f);
            rf.setLocalRotation(new Quaternion().fromAngleAxis(side * -0.45f, Vector3f.UNIT_X));
            n.attachChild(rf);
        }
        box(n, wall, cx, fy + wh + 0.05f, cz, w, 0.08f, d);
        // Einrichtung
        Material wood = G3.lit(new ColorRGBA(0.45f, 0.29f, 0.17f, 1));
        for (int i = -1; i <= 1; i++) {
            float tx = cx + i * 5, tz = cz + d - 1.6f;
            Geometry top = cylY("table", 0.75f, 0.75f, 0.06f, 20, wood);
            top.setLocalTranslation(tx, fy + 0.78f, tz);
            n.attachChild(top);
            Geometry leg = cylY("leg", 0.07f, 0.07f, 0.76f, 8, dark);
            leg.setLocalTranslation(tx, fy + 0.38f, tz);
            n.attachChild(leg);
            box(n, wood, tx, fy + 0.45f, tz - 1.05f, 0.42f, 0.04f, 0.42f);
            box(n, wood, tx, fy + 0.9f, tz - 1.45f, 0.42f, 0.45f, 0.04f);
            Geometry candle = cylY("candle", 0.05f, 0.05f, 0.18f, 8, G3.lit(new ColorRGBA(0.95f, 0.92f, 0.85f, 1)));
            candle.setLocalTranslation(tx + 0.35f, fy + 0.9f, tz + 0.2f);
            n.attachChild(candle);
            Geometry flame = G3.geom("flame", new Sphere(6, 6, 0.035f), G3.emissive(new ColorRGBA(1f, 0.75f, 0.35f, 1)));
            flame.setLocalScale(1, 1.8f, 1);
            flame.setLocalTranslation(tx + 0.35f, fy + 1.03f, tz + 0.2f);
            n.attachChild(flame);
        }
        // Theke
        box(n, wood, cx + 4.5f, fy + 0.55f, cz - d + 1.4f, 2.6f, 0.55f, 0.5f);
        // Bücherregal
        box(n, wood, cx - w + 0.45f, fy + 1.6f, cz - 2.2f, 0.3f, 1.6f, 1.4f);
        Random r = new Random(3);
        ColorRGBA[] bc = {new ColorRGBA(0.5f, 0.12f, 0.1f, 1), new ColorRGBA(0.12f, 0.22f, 0.45f, 1), new ColorRGBA(0.2f, 0.38f, 0.18f, 1), new ColorRGBA(0.55f, 0.42f, 0.15f, 1)};
        for (int shelf = 0; shelf < 4; shelf++) {
            float bz = cz - 3.4f;
            while (bz < cz - 1.0f) {
                float bw = 0.05f + r.nextFloat() * 0.05f, bh = 0.2f + r.nextFloat() * 0.1f;
                box(n, G3.lit(bc[r.nextInt(bc.length)]), cx - w + 0.62f, fy + 0.35f + shelf * 0.8f + bh, bz + bw, 0.12f, bh, bw);
                bz += bw * 2 + 0.01f;
            }
        }
        // Kamin
        box(n, stone, cx - w + 0.6f, fy + 1.4f, cz + 1.8f, 0.5f, 1.4f, 1.2f);
        ParticleEmitter fire = G3.particles("hearth", 40, "Effects/Explosion/flame.png", new ColorRGBA(1f, 0.6f, 0.2f, 1), new ColorRGBA(1f, 0.2f, 0.05f, 0), 0.4f, 0.1f, 0.5f, 0.9f);
        fire.setImagesX(2);
        fire.setImagesY(2);
        fire.setSelectRandomImage(true);
        fire.setShape(new EmitterBoxShape(new Vector3f(0, 0, -0.4f), new Vector3f(0.2f, 0.1f, 0.4f)));
        fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.9f, 0));
        fire.getParticleInfluencer().setVelocityVariation(0.3f);
        fire.setParticlesPerSec(40);
        fire.setLocalTranslation(cx - w + 1.2f, fy + 0.35f, cz + 1.8f);
        n.attachChild(fire);
        // Deckenbalken
        for (float bx = -w + 1; bx <= w - 1; bx += 2.6f) box(n, dark, cx + bx, fy + wh - 0.15f, cz, 0.12f, 0.12f, d);
        // Teppich
        Material rug = G3.lit(ColorRGBA.White);
        rug.setTexture("DiffuseMap", rugTex());
        add(n, G3.geom("rug", new Box(5.5f, 0.01f, 2.2f), rug), cx, fy + 0.07f, cz + 1.4f);
        // Hängelampen über den Tischen
        Material brass = G3.lit(new ColorRGBA(0.7f, 0.55f, 0.3f, 1));
        brass.setColor("Specular", new ColorRGBA(1f, 0.9f, 0.6f, 1));
        brass.setFloat("Shininess", 40);
        for (int i = -1; i <= 1; i++) {
            float lx = cx + i * 5, lz = cz + d - 1.6f;
            box(n, dark, lx, fy + wh - 0.6f, lz, 0.01f, 0.6f, 0.01f);
            Geometry shade = G3.geom("shade", new Dome(Vector3f.ZERO, 6, 16, 0.32f, true), brass);
            shade.setLocalTranslation(lx, fy + wh - 1.45f, lz);
            n.attachChild(shade);
            add(n, G3.geom("bulb", new Sphere(8, 8, 0.09f), G3.emissive(new ColorRGBA(1f, 0.82f, 0.55f, 1))), lx, fy + wh - 1.42f, lz);
        }
        // Pflanzen in Töpfen
        Material pot = G3.lit(new ColorRGBA(0.72f, 0.38f, 0.22f, 1));
        Material leaves = G3.lit(new ColorRGBA(0.85f, 1f, 0.85f, 1));
        leaves.setTexture("DiffuseMap", G3.fernTex());
        leaves.setFloat("AlphaDiscardThreshold", 0.4f);
        leaves.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        float[][] plants = {{cx - w + 0.7f, cz + d - 0.7f, 1.3f}, {cx + w - 0.7f, cz + d - 0.7f, 1.3f}, {cx + w - 0.7f, cz - d + 0.7f, 1.1f}, {cx + 6.2f, cz - d + 1.4f, 0.55f}};
        for (float[] pl : plants) {
            float py = pl[2] < 1 ? fy + 1.12f : fy;
            Geometry pg = cylY("pot", 0.22f * pl[2], 0.16f * pl[2], 0.42f * pl[2], 14, pot);
            pg.setLocalTranslation(pl[0], py + 0.21f * pl[2], pl[1]);
            n.attachChild(pg);
            Geometry fern = G3.geom("plant", G3.crossQuads(1.2f * pl[2], 1.0f * pl[2]), leaves);
            fern.setLocalTranslation(pl[0], py + 0.38f * pl[2], pl[1]);
            n.attachChild(fern);
        }
        // Gemälde
        Material frame = G3.lit(new ColorRGBA(0.45f, 0.3f, 0.12f, 1));
        frame.setColor("Specular", new ColorRGBA(0.6f, 0.5f, 0.3f, 1));
        frame.setFloat("Shininess", 30);
        for (int side = -1; side <= 1; side += 2) {
            float px = cx + side * (w - 0.18f), pz = cz + (side < 0 ? -1.5f : 1.8f);
            Geometry fr = G3.geom("frame", new Box(0.04f, 0.62f, 0.85f), frame);
            fr.setLocalTranslation(px, fy + 2.4f, pz);
            n.attachChild(fr);
            Material art = G3.lit(ColorRGBA.White);
            art.setTexture("DiffuseMap", paintingTex(side < 0 ? 1 : 2));
            Geometry pic = G3.geom("painting", new com.jme3.scene.shape.Quad(1.5f, 1.05f), art);
            pic.setLocalRotation(new Quaternion().fromAngleAxis(side * -FastMath.HALF_PI, Vector3f.UNIT_Y));
            pic.setLocalTranslation(px - side * 0.05f, fy + 2.4f - 0.525f, pz + side * 0.75f);
            n.attachChild(pic);
        }
        // Schlafende Katze vor dem Kamin
        Material catFur = G3.lit(new ColorRGBA(0.85f, 0.55f, 0.25f, 1));
        Node cat = new Node("cat");
        Geometry cb = G3.geom("catBody", new Sphere(12, 14, 1), catFur);
        cb.setLocalScale(0.28f, 0.14f, 0.24f);
        cb.setLocalTranslation(0, 0.13f, 0);
        cat.attachChild(cb);
        Geometry ch = G3.geom("catHead", new Sphere(10, 12, 0.11f), catFur);
        ch.setLocalTranslation(0.2f, 0.14f, 0.12f);
        cat.attachChild(ch);
        for (int sd = -1; sd <= 1; sd += 2) {
            Geometry ear = G3.geom("catEar", new Cylinder(2, 4, 0.04f, 0.005f, 0.07f, true, false), catFur);
            ear.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
            ear.setLocalTranslation(0.2f + sd * 0.05f, 0.25f, 0.13f);
            cat.attachChild(ear);
        }
        Geometry tail = G3.geom("catTail", new Torus(10, 6, 0.035f, 0.2f), catFur);
        tail.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        tail.setLocalTranslation(-0.05f, 0.05f, 0.05f);
        cat.attachChild(tail);
        Geometry cushion = G3.geom("cushion", new Cylinder(2, 18, 0.42f, 0.42f, 0.08f, true, false), G3.lit(new ColorRGBA(0.5f, 0.15f, 0.2f, 1)));
        cushion.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        cat.attachChild(cushion);
        cat.setLocalTranslation(cx - w + 2.1f, fy + 0.08f, cz + 1.9f);
        n.attachChild(cat);
        cafeCat = cb;

        // Schild & Laterne außen
        Geometry sign = G3.geom("sign", new Box(1.6f, 0.4f, 0.05f), G3.litGlow(new ColorRGBA(0.35f, 0.22f, 0.12f, 1), new ColorRGBA(0.35f, 0.25f, 0.1f, 1)));
        sign.setLocalTranslation(cx, fy + wh - 0.3f, cz - d - 0.25f);
        n.attachChild(sign);
        n.setShadowMode(ShadowMode.CastAndReceive);
        tangents(n);
        root.attachChild(n);
        lampPost(root, cx - 2.2f, cz - d - 2.5f);
        cafeLight = light(new Vector3f(cx, fy + 3, cz), new ColorRGBA(1f, 0.72f, 0.45f, 1).multLocal(1.3f), 14, false);
        light(new Vector3f(cx - w + 1.4f, fy + 1, cz + 1.8f), new ColorRGBA(1f, 0.5f, 0.2f, 1), 8, false);
        for (float xx = cx - w; xx <= cx + w; xx += 2.2f) {
            solid(xx, cz - d, 1.2f);
            solid(xx, cz + d, 1.2f);
        }
        for (float zz = cz - d; zz <= cz + d; zz += 2.2f) {
            solid(cx - w, zz, 1.2f);
            solid(cx + w, zz, 1.2f);
        }
        solid(cx, cz, 5);
        cafeDoor.set(cx, height(cx, cz - d - 1.8f), cz - d - 1.8f);
        cafeSeat.set(cx, fy + 1.45f, cz + d - 2.75f);
        cafeLook.set(cx, fy - 9f, cz + 60);
        waitress = new Figure(Look.fairy(Look.rgb(250, 232, 220), Look.rgb(150, 110, 160), Look.rgb(255, 210, 240), Look.rgb(250, 220, 170), Look.rgb(255, 210, 250)).hair(true, Look.BUN));
        waitress.node.setLocalTranslation(cx + 4.5f, fy, cz - d + 2.6f);
        waitress.node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI * 0.85f, Vector3f.UNIT_Y));
        root.attachChild(waitress.node);
        cafeCup = new Node("cup");
        Geometry cup = cylY("cupBody", 0.06f, 0.05f, 0.11f, 16, G3.lit(new ColorRGBA(0.95f, 0.93f, 0.9f, 1)));
        cafeCup.attachChild(cup);
        Geometry drink = cylY("drink", 0.055f, 0.055f, 0.005f, 16, G3.lit(new ColorRGBA(0.35f, 0.2f, 0.1f, 1)));
        drink.setLocalTranslation(0, 0.05f, 0);
        drink.setName("drink");
        cafeCup.attachChild(drink);
        ParticleEmitter steam = G3.particles("cupSteam", 20, "Effects/Smoke/Smoke.png", new ColorRGBA(1, 1, 1, 0.18f), new ColorRGBA(1, 1, 1, 0), 0.04f, 0.14f, 1.5f, 2.5f);
        steam.setImagesX(15);
        steam.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        steam.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.02f));
        steam.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.12f, 0));
        steam.getParticleInfluencer().setVelocityVariation(0.4f);
        steam.setParticlesPerSec(6);
        steam.setLocalTranslation(0, 0.08f, 0);
        cafeCup.attachChild(steam);
        cafeCup.setLocalTranslation(cx - 0.1f, fy + 0.86f, cz + d - 1.95f);
        cafeCup.setCullHint(Spatial.CullHint.Always);
        root.attachChild(cafeCup);
    }

    /** Atmende Katze im Café. */
    public void animateCafe(float t) {
        if (cafeCat != null) cafeCat.setLocalScale(0.28f, 0.14f + FastMath.sin(t * 1.6f) * 0.006f, 0.24f);
    }

    public void showCup(ColorRGBA drinkColor) {
        cafeCup.setCullHint(drinkColor == null ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        if (drinkColor != null) ((Geometry) cafeCup.getChild("drink")).getMaterial().setColor("Diffuse", drinkColor);
    }

    private void box(Node n, Material m, float x, float y, float z, float ex, float ey, float ez) {
        Geometry g = G3.geom("b", new Box(ex, ey, ez), m);
        g.setLocalTranslation(x, y, z);
        n.attachChild(g);
    }

    private static Texture plasterTex() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            double n = G3.fbm(x * 0.05, y * 0.05, 4) * 0.6 + G3.vnoise(x * 0.4, y * 0.4) * 0.15;
            int v = (int) (205 + n * 45);
            img.setRGB(x, y, (Math.min(255, v) << 16) | (Math.min(255, (int) (v * 0.93)) << 8) | Math.min(255, (int) (v * 0.82)));
        }
        com.jme3.texture.Texture2D t = G3.tex(img);
        t.setWrap(Texture.WrapMode.Repeat);
        return t;
    }

    private static Texture rugTex() {
        BufferedImage img = new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(120, 30, 35));
        g.fillRect(0, 0, 512, 256);
        g.setColor(new java.awt.Color(200, 150, 70));
        g.setStroke(new java.awt.BasicStroke(6));
        g.drawRect(14, 14, 484, 228);
        g.setStroke(new java.awt.BasicStroke(2));
        g.drawRect(28, 28, 456, 200);
        for (int i = 0; i < 7; i++) {
            int x = 70 + i * 62;
            java.awt.geom.Path2D d = new java.awt.geom.Path2D.Double();
            d.moveTo(x, 128 - 50); d.lineTo(x + 26, 128); d.lineTo(x, 128 + 50); d.lineTo(x - 26, 128); d.closePath();
            g.setColor(i % 2 == 0 ? new java.awt.Color(40, 60, 100) : new java.awt.Color(210, 170, 90));
            g.fill(d);
        }
        g.dispose();
        return G3.tex(img);
    }

    /** Kleine Landschaftsbilder für das Café. */
    private static Texture paintingTex(int variant) {
        BufferedImage img = new BufferedImage(256, 180, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.Color top = variant == 1 ? new java.awt.Color(250, 160, 90) : new java.awt.Color(40, 50, 100);
        java.awt.Color bottom = variant == 1 ? new java.awt.Color(250, 220, 160) : new java.awt.Color(120, 90, 150);
        g.setPaint(new java.awt.GradientPaint(0, 0, top, 0, 110, bottom));
        g.fillRect(0, 0, 256, 120);
        g.setColor(variant == 1 ? new java.awt.Color(255, 240, 200) : new java.awt.Color(240, 240, 220));
        g.fill(new java.awt.geom.Ellipse2D.Double(variant == 1 ? 150 : 60, variant == 1 ? 70 : 30, 34, 34));
        g.setColor(variant == 1 ? new java.awt.Color(60, 90, 120) : new java.awt.Color(20, 30, 60));
        g.fillRect(0, 110, 256, 70);
        Random r = new Random(variant);
        g.setColor(new java.awt.Color(255, 255, 255, 70));
        for (int i = 0; i < 40; i++) g.fillRect(r.nextInt(256), 112 + r.nextInt(60), 8 + r.nextInt(18), 2);
        g.setColor(variant == 1 ? new java.awt.Color(70, 60, 60) : new java.awt.Color(15, 20, 30));
        java.awt.geom.Path2D cliff = new java.awt.geom.Path2D.Double();
        cliff.moveTo(0, 180); cliff.lineTo(0, 80); cliff.lineTo(30, 70); cliff.lineTo(60, 90); cliff.lineTo(78, 112); cliff.lineTo(90, 180); cliff.closePath();
        g.fill(cliff);
        if (variant == 2) {
            g.setColor(new java.awt.Color(255, 220, 140));
            g.fillRect(40, 60, 6, 10);
        }
        g.dispose();
        return G3.tex(img);
    }

    private static Texture plankTex() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(6);
        for (int y = 0; y < 256; y++) {
            int plank = y / 32;
            int base = 120 + (plank * 37 % 30);
            for (int x = 0; x < 256; x++) {
                double grain = Math.sin((x + plank * 50) * 0.08 + Math.sin(y * 0.3) * 1.5) * 10 + G3.vnoise(x * 0.05, y * 0.4 + plank * 9) * 18;
                int v = (int) (base + grain);
                if (y % 32 == 0 || (x + plank * 97) % 256 == 0) v -= 40;
                img.setRGB(x, y, (Math.max(0, Math.min(255, v)) << 16) | (Math.max(0, Math.min(255, (int) (v * 0.68))) << 8) | Math.max(0, Math.min(255, (int) (v * 0.42))));
            }
        }
        com.jme3.texture.Texture2D t = G3.tex(img);
        t.setWrap(Texture.WrapMode.Repeat);
        return t;
    }

    // ================================================================ Figuren

    private void buildNpcs() {
        Map<String, float[]> place = new HashMap<>();
        place.put("borga", new float[]{-10, -58});
        place.put("ilvy", new float[]{12, -54});
        place.put("rektor", new float[]{6, -90});
        place.put("velmara", new float[]{-6, -90});
        place.put("oberion", new float[]{-92, -18});
        place.put("grumhild", new float[]{116, -38});
        place.put("ysolde", new float[]{4, 88});
        place.put("orakel", new float[]{-120, 92});
        place.put("kestra", new float[]{130, 84});
        place.put("vex", new float[]{-124, -84});
        place.put("aurin", new float[]{112, -96});
        place.put("heart", new float[]{0, 24});
        place.put("cafe", new float[]{cafeDoor.x, cafeDoor.z});
        place.put("brumm", new float[]{26, -58});
        place.put("grol", new float[]{-46, -72});
        place.put("pimpernell", new float[]{18, -40});
        place.put("kessel1", new float[]{WorldGen.SWAMP[0] + 6, WorldGen.SWAMP[1] - 2});
        place.put("kessel2", new float[]{-16, -44});
        for (Npc n : Story.createNpcs()) {
            float[] p = place.getOrDefault(n.id, new float[]{0, 0});
            Vector3f pos = ground(p[0], p[1]);
            npcPos.put(n.id, pos);
            if (n.look != null) {
                Figure f = new Figure(n.look);
                f.node.setLocalTranslation(pos);
                root.attachChild(f.node);
                figures.put(n.id, f);
                solid(p[0], p[1], 0.7f);
            }
            if (n.shard != null || n.id.equals("heart")) {
                Geometry m = G3.geom("marker", new Sphere(3, 4, 0.28f), G3.emissive(n.shard != null ? G3.rgb(n.shard.color) : new ColorRGBA(0.7f, 1f, 0.8f, 1)));
                m.setLocalScale(1, 1.8f, 1);
                m.setShadowMode(ShadowMode.Off);
                root.attachChild(m);
                markers.put(n.id, m);
            }
        }
        npcPos.put("cafe", cafeDoor.clone());
    }

    // ================================================================ Partikel & Filter

    private void buildParticles() {
        fireflies = G3.particles("fireflies", 90, null, new ColorRGBA(1f, 0.9f, 0.45f, 1), new ColorRGBA(0.8f, 1f, 0.4f, 0), 0.09f, 0.05f, 3f, 6f);
        fireflies.setShape(new EmitterBoxShape(new Vector3f(-28, 0.3f, -28), new Vector3f(28, 4, 28)));
        fireflies.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.15f, 0));
        fireflies.getParticleInfluencer().setVelocityVariation(1f);
        fireflies.setInWorldSpace(true);
        root.attachChild(fireflies);
    }

    public void burst(Vector3f pos, ColorRGBA c, int count) {
        ParticleEmitter e = G3.particles("burst", count, null, c, new ColorRGBA(c.r, c.g, c.b, 0), 0.3f, 0.05f, 0.6f, 1.4f);
        e.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.4f));
        e.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 2.5f, 0));
        e.getParticleInfluencer().setVelocityVariation(1f);
        e.setGravity(0, 1.2f, 0);
        e.setParticlesPerSec(0);
        e.setLocalTranslation(pos);
        root.attachChild(e);
        e.emitAllParticles();
        e.setUserData("life", 2f);
        bursts.add(e);
    }

    private void setupFilters() {
        FilterPostProcessor fpp = new FilterPostProcessor(app.getAssetManager());
        int samples = app.getContext().getSettings().getSamples();
        if (samples > 1) fpp.setNumSamples(samples);
        water = makeWater();
        fpp.addFilter(water);
        shadows = makeShadows(lowGraphics ? 1024 : 2048, 3);
        fpp.addFilter(shadows);
        if (!lowGraphics) {
            // Umgebungsverdeckung: weiche Schatten in Ecken, unter Bäumen und an Wurzeln
            com.jme3.post.ssao.SSAOFilter ssao = new com.jme3.post.ssao.SSAOFilter(2.2f, 1.6f, 0.25f, 0.08f);
            ssao.setApproximateNormals(true);
            fpp.addFilter(ssao);
        }
        fog = new SkyFogFilter();
        fpp.addFilter(fog);
        // Lichtstrahlen der tief stehenden Sonne
        sunRays = new com.jme3.post.filters.LightScatteringFilter(new Vector3f(0, 500, 0));
        sunRays.setLightDensity(0.9f);
        sunRays.setBlurWidth(1.1f);
        sunRays.setNbSamples(lowGraphics ? 30 : 50);
        fpp.addFilter(sunRays);
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
        bloom.setBloomIntensity(1.45f);
        bloom.setExposurePower(4.5f);
        bloom.setBlurScale(2.1f);
        bloom.setDownSamplingFactor(2);
        fpp.addFilter(bloom);
        grade = new GradeFilter();
        fpp.addFilter(grade);
        fpp.addFilter(new FXAAFilter());
        app.getViewPort().addProcessor(fpp);
        fpp1 = fpp;
    }

    private WaterFilter makeWater() {
        WaterFilter w = new WaterFilter(app.getRootNode(), new Vector3f(-0.4f, -0.6f, -0.5f));
        w.setWaterHeight(WorldGen.WATER);
        w.setWaterColor(new ColorRGBA(0.04f, 0.13f, 0.18f, 1));
        w.setDeepWaterColor(new ColorRGBA(0.01f, 0.05f, 0.09f, 1));
        w.setWaterTransparency(0.12f);
        w.setMaxAmplitude(0.35f);
        w.setWaveScale(0.006f);
        w.setSpeed(0.5f);
        w.setShoreHardness(0.25f);
        w.setRefractionStrength(0.12f);
        w.setFoamHardness(0.6f);
        w.setFoamIntensity(0.4f);
        w.setReflectionMapSize(lowGraphics ? 256 : 512);
        w.setUnderWaterFogDistance(40);
        w.setSunScale(1.6f);
        return w;
    }

    private DirectionalLightShadowFilter makeShadows(int size, int splits) {
        DirectionalLightShadowFilter sh = new DirectionalLightShadowFilter(app.getAssetManager(), size, splits);
        sh.setLight(dayNight.light);
        sh.setLambda(0.65f);
        sh.setShadowIntensity(0.55f);
        sh.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
        sh.setShadowZExtend(140);
        sh.setShadowZFadeLength(30);
        return sh;
    }

    /** Schaltet den geteilten Bildschirm ein oder aus (links Spieler 1, rechts Spieler 2). */
    public void setSplit(boolean on) {
        if (on == split) return;
        split = on;
        Camera cam = app.getCamera();
        float aspect = (float) cam.getWidth() / cam.getHeight();
        if (on && cam2 == null) {
            cam2 = cam.clone();
            vp2 = app.getRenderManager().createMainView("spieler2", cam2);
            vp2.setClearFlags(true, true, true);
            vp2.setBackgroundColor(app.getViewPort().getBackgroundColor());
            vp2.attachScene(app.getRootNode());
            FilterPostProcessor fpp = new FilterPostProcessor(app.getAssetManager());
            water2 = makeWater();
            if (lowGraphics) water2.setReflectionMapSize(128);
            fpp.addFilter(water2);
            shadows2 = makeShadows(1024, 2);
            fpp.addFilter(shadows2);
            fog2 = new SkyFogFilter();
            fpp.addFilter(fog2);
            grade2 = new GradeFilter();
            fpp.addFilter(grade2);
            fpp.addFilter(new FXAAFilter());
            vp2.addProcessor(fpp);
            fpp2 = fpp;
        }
        if (on) {
            cam.setViewPort(0f, 0.5f, 0f, 1f);
            cam.setFrustumPerspective(58, aspect * 0.5f, 0.25f, 1000f);
            cam2.setViewPort(0.5f, 1f, 0f, 1f);
            cam2.setFrustumPerspective(58, aspect * 0.5f, 0.25f, 1000f);
            vp2.setEnabled(true);
        } else {
            cam.setViewPort(0f, 1f, 0f, 1f);
            cam.setFrustumPerspective(58, aspect, 0.25f, 1000f);
            if (vp2 != null) vp2.setEnabled(false);
        }
        // Die Nachbearbeitung merkt sich den Bildausschnitt der Kamera – neu einlesen lassen
        refit(fpp1, app.getViewPort());
        if (on) refit(fpp2, vp2);
    }

    /** jME merkt sich den Bildausschnitt beim Start der Nachbearbeitung; hier wird er auf den neuen Ausschnitt gesetzt. */
    private static void refit(FilterPostProcessor fpp, com.jme3.renderer.ViewPort vp) {
        if (fpp == null || !fpp.isInitialized()) return;
        Camera c = vp.getCamera();
        try {
            for (String[] f : new String[][]{{"left", "" + c.getViewPortLeft()}, {"right", "" + c.getViewPortRight()},
                {"top", "" + c.getViewPortTop()}, {"bottom", "" + c.getViewPortBottom()}}) {
                java.lang.reflect.Field fl = FilterPostProcessor.class.getDeclaredField(f[0]);
                fl.setAccessible(true);
                fl.setFloat(fpp, Float.parseFloat(f[1]));
            }
        } catch (ReflectiveOperationException e) {
            System.err.println("Bildausschnitt nicht anpassbar: " + e);
        }
        fpp.reshape(vp, c.getWidth(), c.getHeight());
    }

    public boolean split() { return split; }

    // ================================================================ Update

    public float zoneDark() { return zoneDark; }

    public float zoneBright() { return zoneBright; }

    public void update(float tpf, Vector3f focus, GameState st, boolean indoors) {
        t += tpf;
        Camera cam = app.getCamera();
        float dk = WorldGen.darkness(focus.x, focus.z), br = WorldGen.brightness(focus.x, focus.z);
        if (st.flags.contains("freed")) dk *= 0.5f;
        zoneDark += (dk - zoneDark) * Math.min(1, tpf * 1.5f);
        zoneBright += (br - zoneBright) * Math.min(1, tpf * 1.5f);
        chunks.update(focus.x, focus.z);
        dayNight.overcast = weather.cover;
        dayNight.update(tpf, cam, zoneDark, zoneBright);
        weather.sealed = inPalace;
        weather.update(tpf, cam, dayNight, indoors);
        vista.update(tpf, cam, dayNight);
        ambience.update(tpf, focus, dayNight, weather);
        Vector3f sd = dayNight.sunDir;
        boolean raysOn = sd.y > -0.02f && sd.y < 0.45f && cam.getDirection().dot(sd) > 0.2f && weather.cover < 0.75f;
        sunRays.setEnabled(raysOn);
        if (raysOn) {
            sunRays.setLightPosition(cam.getLocation().add(sd.mult(800)));
            sunRays.setLightDensity((0.55f + 0.6f * (1 - sd.y / 0.45f)) * (1 - weather.cover * 0.8f));
        }

        float night = dayNight.night;
        float density = 0.0024f + night * 0.0028f + zoneDark * 0.012f + weather.rain * 0.006f + weather.cover * 0.0012f - zoneBright * 0.0008f;
        ColorRGBA fc = dayNight.fogColor.clone();
        fc = G3.lerp(fc, new ColorRGBA(0.42f, 0.44f, 0.47f, 1).multLocal(0.3f + 0.7f * (1 - night)), weather.rain * 0.6f);
        grade.mood(night, zoneDark, zoneBright, indoors);
        if (grade2 != null) grade2.mood(night, zoneDark, zoneBright, indoors);
        ColorRGBA wc = G3.lerp(new ColorRGBA(0.01f, 0.03f, 0.06f, 1), new ColorRGBA(0.05f, 0.16f, 0.22f, 1), 1 - night);
        for (int i = 0; i < 2; i++) {
            SkyFogFilter f = i == 0 ? fog : fog2;
            DirectionalLightShadowFilter sh = i == 0 ? shadows : shadows2;
            WaterFilter w = i == 0 ? water : water2;
            if (f == null) continue;
            f.set(fc, Math.max(0.0012f, density), 0.06f + weather.cover * 0.25f + zoneDark * 0.3f);
            sh.setShadowIntensity(0.25f + 0.4f * (1 - night) * (1 - weather.cover * 0.6f));
            w.setLightDirection(dayNight.light.getDirection());
            w.setLightColor(dayNight.light.getColor());
            w.setWaterColor(wc);
            w.setDeepWaterColor(wc.mult(0.4f));
        }

        lantern.setPosition(focus.add(0, 2.4f, 0));
        lantern.setColor(new ColorRGBA(1f, 0.76f, 0.48f, 1).multLocal(inPalace ? 1.1f : 0.15f + 1.1f * night));
        if (!inPalace) { fallT += tpf; palace.animateFalls(fallT); if (street != null) street.update(tpf, focus); }
        if (inPalace) palace.update(tpf);
        else if (puzzles != null) puzzles.update(tpf);
        for (int i = 0; i < nightLights.size(); i++) {
            nightLights.get(i).setColor(nightLightBase.get(i).mult(0.2f + 0.8f * night));
        }

        float health = st.count() / 6f;
        if (st.flags.contains("freed")) health = 1;
        ColorRGBA hc = G3.lerp(new ColorRGBA(0.55f, 0.25f, 0.75f, 1), new ColorRGBA(0.45f, 0.95f, 0.55f, 1), health);
        if (heartLeaves != null) heartLeaves.setColor("Diffuse", G3.lerp(new ColorRGBA(0.55f, 0.4f, 0.6f, 1), new ColorRGBA(1, 1, 1, 1), health));
        if (heartVeins != null) heartVeins.setColor("GlowColor", hc.mult(0.12f + 0.08f * FastMath.sin(t * 2)));
        heartLight.setColor(hc.mult(1.3f + 0.3f * FastMath.sin(t * 2)));
        Spatial motes = root.getChild("heartMotes");
        if (motes instanceof ParticleEmitter pe) pe.setStartColor(hc.clone().setAlpha(0.9f));
        if (crystal != null) crystal.setLocalRotation(new Quaternion().fromAngleAxis(t * 0.4f, Vector3f.UNIT_Y));
        for (Geometry g : floating) {
            float b = g.getUserData("bob");
            Vector3f p = g.getLocalTranslation();
            g.setLocalTranslation(p.x, 2.2f + FastMath.sin(t + b) * 0.6f, p.z);
        }
        fireflies.setLocalTranslation(focus.x, height(focus.x, focus.z), focus.z);
        float ff = night * (1 - weather.rain) * (0.4f + zoneBright);
        fireflies.setParticlesPerSec(ff * 22);
        for (Iterator<ParticleEmitter> it = bursts.iterator(); it.hasNext(); ) {
            ParticleEmitter e = it.next();
            float life = (Float) e.getUserData("life") - tpf;
            e.setUserData("life", life);
            if (life <= 0) { e.removeFromParent(); it.remove(); }
        }
    }

    /** Markierungen über Figuren mit offenen Prüfungen. */
    public void updateMarkers(GameState st, List<Npc> npcs) {
        for (Npc n : npcs) {
            Geometry m = markers.get(n.id);
            if (m == null) continue;
            boolean open = (n.shard != null && !st.has(n.shard)) || (n.id.equals("heart") && st.allShards() && !st.flags.contains("ended"));
            m.setCullHint(open ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            Vector3f p = npcPos.get(n.id);
            Figure f = figures.get(n.id);
            float top = f != null ? f.height + 0.9f : 9f;
            m.setLocalTranslation(p.x, p.y + top + FastMath.sin(t * 2.5f) * 0.15f, p.z);
            m.setLocalRotation(new Quaternion().fromAngleAxis(t * 1.5f, Vector3f.UNIT_Y));
        }
        corvina.node.setCullHint(st.allShards() && !st.flags.contains("freed") ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }
}
