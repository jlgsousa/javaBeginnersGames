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
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Was unter Sternenhall liegt (lokale Palast-Koordinaten, weit östlich der Palasträume):
 * die vergessene Bibliothek (Kapitel 5), die alten Ruinen mit Fallen und dem Steinwächter (Kapitel 6)
 * und die Kammer des Schlafenden (Kapitel 7).
 */
public final class Depths {
    final Palace p;
    final Node n;

    // Bibliothek
    public static final float LY = -20;
    public static final Vector3f LIB_ARRIVE = new Vector3f(118, LY, 21), LIB_BACK = new Vector3f(118, LY, 23.2f);
    public static final Vector3f[] CANDELABRA = {new Vector3f(104, LY, 12), new Vector3f(132, LY, 12), new Vector3f(118, LY, 6)};
    public static final Vector3f[] BOOKSTACK = {new Vector3f(108, LY, 5), new Vector3f(118, LY, 9.5f), new Vector3f(128, LY, 5)};
    public static final Vector3f[] BRIDGE_SLOT = {new Vector3f(118, LY, 2), new Vector3f(118, LY, 0), new Vector3f(118, LY, -2)};
    public static final Vector3f[] PILLAR = {new Vector3f(106, LY, -10), new Vector3f(112, LY, -17), new Vector3f(124, LY, -17), new Vector3f(130, LY, -10)};
    public static final Vector3f MURAL = new Vector3f(118, LY + 3.2f, -23.6f), LECTERN = new Vector3f(118, LY, -19);
    public static final Vector3f LIB_NORTH_SAFE = new Vector3f(118, LY, -5);
    public static final Vector3f TO_RUINS = new Vector3f(134, LY, -22);
    /** Reihenfolge der Runensäulen (Index in PILLAR). */
    public static final int[] ORDER = {2, 0, 3, 1};

    // Ruinen
    public static final float RY = -34;
    public static final Vector3f RUIN_ARRIVE = new Vector3f(154, RY, 17), RUIN_BACK = new Vector3f(154, RY, 19.3f);
    public static final float[] FLAME_ROWS = {8, 0, -8};
    public static final float DART_Z0 = -26, DART_Z1 = -17;
    public static final Vector3f BLOCK = new Vector3f(151.5f, RY, -30), PLATE = new Vector3f(156.2f, RY, -31.5f);
    public static final float GATE_Z = -36;
    public static final Vector3f ARENA_C = new Vector3f(158, RY, -82);
    public static final Vector3f[] DEBRIS = {new Vector3f(146, RY, -69), new Vector3f(170, RY, -69), new Vector3f(146, RY, -95), new Vector3f(170, RY, -95)};
    public static final Vector3f TO_HEART = new Vector3f(158, RY, -98.5f);

    // Kammer des Schlafenden
    public static final float HY = -50;
    public static final Vector3f HEART_ARRIVE = new Vector3f(210, HY, 17), HEART_C = new Vector3f(210, HY, -2), STAR_HEART = new Vector3f(210, HY + 6, -14);
    public static final Vector3f[] BRAZIERS = new Vector3f[5];
    public static final Vector3f[] SHADE_SPOTS = {new Vector3f(210, HY, -8), new Vector3f(199, HY, -1), new Vector3f(221, HY, -1), new Vector3f(204, HY, 8), new Vector3f(216, HY, 8)};

    static {
        for (int i = 0; i < 5; i++) {
            float a = i * FastMath.TWO_PI / 5 + FastMath.PI;
            BRAZIERS[i] = new Vector3f(210 + FastMath.sin(a) * 14, HY, -2 + FastMath.cos(a) * 14);
        }
    }

    public Palace.Room libS, libN, bridge, corridor, arena, heart;
    final List<PointLight> libLights = new ArrayList<>();
    public final Geometry[] candleFlames = new Geometry[3];
    public final Node[] pillarNodes = new Node[4];
    public final Geometry[] pillarRunes = new Geometry[4];
    public Geometry muralGlyphs, bookOfFive, ruinPortal, heartPortal, libPortal;
    public final Geometry[] bookStacks = new Geometry[3];
    public final List<Geometry[]> flameJets = new ArrayList<>();
    public Geometry gate, blockGeo, plateGeo;
    public final Geometry[] braziers = new Geometry[5];
    public final PointLight[] brazierLights = new PointLight[5];
    public Node starHeart;
    final Palace.Wall gateWall;

