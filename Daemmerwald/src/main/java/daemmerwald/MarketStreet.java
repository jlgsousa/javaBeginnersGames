package daemmerwald;

import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
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

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Die Laternengasse in Laternhain: eine gepflasterte Gasse mit Fachwerkhäusern, steilen Schieferdächern,
 * warm leuchtenden Schaufenstern, Ladenschildern, Lichterketten – und Leuten in Umhängen.
 * Die Läden kann man betreten.
 */
public final class MarketStreet {
    public static final float Z = -95, X0 = -120, X1 = -62;
    public final Node root = new Node("marketStreet");
    /** Unbewegliches (wird zu wenigen Teilen zusammengefasst) und die Passanten. */
    private final Node still = new Node("streetStatic"), people = new Node("streetPeople");
    private Material winLit, winDark, box1;
    private final Material[] blooms = new Material[3];
    public final List<float[]> solids = new ArrayList<>();
    public final List<PointLight> lights = new ArrayList<>();
    private final List<Walker> walkers = new ArrayList<>();
    private final Random rnd = new Random(41);
    private final Material plaster, timber, slate, stone, cobble, iron;

    /** Läden: Name des Ladens, Inhaber, Art (für Schaufenster und Schild). */
    static final String[][] SHOPS = {
        {"Zauberstäbe Odila Fichtenmark", "Odila Fichtenmark", "stab"},
        {"Kräuter & Tinkturen Fridolin", "Fridolin Wermut", "flasche"},
        {"Buchbinderei Wenna Seidel", "Wenna Seidel", "buch"},
        {"Hutmacherei Quirin", "Quirin Filzbart", "hut"},
        {"Eulenpost Laternhain", "Posthalterin Ilse Federkiel", "feder"},
        {"Schlüssel & Schlösser Bruno", "Bruno Bartschlüssel", "schluessel"},
        {"Kerzenzieherei Lumi", "Lumi Wachsglanz", "kerze"},
        {"Teestube Zur Laterne", "Wirtin Marga Kesselrot", "tasse"},
        {"Sternkarten & Fernrohre", "Theodor Himmelsglas", "stern"},
        {"Kristallschleiferei", "Agnes Funkenstein", "kristall"},
    };

    public final List<House> shops = new ArrayList<>();

    public MarketStreet() {
        plaster = G3.ph("plastered_wall_02", new ColorRGBA(1f, 0.96f, 0.88f, 1));
        timber = G3.lit(new ColorRGBA(0.2f, 0.12f, 0.07f, 1));
        slate = G3.ph("roof_slates_02", new ColorRGBA(0.5f, 0.52f, 0.62f, 1));
        stone = G3.ph("castle_wall_varriation", new ColorRGBA(0.85f, 0.8f, 0.72f, 1));
        cobble = G3.ph("cobblestone_floor_01", new ColorRGBA(0.85f, 0.82f, 0.78f, 1));
        iron = G3.lit(new ColorRGBA(0.1f, 0.1f, 0.11f, 1));
        winLit = G3.litGlow(new ColorRGBA(1f, 0.8f, 0.45f, 1), new ColorRGBA(0.6f, 0.38f, 0.12f, 1));
        winDark = G3.lit(new ColorRGBA(0.12f, 0.14f, 0.2f, 1));
        box1 = G3.lit(new ColorRGBA(0.35f, 0.22f, 0.12f, 1));
        blooms[0] = G3.lit(new ColorRGBA(0.9f, 0.3f, 0.35f, 1));
        blooms[1] = G3.lit(new ColorRGBA(0.95f, 0.8f, 0.3f, 1));
        blooms[2] = G3.lit(new ColorRGBA(0.7f, 0.4f, 0.9f, 1));
        root.attachChild(still);
        root.attachChild(people);
        buildStreet();
        int si = 0;
        float[] north = {-114, -105, -96, -87, -78, -69};
        float[] south = {-114, -105, -78, -69};
        for (float x : north) { house(x, Z - 8.6f, 0, SHOPS[si % SHOPS.length], si); si++; }
        for (float x : south) { house(x, Z + 8.6f, FastMath.PI, SHOPS[si % SHOPS.length], si); si++; }
        fountain(-91.5f, Z + 7);
        stringLights();
        for (int i = 0; i < 6; i++) walkers.add(new Walker(i));
        G3.tangents(still);
        // Alles Unbewegliche zu wenigen Teilen zusammenfassen – sonst wird die Grafikkarte langsam
        jme3tools.optimize.GeometryBatchFactory.optimize(still);
    }

