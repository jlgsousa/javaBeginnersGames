package daemmerwald;

import com.jme3.collision.CollisionResults;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
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
 * Der Zauberpalast Sternenhall – außen ein Schloss mit Türmen, innen begehbar: großer Saal mit verzauberter
 * Sternendecke und schwebenden Kerzen, Bibliothek, Trankkeller – und vier Geheimgänge mit Raritäten.
 * <p>
 * Das Innere liegt hoch über dem Schloss (eigene Welt in der Welt). Wände sind Rechtecke für die Kollision;
 * Geheimtüren öffnen sich, wenn man an der richtigen Stelle zaubert.
 */
public final class Palace {
    public static final float PX = WorldGen.PALACE[0], PZ = WorldGen.PALACE[1];
    public final float groundY = WorldGen.height(PX, PZ);
    /** Höhe des Innenraums (Saalboden). */
    public final float baseY = groundY + 160;

    public final Node exterior = new Node("palaceOutside");
    public final Node interior = new Node("palaceInside");
    final Node collide = new Node("palaceCollide");

    /** Raum: Rechteck (lokal), Bodenhöhe relativ zum Saal, Deckenhöhe. */
    public record Room(String id, String name, float x0, float x1, float z0, float z1, float y, float h) {
        boolean contains(float x, float z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    /** Wandstück (lokal) – mit Geheimgang-ID, falls es eine Geheimtür ist. */
    static final class Wall {
        final float x0, x1, z0, z1;
        final String secret;
        Spatial door;
        boolean open;
        Wall(float x0, float x1, float z0, float z1, String secret) {
            this.x0 = Math.min(x0, x1); this.x1 = Math.max(x0, x1); this.z0 = Math.min(z0, z1); this.z1 = Math.max(z0, z1); this.secret = secret;
        }
    }

    /** Geheimgang: wo man zaubern muss, was er öffnet, welche Rarität dahinter liegt. */
    public static final class Secret {
        public final String id, title, hint;
        public final Vector3f spot;          // lokal
        public final Treasure treasure;
        public final Vector3f treasurePos;   // lokal
        public Geometry treasureGeo;
        Secret(String id, String title, String hint, Vector3f spot, Treasure treasure, Vector3f treasurePos) {
            this.id = id; this.title = title; this.hint = hint; this.spot = spot; this.treasure = treasure; this.treasurePos = treasurePos;
        }
    }

    /** Teleport-Treppe zwischen Räumen (lokal), nur benutzbar, wenn der Geheimgang offen ist. */
    public record Portal(String secret, Vector3f from, Vector3f to, float yawTo, String label) { }

    public final List<Room> rooms = new ArrayList<>();
    final List<Wall> walls = new ArrayList<>();
    public final List<Secret> secrets = new ArrayList<>();
    public final List<Portal> portals = new ArrayList<>();
    public final List<PointLight> lights = new ArrayList<>();
    private final List<Geometry> candles = new ArrayList<>();
    private final Random rnd = new Random(3);

    Material stone, plaster, planks, marble, roofTiles, gold, darkWood, red, starCeiling;

    public Palace() {
        stone = G3.ph("mossy_stone_wall", new ColorRGBA(1f, 0.97f, 0.92f, 1));
        plaster = G3.ph("clay_plaster", new ColorRGBA(0.95f, 0.9f, 0.8f, 1));
        planks = G3.ph("brown_planks_05");
        roofTiles = G3.ph("clay_roof_tiles", new ColorRGBA(0.55f, 0.6f, 0.95f, 1));
        marble = marbleMat();
        gold = G3.lit(new ColorRGBA(0.95f, 0.75f, 0.32f, 1));
        gold.setColor("Specular", ColorRGBA.White);
        gold.setFloat("Shininess", 60);
        darkWood = G3.lit(new ColorRGBA(0.3f, 0.18f, 0.1f, 1));
        red = G3.lit(new ColorRGBA(0.55f, 0.08f, 0.12f, 1));
        starCeiling = starMat();
        buildExterior();
        buildInterior();
        depths = new Depths(this);
    }

    /** Was unter dem Schloss liegt (Kapitel 5–7). */
    public Depths depths;

    // ================================================================ Koordinaten

    public Vector3f toWorld(Vector3f local) { return new Vector3f(PX + local.x, baseY + local.y, PZ + local.z); }

    public Vector3f toLocal(Vector3f w) { return new Vector3f(w.x - PX, w.y - baseY, w.z - PZ); }

    /** Eingang draußen (vor dem Tor) und Ankunft drinnen. */
    public Vector3f gateOutside() { return new Vector3f(PX, WorldGen.height(PX, PZ + 24), PZ + 24); }

    public Vector3f entryInside() { return toWorld(new Vector3f(0, 0, 15.5f)); }

    public Vector3f exitInside() { return toWorld(new Vector3f(0, 0, 17.3f)); }

    public Room roomAt(float wx, float wz) {
        float x = wx - PX, z = wz - PZ;
        for (Room r : rooms) if (r.contains(x, z)) return r;
        return null;
    }

    /** Bodenhöhe im Palast (oder NaN, wenn außerhalb aller Räume). */
    public float floorAt(float wx, float wz) {
        Room r = roomAt(wx, wz);
        return r == null ? Float.NaN : baseY + r.y;
    }

    /** Stößt man innen an eine Wand? */
    public boolean blocked(float wx, float wz, float rad) {
        float x = wx - PX, z = wz - PZ;
        for (Wall w : walls) {
            if (w.open) continue;
            if (x + rad > w.x0 && x - rad < w.x1 && z + rad > w.z0 && z - rad < w.z1) return true;
        }
        return roomAt(wx, wz) == null;
    }

    /** Kamera nicht durch Wände: Abstand bis zur ersten Wand entlang des Strahls. */
    public float rayDistance(Vector3f from, Vector3f dir, float max) {
        CollisionResults res = new CollisionResults();
        collide.collideWith(new Ray(from, dir), res);
        for (int i = 0; i < res.size(); i++) if (res.getCollision(i).getDistance() <= max) return res.getCollision(i).getDistance();
        return max;
    }

    // ================================================================ Materialien

    private static Material marbleMat() {
        int n = 512;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random r = new Random(9);
        for (int ty = 0; ty < 2; ty++) for (int tx = 0; tx < 2; tx++) {
            boolean dark = (tx + ty) % 2 == 1;
            g.setColor(dark ? new Color(38, 34, 48) : new Color(226, 220, 206));
            g.fillRect(tx * n / 2, ty * n / 2, n / 2, n / 2);
            for (int v = 0; v < 14; v++) {
                g.setColor(dark ? new Color(90, 80, 110, 70) : new Color(150, 140, 125, 60));
                g.setStroke(new BasicStroke(0.6f + r.nextFloat() * 1.6f));
                int x = tx * n / 2 + r.nextInt(n / 2), y = ty * n / 2 + r.nextInt(n / 2);
                g.drawLine(x, y, x + r.nextInt(120) - 60, y + r.nextInt(120) - 60);
            }
            g.setColor(new Color(170, 140, 70));
            g.setStroke(new BasicStroke(3));
            g.drawRect(tx * n / 2, ty * n / 2, n / 2, n / 2);
        }
        g.dispose();
        Material m = G3.lit(new ColorRGBA(0.82f, 0.8f, 0.78f, 1));
        Texture t = G3.tex(img);
        t.setWrap(Texture.WrapMode.Repeat);
        m.setTexture("DiffuseMap", t);
        m.setColor("Specular", new ColorRGBA(0.45f, 0.45f, 0.45f, 1));
        m.setFloat("Shininess", 40);
        return m;
    }

    /** Verzauberte Decke: Nachthimmel mit Sternen und Nebelschleiern. */
    private static Material starMat() {
        int w = 1024, h = 512;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 40), w, h, new Color(40, 16, 60)));
        g.fillRect(0, 0, w, h);
        Random r = new Random(4);
        for (int i = 0; i < 40; i++) {
            g.setColor(new Color(120 + r.nextInt(80), 80 + r.nextInt(60), 200, 18));
            int s = 80 + r.nextInt(200);
            g.fillOval(r.nextInt(w) - s / 2, r.nextInt(h) - s / 2, s, s / 2);
        }
        for (int i = 0; i < 900; i++) {
            int b = 150 + r.nextInt(105);
            g.setColor(new Color(b, b, Math.min(255, b + 20)));
            float s = r.nextFloat() < 0.04f ? 3.2f : 1 + r.nextFloat() * 1.4f;
            g.fill(new java.awt.geom.Ellipse2D.Float(r.nextInt(w), r.nextInt(h), s, s));
        }
        g.dispose();
        Material m = G3.emissive(ColorRGBA.White);
        m.setTexture("ColorMap", G3.tex(img));
        m.clearParam("GlowColor");
        m.setColor("Color", new ColorRGBA(0.9f, 0.9f, 1f, 1));
        return m;
    }

    // ================================================================ Hilfen

    Geometry box(Node n, Material m, float hx, float hy, float hz, float x, float y, float z, float tile) {
        Box b = new Box(hx, hy, hz);
        if (tile > 0) b.scaleTextureCoordinates(new Vector2f(Math.max(1, Math.max(hx, hz) * tile), Math.max(1, hy * tile)));
        Geometry g = new Geometry("p", b);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        n.attachChild(g);
        return g;
    }

    Geometry cylY(Node n, Material m, float r1, float r2, float h, int sides, float x, float y, float z) {
        Geometry g = new Geometry("c", new Cylinder(2, sides, r1, r2, h, true, false));
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        n.attachChild(g);
        return g;
    }

    // ================================================================ Außen: das Schloss

    private void buildExterior() {
        Node n = exterior;
        float W = 15, D = 11, H = 15;
        // Sockel und Freitreppe
        box(n, stone, W + 2, 1.2f, D + 2, 0, 0.2f, 0, 0.6f);
        for (int i = 0; i < 6; i++) box(n, stone, 4.5f - i * 0.1f, 0.15f, 0.6f, 0, 0.15f + i * 0.3f, D + 6.4f - i * 0.75f, 0.8f);
        // Hauptbau
        box(n, stone, W, H / 2, D, 0, 1.4f + H / 2, 0, 0.45f);
        // Dach mit blauen Ziegeln
        for (int s = -1; s <= 1; s += 2) {
            Geometry r = box(n, roofTiles, D * 0.78f, 0.25f, W + 0.6f, s * D * 0.5f, 1.4f + H + 3.6f, 0, 0.5f);
            r.setLocalRotation(new Quaternion().fromAngles(0, FastMath.HALF_PI, s * 0.72f));
            r.setLocalTranslation(0, 1.4f + H + 3.4f, s * D * 0.52f);
        }
        // Türme an den Ecken
        float[][] corners = {{-W, -D}, {W, -D}, {-W, D}, {W, D}};
        for (float[] c : corners) tower(n, c[0], c[1], 3.6f, H + 9, 5.5f);
        // Mittelturm mit Kristallspitze
        tower(n, 0, -D * 0.2f, 4.4f, H + 18, 8f);
        Geometry crystal = new Geometry("crystal", new Sphere(4, 6, 1.4f));
        crystal.setMaterial(G3.litGlow(new ColorRGBA(0.6f, 0.8f, 1f, 1), new ColorRGBA(0.5f, 0.75f, 1f, 1)));
        crystal.setLocalScale(1, 2.2f, 1);
        crystal.setLocalTranslation(0, 1.4f + H + 18 + 8f + 2.4f, -D * 0.2f);
        crystal.setShadowMode(ShadowMode.Off);
        n.attachChild(crystal);
        // Tor: Spitzbogen mit leuchtender Tür
        Geometry gate = box(n, darkWood, 2.4f, 3.2f, 0.25f, 0, 1.4f + 3.2f, D + 0.1f, 0);
        Geometry glowDoor = box(n, G3.litGlow(new ColorRGBA(1f, 0.8f, 0.5f, 1), new ColorRGBA(0.5f, 0.32f, 0.12f, 1)), 2.0f, 2.9f, 0.05f, 0, 1.4f + 2.9f, D + 0.4f, 0);
        glowDoor.setShadowMode(ShadowMode.Off);
        Geometry archTop = new Geometry("arch", new Cylinder(2, 24, 2.4f, 0.5f, true));
        archTop.setMaterial(stone);
        archTop.setLocalTranslation(0, 1.4f + 6.4f, D + 0.2f);
        n.attachChild(archTop);
        // Fensterreihen (warm leuchtend)
        Material winM = G3.litGlow(new ColorRGBA(0.95f, 0.8f, 0.5f, 1), new ColorRGBA(0.42f, 0.27f, 0.1f, 1));
        for (int f = 0; f < 2; f++) {
            for (int i = -3; i <= 3; i++) {
                if (f == 0 && Math.abs(i) < 1) continue;
                float y = 1.4f + 4.5f + f * 6;
                Geometry w = box(n, winM, 0.7f, 1.4f, 0.05f, i * 3.8f, y, D + 0.06f, 0);
                w.setShadowMode(ShadowMode.Off);
                Geometry wb = box(n, winM, 0.7f, 1.4f, 0.05f, i * 3.8f, y, -D - 0.06f, 0);
                wb.setShadowMode(ShadowMode.Off);
                Geometry arch = new Geometry("wa", new Cylinder(2, 12, 0.7f, 0.06f, true));
                arch.setMaterial(winM);
                arch.setLocalTranslation(i * 3.8f, y + 1.4f, D + 0.06f);
                n.attachChild(arch);
            }
        }
        // Banner
        for (int s = -1; s <= 1; s += 2) {
            Geometry b = box(n, G3.lit(new ColorRGBA(0.25f, 0.2f, 0.6f, 1)), 0.9f, 2.6f, 0.03f, s * 5.6f, 1.4f + H - 3.5f, D + 0.12f, 0);
            Geometry star = new Geometry("bannerStar", new Sphere(5, 6, 0.35f));
            star.setMaterial(G3.litGlow(new ColorRGBA(1f, 0.85f, 0.4f, 1), new ColorRGBA(0.6f, 0.45f, 0.1f, 1)));
            star.setLocalTranslation(s * 5.6f, 1.4f + H - 3.0f, D + 0.18f);
            star.setLocalScale(1, 1, 0.2f);
            n.attachChild(star);
            b.setShadowMode(ShadowMode.Cast);
        }
        // Laternen am Aufgang
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 2; k++) {
                float lx = s * 5.2f, lz = D + 3 + k * 3.5f;
                cylY(n, darkWood, 0.1f, 0.12f, 3.2f, 8, lx, 1.6f, lz);
                Spatial l = G3.model("Lantern_01");
                l.setLocalScale(1.3f);
                l.setLocalTranslation(lx, 3.0f, lz);
                n.attachChild(l);
                Geometry glow = new Geometry("lg", new Sphere(8, 8, 0.12f));
                glow.setMaterial(G3.emissive(new ColorRGBA(1f, 0.75f, 0.35f, 1)));
                glow.setLocalTranslation(lx, 3.25f, lz);
                n.attachChild(glow);
            }
        }
        G3.tangents(n);
        n.setShadowMode(ShadowMode.CastAndReceive);
        n.setLocalTranslation(PX, groundY - 0.4f, PZ);
    }

    private void tower(Node n, float x, float z, float r, float h, float roofH) {
        Mesh m = Village.lathe(new float[]{r * 1.1f, r, r, r * 1.05f}, new float[]{0, 1.6f, h - 1f, h}, 24, 6);
        Geometry t = new Geometry("tower", m);
        t.setMaterial(stone);
        t.setLocalTranslation(x, 0, z);
        n.attachChild(t);
        // Zinnenkranz und spitzes Dach
        for (int i = 0; i < 12; i++) {
            float a = i * FastMath.TWO_PI / 12;
            box(n, stone, 0.35f, 0.4f, 0.3f, x + FastMath.sin(a) * r * 1.02f, h + 0.4f, z + FastMath.cos(a) * r * 1.02f, 1).setLocalRotation(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y));
        }
        Mesh roof = Village.lathe(new float[]{0, r * 1.3f, r * 1.2f, r * 0.6f, 0.02f}, new float[]{h + 0.2f, h + 0.1f, h + roofH * 0.25f, h + roofH * 0.65f, h + roofH}, 24, 5);
        Geometry rg = new Geometry("towerRoof", roof);
        rg.setMaterial(roofTiles);
        rg.setLocalTranslation(x, 0, z);
        n.attachChild(rg);
        Geometry tip = new Geometry("tip", new Sphere(6, 6, 0.3f));
        tip.setMaterial(gold);
        tip.setLocalTranslation(x, h + roofH + 0.2f, z);
        n.attachChild(tip);
        // leuchtende Fensterschlitze
        Material winM = G3.litGlow(new ColorRGBA(0.95f, 0.8f, 0.5f, 1), new ColorRGBA(0.4f, 0.26f, 0.1f, 1));
        for (int f = 1; f < 4; f++) {
            float a = f * 1.9f;
            Geometry w = box(n, winM, 0.3f, 0.8f, 0.05f, x + FastMath.sin(a) * (r + 0.02f), h * f / 4.2f + 2, z + FastMath.cos(a) * (r + 0.02f), 0);
            w.setLocalRotation(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y));
            w.setShadowMode(ShadowMode.Off);
        }
    }

    // ================================================================ Innen

    Room room(String id, String name, float x0, float x1, float z0, float z1, float y, float h) {
        Room r = new Room(id, name, x0, x1, z0, z1, y, h);
        rooms.add(r);
        return r;
    }

    private void buildInterior() {
        Room hall = room("hall", "Großer Saal", -12, 12, -18, 18.5f, 0, 12);
        Room lib = room("library", "Bibliothek", -30, -12, -10, 10, 0, 8);
        Room cel = room("cellar", "Trankkeller", 12, 30, -10, 10, 0, 7);
        Room study = room("study", "Verborgene Schreibstube", -30, -22, -20, -10, 0, 4.2f);
        Room fire = room("fire", "Kammer hinter dem Kamin", -4, 4, -28, -18, 0, 4.2f);
        Room obs = room("observatory", "Geheime Sternwarte", 50, 66, -8, 8, 22, 9);
        Room crypt = room("crypt", "Gruft unter dem Palast", 50, 66, 22, 38, -9, 5);
        Room gallery = room("gallery", "Galerie der Gründer", -21.5f, -12f, -18f, -10.4f, 0, 6);

        // Wände (mit Durchgängen und Geheimtüren)
        float t = 0.35f;
        // Saal
        wallX(hall, hall.z0, -12, 12, new float[][]{{-2, 2, 1}}, "kamin", stone);     // Nordwand mit Kamin-Geheimtür
        wallX(hall, hall.z1, -12, 12, new float[][]{}, null, stone);                    // Südwand (Tor innen)
        wallZ(hall, hall.x0, -18, 18.5f, new float[][]{{-15f, -13f, 1}, {-2.2f, 2.2f, 0}}, "gemaelde", stone); // West: Gemälde-Geheimtür, Tür zur Bibliothek
        wallZ(hall, hall.x1, -18, 18.5f, new float[][]{{-2.2f, 2.2f, 0}}, null, stone); // Ost: Tür zum Trankkeller
        // Bibliothek
        wallX(lib, lib.z0, -30, -12, new float[][]{{-27.2f, -24.8f, 1}}, "regal", plaster);
        wallX(lib, lib.z1, -30, -12, new float[][]{}, null, plaster);
        wallZ(lib, lib.x0, -10, 10, new float[][]{}, null, plaster);
        // Keller
        wallX(cel, cel.z0, 12, 30, new float[][]{}, null, stone);
        wallX(cel, cel.z1, 12, 30, new float[][]{}, null, stone);
        wallZ(cel, cel.x1, -10, 10, new float[][]{}, null, stone);
        // Schreibstube, Kaminkammer, Sternwarte, Gruft
        wallX(study, study.z0, -30, -22, new float[][]{}, null, planks);
        wallZ(study, study.x0, -20, -10, new float[][]{}, null, planks);
        wallZ(study, study.x1, -20, -10, new float[][]{}, null, planks);
        wallX(fire, fire.z0, -4, 4, new float[][]{}, null, stone);
        wallZ(fire, fire.x0, -28, -18, new float[][]{}, null, stone);
        wallZ(fire, fire.x1, -28, -18, new float[][]{}, null, stone);
        for (Room r : new Room[]{obs, crypt}) {
            wallX(r, r.z0, r.x0, r.x1, new float[][]{}, null, stone);
            wallX(r, r.z1, r.x0, r.x1, new float[][]{}, null, stone);
            wallZ(r, r.x0, r.z0, r.z1, new float[][]{}, null, stone);
            wallZ(r, r.x1, r.z0, r.z1, new float[][]{}, null, stone);
        }
        wallX(gallery, gallery.z0(), gallery.x0(), gallery.x1(), new float[][]{}, null, plaster);
        wallX(gallery, gallery.z1(), gallery.x0(), gallery.x1(), new float[][]{}, null, plaster);
        wallZ(gallery, gallery.x0(), gallery.z0(), gallery.z1(), new float[][]{}, null, plaster);
        for (Room r : rooms) floorAndCeiling(r);
        buildGallery();
        buildPainting();
        buildSymbol();

        decorateHall(hall);
        decorateLibrary(lib);
        decorateCellar(cel);
        decorateSecretRooms(study, fire, obs, crypt);

        // Geheimnisse: wo zaubern? welche Rarität?
        secrets.add(new Secret("regal", "Das Regal mit dem blauen Buch", "Ein blaues Buch steht verkehrt herum im Regal …",
            new Vector3f(-26, 1.4f, -9.3f), Treasure.STERNENSTAUB, new Vector3f(-26, 1.0f, -16)));
        secrets.add(new Secret("kamin", "Der kalte Kamin", "Der Kamin ist kalt, aber es zieht …",
            new Vector3f(0, 1.0f, -17.2f), Treasure.ZEITSAND, new Vector3f(0, 1.0f, -25)));
        secrets.add(new Secret("sternwarte", "Der atmende Wandteppich", "Der Wandteppich bewegt sich, als ob dahinter Wind weht …",
            new Vector3f(11.6f, 2.5f, -12), Treasure.STERNKARTE, new Vector3f(58, 23.0f, 0)));
        secrets.add(new Secret("gruft", "Die Platte mit dem Mondzeichen", "Eine Bodenplatte trägt ein silbernes Mondzeichen …",
            new Vector3f(24, 0.1f, -4), Treasure.SPIEGELSCHERBE, new Vector3f(58, -8.0f, 30)));
        for (Secret s : secrets) {
            Geometry tg = new Geometry("secretTreasure", new Sphere(3, 4, 0.25f));
            java.awt.Color c = s.treasure.glow();
            ColorRGBA cc = new ColorRGBA(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1);
            tg.setMaterial(G3.litGlow(cc, cc));
            tg.setLocalScale(1, 1.6f, 1);
            tg.setLocalTranslation(s.treasurePos);
            tg.setShadowMode(ShadowMode.Off);
            interior.attachChild(tg);
            s.treasureGeo = tg;
            // Sockel unter dem Fundstück
            cylY(interior, gold, 0.35f, 0.45f, 0.6f, 14, s.treasurePos.x, s.treasurePos.y - 0.7f, s.treasurePos.z);
        }
        // Treppen-Portale (Wendeltreppe hinter dem Wandteppich, Falltür unter der Mondplatte)
        portals.add(new Portal("sternwarte", new Vector3f(11.3f, 0, -12), new Vector3f(52, 22, 0), -FastMath.HALF_PI, "Die Wendeltreppe hinauf"));
        portals.add(new Portal("sternwarte", new Vector3f(51.2f, 22, 0), new Vector3f(10, 0, -12), -FastMath.HALF_PI, "Die Wendeltreppe hinab"));
        portals.add(new Portal("gruft", new Vector3f(24, 0, -4), new Vector3f(52, -9, 30), FastMath.HALF_PI, "In die Gruft hinabsteigen"));
        portals.add(new Portal("gruft", new Vector3f(51.2f, -9, 30), new Vector3f(24, 0, -1.5f), 0, "Zurück nach oben"));

        G3.tangents(interior);
        interior.setLocalTranslation(PX, baseY, PZ);
        interior.setShadowMode(ShadowMode.CastAndReceive);
        collide.setLocalTranslation(PX, baseY, PZ);
        for (PointLight l : lights) interior.addLight(l);
        collide.updateGeometricState();
    }

    /** Wand entlang x bei z (von xa bis xb), mit Lücken {von, bis, geheim?}. */
    void wallX(Room r, float z, float xa, float xb, float[][] gaps, String secret, Material m) {
        float cur = xa;
        for (float[] gp : gaps) {
            if (gp[0] > cur) segX(r, z, cur, gp[0], m, null);
            if (gp[2] > 0) segX(r, z, gp[0], gp[1], m, secret);
            else lintel(r, (gp[0] + gp[1]) / 2, z, (gp[1] - gp[0]) / 2, true, m);
            cur = gp[1];
        }
        if (cur < xb) segX(r, z, cur, xb, m, null);
    }

    void wallZ(Room r, float x, float za, float zb, float[][] gaps, String secret, Material m) {
        float cur = za;
        for (float[] gp : gaps) {
            if (gp[0] > cur) segZ(r, x, cur, gp[0], m, null);
            if (gp[2] > 0) segZ(r, x, gp[0], gp[1], m, secret);
            else lintel(r, x, (gp[0] + gp[1]) / 2, (gp[1] - gp[0]) / 2, false, m);
            cur = gp[1];
        }
        if (cur < zb) segZ(r, x, cur, zb, m, null);
    }

    private void segX(Room r, float z, float x0, float x1, Material m, String secret) {
        float hw = (x1 - x0) / 2, cx = (x0 + x1) / 2;
        if (secret != null) {
            // fester Wandteil über der Geheimtür (sonst sähe man später durch ein Loch in den Himmel)
            float doorH = 3.6f;
            Geometry top = box(interior, m, hw, (r.h() - doorH) / 2, 0.3f, cx, r.y() + doorH + (r.h() - doorH) / 2, z, 0.5f);
            collide.attachChild(top.clone(false));
            Geometry low = box(interior, m, hw, doorH / 2, 0.3f, cx, r.y() + doorH / 2, z, 0.5f);
            walls.add(new Wall(x0, x1, z - 0.35f, z + 0.35f, secret));
            walls.get(walls.size() - 1).door = low;
            return;
        }
        Geometry g = box(interior, m, hw, r.h() / 2, 0.3f, cx, r.y() + r.h() / 2, z, 0.5f);
        if (secret == null) collide.attachChild(g.clone(false));
        Wall w = new Wall(x0, x1, z - 0.35f, z + 0.35f, secret);
        if (secret != null) w.door = g;
        walls.add(w);
    }

    private void segZ(Room r, float x, float z0, float z1, Material m, String secret) {
        float hw = (z1 - z0) / 2, cz = (z0 + z1) / 2;
        if (secret != null) {
            float doorH = 3.8f;
            Geometry top = box(interior, m, 0.3f, (r.h() - doorH) / 2, hw, x, r.y() + doorH + (r.h() - doorH) / 2, cz, 0.5f);
            collide.attachChild(top.clone(false));
            Geometry low = box(interior, m, 0.3f, doorH / 2, hw, x, r.y() + doorH / 2, cz, 0.5f);
            walls.add(new Wall(x - 0.35f, x + 0.35f, z0, z1, secret));
            walls.get(walls.size() - 1).door = low;
            return;
        }
        Geometry g = box(interior, m, 0.3f, r.h() / 2, hw, x, r.y() + r.h() / 2, cz, 0.5f);
        if (secret == null) collide.attachChild(g.clone(false));
        Wall w = new Wall(x - 0.35f, x + 0.35f, z0, z1, secret);
        if (secret != null) { w.door = g; }
        walls.add(w);
    }

    /** Sturz über einem Durchgang. */
    private void lintel(Room r, float x, float z, float half, boolean alongX, Material m) {
        float doorH = Math.min(4.5f, r.h() - 0.5f);
        float hh = (r.h() - doorH) / 2;
        Geometry g = alongX ? box(interior, m, half, hh, 0.3f, x, r.y() + doorH + hh, z, 0.5f)
            : box(interior, m, 0.3f, hh, half, x, r.y() + doorH + hh, z, 0.5f);
        collide.attachChild(g.clone(false));
    }

    void floorAndCeiling(Room r) {
        float hx = (r.x1() - r.x0()) / 2, hz = (r.z1() - r.z0()) / 2, cx = (r.x0() + r.x1()) / 2, cz = (r.z0() + r.z1()) / 2;
        Material fm = r.id().equals("hall") || r.id().equals("observatory") ? marble : r.id().equals("library") || r.id().equals("study") ? planks : stone;
        Box fb = new Box(hx, 0.1f, hz);
        fb.scaleTextureCoordinates(new Vector2f(hx / (fm == marble ? 2f : 1.2f), hz / (fm == marble ? 2f : 1.2f)));
        Geometry f = new Geometry("floor", fb);
        f.setMaterial(fm);
        f.setLocalTranslation(cx, r.y() - 0.1f, cz);
        interior.attachChild(f);
        Box cb = new Box(hx + 0.4f, 0.2f, hz + 0.4f);
        Material cm = r.id().equals("hall") || r.id().equals("observatory") ? starCeiling : planks;
        if (cm != starCeiling) cb.scaleTextureCoordinates(new Vector2f(hx / 1.5f, hz / 1.5f));
        Geometry c = new Geometry("ceiling", cb);
        c.setMaterial(cm);
        c.setLocalTranslation(cx, r.y() + r.h() + 0.2f, cz);
        interior.attachChild(c);
        collide.attachChild(c.clone(false));
        // Grundlicht je Raum
        ColorRGBA lc = r.id().equals("crypt") ? new ColorRGBA(0.5f, 0.6f, 1f, 1) : r.id().equals("cellar") ? new ColorRGBA(0.6f, 1f, 0.7f, 1) : new ColorRGBA(1f, 0.78f, 0.5f, 1);
        lights.add(new PointLight(new Vector3f(PX + cx, baseY + r.y() + r.h() * 0.6f, PZ + cz), lc.mult(0.75f), Math.max(hx, hz) * 1.7f));
    }

    void candle(Node n, float x, float y, float z, boolean floating) {
        Geometry c = new Geometry("candle", new Cylinder(2, 8, 0.05f, 0.35f, true));
        c.setMaterial(G3.lit(new ColorRGBA(0.95f, 0.92f, 0.82f, 1)));
        c.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        c.setLocalTranslation(x, y, z);
        n.attachChild(c);
        Geometry fl = new Geometry("flame", new Sphere(6, 6, 0.06f));
        fl.setMaterial(G3.emissive(new ColorRGBA(1f, 0.75f, 0.3f, 1)));
        fl.setLocalScale(1, 1.8f, 1);
        fl.setLocalTranslation(x, y + 0.24f, z);
        fl.setShadowMode(ShadowMode.Off);
        n.attachChild(fl);
        if (floating) {
            c.setUserData("float", rnd.nextFloat() * 6);
            fl.setUserData("float", c.getUserData("float"));
            candles.add(c);
            candles.add(fl);
        }
    }

    private void decorateHall(Room r) {
        Node n = interior;
        // Säulen mit goldenen Kapitellen
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 5; i++) {
                float z = -14 + i * 7, x = s * 9.5f;
                Geometry col = cylY(n, marble, 0.55f, 0.55f, r.h() - 1.2f, 16, x, (r.h() - 1.2f) / 2, z);
                cylY(n, gold, 0.8f, 0.8f, 0.4f, 16, x, r.h() - 1.0f, z);
                cylY(n, gold, 0.8f, 0.8f, 0.4f, 16, x, 0.2f, z);
                collide.attachChild(col.clone(false));
                walls.add(new Wall(x - 0.6f, x + 0.6f, z - 0.6f, z + 0.6f, null));
            }
        }
        // Lange Tafeln mit Bänken, Kelchen und Kerzen
        for (int s = -1; s <= 1; s += 2) {
            float x = s * 5;
            box(n, planks, 0.9f, 0.06f, 11, x, 0.85f, -1, 1.2f);
            for (int k = -1; k <= 1; k += 2) box(n, planks, 0.3f, 0.05f, 11, x + k * 1.35f, 0.48f, -1, 1.2f);
            for (int i = 0; i < 8; i++) {
                float z = -11 + i * 2.8f;
                cylY(n, gold, 0.07f, 0.05f, 0.22f, 10, x + 0.4f, 1.02f, z);
                if (i % 2 == 0) candle(n, x - 0.3f, 1.05f, z + 1.2f, false);
            }
            walls.add(new Wall(x - 1.7f, x + 1.7f, -12.2f, 10.2f, null));
        }
        // Schwebende Kerzen unter der Sternendecke
        for (int i = 0; i < 70; i++) {
            candle(n, -10 + rnd.nextFloat() * 20, 6.5f + rnd.nextFloat() * 3.5f, -16 + rnd.nextFloat() * 33, true);
        }
        // Kamin an der Nordwand (Geheimtür dahinter)
        for (int s = -1; s <= 1; s += 2) box(n, stone, 1.1f, 2.6f, 0.6f, s * 2.1f, 2.6f, -17.0f, 0.6f);
        box(n, stone, 1.0f, 0.6f, 0.6f, 0, 4.6f, -17.0f, 0.6f);
        Geometry back = box(n, G3.lit(new ColorRGBA(0.04f, 0.03f, 0.03f, 1)), 1.0f, 2.0f, 0.05f, 0, 2.0f, -16.45f, 0);
        back.setName("fireplaceBack");
        box(n, gold, 3.4f, 0.12f, 0.75f, 0, 5.25f, -16.9f, 0);
        walls.add(new Wall(-3.2f, -1.0f, -17.8f, -16.3f, null));
        walls.add(new Wall(1.0f, 3.2f, -17.8f, -16.3f, null));
        walls.add(new Wall(-1.0f, 1.0f, -17.8f, -16.3f, "kamin"));
        // Wandteppich an der Ostwand (Geheimgang zur Sternwarte)
        Geometry tap = box(n, tapestryMat(), 0.05f, 2.6f, 1.8f, 11.6f, 3.2f, -12, 0);
        tap.setName("tapestry");
        // Thronpodest und Banner
        for (int s = -1; s <= 1; s += 2) {
            Geometry b = box(n, G3.lit(new ColorRGBA(0.22f, 0.2f, 0.6f, 1)), 0.9f, 2.8f, 0.03f, s * 6, 7.5f, -17.6f, 0);
            b.setShadowMode(ShadowMode.Off);
        }
        // Roter Teppich vom Tor zum Kamin
        Box rb = new Box(1.6f, 0.02f, 16);
        Geometry rug = new Geometry("rug", rb);
        rug.setMaterial(red);
        rug.setLocalTranslation(0, 0.02f, 1);
        n.attachChild(rug);
        // Großer Kronleuchter
        Spatial ch = G3.model("lantern_chandelier_01");
        ch.setLocalScale(3.2f);
        ch.setLocalTranslation(0, r.h() - 0.3f, 0);
        n.attachChild(ch);
        lights.add(new PointLight(new Vector3f(PX, baseY + 8, PZ - 8), new ColorRGBA(1f, 0.8f, 0.55f, 1).mult(0.8f), 20));
        lights.add(new PointLight(new Vector3f(PX, baseY + 8, PZ + 9), new ColorRGBA(1f, 0.8f, 0.55f, 1).mult(0.8f), 20));
        // Großes altes Eichentor mit Eisenbeschlägen
        Material oak = G3.ph("brown_planks_05", new ColorRGBA(0.62f, 0.48f, 0.36f, 1));
        Material iron = G3.lit(new ColorRGBA(0.12f, 0.11f, 0.12f, 1));
        iron.setColor("Specular", new ColorRGBA(0.4f, 0.4f, 0.4f, 1));
        iron.setFloat("Shininess", 30);
        for (int sd = -1; sd <= 1; sd += 2) {
            box(n, oak, 1.28f, 3.0f, 0.12f, sd * 1.3f, 3.0f, 18.05f, 0.7f);
            for (float by : new float[]{0.9f, 3.0f, 5.1f}) box(n, iron, 1.22f, 0.07f, 0.03f, sd * 1.3f, by, 17.9f, 0);
            for (int k = 0; k < 4; k++) {
                Geometry rivet = new Geometry("rivet", new Sphere(5, 5, 0.05f));
                rivet.setMaterial(iron);
                rivet.setLocalTranslation(sd * (0.3f + k * 0.6f), 3.0f, 17.86f);
                n.attachChild(rivet);
            }
            Geometry ring = new Geometry("doorRing", new com.jme3.scene.shape.Torus(16, 6, 0.03f, 0.16f));
            ring.setMaterial(iron);
            ring.setLocalTranslation(sd * 0.35f, 2.6f, 17.86f);
            n.attachChild(ring);
            box(n, stone, 0.45f, 3.4f, 0.45f, sd * 3.0f, 3.4f, 18.0f, 0.6f);
        }
        box(n, stone, 3.45f, 0.45f, 0.5f, 0, 6.6f, 18.0f, 0.6f);
    }

    private static Material tapestryMat() {
        BufferedImage img = new BufferedImage(256, 384, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(90, 22, 40));
        g.fillRect(0, 0, 256, 384);
        g.setColor(new Color(220, 180, 90));
        g.setStroke(new BasicStroke(6));
        g.drawRect(10, 10, 236, 364);
        g.setColor(new Color(40, 30, 80));
        g.fillOval(48, 90, 160, 160);
        g.setColor(new Color(240, 210, 120));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            g.drawLine(128, 170, 128 + (int) (Math.cos(a) * 70), 170 + (int) (Math.sin(a) * 70));
        }
        g.fillOval(113, 155, 30, 30);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 30));
        g.drawString("Sternenhall", 52, 320);
        g.dispose();
        Material m = G3.lit(ColorRGBA.White);
        m.setTexture("DiffuseMap", G3.tex(img));
        return m;
    }

    private static Material signMat(String text) {
        BufferedImage img = new BufferedImage(512, 102, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(60, 36, 18));
        g.fillRoundRect(0, 0, 512, 102, 24, 24);
        g.setColor(new Color(236, 200, 120));
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 52));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, 256 - fm.stringWidth(text) / 2, 70);
        g.dispose();
        Material m = G3.lit(ColorRGBA.White);
        m.setTexture("DiffuseMap", G3.tex(img));
        m.setColor("GlowColor", new ColorRGBA(0.1f, 0.07f, 0.02f, 1));
        return m;
    }

    private void decorateLibrary(Room r) {
        Node n = interior;
        // Hohe Regale an den Wänden (das echte Bücherregal-Modell, gestapelt)
        for (int i = 0; i < 6; i++) {
            float x = -28.5f + i * 2.9f;
            if (Math.abs(x + 26) < 1.6f) continue; // Platz für das geheime Regal
            shelf(n, x, -9.4f, 0, 1.6f);
            shelf(n, x, 9.4f, FastMath.PI, 1.6f);
        }
        for (int i = 0; i < 6; i++) shelf(n, -29.4f, -7.5f + i * 3f, FastMath.HALF_PI, 1.6f);
        // Lesetische mit Öllampen
        for (int k = 0; k < 2; k++) {
            float x = -24 + k * 7;
            Spatial t = G3.model("round_wooden_table_01");
            t.setLocalTranslation(x, 0, 0);
            n.attachChild(t);
            Spatial lamp = G3.model("vintage_oil_lamp");
            lamp.setLocalScale(0.6f);
            lamp.setLocalTranslation(x + 0.2f, 1.0f, 0.1f);
            n.attachChild(lamp);
            walls.add(new Wall(x - 0.8f, x + 0.8f, -0.8f, 0.8f, null));
            lights.add(new PointLight(new Vector3f(PX + x, baseY + 1.8f, PZ), new ColorRGBA(1f, 0.72f, 0.4f, 1).mult(1.2f), 7));
        }
        // Schwebende Bücher
        for (int i = 0; i < 14; i++) {
            Geometry b = box(n, G3.lit(new ColorRGBA(0.3f + rnd.nextFloat() * 0.5f, 0.15f + rnd.nextFloat() * 0.3f, 0.2f + rnd.nextFloat() * 0.4f, 1)), 0.18f, 0.03f, 0.13f,
                -28 + rnd.nextFloat() * 14, 3 + rnd.nextFloat() * 3.5f, -7 + rnd.nextFloat() * 14, 0);
            b.setUserData("float", rnd.nextFloat() * 6);
            candles.add(b);
        }
        // Das geheime Regal: Bücherregal-Modell als Tür, mit leuchtend blauem Buch
        Node secretShelf = new Node("secretShelf");
        Spatial s = G3.model("wooden_bookshelf_worn");
        s.setLocalScale(1.6f);
        secretShelf.attachChild(s);
        Geometry blue = new Geometry("blueBook", new Box(0.04f, 0.18f, 0.13f));
        blue.setMaterial(G3.litGlow(new ColorRGBA(0.3f, 0.5f, 1f, 1), new ColorRGBA(0.2f, 0.35f, 0.9f, 1)));
        blue.setLocalTranslation(0.3f, 1.5f, 0.25f);
        blue.setLocalRotation(new Quaternion().fromAngleAxis(0.3f, Vector3f.UNIT_Z));
        secretShelf.attachChild(blue);
        secretShelf.setLocalTranslation(-26, 0, -9.6f);
        n.attachChild(secretShelf);
        for (Wall w : walls) if ("regal".equals(w.secret)) { w.door.removeFromParent(); w.door = secretShelf; }
    }

    void shelf(Node n, float x, float z, float yaw, float scale) { shelf(n, x, 0, z, yaw, scale); }

    void shelf(Node n, float x, float y0, float z, float yaw, float scale) {
        Spatial s = G3.model("wooden_bookshelf_worn");
        s.setLocalScale(scale);
        s.setLocalTranslation(x, y0, z);
        s.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        n.attachChild(s);
        // bunte Buchrücken in die Fächer
        Quaternion q = new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y);
        for (int f = 0; f < 4; f++) {
            for (int b = 0; b < 7; b++) {
                Vector3f p = q.mult(new Vector3f(-0.85f + b * 0.27f, 0.42f + f * 0.72f, 0.05f));
                Geometry book = box(n, G3.lit(new ColorRGBA(0.25f + rnd.nextFloat() * 0.6f, 0.1f + rnd.nextFloat() * 0.35f, 0.1f + rnd.nextFloat() * 0.45f, 1)),
                    0.06f, 0.2f + rnd.nextFloat() * 0.06f, 0.18f, x + p.x, y0 + p.y, z + p.z, 0);
                book.setLocalRotation(q);
            }
        }
    }

    private void decorateCellar(Room r) {
        Node n = interior;
        // Brodelnde Kessel
        for (int k = 0; k < 3; k++) {
            float x = 16 + k * 5, z = 4;
            Geometry pot = new Geometry("cauldron", new Sphere(14, 16, 0.9f));
            pot.setMaterial(G3.lit(new ColorRGBA(0.08f, 0.08f, 0.09f, 1)));
            pot.setLocalScale(1, 0.8f, 1);
            pot.setLocalTranslation(x, 0.75f, z);
            n.attachChild(pot);
            ColorRGBA c = k == 0 ? new ColorRGBA(0.3f, 1f, 0.5f, 1) : k == 1 ? new ColorRGBA(0.8f, 0.4f, 1f, 1) : new ColorRGBA(1f, 0.6f, 0.2f, 1);
            cylY(n, G3.emissive(c), 0.8f, 0.8f, 0.02f, 18, x, 1.3f, z);
            com.jme3.effect.ParticleEmitter steam = G3.particles("steam", 30, "Effects/Smoke/Smoke.png", c.clone().setAlpha(0.4f), new ColorRGBA(c.r, c.g, c.b, 0), 0.4f, 1.4f, 1.5f, 3f);
            steam.setImagesX(15);
            steam.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
            steam.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.6f, 0));
            steam.getParticleInfluencer().setVelocityVariation(0.3f);
            steam.setParticlesPerSec(8);
            steam.setLocalTranslation(x, 1.4f, z);
            n.attachChild(steam);
            walls.add(new Wall(x - 1, x + 1, z - 1, z + 1, null));
            lights.add(new PointLight(new Vector3f(PX + x, baseY + 2.2f, PZ + z), c.mult(1.1f), 6));
        }
        // Regale voller leuchtender Fläschchen an der Ostwand
        ColorRGBA[] pc = {new ColorRGBA(0.4f, 0.95f, 0.55f, 1), new ColorRGBA(0.95f, 0.4f, 0.85f, 1), new ColorRGBA(0.4f, 0.65f, 1f, 1), new ColorRGBA(1f, 0.8f, 0.3f, 1)};
        for (int sh = 0; sh < 4; sh++) {
            box(n, planks, 0.25f, 0.04f, 8, 29.3f, 0.8f + sh * 0.9f, 0, 1);
            for (int k = 0; k < 24; k++) {
                ColorRGBA c = pc[(k + sh) % pc.length];
                Geometry b = new Geometry("bottle", new Sphere(8, 8, 0.1f));
                b.setMaterial(G3.litGlow(c.mult(0.8f), c.mult(0.5f)));
                b.setLocalScale(1, 1.5f, 1);
                b.setLocalTranslation(29.3f, 0.98f + sh * 0.9f, -7.6f + k * 0.66f);
                b.setShadowMode(ShadowMode.Off);
                n.attachChild(b);
            }
        }
        // Die Mondplatte im Boden
        Geometry plate = cylY(n, stone, 1.1f, 1.1f, 0.05f, 24, 24, 0.03f, -4);
        Geometry moon = cylY(n, G3.litGlow(new ColorRGBA(0.85f, 0.9f, 1f, 1), new ColorRGBA(0.5f, 0.6f, 0.9f, 1)), 0.45f, 0.45f, 0.02f, 20, 24, 0.07f, -4);
        moon.setName("moonPlate");
        plate.setName("plate");
        // Fässer und Körbe
        for (int k = 0; k < 3; k++) {
            Spatial bucket = G3.model(k % 2 == 0 ? "wooden_bucket_01" : "wicker_basket_01");
            bucket.setLocalScale(1.4f);
            bucket.setLocalTranslation(14 + k * 1.1f, 0, -8.6f);
            n.attachChild(bucket);
        }
    }

    private void decorateSecretRooms(Room study, Room fire, Room obs, Room crypt) {
        Node n = interior;
        // Schreibstube: Pult mit Kerzen, Bücherstapel
        box(n, darkWood, 1.2f, 0.05f, 0.6f, -26, 0.9f, -14, 0);
        for (int i = 0; i < 3; i++) candle(n, -27 + i * 0.6f, 0.95f, -14.3f, false);
        lights.add(new PointLight(new Vector3f(PX - 26, baseY + 2.2f, PZ - 15), new ColorRGBA(1f, 0.75f, 0.45f, 1).mult(1.3f), 7));
        // Kaminkammer: glühende Kohlen
        cylY(n, G3.emissive(new ColorRGBA(1f, 0.4f, 0.1f, 1)), 0.8f, 0.8f, 0.05f, 16, 2.5f, 0.05f, -26);
        lights.add(new PointLight(new Vector3f(PX, baseY + 2.2f, PZ - 23), new ColorRGBA(1f, 0.5f, 0.2f, 1).mult(1.5f), 8));
        // Sternwarte: Teleskop, Sternenkuppel-Fenster
        Material brass = G3.lit(new ColorRGBA(0.85f, 0.65f, 0.3f, 1));
        brass.setColor("Specular", ColorRGBA.White);
        brass.setFloat("Shininess", 60);
        Geometry tube = new Geometry("telescope", new Cylinder(2, 16, 0.25f, 0.4f, 3.5f, true, false));
        tube.setMaterial(brass);
        tube.setLocalTranslation(61, 22 + 2.4f, 0);
        tube.setLocalRotation(new Quaternion().fromAngles(-0.7f, 0.5f, 0));
        n.attachChild(tube);
        cylY(n, darkWood, 0.15f, 0.25f, 2.2f, 10, 61, 22 + 1.1f, 0);
        walls.add(new Wall(60, 62, -1, 1, null));
        lights.add(new PointLight(new Vector3f(PX + 58, baseY + 22 + 5, PZ), new ColorRGBA(0.6f, 0.7f, 1f, 1).mult(1.4f), 14));
        // Gruft: Sarkophag, Kerzen, Spiegel
        box(n, stone, 1.0f, 0.6f, 2.2f, 62, -9 + 0.6f, 30, 0.8f);
        walls.add(new Wall(61, 63, 27.8f, 32.2f, null));
        for (int i = 0; i < 10; i++) candle(n, 52 + rnd.nextFloat() * 12, -9 + 0.05f, 23 + rnd.nextFloat() * 14, false);
        Geometry mirror = box(n, G3.litGlow(new ColorRGBA(0.75f, 0.85f, 1f, 1), new ColorRGBA(0.3f, 0.4f, 0.6f, 1)), 0.9f, 1.5f, 0.05f, 58, -9 + 2.2f, 37.6f, 0);
        mirror.setShadowMode(ShadowMode.Off);
        lights.add(new PointLight(new Vector3f(PX + 58, baseY - 9 + 3, PZ + 30), new ColorRGBA(0.5f, 0.6f, 1f, 1).mult(1.3f), 12));
        // Rückweg-Markierungen bei den Treppen
        for (Vector3f p : new Vector3f[]{new Vector3f(51.2f, 22, 0), new Vector3f(51.2f, -9, 30)}) {
            Geometry st = cylY(n, G3.litGlow(new ColorRGBA(0.9f, 0.8f, 0.5f, 1), new ColorRGBA(0.5f, 0.4f, 0.2f, 1)), 0.6f, 0.6f, 0.05f, 18, p.x, p.y + 0.03f, p.z);
            st.setShadowMode(ShadowMode.Off);
        }
    }

    // ================================================================ Kapitel: Symbol, Gemälde, Galerie

    private Node symbol, painting;
    /** Position des Gemäldes (lokal) und des Pults mit der Gründertafel in der Galerie. */
    public static final Vector3f PAINTING_POS = new Vector3f(-11.6f, 2.8f, -14f), PLAQUE_POS = new Vector3f(-17f, 0, -14.2f);
    /** Orte des wandernden Symbols: Saal, Bibliothek, Trankkeller (dunkle Ecke). */
    public static final Vector3f[] SYMBOL_POS = {new Vector3f(5.5f, 3.4f, -17.55f), new Vector3f(-29.55f, 2.6f, -3f), new Vector3f(29.55f, 1.8f, 8.5f)};
    private static final float[] SYMBOL_YAW = {0, FastMath.HALF_PI, -FastMath.HALF_PI};
    private int symbolAt = -1;
    private float symbolFade;

    private static java.awt.image.BufferedImage sigil() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 230, 160));
        g.setStroke(new BasicStroke(9, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(28, 28, 200, 200);
        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Auge über einer gespaltenen Wurzel – das Zeichen der Gründer
        g.drawArc(68, 86, 120, 70, 0, 180);
        g.drawArc(68, 50, 120, 70, 180, 180);
        g.fillOval(113, 92, 30, 30);
        g.drawLine(128, 150, 128, 210);
        g.drawLine(128, 180, 92, 214);
        g.drawLine(128, 180, 164, 214);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            g.fillOval(128 + (int) (Math.cos(a) * 112) - 5, 128 + (int) (Math.sin(a) * 112) - 5, 10, 10);
        }
        g.dispose();
        return img;
    }

    private void buildSymbol() {
        Material m = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", G3.tex(sigil()));
        m.setColor("Color", new ColorRGBA(0.85f, 0.6f, 1f, 1));
        m.setColor("GlowColor", new ColorRGBA(0.6f, 0.35f, 0.9f, 1));
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        m.getAdditionalRenderState().setDepthWrite(false);
        Geometry q = new Geometry("sigil", new Quad(1.6f, 1.6f));
        q.setMaterial(m);
        q.setLocalTranslation(-0.8f, -0.8f, 0);
        q.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        q.setShadowMode(ShadowMode.Off);
        symbol = new Node("symbol");
        symbol.attachChild(q);
        symbol.setCullHint(Spatial.CullHint.Always);
        interior.attachChild(symbol);
    }

    /** Symbol an Ort i zeigen (-1 = verborgen). revealed=false: nur ein Hauch, bis Lumen es sichtbar macht. */
    public void showSymbol(int i, boolean revealed) {
        symbolAt = i;
        if (i < 0) { symbol.setCullHint(Spatial.CullHint.Always); return; }
        symbol.setCullHint(Spatial.CullHint.Inherit);
        symbol.setLocalTranslation(SYMBOL_POS[i]);
        symbol.setLocalRotation(new Quaternion().fromAngleAxis(SYMBOL_YAW[i], Vector3f.UNIT_Y));
        symbolFade = revealed ? 1 : 0.08f;
    }

    public void revealSymbol() { symbolFade = 1; }

    public boolean symbolRevealed() { return symbolFade >= 0.99f; }

    public int symbolAt() { return symbolAt; }

    private void buildPainting() {
        painting = new Node("painting");
        BufferedImage img = new BufferedImage(256, 340, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(30, 24, 50), 0, 340, new Color(70, 45, 40)));
        g.fillRect(0, 0, 256, 340);
        // fünf Gestalten vor einem Schloss – die Gründer
        g.setColor(new Color(60, 50, 80));
        g.fillPolygon(new int[]{40, 70, 70, 110, 128, 146, 186, 186, 216}, new int[]{200, 200, 120, 120, 70, 120, 120, 200, 200}, 9);
        for (int i = 0; i < 5; i++) {
            int x = 34 + i * 44;
            g.setColor(new Color(20, 16, 26));
            g.fillOval(x + 6, 214, 22, 22);
            g.fillRoundRect(x, 234, 34, 70, 14, 14);
        }
        g.setColor(new Color(255, 220, 150, 120));
        g.fillOval(118, 40, 20, 20);
        g.dispose();
        Material pm = G3.lit(ColorRGBA.White);
        pm.setTexture("DiffuseMap", G3.tex(img));
        Geometry pic = new Geometry("canvas", new Quad(2.0f, 2.66f));
        pic.setMaterial(pm);
        pic.setLocalTranslation(-1.0f, -1.33f, 0.02f);
        painting.attachChild(pic);
        // goldener Rahmen
        for (int k = 0; k < 4; k++) {
            boolean hor = k < 2;
            Geometry f = new Geometry("frame", new Box(hor ? 1.15f : 0.1f, hor ? 0.1f : 1.43f, 0.06f));
            f.setMaterial(gold);
            f.setLocalTranslation(hor ? 0 : (k == 2 ? -1.08f : 1.08f), hor ? (k == 0 ? 1.38f : -1.38f) : 0, 0);
            painting.attachChild(f);
        }
        // drei Siegel
        Spell[] seals = {Spell.FLAMME, Spell.SCHWEBE, Spell.LICHT};
        for (int i = 0; i < 3; i++) {
            Geometry sg = new Geometry("seal" + seals[i].name(), new Cylinder(2, 18, 0.2f, 0.06f, true));
            ColorRGBA c = G3.rgb(seals[i].color);
            sg.setMaterial(G3.litGlow(c, c.mult(0.7f)));
            sg.setLocalTranslation(-0.7f + i * 0.7f, -1.38f, 0.12f);
            sg.setShadowMode(ShadowMode.Off);
            painting.attachChild(sg);
        }
        painting.setLocalTranslation(PAINTING_POS);
        painting.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
        interior.attachChild(painting);
    }

    private void buildGallery() {
        String[][] founders = {{"Arvid Sternenhall", "baute die Mauern"}, {"Mira Tausendlicht", "entzündete die Kerzen"},
            {"Oswin Graufels", "grub die Keller"}, {"Elena Wurzelherz", "pflanzte den Herzbaum"}, {"Der Fünfte", "Name ausgekratzt"}};
        for (int i = 0; i < founders.length; i++) {
            BufferedImage img = new BufferedImage(160, 220, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int hue = i * 63;
            g.setPaint(new GradientPaint(0, 0, new Color(Color.HSBtoRGB(hue / 360f, 0.4f, 0.35f)), 0, 220, new Color(20, 15, 25)));
            g.fillRect(0, 0, 160, 220);
            g.setColor(i == 4 ? new Color(10, 8, 12) : new Color(Color.HSBtoRGB(hue / 360f, 0.25f, 0.8f)));
            g.fillOval(52, 40, 56, 64);
            g.fillRoundRect(36, 100, 88, 90, 30, 30);
            if (i == 4) {
                g.setColor(new Color(120, 20, 30));
                g.setStroke(new BasicStroke(5));
                g.drawLine(40, 40, 120, 120);
                g.drawLine(120, 40, 40, 120);
            }
            g.setColor(new Color(230, 200, 130));
            g.setFont(new Font("Serif", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(founders[i][0], 80 - fm.stringWidth(founders[i][0]) / 2, 208);
            g.dispose();
            Material m = G3.lit(ColorRGBA.White);
            m.setTexture("DiffuseMap", G3.tex(img));
            Geometry pq = new Geometry("founder", new Quad(1.4f, 1.92f));
            pq.setMaterial(m);
            Node holder = new Node("founderPortrait");
            pq.setLocalTranslation(-0.7f, -0.96f, 0.04f);
            holder.attachChild(pq);
            Geometry fr = new Geometry("frame", new Box(0.8f, 1.06f, 0.03f));
            fr.setMaterial(gold);
            holder.attachChild(fr);
            if (i < 3) {
                holder.setLocalTranslation(-20f + i * 3.2f, 2.8f, -17.6f);
            } else {
                holder.setLocalTranslation(-21.1f, 2.8f, -15.6f + (i - 3) * 3.4f);
                holder.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
            }
            interior.attachChild(holder);
        }
        // Pult mit der Gründertafel
        box(interior, darkWood, 0.5f, 0.55f, 0.4f, PLAQUE_POS.x, 0.55f, PLAQUE_POS.z, 0);
        Geometry plaque = box(interior, G3.litGlow(new ColorRGBA(0.9f, 0.75f, 0.4f, 1), new ColorRGBA(0.3f, 0.2f, 0.05f, 1)), 0.4f, 0.03f, 0.3f, PLAQUE_POS.x, 1.14f, PLAQUE_POS.z, 0);
        plaque.setLocalRotation(new Quaternion().fromAngleAxis(0.3f, Vector3f.UNIT_X));
        walls.add(new Wall(PLAQUE_POS.x - 0.6f, PLAQUE_POS.x + 0.6f, PLAQUE_POS.z - 0.5f, PLAQUE_POS.z + 0.5f, null));
        for (int i = 0; i < 4; i++) candle(interior, -20.5f + i * 2.6f, 0.05f, -11f, false);
        lights.add(new PointLight(new Vector3f(PX - 17, baseY + 4, PZ - 14), new ColorRGBA(0.8f, 0.65f, 1f, 1).mult(1.0f), 10));
    }

    // ================================================================ Geheimnisse

    /** Ist ein Geheimgang schon offen? (Flag im Spielstand) */
    public static boolean isOpen(GameState st, String id) { return st.flags.contains("secret:" + id); }

    /** Öffnet passende Wände (beim Laden und nach dem Zauber). */
    public void applyOpen(GameState st) {
        for (Wall w : walls) {
            if (w.secret == null || !isOpen(st, w.secret)) continue;
            if (!w.open) {
                w.open = true;
                if (w.door != null) {
                    if ("kamin".equals(w.secret) || "gemaelde".equals(w.secret)) w.door.removeFromParent();
                    else w.door.move(0, 0, -1.6f);
                }
            }
        }
        if (isOpen(st, "gemaelde") && painting != null) painting.removeFromParent();
        if (painting != null) for (Spell sp : new Spell[]{Spell.FLAMME, Spell.SCHWEBE, Spell.LICHT}) {
            Spatial seal = painting.getChild("seal" + sp.name());
            if (seal != null && st.flags.contains("seal:" + sp.name())) seal.removeFromParent();
        }
        Spatial fb = interior.getChild("fireplaceBack");
        if (fb != null && isOpen(st, "kamin")) fb.removeFromParent();
        // Wandteppich hochgerollt, Mondplatte abgesenkt
        Spatial tap = interior.getChild("tapestry");
        if (tap != null && isOpen(st, "sternwarte")) tap.setLocalScale(1, 0.25f, 1);
        Spatial moon = interior.getChild("moonPlate");
        if (moon != null && isOpen(st, "gruft")) ((Geometry) moon).getMaterial().setColor("GlowColor", new ColorRGBA(0.9f, 1f, 1f, 1));
        for (Secret s : secrets) if (s.treasureGeo != null && st.flags.contains("secretTreasure:" + s.id)) s.treasureGeo.setCullHint(Spatial.CullHint.Always);
    }

    /** Welches Geheimnis liegt nahe an dieser Stelle (Weltkoordinaten)? */
    public Secret secretNear(Vector3f w, float r) {
        Vector3f l = toLocal(w);
        for (Secret s : secrets) if (s.spot.distance(l) < r) return s;
        return null;
    }

    public Secret treasureNear(Vector3f w, float r) {
        Vector3f l = toLocal(w);
        for (Secret s : secrets) if (s.treasurePos.distance(l) < r) return s;
        return null;
    }

    public Portal portalNear(Vector3f w, float r) {
        Vector3f l = toLocal(w);
        for (Portal p : portals) if (p.from().distance(l) < r) return p;
        return null;
    }

    private float t;
    /** Professorin für den Zauberunterricht (im großen Saal am Pult). */
    public Figure teacher;
    public static final Vector3f TEACHER_POS = new Vector3f(-7.6f, 0, 13.4f);

    public void addTeacher(Look look) {
        if (teacher != null) return;
        teacher = new Figure(look);
        teacher.node.setLocalTranslation(TEACHER_POS);
        teacher.node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
        interior.attachChild(teacher.node);
        // Pult mit aufgeschlagenem Zauberbuch und Kerze
        box(interior, darkWood, 0.45f, 0.55f, 0.35f, TEACHER_POS.x + 1.1f, 0.55f, TEACHER_POS.z, 0);
        Geometry book = box(interior, G3.lit(new ColorRGBA(0.95f, 0.9f, 0.78f, 1)), 0.35f, 0.03f, 0.25f, TEACHER_POS.x + 1.1f, 1.14f, TEACHER_POS.z, 0);
        book.setLocalRotation(new Quaternion().fromAngleAxis(0.25f, Vector3f.UNIT_Z));
        candle(interior, TEACHER_POS.x + 1.45f, 1.12f, TEACHER_POS.z + 0.25f, false);
        walls.add(new Wall(TEACHER_POS.x - 0.5f, TEACHER_POS.x + 1.6f, TEACHER_POS.z - 0.5f, TEACHER_POS.z + 0.5f, null));
    }

    public void update(float dt) {
        t += dt;
        if (depths != null) depths.update(dt);
        if (teacher != null) teacher.update(dt, 0, false);
        if (symbol != null && symbolAt >= 0) {
            Geometry q = (Geometry) symbol.getChild("sigil");
            float a = symbolFade * (0.65f + 0.35f * FastMath.sin(t * 3));
            q.getMaterial().setColor("Color", new ColorRGBA(0.85f, 0.6f, 1f, 1).mult(a));
            symbol.setLocalScale(1 + FastMath.sin(t * 1.5f) * 0.05f);
        }
        for (Geometry c : candles) {
            Object o = c.getUserData("float");
            if (o == null) continue;
            float ph = (Float) o;
            Vector3f p = c.getLocalTranslation();
            c.setLocalTranslation(p.x, p.y + FastMath.sin(t * 0.9f + ph) * 0.0015f, p.z);
        }
        for (Secret s : secrets) if (s.treasureGeo != null) s.treasureGeo.setLocalRotation(new Quaternion().fromAngleAxis(t * 1.6f, Vector3f.UNIT_Y));
    }
}
