package daemmerwald;

import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import jme3tools.optimize.GeometryBatchFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Laternhain – das Feendorf am Laternbach: Pilzhäuser mit runden Türen, Baumhäuser mit Hängebrücke,
 * eine geschwungene Holzbrücke, Laternen, Lichterketten, ein Trankladen und leuchtende Pilze.
 */
public final class Village {
    public final Node root = new Node("village");
    /** Hindernisse {x, z, radius}. */
    public final List<float[]> solids = new ArrayList<>();
    public final List<PointLight> lights = new ArrayList<>();
    /** Brücke: Mitte, Richtung (quer zum Bach), halbe Länge und Bogenhöhe. */
    public final Vector3f bridgeMid = new Vector3f();
    public final Vector3f bridgeDir = new Vector3f();
    public float bridgeHalf = 6.2f, bridgeArch = 1.3f, bridgeDeck;

    private final Random rnd = new Random(77);
    private final Node bulbs = new Node("bulbs");
    private static final ColorRGBA[] BULB = {new ColorRGBA(1f, 0.82f, 0.45f, 1), new ColorRGBA(1f, 0.6f, 0.85f, 1),
        new ColorRGBA(0.6f, 0.85f, 1f, 1), new ColorRGBA(0.85f, 0.7f, 1f, 1)};
    private final Material[] bulbMat = new Material[BULB.length];
    private final Mesh bulbMesh = new Sphere(6, 8, 0.055f);
    private Material capRed, capBrown, wallStone, wallPlaster, planks, bark, thatch, darkWood;

    public Village() {
        for (int i = 0; i < BULB.length; i++) bulbMat[i] = G3.emissive(BULB[i].mult(1.15f));
        capRed = capMaterial(new Color(170, 52, 38), new Color(238, 222, 196), 11);
        capBrown = capMaterial(new Color(128, 72, 40), new Color(222, 200, 160), 23);
        wallStone = G3.ph("mossy_stone_wall", new ColorRGBA(1f, 0.95f, 0.88f, 1));
        wallPlaster = G3.ph("clay_plaster", new ColorRGBA(1f, 0.93f, 0.8f, 1));
        planks = G3.ph("brown_planks_05");
        bark = G3.ph("bark_brown_02");
        thatch = G3.ph("thatch_roof_angled", new ColorRGBA(0.9f, 0.8f, 0.65f, 1));
        darkWood = G3.lit(new ColorRGBA(0.3f, 0.18f, 0.1f, 1));
        build();
    }

    private static float h(float x, float z) { return WorldGen.height(x, z); }

    // ================================================================ Aufbau

    private void build() {
        // Brücke an der Stelle, an der der Weg den Bach kreuzt
        double[] b = WorldGen.streamPoint(2, 0.25);
        double[] b2 = WorldGen.streamPoint(2, 0.3);
        Vector3f flow = new Vector3f((float) (b2[0] - b[0]), 0, (float) (b2[1] - b[1])).normalizeLocal();
        bridgeDir.set(-flow.z, 0, flow.x);
        if (bridgeDir.x < 0) bridgeDir.negateLocal();
        float bankH = Math.max(h((float) b[0] + bridgeDir.x * 7, (float) b[1] + bridgeDir.z * 7),
            h((float) b[0] - bridgeDir.x * 7, (float) b[1] - bridgeDir.z * 7));
        bridgeDeck = bankH;
        bridgeMid.set((float) b[0], bankH, (float) b[1]);
        bridge();

        // Pilzhäuser auf beiden Ufern
        float[][] houses = {{-80, -29, 4.2f, 0}, {-84, -52, 3.6f, 1}, {-105, -33, 3.8f, 1}, {-107, -49, 4.4f, 0}, {-99, -60, 3.2f, 0}, {-74, -40, 3.0f, 1}};
        for (float[] hs : houses) mushroomHouse(hs[0], hs[1], hs[2], hs[3] > 0);
        // Baumhäuser mit Hängebrücke
        Vector3f t1 = treeHouse(-72, -55, 0.4f), t2 = treeHouse(-66, -42, 2.6f);
        ropeBridge(t1, t2);
        // Trankladen und Wegweiser
        potionStall(-82, -44.5f, FastMath.atan2(bridgeMid.x - (-82), bridgeMid.z - (-44.5f)));
        signpost(-79.5f, -40.5f);
        // Laternen entlang des Weges und an der Brücke
        Vector3f e = bridgeMid.add(bridgeDir.mult(bridgeHalf + 0.8f)), w = bridgeMid.subtract(bridgeDir.mult(bridgeHalf + 0.8f));
        Vector3f side = new Vector3f(-bridgeDir.z, 0, bridgeDir.x).multLocal(1.4f);
        lanternPost(e.x + side.x, e.z + side.z, true);
        lanternPost(w.x - side.x, w.z - side.z, true);
        float[][] path = {{-70, -46}, {-62, -50}, {-77, -40.5f}};
        for (float[] p : path) lanternPost(p[0], p[1], false);
        // Lichterketten zwischen Häusern und Bäumen
        fairyString(new Vector3f(-80, h(-80, -29) + 3.4f, -29), new Vector3f(-74, h(-74, -40) + 2.8f, -40), 0.9f);
        fairyString(new Vector3f(-74, h(-74, -40) + 2.8f, -40), new Vector3f(-66, h(-66, -42) + 5.6f, -42), 0.8f);
        fairyString(new Vector3f(-84, h(-84, -52) + 3.0f, -52), new Vector3f(-72, h(-72, -55) + 5.6f, -55), 1.1f);
        fairyString(new Vector3f(-105, h(-105, -33) + 3.1f, -33), new Vector3f(-107, h(-107, -49) + 3.5f, -49), 1.2f);
        fairyString(new Vector3f(-107, h(-107, -49) + 3.5f, -49), new Vector3f(-99, h(-99, -60) + 2.6f, -60), 0.9f);
        // Leuchtpilze, Farne, Blumen, Seerosen
        decorate();
        GeometryBatchFactory.optimize(bulbs);
        bulbs.setShadowMode(ShadowMode.Off);
        root.attachChild(bulbs);
    }

    // ---------------------------------------------------------------- Formen