    private float h(float x, float z) { return WorldGen.height(x, z); }

    // ---------------------------------------------------------------- Pflaster und Laternen

    private void buildStreet() {
        // Pflaster als Gitter, das dem Gelände folgt
        int nx = (int) (X1 - X0), nz = 11;
        float[] pos = new float[(nx + 1) * (nz + 1) * 3], uv = new float[(nx + 1) * (nz + 1) * 2], nor = new float[pos.length];
        short[] idx = new short[nx * nz * 6];
        int k = 0, t = 0;
        for (int j = 0; j <= nz; j++) for (int i = 0; i <= nx; i++) {
            float x = X0 + i, z = Z - 5.5f + j;
            pos[k * 3] = x; pos[k * 3 + 1] = h(x, z) + 0.05f; pos[k * 3 + 2] = z;
            nor[k * 3 + 1] = 1;
            uv[k * 2] = i / 2.5f; uv[k * 2 + 1] = j / 2.5f;
            k++;
        }
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int a0 = j * (nx + 1) + i, a1 = a0 + 1, b0 = a0 + nx + 1, b1 = b0 + 1;
            idx[t++] = (short) a0; idx[t++] = (short) b0; idx[t++] = (short) a1;
            idx[t++] = (short) a1; idx[t++] = (short) b0; idx[t++] = (short) b1;
        }
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.Normal, 3, nor);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();
        Geometry g = new Geometry("cobbles", m);
        g.setMaterial(cobble);
        g.setShadowMode(ShadowMode.Receive);
        still.attachChild(g);
        // Straßenlaternen auf beiden Seiten
        for (int i = 0; i < 4; i++) {
            float x = X0 + 8 + i * 14, z = Z + (i % 2 == 0 ? -4.6f : 4.6f);
            float y = h(x, z);
            Geometry post = new Geometry("lampPost", new Cylinder(2, 8, 0.07f, 3.4f, true));
            post.setMaterial(iron);
            post.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
            post.setLocalTranslation(x, y + 1.7f, z);
            still.attachChild(post);
            Spatial l = G3.model("Lantern_01");
            l.setLocalScale(1.5f);
            l.setLocalTranslation(x, y + 3.3f, z);
            still.attachChild(l);
            Geometry glow = new Geometry("lampGlow", new Sphere(8, 8, 0.13f));
            glow.setMaterial(G3.emissive(new ColorRGBA(1f, 0.72f, 0.35f, 1)));
            glow.setLocalTranslation(x, y + 3.58f, z);
            still.attachChild(glow);
            lights.add(new PointLight(new Vector3f(x, y + 3.5f, z), new ColorRGBA(1f, 0.68f, 0.35f, 1).mult(1.6f), 14));
            solids.add(new float[]{x, z, 0.3f});
        }
    }

    // ---------------------------------------------------------------- Fachwerkhaus mit Laden

    private void house(float cx, float cz, float yaw, String[] shop, int idx) {
        Node n = new Node("streetHouse");
        float w = 3.9f, d = 3.6f, base = h(cx, cz);
        float f1 = 3.1f, f2 = 2.8f, f3 = 2.6f;
        int floors = 2 + (idx % 3 == 0 ? 1 : 0);
        // Erdgeschoss aus Stein mit Laden
        box(n, stone, w, f1 / 2, d, 0, f1 / 2 - 0.2f, 0, 0.6f);
        Material shopMat = G3.emissive(ColorRGBA.White);
        shopMat.setTexture("ColorMap", G3.tex(shopImage(shop[2], idx)));
        shopMat.setColor("GlowColor", new ColorRGBA(0.5f, 0.32f, 0.12f, 1));
        for (int s = -1; s <= 1; s += 2) {
            Geometry win = new Geometry("shopWindow", new Quad(2.1f, 2.2f));
            win.setMaterial(shopMat);
            win.setLocalTranslation(s * 1.95f - 1.05f, 0.45f, d + 0.03f);
            win.setShadowMode(ShadowMode.Off);
            n.attachChild(win);
            box(n, timber, 1.15f, 0.08f, 0.12f, s * 1.95f, 2.7f, d + 0.06f, 0);
            box(n, timber, 1.15f, 0.06f, 0.18f, s * 1.95f, 0.4f, d + 0.1f, 0);
            for (int k = -1; k <= 1; k++) box(n, timber, 0.04f, 1.1f, 0.06f, s * 1.95f + k * 0.7f, 1.55f, d + 0.06f, 0);
        }
        // Tür mit kleinem Oberlicht
        box(n, timber, 0.62f, 1.2f, 0.08f, 0, 1.0f, d + 0.04f, 0);
        Geometry top = new Geometry("doorLight", new Quad(0.9f, 0.35f));
        top.setMaterial(G3.emissive(new ColorRGBA(1f, 0.75f, 0.4f, 1)));
        top.setLocalTranslation(-0.45f, 2.3f, d + 0.06f);
        n.attachChild(top);
        // Obergeschosse: verputzt, auskragend, mit Fachwerk
        float y = f1 - 0.2f, ow = w, od = d;
        for (int f = 0; f < floors; f++) {
            float fh = f == 0 ? f2 : f3;
            ow += 0.25f;
            od += 0.3f;
            box(n, plaster, ow, fh / 2, od, 0, y + fh / 2, 0, 0.5f);
            // Balkenlage
            box(n, timber, ow + 0.05f, 0.1f, od + 0.05f, 0, y + 0.05f, 0, 0);
            float fz = od + 0.03f;
            for (int k = -3; k <= 3; k++) box(n, timber, 0.09f, fh / 2, 0.05f, k * ow / 3f, y + fh / 2, fz, 0);
            box(n, timber, ow, 0.07f, 0.05f, 0, y + fh * 0.5f, fz, 0);
            // Andreaskreuze in zwei Feldern
            for (int s = -1; s <= 1; s += 2) {
                Geometry br = box(n, timber, 0.06f, fh * 0.42f, 0.04f, s * ow * 0.83f, y + fh * 0.5f, fz + 0.01f, 0);
                br.setLocalRotation(new Quaternion().fromAngleAxis(s * 0.62f, Vector3f.UNIT_Z));
            }
            // Fenster mit Sprossen, warm erleuchtet oder dunkel
            for (int k = -1; k <= 1; k += 2) {
                boolean lit = rnd.nextFloat() < 0.65f;
                Material wm = lit ? winLit : winDark;
                Geometry gw = box(n, wm, 0.42f, 0.55f, 0.03f, k * ow * 0.45f, y + fh * 0.5f, fz + 0.02f, 0);
                gw.setShadowMode(ShadowMode.Off);
                box(n, timber, 0.03f, 0.55f, 0.04f, k * ow * 0.45f, y + fh * 0.5f, fz + 0.04f, 0);
                box(n, timber, 0.42f, 0.03f, 0.04f, k * ow * 0.45f, y + fh * 0.5f, fz + 0.04f, 0);
                // Blumenkasten
                box(n, box1, 0.5f, 0.1f, 0.15f, k * ow * 0.45f, y + fh * 0.5f - 0.7f, fz + 0.15f, 0);
                for (int fl = 0; fl < 4; fl++) {
                    Geometry b = new Geometry("bloom", new Sphere(5, 5, 0.08f));
                    b.setMaterial(blooms[(fl + idx) % 3]);
                    b.setLocalTranslation(k * ow * 0.45f - 0.35f + fl * 0.23f, y + fh * 0.5f - 0.56f, fz + 0.18f);
                    n.attachChild(b);
                }
            }
            y += fh;
        }
        // Steiles Satteldach, Giebel zur Gasse
        float rh = 3.6f + (idx % 2) * 0.8f, rw = ow + 0.5f;
        float slope = FastMath.atan2(rh, rw);
        float len = FastMath.sqrt(rh * rh + rw * rw);
        for (int s = -1; s <= 1; s += 2) {
            Geometry r = box(n, slate, len / 2, 0.14f, od + 0.6f, 0, 0, 0, 0.35f);
            r.setLocalRotation(new Quaternion().fromAngleAxis(-s * slope, Vector3f.UNIT_Z));
            r.setLocalTranslation(s * rw / 2, y + rh / 2, 0);
        }
        Geometry gable = new Geometry("gable", triangle(ow, rh));
        gable.setMaterial(plaster);
        gable.setLocalTranslation(0, y, od + 0.02f);
        n.attachChild(gable);
        Geometry gableB = gable.clone();
        gableB.setLocalTranslation(0, y, -od - 0.02f);
        gableB.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y));
        n.attachChild(gableB);
        for (int s = -1; s <= 1; s += 2) {
            Geometry t = box(n, timber, 0.07f, len / 2 * 0.95f, 0.05f, s * rw / 4, y + rh / 2, od + 0.06f, 0);
            t.setLocalRotation(new Quaternion().fromAngleAxis(s * (FastMath.HALF_PI - slope), Vector3f.UNIT_Z));
        }
        Geometry gw = box(n, winLit, 0.32f, 0.42f, 0.03f, 0, y + rh * 0.35f, od + 0.05f, 0);
        gw.setShadowMode(ShadowMode.Off);
        // Schornstein
        box(n, stone, 0.35f, 0.9f, 0.35f, ow * 0.45f, y + rh * 0.7f, -od * 0.3f, 0.8f);
        // Ladenschild am Ausleger
        box(n, iron, 0.6f, 0.03f, 0.03f, -w + 0.2f, f1 + 0.1f, d + 0.6f, 0).setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
        Geometry sign = new Geometry("shopSign", new Box(0.03f, 0.38f, 0.45f));
        Material sm = G3.lit(ColorRGBA.White);
        sm.setTexture("DiffuseMap", G3.tex(signImage(shop[2])));
        sign.setMaterial(sm);
        sign.setLocalTranslation(-w + 0.2f, f1 - 0.4f, d + 0.95f);
        n.attachChild(sign);
        n.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        n.setLocalTranslation(cx, base, cz);
        n.setShadowMode(ShadowMode.CastAndReceive);
        still.attachChild(n);
        // Hindernisse und begehbarer Laden
        for (float ox = -2.6f; ox <= 2.61f; ox += 2.6f) solids.add(new float[]{cx + ox, cz, 2.9f});
        House shopHouse = new House("shop" + idx, shop[1], shop[0], cx, cz, yaw, false, false,
            Look.human(Look.rgb(225 - idx * 7, 190 - idx * 5, 165 - idx * 4), Look.rgb(60 + idx * 12, 50, 70), Look.rgb(50, 45, 40), Look.rgb(90 + idx * 15, 60, 40), Look.rgb(40, 30, 25), idx % 2 == 0, idx % 2 == 0 ? Look.BUN : Look.BEARD), 900 + idx);
        shopHouse.facade = true;
        shops.add(shopHouse);
    }

    private Geometry box(Node n, Material m, float hx, float hy, float hz, float x, float y, float z, float tile) {
        Box b = new Box(hx, hy, hz);
        if (tile > 0) b.scaleTextureCoordinates(new Vector2f(Math.max(1, Math.max(hx, hz) * tile), Math.max(1, hy * tile)));
        Geometry g = new Geometry("sh", b);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        n.attachChild(g);
        return g;
    }

    private static Mesh triangle(float hw, float hgt) {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-hw, 0, 0, hw, 0, 0, 0, hgt, 0});
        m.setBuffer(VertexBuffer.Type.Normal, 3, new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1});
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[]{0, 0, hw, 0, hw / 2, hgt / 2});
        m.setBuffer(VertexBuffer.Type.Index, 3, new short[]{0, 1, 2});
        m.updateBound();
        return m;
    }

    // ---------------------------------------------------------------- Schaufenster und Schilder (gemalt)

    private static BufferedImage shopImage(String kind, int seed) {
        int W = 256, H = 268;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random r = new Random(seed * 31L + kind.hashCode());
        g.setPaint(new GradientPaint(0, 0, new Color(255, 196, 110), 0, H, new Color(150, 80, 30)));
        g.fillRect(0, 0, W, H);
        // Regalbretter mit Waren
        for (int row = 0; row < 4; row++) {
            int yy = 40 + row * 56;
            g.setColor(new Color(90, 50, 25));
            g.fillRect(8, yy + 36, W - 16, 6);
            for (int x = 14; x < W - 20; x += 14 + r.nextInt(12)) {
                Color c = Color.getHSBColor(r.nextFloat(), 0.55f + r.nextFloat() * 0.35f, 0.55f + r.nextFloat() * 0.4f);
                g.setColor(c);
                switch (kind) {
                    case "flasche", "kristall" -> { g.fillRoundRect(x, yy + 10, 10, 26, 5, 5); g.fillRect(x + 3, yy + 2, 4, 9); }
                    case "buch" -> g.fillRect(x, yy + 6 + r.nextInt(8), 9, 30 - r.nextInt(8));
                    case "hut" -> { g.fillPolygon(new int[]{x - 6, x + 20, x + 7}, new int[]{yy + 34, yy + 34, yy + 2}, 3); x += 10; }
                    case "kerze" -> { g.setColor(new Color(250, 240, 220)); g.fillRect(x, yy + 12, 6, 24); g.setColor(new Color(255, 220, 120)); g.fillOval(x - 1, yy + 4, 8, 10); }
                    case "stab" -> { g.setColor(new Color(110 + r.nextInt(60), 70, 40)); g.fillRect(x, yy + 4, 4, 32); g.setColor(new Color(255, 240, 200)); g.fillOval(x - 1, yy + 1, 6, 6); }
                    case "schluessel" -> { g.setColor(new Color(220, 180, 80)); g.fillOval(x, yy + 8, 10, 10); g.fillRect(x + 4, yy + 16, 3, 18); }
                    case "stern" -> { g.setColor(new Color(60, 70, 120)); g.fillRect(x, yy + 8, 16, 26); g.setColor(new Color(255, 240, 180)); g.fillOval(x + 5, yy + 15, 4, 4); x += 6; }
                    case "tasse" -> { g.fillRoundRect(x, yy + 20, 14, 14, 4, 4); g.drawArc(x + 11, yy + 22, 8, 8, -90, 180); x += 4; }
                    default -> { g.fillOval(x, yy + 12, 12, 22); }
                }
            }
        }
        // warmer Schein von innen
        g.setPaint(new java.awt.RadialGradientPaint(W / 2f, H * 0.45f, W * 0.7f, new float[]{0, 1}, new Color[]{new Color(255, 230, 160, 90), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, W, H);
        // Sprossenfenster mit Rundbogen
        g.setColor(new Color(50, 28, 14));
        g.setStroke(new java.awt.BasicStroke(9));
        g.drawRect(4, 40, W - 8, H - 44);
        g.drawArc(4, 2, W - 8, 80, 0, 180);
        g.setStroke(new java.awt.BasicStroke(5));
        for (int k = 1; k < 3; k++) g.drawLine(k * W / 3, 30, k * W / 3, H);
        g.drawLine(0, H / 2 + 10, W, H / 2 + 10);
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, 18, 30);
        g.fillRect(W - 18, 0, 18, 30);
        g.dispose();
        return img;
    }

    private static BufferedImage signImage(String kind) {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(40, 50, 70));
        g.fillRect(0, 0, 128, 128);
        g.setColor(new Color(210, 170, 80));
        g.setStroke(new java.awt.BasicStroke(5));
        g.drawRect(6, 6, 116, 116);
        g.setStroke(new java.awt.BasicStroke(6, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        switch (kind) {
            case "stab" -> { g.drawLine(34, 96, 92, 30); g.fillOval(86, 22, 14, 14); }
            case "flasche" -> { g.drawRoundRect(44, 50, 40, 52, 18, 18); g.drawLine(58, 50, 58, 30); g.drawLine(70, 50, 70, 30); }
            case "buch" -> { g.drawRect(32, 34, 64, 60); g.drawLine(64, 34, 64, 94); }
            case "hut" -> { g.drawPolygon(new int[]{30, 98, 64}, new int[]{96, 96, 24}, 3); g.drawLine(22, 96, 106, 96); }
            case "feder" -> { g.drawArc(30, 24, 70, 90, 20, 140); g.drawLine(40, 100, 90, 34); }
            case "schluessel" -> { g.drawOval(30, 40, 30, 30); g.drawLine(60, 55, 100, 55); g.drawLine(88, 55, 88, 70); }
            case "kerze" -> { g.drawRect(54, 50, 20, 50); g.fillOval(56, 26, 16, 22); }
            case "tasse" -> { g.drawRoundRect(36, 50, 46, 40, 10, 10); g.drawArc(76, 58, 22, 22, -90, 180); }
            case "stern" -> { for (int i = 0; i < 5; i++) { double a1 = -Math.PI / 2 + i * 4 * Math.PI / 5, a2 = a1 + 4 * Math.PI / 5; g.drawLine(64 + (int) (Math.cos(a1) * 36), 64 + (int) (Math.sin(a1) * 36), 64 + (int) (Math.cos(a2) * 36), 64 + (int) (Math.sin(a2) * 36)); } }
            default -> g.drawPolygon(new int[]{64, 96, 64, 32}, new int[]{24, 64, 104, 64}, 4);
        }
        g.dispose();
        return img;
    }

    // ---------------------------------------------------------------- Brunnen und Lichterketten

    private void fountain(float x, float z) {
        float y = h(x, z);
        Material st = stone;
        Geometry rim = new Geometry("fountainRim", new Cylinder(2, 24, 2.2f, 0.7f, true));
        rim.setMaterial(st);
        rim.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        rim.setLocalTranslation(x, y + 0.35f, z);
        still.attachChild(rim);
        Geometry water = new Geometry("fountainWater", new Cylinder(2, 24, 1.9f, 0.05f, true));
        water.setMaterial(G3.litGlow(new ColorRGBA(0.3f, 0.5f, 0.6f, 1), new ColorRGBA(0.05f, 0.12f, 0.16f, 1)));
        water.setLocalRotation(rim.getLocalRotation());
        water.setLocalTranslation(x, y + 0.65f, z);
        still.attachChild(water);
        Geometry col = new Geometry("fountainCol", new Cylinder(2, 12, 0.25f, 1.8f, true));
        col.setMaterial(st);
        col.setLocalRotation(rim.getLocalRotation());
        col.setLocalTranslation(x, y + 1.3f, z);
        still.attachChild(col);
        Geometry bowl = new Geometry("fountainBowl", new Cylinder(2, 16, 0.75f, 0.2f, true));
        bowl.setMaterial(st);
        bowl.setLocalRotation(rim.getLocalRotation());
        bowl.setLocalTranslation(x, y + 2.2f, z);
        still.attachChild(bowl);
        com.jme3.effect.ParticleEmitter spray = G3.particles("fountainSpray", 60, null, new ColorRGBA(0.85f, 0.95f, 1f, 0.8f), new ColorRGBA(0.85f, 0.95f, 1f, 0), 0.08f, 0.04f, 0.8f, 1.1f);
        spray.setShape(new com.jme3.effect.shapes.EmitterSphereShape(Vector3f.ZERO, 0.1f));
        spray.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 2.6f, 0));
        spray.getParticleInfluencer().setVelocityVariation(0.35f);
        spray.setGravity(0, 4, 0);
        spray.setParticlesPerSec(50);
        spray.setLocalTranslation(x, y + 2.4f, z);
        people.attachChild(spray);
        solids.add(new float[]{x, z, 2.3f});
    }

    private void stringLights() {
        Material[] bulbs = {G3.emissive(new ColorRGBA(1f, 0.8f, 0.45f, 1)), G3.emissive(new ColorRGBA(1f, 0.6f, 0.75f, 1)), G3.emissive(new ColorRGBA(0.7f, 0.85f, 1f, 1))};
        for (int i = 0; i < 6; i++) {
            float x = X0 + 6 + i * 10;
            float ya = h(x, Z - 4) + 6.2f, yb = h(x + 3, Z + 4) + 6.2f;
            Vector3f a = new Vector3f(x, ya, Z - 4.6f), b = new Vector3f(x + 3, yb, Z + 4.6f);
            for (int k = 0; k <= 14; k++) {
                float t = k / 14f;
                Vector3f p = a.clone().interpolateLocal(b, t);
                p.y -= FastMath.sin(t * FastMath.PI) * 1.1f;
                Geometry bl = new Geometry("bulb", new Sphere(5, 5, 0.06f));
                bl.setMaterial(bulbs[(k + i) % 3]);
                bl.setLocalTranslation(p);
                bl.setShadowMode(ShadowMode.Off);
                still.attachChild(bl);
            }
        }
    }

    // ---------------------------------------------------------------- Leute in Umhängen

    private final class Walker {
        final Figure fig;
        float x, dir, speed, pause;
        final float lane;

        Walker(int i) {
            boolean female = i % 2 == 0;
            int[] cloak = i % 3 == 0 ? Look.rgb(30, 28, 40) : i % 3 == 1 ? Look.rgb(60, 45, 35) : Look.rgb(40, 50, 45);
            Look look = Look.human(Look.rgb(225 - i * 12, 190 - i * 9, 160 - i * 6), cloak, cloak, Look.rgb(60 + i * 20, 45, 30), Look.rgb(30, 25, 20), female, female ? Look.LONG : Look.SHORT);
            fig = new Figure(look, Outfit.Accessory.HOOD);
            x = X0 + 6 + rnd.nextFloat() * (X1 - X0 - 12);
            dir = rnd.nextBoolean() ? 1 : -1;
            speed = 1.1f + rnd.nextFloat() * 0.5f;
            lane = Z + (rnd.nextFloat() - 0.5f) * 5;
            people.attachChild(fig.node);
        }

        void update(float dt) {
            if (pause > 0) { pause -= dt; fig.update(dt, 0, false); place(); return; }
            x += dir * speed * dt;
            if (x < X0 + 4 || x > X1 - 4) { dir = -dir; pause = 1 + rnd.nextFloat() * 3; }
            else if (rnd.nextFloat() < dt * 0.08f) pause = 2 + rnd.nextFloat() * 4;
            fig.update(dt, speed, false);
            place();
        }

        void place() {
            fig.node.setLocalTranslation(x, WorldGen.height(x, lane), lane);
            fig.node.setLocalRotation(new Quaternion().fromAngleAxis(dir > 0 ? FastMath.HALF_PI : -FastMath.HALF_PI, Vector3f.UNIT_Y));
        }
    }

    /** Nur in der Nähe bewegen (spart Rechenzeit). */
    public void update(float dt, Vector3f player) {
        if (Math.abs(player.x - (X0 + X1) / 2) > 120 || Math.abs(player.z - Z) > 120) return;
        for (Walker w : walkers) w.update(dt);
    }
}