    Depths(Palace p) {
        this.p = p;
        this.n = p.interior;
        int before = p.lights.size();
        buildLibrary();
        gateWall = buildRuins();
        buildHeart();
        for (int i = before; i < p.lights.size(); i++) n.addLight(p.lights.get(i));
        G3.tangents(n);
        p.collide.updateGeometricState();
    }

    Vector3f w(Vector3f local) { return p.toWorld(local); }

    private PointLight light(float x, float y, float z, ColorRGBA c, float r, boolean attach) {
        PointLight l = new PointLight(new Vector3f(Palace.PX + x, p.baseY + y, Palace.PZ + z), c, r);
        if (attach) n.addLight(l);
        return l;
    }

    private void walls4(Palace.Room r, Material m, float[][] gN, float[][] gS, float[][] gW, float[][] gE) {
        p.wallX(r, r.z0(), r.x0(), r.x1(), gN, null, m);
        p.wallX(r, r.z1(), r.x0(), r.x1(), gS, null, m);
        p.wallZ(r, r.x0(), r.z0(), r.z1(), gW, null, m);
        p.wallZ(r, r.x1(), r.z0(), r.z1(), gE, null, m);
    }

    // ================================================================ Die vergessene Bibliothek

    private void buildLibrary() {
        Material wood = p.planks, stone = p.stone;
        libS = p.room("deep_lib", "Die vergessene Bibliothek", 100, 136, 3, 24, LY, 11);
        libN = p.room("deep_lib2", "Die vergessene Bibliothek", 100, 136, -24, -3, LY, 11);
        // Wände: Süd und Nord, West und Ost über die ganze Länge (mit Schlucht)
        Palace.Room all = new Palace.Room("x", "x", 100, 136, -24, 24, LY, 11);
        p.wallX(all, -24, 100, 136, new float[][]{}, null, stone);
        p.wallX(all, 24, 100, 136, new float[][]{}, null, stone);
        p.wallZ(all, 100, -24, 24, new float[][]{}, null, stone);
        p.wallZ(all, 136, -24, 24, new float[][]{}, null, stone);
        p.floorAndCeiling(libS);
        p.floorAndCeiling(libN);
        // die Grundlichter der beiden Hälften: erst an, wenn die Leuchter brennen
        for (int i = 0; i < 2; i++) {
            PointLight l = p.lights.remove(p.lights.size() - 1);
            l.setColor(new ColorRGBA(1f, 0.72f, 0.45f, 1).mult(0.9f));
            libLights.add(l);
        }
        // Decke über der Schlucht und die Tiefe darunter
        p.box(n, wood, 18, 0.2f, 3.2f, 118, LY + 11.2f, 0, 0.6f);
        Geometry abyss = p.box(n, G3.emissive(new ColorRGBA(0.22f, 0.06f, 0.4f, 1)), 18, 0.2f, 3, 118, LY - 9, 0, 0);
        for (int s = -1; s <= 1; s += 2) {
            Geometry edge = p.box(n, G3.emissive(new ColorRGBA(0.6f, 0.3f, 1f, 1)), 18, 0.03f, 0.05f, 118, LY + 0.02f, s * 3f, 0);
            edge.setShadowMode(ShadowMode.Off);
        }
        abyss.setShadowMode(ShadowMode.Off);
        for (int s = -1; s <= 1; s += 2) p.box(n, stone, 18, 7, 0.2f, 118, LY - 7, s * 3, 0.5f);
        com.jme3.effect.ParticleEmitter mist = G3.particles("abyssMist", 60, "Effects/Smoke/Smoke.png",
            new ColorRGBA(0.45f, 0.2f, 0.8f, 0.5f), new ColorRGBA(0.2f, 0.05f, 0.4f, 0), 1.5f, 3f, 3f, 5f);
        mist.setImagesX(15);
        mist.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mist.setShape(new com.jme3.effect.shapes.EmitterBoxShape(new Vector3f(-17, -1, -2.5f), new Vector3f(17, 1, 2.5f)));
        mist.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.8f, 0));
        mist.setParticlesPerSec(14);
        mist.setLocalTranslation(118, LY - 6, 0);
        n.attachChild(mist);
        // Hohe Regale an allen Wänden
        for (int i = 0; i < 11; i++) {
            float x = 102 + i * 3.2f;
            if (Math.abs(x - 118) > 2.5f) p.shelf(n, x, LY, 23.4f, FastMath.PI, 1.9f);
            if (Math.abs(x - 118) > 4 && Math.abs(x - 134) > 2) p.shelf(n, x, LY, -23.4f, 0, 1.9f);
        }
        for (int i = 0; i < 6; i++) {
            float z = 5.5f + i * 3.1f;
            p.shelf(n, 100.6f, LY, z, FastMath.HALF_PI, 1.9f);
            p.shelf(n, 135.4f, LY, z, -FastMath.HALF_PI, 1.9f);
            p.shelf(n, 100.6f, LY, -z, FastMath.HALF_PI, 1.9f);
            p.shelf(n, 135.4f, LY, -z, -FastMath.HALF_PI, 1.9f);
        }
        // Spinnweben-graue Schwebebücher
        java.util.Random r = new java.util.Random(77);
        for (int i = 0; i < 26; i++) {
            Geometry b = p.box(n, G3.lit(new ColorRGBA(0.25f + r.nextFloat() * 0.3f, 0.2f + r.nextFloat() * 0.2f, 0.2f + r.nextFloat() * 0.3f, 1)), 0.2f, 0.035f, 0.14f,
                102 + r.nextFloat() * 32, LY + 3.5f + r.nextFloat() * 5, -22 + r.nextFloat() * 44, 0);
            b.setLocalRotation(new Quaternion().fromAngles(r.nextFloat() * 0.6f, r.nextFloat() * 6, r.nextFloat() * 0.6f));
        }
        // Drei kalte Leuchter (Ignis)
        for (int i = 0; i < 3; i++) {
            Vector3f c = CANDELABRA[i];
            p.cylY(n, p.gold, 0.1f, 0.14f, 2.2f, 10, c.x, c.y + 1.1f, c.z);
            p.cylY(n, p.gold, 0.5f, 0.6f, 0.15f, 14, c.x, c.y + 0.08f, c.z);
            Geometry bowl = p.cylY(n, p.gold, 0.55f, 0.25f, 0.3f, 14, c.x, c.y + 2.3f, c.z);
            bowl.setShadowMode(ShadowMode.Off);
            Geometry fl = G3.geom("candelaFlame", new Sphere(8, 8, 0.3f), G3.emissive(new ColorRGBA(1f, 0.6f, 0.2f, 1)));
            fl.setLocalScale(1, 1.6f, 1);
            fl.setLocalTranslation(c.x, c.y + 2.75f, c.z);
            fl.setCullHint(Spatial.CullHint.Always);
            fl.setShadowMode(ShadowMode.Off);
            n.attachChild(fl);
            candleFlames[i] = fl;
            p.walls.add(new Palace.Wall(c.x - 0.5f, c.x + 0.5f, c.z - 0.5f, c.z + 0.5f, null));
        }
        // Drei Bücherstapel (Levitaris baut die Brücke)
        Material books = G3.lit(new ColorRGBA(0.45f, 0.2f, 0.18f, 1));
        for (int i = 0; i < 3; i++) {
            Vector3f b = BOOKSTACK[i];
            Geometry g = new Geometry("bookStack", new Box(1.2f, 0.35f, 0.9f));
            g.setMaterial(books);
            g.setLocalTranslation(b.x, b.y + 0.35f, b.z);
            n.attachChild(g);
            bookStacks[i] = g;
        }
        // Vier Runensäulen und das Wandbild mit der Reihenfolge
        for (int i = 0; i < 4; i++) {
            Vector3f c = PILLAR[i];
            Node pn = new Node("pillar" + i);
            Geometry col = new Geometry("runePillar", new Cylinder(2, 6, 0.6f, 3.2f, true));
            col.setMaterial(stone);
            col.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
            col.setLocalTranslation(0, 1.6f, 0);
            pn.attachChild(col);
            Geometry rune = G3.geom("pillarRune", new Quad(0.9f, 0.9f), runeMat(i, new ColorRGBA(0.5f, 0.45f, 0.6f, 1)));
            rune.setLocalTranslation(-0.45f, 1.6f, 0.62f);
            rune.setShadowMode(ShadowMode.Off);
            pn.attachChild(rune);
            pillarRunes[i] = rune;
            pn.setLocalTranslation(c);
            n.attachChild(pn);
            pillarNodes[i] = pn;
            p.walls.add(new Palace.Wall(c.x - 0.65f, c.x + 0.65f, c.z - 0.65f, c.z + 0.65f, null));
        }
        // Wandbild: Lumen macht die vier Runen in der richtigen Reihenfolge sichtbar
        p.box(n, p.darkWood, 4.2f, 1.6f, 0.05f, MURAL.x, MURAL.y, MURAL.z - 0.15f, 0);
        muralGlyphs = G3.geom("mural", new Quad(8, 2.6f), muralMat());
        muralGlyphs.setLocalTranslation(MURAL.x - 4, MURAL.y - 1.3f, MURAL.z - 0.05f);
        muralGlyphs.setShadowMode(ShadowMode.Off);
        muralGlyphs.getMaterial().setColor("Color", new ColorRGBA(1, 1, 1, 0.04f));
        n.attachChild(muralGlyphs);
        // Pult mit dem Buch der Fünf (erscheint am Ende)
        p.box(n, p.darkWood, 0.5f, 0.6f, 0.4f, LECTERN.x, LY + 0.6f, LECTERN.z, 0);
        bookOfFive = p.box(n, G3.litGlow(new ColorRGBA(0.85f, 0.65f, 0.3f, 1), new ColorRGBA(0.5f, 0.35f, 0.1f, 1)), 0.4f, 0.06f, 0.3f, LECTERN.x, LY + 1.28f, LECTERN.z, 0);
        bookOfFive.setCullHint(Spatial.CullHint.Always);
        p.walls.add(new Palace.Wall(LECTERN.x - 0.6f, LECTERN.x + 0.6f, LECTERN.z - 0.5f, LECTERN.z + 0.5f, null));
        // Ausgänge: zurück hinauf (Süd) und hinab in die Ruinen (Nordost, erst offen nach dem Rätsel)
        archway(LIB_BACK.x, LY, 23.5f, 0, new ColorRGBA(1f, 0.8f, 0.5f, 1));
        libPortal = archway(TO_RUINS.x, LY, -23.5f, FastMath.PI, new ColorRGBA(0.6f, 0.4f, 1f, 1));
        libPortal.setCullHint(Spatial.CullHint.Always);
        light(118, LY + 3, 20, new ColorRGBA(0.5f, 0.45f, 0.7f, 1).mult(0.35f), 18, true);
    }

    /** Leuchtender Torbogen an einer Wand. */
    private Geometry archway(float x, float y, float z, float yaw, ColorRGBA c) {
        Geometry g = G3.geom("archway", new Quad(2.4f, 3.6f), G3.emissive(c.mult(0.6f)));
        g.getMaterial().getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        g.setLocalTranslation(x - 1.2f, y, z);
        g.setShadowMode(ShadowMode.Off);
        n.attachChild(g);
        Geometry arch = new Geometry("arch", new Torus(20, 6, 0.15f, 1.3f));
        arch.setMaterial(p.stone);
        arch.setLocalTranslation(x, y + 2.6f, z);
        n.attachChild(arch);
        return g;
    }

    private static Material runeMat(int i, ColorRGBA c) {
        Material m = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", G3.tex(runeImg(i)));
        m.setColor("Color", c);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return m;
    }

    /** Vier Runen: Stern, Mond, Flamme, Auge. */
    static BufferedImage runeImg(int i) {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(8, 8, 112, 112);
        switch (i) {
            case 0 -> { for (int k = 0; k < 5; k++) { double a1 = -Math.PI / 2 + k * 4 * Math.PI / 5, a2 = a1 + 4 * Math.PI / 5;
                g.drawLine(64 + (int) (Math.cos(a1) * 40), 64 + (int) (Math.sin(a1) * 40), 64 + (int) (Math.cos(a2) * 40), 64 + (int) (Math.sin(a2) * 40)); } }
            case 1 -> { g.drawArc(30, 26, 70, 76, 60, 240); g.drawArc(48, 26, 60, 76, 80, 200); }
            case 2 -> { g.drawLine(64, 24, 40, 80); g.drawLine(64, 24, 88, 80); g.drawArc(40, 56, 48, 48, 180, 180); g.drawLine(64, 50, 64, 86); }
            default -> { g.drawOval(30, 46, 68, 36); g.fillOval(54, 54, 20, 20); }
        }
        g.dispose();
        return img;
    }

    private static Material muralMat() {
        BufferedImage img = new BufferedImage(1024, 333, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 230, 160));
        g.setFont(new java.awt.Font("Serif", java.awt.Font.ITALIC, 30));
        g.drawString("Erst der Mond im Osten, dann der Stern, der Westen träumt,", 60, 40);
        for (int k = 0; k < 4; k++) {
            BufferedImage r = runeImg(ORDER[k]);
            g.drawImage(r, 70 + k * 235, 80, 150, 150, null);
            g.setFont(new java.awt.Font("Serif", java.awt.Font.BOLD, 40));
            g.drawString(String.valueOf(k + 1), 135 + k * 235, 300);
        }
        g.dispose();
        Material m = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", G3.tex(img));
        m.setColor("Color", ColorRGBA.White);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        m.getAdditionalRenderState().setDepthWrite(false);
        return m;
    }

    /** Die Brücke über die Schlucht ist fertig: begehbar machen. */
    public void openBridge() {
        if (bridge != null) return;
        bridge = p.room("deep_bridge", "Die vergessene Bibliothek", 116.6f, 119.4f, -3.2f, 3.2f, LY, 11);
    }

    boolean libLit;

    public void setLibraryLit(boolean on) {
        if (on == libLit) return;
        libLit = on;
        for (PointLight l : libLights) { if (on) n.addLight(l); else n.removeLight(l); }
    }

    // ================================================================ Die alten Ruinen

    private Palace.Wall buildRuins() {
        Material stone = p.stone;
        corridor = p.room("deep_ruin", "Die alten Ruinen", 150, 158, -60, 20, RY, 8);
        arena = p.room("deep_arena", "Halle des Steinwächters", 140, 176, -100, -60, RY, 16);
        p.wallX(corridor, 20, 150, 158, new float[][]{}, null, stone);
        p.wallZ(corridor, 150, -60, 20, new float[][]{}, null, stone);
        p.wallZ(corridor, 158, -60, 20, new float[][]{}, null, stone);
        p.wallX(arena, -60, 140, 176, new float[][]{{150, 158, 0}}, null, stone);
        p.wallX(arena, -100, 140, 176, new float[][]{}, null, stone);
        p.wallZ(arena, 140, -100, -60, new float[][]{}, null, stone);
        p.wallZ(arena, 176, -100, -60, new float[][]{}, null, stone);
        p.floorAndCeiling(corridor);
        p.floorAndCeiling(arena);
        // zerbrochene Säulen und Fackeln entlang des Gangs
        for (int i = 0; i < 9; i++) {
            float z = 16 - i * 9.5f;
            for (int s = -1; s <= 1; s += 2) {
                p.cylY(n, stone, 0.35f, 0.4f, 6 - (i % 3), 8, 154 + s * 3.4f, RY + (6 - (i % 3)) / 2f, z);
                if (i % 2 == 0) {
                    Geometry torch = G3.geom("torch", new Sphere(6, 6, 0.14f), G3.emissive(new ColorRGBA(1f, 0.55f, 0.2f, 1)));
                    torch.setLocalScale(1, 1.7f, 1);
                    torch.setLocalTranslation(154 + s * 3.65f, RY + 2.6f, z + 1.2f);
                    torch.setShadowMode(ShadowMode.Off);
                    n.attachChild(torch);
                }
            }
            if (i % 2 == 0) light(154, RY + 3, z, new ColorRGBA(1f, 0.55f, 0.25f, 1).mult(1.1f), 9, true);
        }
        // Flammenfallen: drei Reihen mit je drei Düsen
        for (float z : FLAME_ROWS) {
            Geometry[] row = new Geometry[3];
            for (int k = 0; k < 3; k++) {
                float x = 151.5f + k * 2.5f;
                p.cylY(n, p.darkWood, 0.25f, 0.3f, 0.12f, 10, x, RY + 0.06f, z);
                Geometry jet = G3.geom("flameJet", new Cylinder(2, 10, 0.05f, 0.45f, 2.6f, true, false), G3.emissive(new ColorRGBA(1f, 0.5f, 0.15f, 1)));
                jet.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
                jet.setLocalTranslation(x, RY + 1.3f, z);
                jet.setShadowMode(ShadowMode.Off);
                jet.setCullHint(Spatial.CullHint.Always);
                n.attachChild(jet);
                row[k] = jet;
            }
            flameJets.add(row);
        }
        // Pfeilwand (Westseite), Löcher als dunkle Punkte
        for (float z = DART_Z0; z <= DART_Z1; z += 1.5f) {
            Geometry hole = G3.geom("dartHole", new Sphere(6, 6, 0.08f), G3.emissive(new ColorRGBA(0.6f, 0.15f, 0.1f, 1)));
            hole.setLocalTranslation(150.35f, RY + 1.2f, z);
            n.attachChild(hole);
        }
        // Druckplatte, Steinblock und Fallgitter
        plateGeo = p.box(n, G3.litGlow(new ColorRGBA(0.5f, 0.45f, 0.35f, 1), new ColorRGBA(0.15f, 0.1f, 0.02f, 1)), 0.9f, 0.05f, 0.9f, PLATE.x, RY + 0.05f, PLATE.z, 0);
        blockGeo = p.box(n, stone, 0.7f, 0.7f, 0.7f, BLOCK.x, RY + 0.7f, BLOCK.z, 0.8f);
        Palace.Wall bw = new Palace.Wall(BLOCK.x - 0.75f, BLOCK.x + 0.75f, BLOCK.z - 0.75f, BLOCK.z + 0.75f, "block");
        p.walls.add(bw);
        gate = p.box(n, G3.lit(new ColorRGBA(0.25f, 0.22f, 0.2f, 1)), 4, 3, 0.15f, 154, RY + 3, GATE_Z, 0);
        Palace.Wall gw = new Palace.Wall(150, 158, GATE_Z - 0.3f, GATE_Z + 0.3f, "gate");
        p.walls.add(gw);
        // Arena: zerborstener Boden, Säulenstümpfe, Feuerschalen
        for (int i = 0; i < 6; i++) {
            float a = i * FastMath.TWO_PI / 6 + FastMath.PI / 6;
            float x = ARENA_C.x + FastMath.sin(a) * 15, z = ARENA_C.z + FastMath.cos(a) * 15;
            p.cylY(n, stone, 0.9f, 1f, 10, 10, x, RY + 5, z);
            p.walls.add(new Palace.Wall(x - 1, x + 1, z - 1, z + 1, null));
            Geometry fire = G3.geom("brazier", new Sphere(8, 8, 0.4f), G3.emissive(new ColorRGBA(1f, 0.55f, 0.2f, 1)));
            fire.setLocalTranslation(x, RY + 10.4f, z);
            fire.setShadowMode(ShadowMode.Off);
            n.attachChild(fire);
        }
        light(ARENA_C.x, RY + 9, ARENA_C.z, new ColorRGBA(1f, 0.6f, 0.3f, 1).mult(1.3f), 34, true);
        light(ARENA_C.x, RY + 4, ARENA_C.z - 12, new ColorRGBA(0.7f, 0.5f, 1f, 1).mult(0.7f), 18, true);
        heartPortal = archway(TO_HEART.x, RY, -99.6f, 0, new ColorRGBA(0.6f, 0.4f, 1f, 1));
        heartPortal.setCullHint(Spatial.CullHint.Always);
        archway(RUIN_BACK.x, RY, 19.6f, 0, new ColorRGBA(1f, 0.8f, 0.5f, 1));
        return gw;
    }

    public void openGate(boolean open) {
        gateWall.open = open;
        gate.setLocalTranslation(154, RY + (open ? 6.5f : 3), GATE_Z);
    }

    public void blockOnPlate(boolean on) {
        Palace.Wall bw = null;
        for (Palace.Wall wl : p.walls) if ("block".equals(wl.secret)) bw = wl;
        if (bw != null) bw.open = true;
        Vector3f at = on ? PLATE : BLOCK;
        blockGeo.setLocalTranslation(at.x, RY + 0.7f, at.z);
        if (on) p.walls.add(new Palace.Wall(PLATE.x - 0.75f, PLATE.x + 0.75f, PLATE.z - 0.75f, PLATE.z + 0.75f, null));
        plateGeo.getMaterial().setColor("GlowColor", on ? new ColorRGBA(0.6f, 0.45f, 0.1f, 1) : new ColorRGBA(0.15f, 0.1f, 0.02f, 1));
    }

    // ================================================================ Die Kammer des Schlafenden

    private void buildHeart() {
        Material stone = p.stone;
        heart = p.room("deep_heart", "Die Kammer von Cabeceiras", 190, 230, -24, 20, HY, 18);
        walls4(heart, stone, new float[][]{}, new float[][]{}, new float[][]{}, new float[][]{});
        p.floorAndCeiling(heart);
        // Das Sternenherz: ein riesiger Kristall in Ketten
        starHeart = new Node("starHeart");
        Geometry crystal = G3.geom("starCrystal", new Sphere(6, 8, 2.4f), G3.litGlow(new ColorRGBA(0.6f, 0.75f, 1f, 1), new ColorRGBA(0.35f, 0.5f, 1f, 1)));
        crystal.setLocalScale(1, 1.8f, 1);
        crystal.setShadowMode(ShadowMode.Off);
        starHeart.attachChild(crystal);
        for (int i = 0; i < 4; i++) {
            float a = i * FastMath.HALF_PI + 0.4f;
            Geometry chain = G3.geom("chain", new Cylinder(2, 6, 0.08f, 9, true), p.darkWood);
            chain.setLocalTranslation(FastMath.sin(a) * 4, 2, FastMath.cos(a) * 4);
            chain.setLocalRotation(new Quaternion().fromAngles(FastMath.cos(a) * 0.9f, 0, -FastMath.sin(a) * 0.9f).mult(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X)));
            starHeart.attachChild(chain);
        }
        starHeart.setLocalTranslation(STAR_HEART);
        n.attachChild(starHeart);
        p.walls.add(new Palace.Wall(STAR_HEART.x - 2.6f, STAR_HEART.x + 2.6f, STAR_HEART.z - 2.6f, STAR_HEART.z + 2.6f, null));
        light(STAR_HEART.x, HY + 6, STAR_HEART.z + 3, new ColorRGBA(0.5f, 0.65f, 1f, 1).mult(1.6f), 30, true);
        light(210, HY + 10, 10, new ColorRGBA(0.6f, 0.4f, 0.9f, 1).mult(0.6f), 26, true);
        // fünf Gründerfeuer
        for (int i = 0; i < 5; i++) {
            Vector3f b = BRAZIERS[i];
            p.cylY(n, stone, 0.5f, 0.35f, 1.2f, 12, b.x, HY + 0.6f, b.z);
            p.cylY(n, p.gold, 0.7f, 0.45f, 0.3f, 14, b.x, HY + 1.3f, b.z);
            Geometry f = G3.geom("founderFire", new Sphere(8, 8, 0.45f), G3.emissive(new ColorRGBA(1f, 0.85f, 0.45f, 1)));
            f.setLocalScale(1, 1.7f, 1);
            f.setLocalTranslation(b.x, HY + 1.9f, b.z);
            f.setShadowMode(ShadowMode.Off);
            f.setCullHint(Spatial.CullHint.Always);
            n.attachChild(f);
            braziers[i] = f;
            brazierLights[i] = light(b.x, HY + 2.5f, b.z, new ColorRGBA(1f, 0.75f, 0.4f, 1).mult(1.2f), 10, false);
            p.walls.add(new Palace.Wall(b.x - 0.6f, b.x + 0.6f, b.z - 0.6f, b.z + 0.6f, null));
        }
        // Statuen der fünf Gründer entlang der Wände (Silhouetten aus Stein)
        for (int i = 0; i < 5; i++) {
            float x = 194 + i * 8;
            p.cylY(n, stone, 0.6f, 0.4f, 3.6f, 10, x, HY + 1.8f, -22.8f);
            Geometry head = G3.geom("statueHead", new Sphere(10, 10, 0.45f), stone);
            head.setLocalTranslation(x, HY + 4.0f, -22.8f);
            n.attachChild(head);
        }
        archway(210, HY, 19.6f, 0, new ColorRGBA(1f, 0.8f, 0.5f, 1));
    }

    public void lightBrazier(int i, boolean on) {
        braziers[i].setCullHint(on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (on) n.addLight(brazierLights[i]);
        else n.removeLight(brazierLights[i]);
    }

    float t;

    public void update(float dt) {
        t += dt;
        if (starHeart != null) {
            starHeart.setLocalRotation(new Quaternion().fromAngleAxis(t * 0.2f, Vector3f.UNIT_Y));
            starHeart.setLocalTranslation(STAR_HEART.x, STAR_HEART.y + FastMath.sin(t * 0.8f) * 0.3f, STAR_HEART.z);
        }
        for (Geometry f : candleFlames) f.setLocalScale(1 + FastMath.sin(t * 13) * 0.08f, 1.6f + FastMath.sin(t * 9) * 0.15f, 1);
    }
}