    /** Drehkörper aus einem Profil (Radius, Höhe), von unten nach oben; mit Normalen und Texturkoordinaten. */
    static Mesh lathe(float[] r, float[] y, int seg, float uRepeat) {
        int n = r.length;
        float[] pos = new float[n * (seg + 1) * 3], nor = new float[n * (seg + 1) * 3], uv = new float[n * (seg + 1) * 2];
        float[] len = new float[n];
        for (int j = 1; j < n; j++) len[j] = len[j - 1] + (float) Math.hypot(r[j] - r[j - 1], y[j] - y[j - 1]);
        for (int j = 0; j < n; j++) {
            int a = Math.max(0, j - 1), b = Math.min(n - 1, j + 1);
            float dr = r[b] - r[a], dy = y[b] - y[a];
            float l = (float) Math.hypot(dr, dy);
            float nr = dy / l, ny = -dr / l;
            for (int i = 0; i <= seg; i++) {
                float ang = i * FastMath.TWO_PI / seg, c = FastMath.cos(ang), s = FastMath.sin(ang);
                int k = j * (seg + 1) + i;
                pos[k * 3] = r[j] * c; pos[k * 3 + 1] = y[j]; pos[k * 3 + 2] = r[j] * s;
                nor[k * 3] = nr * c; nor[k * 3 + 1] = ny; nor[k * 3 + 2] = nr * s;
                uv[k * 2] = i / (float) seg * uRepeat; uv[k * 2 + 1] = len[j] / Math.max(0.001f, len[n - 1]);
            }
        }
        int[] idx = new int[(n - 1) * seg * 6];
        int q = 0;
        for (int j = 0; j < n - 1; j++) {
            for (int i = 0; i < seg; i++) {
                int a = j * (seg + 1) + i, b = a + seg + 1;
                idx[q++] = a; idx[q++] = b; idx[q++] = a + 1;
                idx[q++] = a + 1; idx[q++] = b; idx[q++] = b + 1;
            }
        }
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        m.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nor));
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        m.updateBound();
        return m;
    }

    /** Pilzhut mit Tupfen (gemalte Textur, leicht schimmernd). */
    private static Material capMaterial(Color base, Color spot, long seed) {
        int w = 512, hgt = 256;
        BufferedImage img = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, base.darker().darker(), 0, hgt, base.brighter()));
        g.fillRect(0, 0, w, hgt);
        Random r = new Random(seed);
        // feine Maserung
        for (int i = 0; i < 2600; i++) {
            int x = r.nextInt(w), y = r.nextInt(hgt);
            g.setColor(new Color(0, 0, 0, 10 + r.nextInt(18)));
            g.fillRect(x, y, 1 + r.nextInt(3), 6 + r.nextInt(18));
        }
        // Moosflecken am Rand (unten in der Textur)
        for (int i = 0; i < 70; i++) {
            int x = r.nextInt(w), y = hgt - r.nextInt(50);
            g.setColor(new Color(80 + r.nextInt(40), 110 + r.nextInt(40), 50, 90));
            g.fillOval(x, y, 10 + r.nextInt(30), 6 + r.nextInt(14));
        }
        // Tupfen
        for (int i = 0; i < 26; i++) {
            int x = r.nextInt(w), y = 20 + r.nextInt(hgt - 70);
            int s = 10 + r.nextInt(22);
            g.setColor(spot.darker());
            g.fillOval(x - 1, y + 1, s + 2, (int) (s * 0.8f) + 2);
            g.setColor(spot);
            g.fillOval(x, y, s, (int) (s * 0.8f));
            g.setColor(new Color(255, 255, 255, 70));
            g.fillOval(x + s / 4, y + s / 6, s / 3, s / 4);
        }
        g.dispose();
        Material m = G3.lit(ColorRGBA.White);
        Texture t = G3.tex(img);
        t.setWrap(Texture.WrapMode.Repeat);
        m.setTexture("DiffuseMap", t);
        m.setColor("Specular", new ColorRGBA(0.25f, 0.22f, 0.2f, 1));
        m.setFloat("Shininess", 24);
        return m;
    }

    private Geometry geo(String name, Mesh mesh, Material m, Node parent, float x, float y, float z) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    private Geometry box(Node p, Material m, float hx, float hy, float hz, float x, float y, float z) {
        return geo("box", new Box(hx, hy, hz), m, p, x, y, z);
    }

    private Geometry cylY(Node p, Material m, float r1, float r2, float hgt, int sides, float x, float y, float z) {
        Geometry g = geo("cyl", new Cylinder(2, sides, r1, r2, hgt, true, false), m, p, x, y, z);
        g.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        return g;
    }

    private void bulb(Node p, Vector3f at, int c) {
        geo("bulb", bulbMesh, bulbMat[c % bulbMat.length], p, at.x, at.y, at.z);
    }

    // ---------------------------------------------------------------- Pilzhaus

    private void mushroomHouse(float x, float z, float r, boolean thatchRoof) {
        Node n = new Node("mushroomHouse");
        float base = h(x, z);
        float wallH = 2.5f + r * 0.12f;
        // Wand: leicht bauchiger Stein- oder Lehmturm
        Mesh wall = lathe(new float[]{r * 0.98f, r, r * 0.97f, r * 0.9f}, new float[]{-0.4f, 0.4f, wallH * 0.7f, wallH}, 28, 4);
        Geometry wg = geo("wall", wall, thatchRoof ? wallPlaster : wallStone, n, 0, 0, 0);
        G3.tangents(wg);
        if (thatchRoof) {
            // Spitzes Reetdach (wie ein Hexenhut) mit kleinem Überstand
            float R = r * 1.35f;
            Mesh roof = lathe(new float[]{0, R * 0.95f, R, R * 0.82f, R * 0.5f, R * 0.18f, 0.02f},
                new float[]{wallH - 0.1f, wallH - 0.25f, wallH - 0.1f, wallH + 0.9f, wallH + 2.4f, wallH + 4.0f, wallH + 4.8f}, 28, 6);
            Geometry rg = geo("roof", roof, thatch, n, 0, 0, 0);
            G3.tangents(rg);
            rg.setLocalRotation(new Quaternion().fromAngles(0.06f, 0, -0.05f));
        } else {
            // Pilzhut: weit ausladend, rund, mit Tupfen
            float R = r * 1.6f;
            Mesh cap = lathe(new float[]{0, R * 0.55f, R * 0.95f, R, R * 0.96f, R * 0.82f, R * 0.55f, R * 0.25f, 0.01f},
                new float[]{wallH + 0.15f, wallH - 0.05f, wallH - 0.35f, wallH - 0.15f, wallH + 0.4f, wallH + 1.3f, wallH + 2.1f, wallH + 2.5f, wallH + 2.6f}, 36, 3);
            geo("cap", cap, capRed, n, 0, 0, 0);
        }
        // Tür (zur Dorfmitte), runde Fenster, Schornstein
        float face = FastMath.atan2(WorldGen.VILLAGE[0] - x, WorldGen.VILLAGE[1] - z);
        Node front = new Node("front");
        front.setLocalRotation(new Quaternion().fromAngleAxis(face, Vector3f.UNIT_Y));
        n.attachChild(front);
        roundDoor(front, 0, 0, r * 0.97f);
        for (int s = -1; s <= 1; s += 2) roundWindow(front, s * 0.62f, 1.55f, s);
        Node back = new Node("back");
        back.setLocalRotation(new Quaternion().fromAngleAxis(face + 2.4f, Vector3f.UNIT_Y));
        n.attachChild(back);
        Geometry chim = cylY(back, wallStone, 0.28f, 0.32f, 1.6f, 10, 0, wallH + (thatchRoof ? 2.4f : 2.0f), -r * 0.7f);
        G3.tangents(chim);
        smoke(n, new Vector3f(FastMath.sin(face + 2.4f + FastMath.PI) * r * 0.7f, wallH + (thatchRoof ? 3.3f : 2.9f), FastMath.cos(face + 2.4f + FastMath.PI) * r * 0.7f));
        // Blumenkästen, Bank, Laterne an der Tür
        Material pot = G3.lit(new ColorRGBA(0.72f, 0.38f, 0.22f, 1));
        for (int s = -1; s <= 1; s += 2) {
            Vector3f p = new Vector3f(s * 1.3f, 0, r + 0.25f);
            cylY(front, pot, 0.22f, 0.16f, 0.38f, 12, p.x, 0.19f, p.z);
            flowers(front, p.x, 0.38f, p.z, 6, 0.25f);
        }
        hangingLantern(front, 0.95f, 2.25f, r + 0.35f);
        n.setLocalTranslation(x, base, z);
        n.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(n);
        solids.add(new float[]{x, z, r + 0.3f});
    }

    private void roundDoor(Node front, float x, float y, float zr) {
        Material door = planks;
        float w = 0.62f, hgt = 1.45f;
        Geometry d = box(front, door, w, hgt / 2, 0.06f, x, hgt / 2, zr + 0.02f);
        G3.tangents(d);
        Geometry top = geo("doorTop", new Cylinder(2, 20, w, 0.12f, true), door, front, x, hgt, zr + 0.02f);
        G3.tangents(top);
        // Rahmen aus Stein
        Mesh arch = new com.jme3.scene.shape.Torus(20, 6, 0.09f, w + 0.08f);
        Geometry a = geo("doorArch", arch, wallStone, front, x, hgt, zr + 0.06f);
        G3.tangents(a);
        box(front, darkWood, 0.04f, hgt / 2, 0.08f, x - w - 0.04f, hgt / 2, zr + 0.05f);
        box(front, darkWood, 0.04f, hgt / 2, 0.08f, x + w + 0.04f, hgt / 2, zr + 0.05f);
        // Runder Türknauf und kleines Bullauge
        Material brass = G3.lit(new ColorRGBA(0.95f, 0.75f, 0.35f, 1));
        brass.setColor("Specular", ColorRGBA.White);
        brass.setFloat("Shininess", 50);
        geo("knob", new Sphere(8, 8, 0.05f), brass, front, x + 0.4f, 0.95f, zr + 0.12f);
        Geometry port = geo("porthole", new Cylinder(2, 16, 0.17f, 0.02f, true), G3.litGlow(new ColorRGBA(1f, 0.85f, 0.55f, 1), new ColorRGBA(0.9f, 0.6f, 0.25f, 1)), front, x, 1.62f, zr + 0.09f);
        port.setShadowMode(ShadowMode.Off);
        Geometry ring = geo("portRing", new com.jme3.scene.shape.Torus(16, 6, 0.03f, 0.18f), brass, front, x, 1.62f, zr + 0.11f);
        ring.setShadowMode(ShadowMode.Off);
        // Stufen
        Geometry step = box(front, wallStone, 0.75f, 0.08f, 0.32f, x, 0.04f, zr + 0.35f);
        G3.tangents(step);
    }

    /** Rundes, warm leuchtendes Fenster mit Sprossenkreuz. side 2 = seitlich. */
    private void roundWindow(Node front, float x, float y, int side) {
        Node w = new Node("window");
        Geometry pane = geo("pane", new Cylinder(2, 20, 0.32f, 0.02f, true), G3.litGlow(new ColorRGBA(1f, 0.82f, 0.5f, 1), new ColorRGBA(1f, 0.62f, 0.25f, 1)), w, 0, 0, 0);
        pane.setShadowMode(ShadowMode.Off);
        geo("frame", new com.jme3.scene.shape.Torus(20, 6, 0.045f, 0.33f), darkWood, w, 0, 0, 0.02f);
        box(w, darkWood, 0.025f, 0.32f, 0.03f, 0, 0, 0.03f);
        box(w, darkWood, 0.32f, 0.025f, 0.03f, 0, 0, 0.03f);
        // Blumenkasten darunter
        box(w, planks, 0.36f, 0.07f, 0.1f, 0, -0.42f, 0.1f);
        flowers(w, 0, -0.35f, 0.1f, 5, 0.3f);
        if (side == 2) {
            w.setLocalTranslation(0, 1.6f, 0);
            Node pivot = new Node("sideWin");
            pivot.attachChild(w);
            pivot.setLocalRotation(new Quaternion().fromAngleAxis(-1.6f, Vector3f.UNIT_Y));
            w.setLocalTranslation(0, 1.6f, 0);
            // auf die Wand setzen
            w.move(0, 0, 0);
            front.attachChild(pivot);
            // Abstand zur Wand wird vom Aufrufer über den Hausradius bestimmt
            w.setUserData("wall", 1);
            return;
        }
        w.setLocalTranslation(x, y, 0);
        front.attachChild(w);
    }

    private void hangingLantern(Node p, float x, float y, float z) {
        box(p, darkWood, 0.03f, 0.03f, 0.25f, x, y + 0.28f, z - 0.2f);
        Spatial l = G3.model("wooden_lantern_01");
        l.setLocalScale(0.9f);
        l.setLocalTranslation(x, y - 0.25f, z);
        p.attachChild(l);
        Geometry glow = geo("lanternGlow", new Sphere(8, 8, 0.07f), G3.emissive(new ColorRGBA(1f, 0.75f, 0.35f, 1)), p, x, y - 0.05f, z);
        glow.setShadowMode(ShadowMode.Off);
    }

    private void smoke(Node n, Vector3f p) {
        com.jme3.effect.ParticleEmitter e = G3.particles("villageSmoke", 30, "Effects/Smoke/Smoke.png",
            new ColorRGBA(0.82f, 0.82f, 0.85f, 0.32f), new ColorRGBA(0.7f, 0.7f, 0.75f, 0), 0.35f, 1.8f, 3f, 5f);
        e.setImagesX(15);
        e.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        e.getParticleInfluencer().setInitialVelocity(new Vector3f(0.2f, 0.75f, 0));
        e.getParticleInfluencer().setVelocityVariation(0.25f);
        e.setParticlesPerSec(5);
        e.setLocalTranslation(p);
        n.attachChild(e);
    }

    // ---------------------------------------------------------------- Brücke über den Bach

    /** Deckhöhe der Brücke an (x, z) – oder NaN, wenn man nicht auf der Brücke steht. */
    public float deckHeight(float x, float z) {
        float dx = x - bridgeMid.x, dz = z - bridgeMid.z;
        float along = dx * bridgeDir.x + dz * bridgeDir.z;
        float across = -dx * bridgeDir.z + dz * bridgeDir.x;
        if (Math.abs(along) > bridgeHalf || Math.abs(across) > 1.05f) return Float.NaN;
        float t = along / bridgeHalf;
        return bridgeDeck + bridgeArch * (1 - t * t) + 0.12f;
    }

    private void bridge() {
        Node n = new Node("bridge");
        float yaw = FastMath.atan2(bridgeDir.x, bridgeDir.z);
        int count = 30;
        for (int i = 0; i <= count; i++) {
            float t = -1 + 2f * i / count, along = t * bridgeHalf;
            float y = bridgeArch * (1 - t * t);
            float slope = -2 * t * bridgeArch / bridgeHalf;
            Geometry p = box(n, planks, 1.0f, 0.05f, bridgeHalf / count * 0.92f, 0, y, along);
            p.setLocalRotation(new Quaternion().fromAngles(-FastMath.atan(slope), 0, (rnd.nextFloat() - 0.5f) * 0.03f));
            G3.tangents(p);
        }
        // Tragbalken und Geländer mit Pfosten
        for (int s = -1; s <= 1; s += 2) {
            Vector3f prev = null;
            for (int i = 0; i <= 12; i++) {
                float t = -1 + 2f * i / 12, along = t * bridgeHalf;
                float y = bridgeArch * (1 - t * t);
                Vector3f top = new Vector3f(s * 0.98f, y + 0.95f, along);
                if (i % 2 == 0) {
                    Geometry post = box(n, darkWood, 0.06f, 0.5f, 0.06f, s * 0.98f, y + 0.45f, along);
                    if (i == 0 || i == 12) {
                        post.setLocalScale(1.4f, 1.5f, 1.4f);
                        bulb(n, new Vector3f(s * 0.98f, y + 1.25f, along), 0);
                    }
                }
                if (prev != null) {
                    segment(n, darkWood, prev, top, 0.045f);
                    // geschwungener Unterbalken
                    segment(n, bark, prev.add(0, -0.95f - 0.12f, 0), top.add(0, -0.95f - 0.12f, 0), 0.09f);
                    // Lichterkette am Geländer
                    for (int k = 0; k < 3; k++) {
                        Vector3f q = prev.clone().interpolateLocal(top, (k + 0.5f) / 3f).addLocal(0, 0.06f, 0);
                        bulb(bulbs == null ? n : n, q, (i + k) % 4);
                    }
                    // Ranken
                    if (rnd.nextFloat() < 0.5f) flowers(n, top.x, top.y - 0.05f, top.z, 3, 0.15f);
                }
                prev = top;
            }
        }
        n.setLocalTranslation(bridgeMid.x, bridgeDeck, bridgeMid.z);
        n.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        n.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(n);
        lights.add(new PointLight(bridgeMid.add(0, bridgeArch + 1.6f, 0), new ColorRGBA(1f, 0.7f, 0.4f, 1).mult(1.3f), 10));
    }

    /** Balken von a nach b (lokale Koordinaten). */
    private Geometry segment(Node n, Material m, Vector3f a, Vector3f b, float r) {
        Vector3f d = b.subtract(a);
        float len = d.length();
        Geometry g = geo("seg", new Box(r, r, len / 2 + r * 0.5f), m, n, (a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2);
        Quaternion q = new Quaternion();
        q.lookAt(d.normalize(), Vector3f.UNIT_Y);
        g.setLocalRotation(q);
        return g;
    }

    // ---------------------------------------------------------------- Baumhäuser

    /** Großer Baum mit Plattform, Geländer, kleiner Hütte mit Spitzdach und Strickleiter. Liefert die Plattformmitte. */
    private Vector3f treeHouse(float x, float z, float yaw) {
        Node n = new Node("treeHouse");
        float base = h(x, z);
        // Stamm mit Wurzeln
        Mesh trunk = lathe(new float[]{2.2f, 1.5f, 1.15f, 1.0f, 0.9f, 0.85f}, new float[]{-0.5f, 0.5f, 1.8f, 4f, 7f, 10.5f}, 18, 3);
        Geometry tg = geo("trunk", trunk, bark, n, 0, 0, 0);
        G3.tangents(tg);
        for (int i = 0; i < 6; i++) {
            float a = i * FastMath.TWO_PI / 6 + 0.3f;
            Geometry root = geo("root", new Cylinder(2, 8, 0.32f, 0.08f, 2.8f, true, false), bark, n, FastMath.sin(a) * 1.7f, 0.2f, FastMath.cos(a) * 1.7f);
            root.setLocalRotation(new Quaternion().fromAngles(0, a, 0).mult(new Quaternion().fromAngles(-0.15f, 0, 0)));
            G3.tangents(root);
        }
        // Krone: das große Baummodell der Engine
        try {
            Spatial crown = G3.am.loadModel("Models/Tree/Tree.mesh.j3o");
            crown.setLocalScale(4.2f);
            crown.setLocalTranslation(0, 6.5f, 0);
            n.attachChild(crown);
        } catch (Exception ignored) { }
        // Plattform
        float py = 5.0f, pr = 2.9f;
        Geometry deck = cylY(n, planks, pr, pr, 0.16f, 24, 0, py, 0);
        G3.tangents(deck);
        for (int i = 0; i < 16; i++) {
            float a = i * FastMath.TWO_PI / 16;
            box(n, darkWood, 0.05f, 0.45f, 0.05f, FastMath.sin(a) * (pr - 0.1f), py + 0.5f, FastMath.cos(a) * (pr - 0.1f));
            float a2 = (i + 1) * FastMath.TWO_PI / 16;
            segment(n, darkWood, new Vector3f(FastMath.sin(a) * (pr - 0.1f), py + 0.95f, FastMath.cos(a) * (pr - 0.1f)),
                new Vector3f(FastMath.sin(a2) * (pr - 0.1f), py + 0.95f, FastMath.cos(a2) * (pr - 0.1f)), 0.035f);
            bulb(n, new Vector3f(FastMath.sin(a) * (pr - 0.1f), py + 1.02f, FastMath.cos(a) * (pr - 0.1f)), i);
        }
        // Stützen schräg zum Stamm
        for (int i = 0; i < 4; i++) {
            float a = i * FastMath.HALF_PI + 0.4f;
            segment(n, bark, new Vector3f(FastMath.sin(a) * 1.0f, py - 2.2f, FastMath.cos(a) * 1.0f), new Vector3f(FastMath.sin(a) * (pr - 0.4f), py - 0.05f, FastMath.cos(a) * (pr - 0.4f)), 0.1f);
        }
        // Hütte mit Spitzdach auf der Plattform (seitlich am Stamm)
        Node hut = new Node("hut");
        float hr = 1.15f;
        Mesh hw = lathe(new float[]{hr, hr * 1.02f, hr * 0.95f}, new float[]{0, 1.0f, 1.9f}, 20, 3);
        Geometry hwg = geo("hutWall", hw, wallStone, hut, 0, 0, 0);
        G3.tangents(hwg);
        Mesh hroof = lathe(new float[]{0, hr * 1.55f, hr * 1.5f, hr * 0.9f, hr * 0.3f, 0.02f}, new float[]{1.8f, 1.65f, 1.85f, 2.7f, 3.6f, 4.1f}, 20, 4);
        Geometry hrg = geo("hutRoof", hroof, rnd.nextBoolean() ? thatch : capBrown, hut, 0, 0, 0);
        G3.tangents(hrg);
        Geometry door = box(hut, planks, 0.38f, 0.6f, 0.04f, 0, 0.6f, hr + 0.01f);
        G3.tangents(door);
        Geometry win = geo("hutWin", new Cylinder(2, 16, 0.22f, 0.02f, true), G3.litGlow(new ColorRGBA(1f, 0.85f, 0.55f, 1), new ColorRGBA(1f, 0.6f, 0.25f, 1)), hut, 0.75f, 1.3f, hr * 0.75f);
        win.setLocalRotation(new Quaternion().fromAngleAxis(0.75f, Vector3f.UNIT_Y));
        hut.setLocalTranslation(FastMath.sin(yaw) * 1.6f, py + 0.08f, FastMath.cos(yaw) * 1.6f);
        hut.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        n.attachChild(hut);
        smoke(n, new Vector3f(FastMath.sin(yaw) * 1.6f, py + 4.3f, FastMath.cos(yaw) * 1.6f));
        hangingLantern(n, FastMath.sin(yaw + 1.2f) * (pr + 0.05f), py - 0.15f, FastMath.cos(yaw + 1.2f) * (pr + 0.05f));
        // Strickleiter
        float la = yaw + FastMath.PI;
        Node ladder = new Node("ladder");
        for (int s = -1; s <= 1; s += 2) box(ladder, G3.lit(new ColorRGBA(0.62f, 0.52f, 0.36f, 1)), 0.02f, py / 2, 0.02f, s * 0.28f, py / 2, 0);
        for (int i = 1; i < 14; i++) box(ladder, planks, 0.3f, 0.025f, 0.05f, 0, i * py / 14, 0);
        ladder.setLocalTranslation(FastMath.sin(la) * (pr + 0.05f), 0, FastMath.cos(la) * (pr + 0.05f));
        ladder.setLocalRotation(new Quaternion().fromAngleAxis(la, Vector3f.UNIT_Y));
        n.attachChild(ladder);
        n.setLocalTranslation(x, base, z);
        n.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(n);
        solids.add(new float[]{x, z, 1.5f});
        lights.add(new PointLight(new Vector3f(x, base + py + 1.6f, z), new ColorRGBA(1f, 0.72f, 0.42f, 1).mult(1.1f), 9));
        return new Vector3f(x, base + py, z);
    }

    /** Hängebrücke zwischen zwei Plattformen (Bretter durchhängend, Seile, Lichter). */
    private void ropeBridge(Vector3f a, Vector3f b) {
        Node n = new Node("ropeBridge");
        Vector3f dir = b.subtract(a).setY(0).normalizeLocal();
        Vector3f side = new Vector3f(-dir.z, 0, dir.x);
        Vector3f s0 = a.add(dir.mult(2.8f)), s1 = b.subtract(dir.mult(2.8f));
        int count = 22;
        Material rope = G3.lit(new ColorRGBA(0.62f, 0.52f, 0.36f, 1));
        Vector3f[] prevRail = new Vector3f[2];
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count;
            Vector3f p = s0.clone().interpolateLocal(s1, t);
            p.y -= FastMath.sin(t * FastMath.PI) * 1.1f;
            Geometry pl = box(n, planks, 0.55f, 0.03f, 0.14f, p.x, p.y + 0.12f, p.z);
            pl.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.atan2(dir.x, dir.z), Vector3f.UNIT_Y));
            for (int s = 0; s < 2; s++) {
                Vector3f rail = p.add(side.mult(s == 0 ? -0.6f : 0.6f)).addLocal(0, 0.95f - FastMath.sin(t * FastMath.PI) * 0.25f, 0);
                if (prevRail[s] != null) {
                    segment(n, rope, prevRail[s], rail, 0.02f);
                    if (i % 2 == 0) bulb(n, prevRail[s].clone().interpolateLocal(rail, 0.5f).addLocal(0, -0.06f, 0), i / 2 + s);
                }
                if (i % 3 == 0) segment(n, rope, rail, p.add(side.mult(s == 0 ? -0.55f : 0.55f)).addLocal(0, 0.12f, 0), 0.012f);
                prevRail[s] = rail;
            }
        }
        root.attachChild(n);
    }

    // ---------------------------------------------------------------- Trankladen, Wegweiser, Laternen

    private void potionStall(float x, float z, float yaw) {
        Node n = new Node("potionStall");
        float base = h(x, z);
        Geometry counter = box(n, planks, 1.4f, 0.5f, 0.45f, 0, 0.5f, 0.3f);
        G3.tangents(counter);
        box(n, darkWood, 1.45f, 0.04f, 0.5f, 0, 1.02f, 0.3f);
        // Rückwand mit Regalbrettern
        Geometry backW = box(n, planks, 1.4f, 1.2f, 0.06f, 0, 1.2f, -0.5f);
        G3.tangents(backW);
        ColorRGBA[] pc = {new ColorRGBA(0.4f, 0.95f, 0.55f, 1), new ColorRGBA(0.95f, 0.4f, 0.85f, 1), new ColorRGBA(0.4f, 0.65f, 1f, 1), new ColorRGBA(1f, 0.8f, 0.3f, 1), new ColorRGBA(0.75f, 0.45f, 1f, 1)};
        for (int sh = 0; sh < 3; sh++) {
            box(n, darkWood, 1.35f, 0.03f, 0.16f, 0, 0.75f + sh * 0.55f, -0.36f);
            for (int k = 0; k < 9; k++) {
                ColorRGBA c = pc[(k + sh * 2) % pc.length];
                float bx = -1.2f + k * 0.3f, by = 0.78f + sh * 0.55f;
                float bh = 0.1f + rnd.nextFloat() * 0.1f;
                Geometry bottle = geo("bottle", new Sphere(10, 10, 0.08f + rnd.nextFloat() * 0.03f), G3.litGlow(c.mult(0.8f), c.mult(0.55f)), n, bx, by + 0.09f, -0.34f);
                bottle.setLocalScale(1, 1 + bh * 3, 1);
                bottle.setShadowMode(ShadowMode.Off);
                cylY(n, G3.lit(new ColorRGBA(0.55f, 0.4f, 0.28f, 1)), 0.025f, 0.025f, 0.06f, 6, bx, by + 0.2f + bh * 0.25f, -0.34f);
            }
        }
        // Flaschen und Kessel auf der Theke
        for (int k = 0; k < 5; k++) {
            ColorRGBA c = pc[k];
            Geometry bottle = geo("bottle", new Sphere(10, 10, 0.1f), G3.litGlow(c.mult(0.8f), c.mult(0.6f)), n, -1.0f + k * 0.42f, 1.15f, 0.45f);
            bottle.setLocalScale(1, 1.4f, 1);
        }
        // Dach: gestreifte Markise
        BufferedImage img = new BufferedImage(256, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < 8; i++) {
            g.setColor(i % 2 == 0 ? new Color(120, 60, 140) : new Color(236, 214, 170));
            g.fillRect(i * 32, 0, 32, 64);
        }
        g.dispose();
        Material awning = G3.lit(ColorRGBA.White);
        awning.setTexture("DiffuseMap", G3.tex(img));
        awning.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        Geometry aw = box(n, awning, 1.6f, 0.03f, 0.75f, 0, 2.6f, 0.35f);
        aw.setLocalRotation(new Quaternion().fromAngleAxis(0.32f, Vector3f.UNIT_X));
        for (int s = -1; s <= 1; s += 2) box(n, darkWood, 0.05f, 1.25f, 0.05f, s * 1.45f, 1.25f, 0.95f);
        // Schild
        Geometry sign = geo("sign", new Quad(1.8f, 0.45f), signMaterial("Tränke & Tinkturen"), n, -0.9f, 2.25f, 1.08f);
        sign.setShadowMode(ShadowMode.Off);
        hangingLantern(n, 1.45f, 2.3f, 1.05f);
        // Fässer und Körbe
        Spatial bucket = G3.model("wooden_bucket_01");
        bucket.setLocalTranslation(1.9f, 0, 0.5f);
        n.attachChild(bucket);
        Spatial basket = G3.model("wicker_basket_01");
        basket.setLocalTranslation(-1.9f, 0, 0.7f);
        n.attachChild(basket);
        n.setLocalTranslation(x, base, z);
        n.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        n.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(n);
        solids.add(new float[]{x, z, 1.6f});
        lights.add(new PointLight(new Vector3f(x, base + 2.0f, z).addLocal(FastMath.sin(yaw) * 1.4f, 0, FastMath.cos(yaw) * 1.4f), new ColorRGBA(0.9f, 0.6f, 1f, 1).mult(1.0f), 6));
    }

    private static Material signMaterial(String text) {
        BufferedImage img = new BufferedImage(512, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(96, 62, 34), 0, 128, new Color(62, 38, 20)));
        g.fillRoundRect(0, 0, 512, 128, 30, 30);
        Random r = new Random(text.hashCode());
        for (int i = 0; i < 40; i++) {
            g.setColor(new Color(40, 24, 10, 60));
            int y = r.nextInt(128);
            g.drawLine(0, y, 512, y + r.nextInt(5) - 2);
        }
        g.setColor(new Color(236, 200, 120));
        g.setStroke(new BasicStroke(4));
        g.drawRoundRect(8, 8, 496, 112, 24, 24);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, text.length() > 16 ? 42 : 52));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(30, 16, 6));
        Art.str(g, text, 258 - fm.stringWidth(text) / 2, 82);
        g.setColor(new Color(246, 222, 160));
        Art.str(g, text, 256 - fm.stringWidth(text) / 2, 80);
        g.dispose();
        Material m = G3.lit(ColorRGBA.White);
        m.setTexture("DiffuseMap", G3.tex(img));
        m.setFloat("AlphaDiscardThreshold", 0.3f);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        m.setColor("GlowColor", new ColorRGBA(0.08f, 0.05f, 0.02f, 1));
        return m;
    }

    private void signpost(float x, float z) {
        Node n = new Node("signpost");
        Geometry post = cylY(n, bark, 0.09f, 0.11f, 2.6f, 10, 0, 1.3f, 0);
        G3.tangents(post);
        String[] names = {"Laternhain", "Mondsee", "Akademie", "Zuhause"};
        float[][] tgt = {{WorldGen.VILLAGE[0], WorldGen.VILLAGE[1]}, {-124, -4}, {0, -112}, {Home.X, Home.Z}};
        for (int i = 0; i < names.length; i++) {
            float a = FastMath.atan2(tgt[i][0] - x, tgt[i][1] - z);
            Node arm = new Node("arm");
            Geometry board = geo("board", new Box(0.72f, 0.14f, 0.025f), planks, arm, 0.68f, 0, 0);
            G3.tangents(board);
            Geometry txt = geo("txt", new Quad(1.3f, 0.26f), signMaterial(names[i]), arm, 0.03f, -0.13f, 0.03f);
            txt.setShadowMode(ShadowMode.Off);
            Geometry txt2 = geo("txt", new Quad(1.3f, 0.26f), signMaterial(names[i]), arm, 1.33f, -0.13f, -0.03f);
            txt2.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y));
            txt2.setShadowMode(ShadowMode.Off);
            arm.setLocalTranslation(0, 2.3f - i * 0.36f, 0);
            arm.setLocalRotation(new Quaternion().fromAngleAxis(a - FastMath.HALF_PI, Vector3f.UNIT_Y));
            n.attachChild(arm);
        }
        // kleiner Leuchtpilz am Fuß
        glowMushroom(n, 0.35f, 0.2f, 0.45f, 2);
        n.setLocalTranslation(x, h(x, z), z);
        n.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(n);
        solids.add(new float[]{x, z, 0.3f});
    }

    private void lanternPost(float x, float z, boolean withLight) {
        Node n = new Node("lanternPost");
        Geometry post = cylY(n, darkWood, 0.07f, 0.09f, 2.4f, 8, 0, 1.2f, 0);
        segment(n, darkWood, new Vector3f(0, 2.3f, 0), new Vector3f(0.55f, 2.45f, 0), 0.035f);
        Spatial l = G3.model("Lantern_01");
        l.setLocalScale(0.85f);
        l.setLocalTranslation(0.55f, 1.85f, 0);
        n.attachChild(l);
        Geometry glow = geo("glow", new Sphere(8, 8, 0.09f), G3.emissive(new ColorRGBA(1f, 0.72f, 0.32f, 1)), n, 0.55f, 2.03f, 0);
        glow.setShadowMode(ShadowMode.Off);
        flowers(n, 0.2f, 0.05f, 0.2f, 5, 0.35f);
        n.setLocalTranslation(x, h(x, z), z);
        n.setLocalRotation(new Quaternion().fromAngleAxis(rnd.nextFloat() * FastMath.TWO_PI, Vector3f.UNIT_Y));
        n.setShadowMode(ShadowMode.CastAndReceive);
        root.attachChild(n);
        solids.add(new float[]{x, z, 0.2f});
        if (withLight) lights.add(new PointLight(new Vector3f(x, h(x, z) + 2.2f, z), new ColorRGBA(1f, 0.7f, 0.38f, 1).mult(1.2f), 8));
    }

    /** Durchhängende Lichterkette zwischen zwei Punkten. */
    private void fairyString(Vector3f a, Vector3f b, float sag) {
        int count = (int) (a.distance(b) / 0.45f);
        Material wire = G3.lit(new ColorRGBA(0.15f, 0.12f, 0.1f, 1));
        Node n = new Node("fairyString");
        Vector3f prev = null;
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count;
            Vector3f p = a.clone().interpolateLocal(b, t);
            p.y -= FastMath.sin(t * FastMath.PI) * sag;
            if (prev != null) segment(n, wire, prev, p, 0.008f);
            if (i > 0 && i < count) bulb(bulbs, p.add(0, -0.07f, 0), i);
            prev = p;
        }
        n.setShadowMode(ShadowMode.Off);
        root.attachChild(n);
    }

    // ---------------------------------------------------------------- Pflanzen

    private void glowMushroom(Node p, float x, float z, float size, int colorIdx) {
        ColorRGBA[] cc = {new ColorRGBA(0.75f, 0.45f, 1f, 1), new ColorRGBA(0.45f, 0.75f, 1f, 1), new ColorRGBA(1f, 0.5f, 0.75f, 1)};
        ColorRGBA c = cc[colorIdx % cc.length];
        Mesh stem = lathe(new float[]{0.16f, 0.12f, 0.1f, 0.11f}, new float[]{0, 0.3f, 0.8f, 1.0f}, 10, 1);
        Geometry sg = geo("stem", stem, G3.litGlow(new ColorRGBA(0.92f, 0.9f, 0.85f, 1), c.mult(0.12f)), p, x, 0, z);
        sg.setLocalScale(size);
        Mesh cap = lathe(new float[]{0.08f, 0.5f, 0.55f, 0.45f, 0.25f, 0.01f}, new float[]{0.95f, 0.88f, 0.98f, 1.2f, 1.35f, 1.4f}, 18, 1);
        Geometry cg = geo("glowCap", cap, G3.litGlow(c.mult(0.75f), c.mult(0.7f)), p, x, 0, z);
        cg.setLocalScale(size);
        cg.setShadowMode(ShadowMode.Off);
    }

    private Material flowerMat;

    private void flowers(Node p, float x, float y, float z, int count, float spread) {
        if (flowerMat == null) flowerMat = null;
        ColorRGBA[] fc = {new ColorRGBA(1f, 0.45f, 0.6f, 1), new ColorRGBA(0.75f, 0.55f, 1f, 1), new ColorRGBA(1f, 0.85f, 0.4f, 1), new ColorRGBA(1f, 0.95f, 0.95f, 1)};
        for (int i = 0; i < count; i++) {
            float fx = x + (rnd.nextFloat() - 0.5f) * spread * 2, fz = z + (rnd.nextFloat() - 0.5f) * spread * 2;
            Geometry f = geo("flower", new Sphere(5, 6, 0.045f + rnd.nextFloat() * 0.03f), G3.litGlow(fc[rnd.nextInt(fc.length)], new ColorRGBA(0.04f, 0.02f, 0.03f, 1)), p, fx, y + rnd.nextFloat() * 0.12f, fz);
            f.setLocalScale(1, 0.6f, 1);
            Geometry leaf = geo("leaf", new Sphere(4, 6, 0.06f), G3.lit(new ColorRGBA(0.3f, 0.5f, 0.22f, 1)), p, fx + 0.04f, y - 0.03f, fz);
            leaf.setLocalScale(1, 0.3f, 1.6f);
        }
    }

    private void decorate() {
        Node deco = new Node("deco");
        // Leuchtpilze in Gruppen
        for (int i = 0; i < 26; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI, d = 8 + rnd.nextFloat() * 24;
            float x = WorldGen.VILLAGE[0] + FastMath.sin(a) * d, z = WorldGen.VILLAGE[1] + FastMath.cos(a) * d;
            if (WorldGen.streamDist(x, z) < 4 || WorldGen.pathDist(x, z) < 2 || blockedBy(x, z, 1.2f)) continue;
            Node g = new Node("glowGroup");
            int k = 1 + rnd.nextInt(3);
            for (int j = 0; j < k; j++) glowMushroom(g, (rnd.nextFloat() - 0.5f) * 0.9f, (rnd.nextFloat() - 0.5f) * 0.9f, 0.35f + rnd.nextFloat() * 0.55f, i);
            g.setLocalTranslation(x, h(x, z), z);
            deco.attachChild(g);
        }
        // Große Fliegenpilze am Wegrand
        for (int i = 0; i < 10; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI, d = 6 + rnd.nextFloat() * 26;
            float x = WorldGen.VILLAGE[0] + FastMath.sin(a) * d, z = WorldGen.VILLAGE[1] + FastMath.cos(a) * d;
            if (WorldGen.streamDist(x, z) < 4 || WorldGen.pathDist(x, z) < 2 || blockedBy(x, z, 1.5f)) continue;
            float s = 0.6f + rnd.nextFloat() * 0.9f;
            Node m = new Node("toadstool");
            Mesh stem = lathe(new float[]{0.22f, 0.17f, 0.15f, 0.16f}, new float[]{0, 0.4f, 0.9f, 1.1f}, 12, 1);
            geo("stem", stem, G3.lit(new ColorRGBA(0.93f, 0.9f, 0.82f, 1)), m, 0, 0, 0);
            Mesh cap = lathe(new float[]{0.1f, 0.6f, 0.68f, 0.58f, 0.32f, 0.01f}, new float[]{1.05f, 0.95f, 1.08f, 1.35f, 1.55f, 1.6f}, 20, 2);
            geo("cap", cap, i % 3 == 0 ? capBrown : capRed, m, 0, 0, 0);
            m.setLocalScale(s);
            m.setLocalTranslation(x, h(x, z), z);
            m.setShadowMode(ShadowMode.CastAndReceive);
            deco.attachChild(m);
        }
        // Farne und Blumenbeete
        for (int i = 0; i < 22; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI, d = 5 + rnd.nextFloat() * 27;
            float x = WorldGen.VILLAGE[0] + FastMath.sin(a) * d, z = WorldGen.VILLAGE[1] + FastMath.cos(a) * d;
            if (WorldGen.pathDist(x, z) < 1.6 || blockedBy(x, z, 1f)) continue;
            if (WorldGen.streamDist(x, z) < 2.6) continue;
            Spatial fern = G3.model("fern_02");
            fern.setLocalScale(0.9f + rnd.nextFloat() * 0.8f);
            fern.setLocalRotation(new Quaternion().fromAngleAxis(rnd.nextFloat() * FastMath.TWO_PI, Vector3f.UNIT_Y));
            fern.setLocalTranslation(x, h(x, z), z);
            deco.attachChild(fern);
            flowers(deco, x + 0.8f, h(x + 0.8f, z) + 0.05f, z, 8, 0.6f);
        }
        // Moosige Steine und Baumstümpfe
        for (int i = 0; i < 5; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI, d = 10 + rnd.nextFloat() * 22;
            float x = WorldGen.VILLAGE[0] + FastMath.sin(a) * d, z = WorldGen.VILLAGE[1] + FastMath.cos(a) * d;
            if (WorldGen.pathDist(x, z) < 2 || blockedBy(x, z, 1.5f) || WorldGen.streamDist(x, z) < 3) continue;
            Spatial s = G3.model(i % 2 == 0 ? "tree_stump_01" : "rock_moss_set_01");
            s.setLocalScale(i % 2 == 0 ? 1.2f : 0.8f);
            s.setLocalRotation(new Quaternion().fromAngleAxis(rnd.nextFloat() * FastMath.TWO_PI, Vector3f.UNIT_Y));
            s.setLocalTranslation(x, h(x, z) - 0.05f, z);
            deco.attachChild(s);
            solids.add(new float[]{x, z, 0.9f});
        }
        // Seerosen im Bach
        Material pad = G3.lit(new ColorRGBA(0.25f, 0.45f, 0.2f, 1));
        for (int i = 0; i < 18; i++) {
            int seg = 1 + rnd.nextInt(3);
            double[] sp = WorldGen.streamPoint(seg, rnd.nextFloat());
            float x = (float) sp[0] + (rnd.nextFloat() - 0.5f) * 3f, z = (float) sp[1] + (rnd.nextFloat() - 0.5f) * 2f;
            if (h(x, z) > WorldGen.WATER - 0.2f) continue;
            Geometry p = cylY(deco, pad, 0.32f + rnd.nextFloat() * 0.2f, 0.32f, 0.01f, 14, x, WorldGen.WATER + 0.03f, z);
            p.setShadowMode(ShadowMode.Receive);
            if (rnd.nextFloat() < 0.5f) {
                Geometry bl = geo("lily", new Sphere(6, 8, 0.1f), G3.litGlow(new ColorRGBA(1f, 0.6f, 0.75f, 1), new ColorRGBA(0.25f, 0.08f, 0.15f, 1)), deco, x, WorldGen.WATER + 0.1f, z);
                bl.setLocalScale(1, 0.6f, 1);
            }
        }
        // Bänke und Tisch am Dorfplatz
        Spatial bench = G3.model("painted_wooden_bench");
        bench.setLocalTranslation(-78, h(-78, -35), -35);
        bench.setLocalRotation(new Quaternion().fromAngleAxis(1.2f, Vector3f.UNIT_Y));
        deco.attachChild(bench);
        solids.add(new float[]{-78, -35, 0.7f});
        root.attachChild(deco);
    }

    private boolean blockedBy(float x, float z, float r) {
        for (float[] s : solids) if (FastMath.sqr(s[0] - x) + FastMath.sqr(s[1] - z) < FastMath.sqr(s[2] + r)) return true;
        return false;
    }
}
