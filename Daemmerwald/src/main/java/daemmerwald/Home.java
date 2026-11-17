package daemmerwald;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Dome;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

import java.util.*;

/**
 * Das eigene Haus: Man beginnt mit einer kleinen Holzhütte westlich vom Akademiehof und baut sie mit
 * gesammelten und an der Werkbank kombinierten Baustoffen Stufe um Stufe aus.
 */
public final class Home {
    private Home() { }

    public static final float X = WorldGen.HOME[0], Z = WorldGen.HOME[1];
    /** Blickrichtung der Haustür: zum Akademiehof. */
    public static final float YAW = FastMath.atan2(WorldGen.COURTYARD[0] - X, WorldGen.COURTYARD[1] - Z);

    // ---------------------------------------------------------------- Ausbaustufen

    public record Level(String title, String text, int coins, Map<String, Integer> needs) { }

    public static final List<Level> LEVELS = List.of(
        new Level("Kleine Holzhütte", "Vier Wände, ein Dach, ein Strohbett. Ein Anfang.", 0, Map.of()),
        new Level("Hütte mit Steinsockel", "Ein fester Sockel aus Stein und ein gemauerter Schornstein.", 20,
            need("B:BRETTER", 6, "B:STEIN", 8)),
        new Level("Blockhaus mit Veranda", "Größer, mit Veranda, Bank und Laternen vor der Tür.", 50,
            need("B:BRETTER", 12, "B:ZIEGEL", 6, "B:STEIN", 6)),
        new Level("Fachwerkhaus", "Zwei Stockwerke, Fachwerk und echte Glasfenster.", 100,
            need("B:BRETTER", 16, "B:ZIEGEL", 14, "B:KRISTALLGLAS", 3)),
        new Level("Haus mit Turmzimmer", "Ein runder Turm mit Sternenfenster und Wetterfahne aus Greifenfedern.", 180,
            need("B:BRETTER", 12, "B:ZIEGEL", 22, "B:KRISTALLGLAS", 6, "I:FEDER", 2)),
        new Level("Baumvilla im Dämmerwald", "Ein uralter Baum wächst durch dein Haus, im Garten leuchten Mondblumen.", 300,
            need("B:BRETTER", 24, "B:ZIEGEL", 20, "B:KRISTALLGLAS", 10, "I:MONDBLUME", 6))
    );

    private static Map<String, Integer> need(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    // ---------------------------------------------------------------- Zutaten (Baustoffe und Sammelzutaten)

    /** Schlüssel "B:HOLZ" für Baustoffe, "I:FEDER" für Sammelzutaten. */
    public static String title(String key) {
        String n = key.substring(2);
        return key.startsWith("B:") ? Baustoff.valueOf(n).title : Item.valueOf(n).title;
    }

    public static java.awt.Color color(String key) {
        String n = key.substring(2);
        return key.startsWith("B:") ? Baustoff.valueOf(n).color : Item.valueOf(n).color;
    }

    public static int have(GameState st, String key) {
        String n = key.substring(2);
        return key.startsWith("B:") ? st.mats(Baustoff.valueOf(n)) : st.items(Item.valueOf(n));
    }

    public static void take(GameState st, String key, int n) {
        String k = key.substring(2);
        if (key.startsWith("B:")) st.mats.merge(Baustoff.valueOf(k), -n, Integer::sum);
        else st.items.merge(Item.valueOf(k), -n, Integer::sum);
    }

    public static void give(GameState st, String key, int n) {
        String k = key.substring(2);
        if (key.startsWith("B:")) st.addMat(Baustoff.valueOf(k), n);
        else st.addItem(Item.valueOf(k), n);
    }

    public static boolean canAfford(GameState st, Map<String, Integer> needs, int coins) {
        if (st.coins < coins) return false;
        for (Map.Entry<String, Integer> e : needs.entrySet()) if (have(st, e.getKey()) < e.getValue()) return false;
        return true;
    }

    public static void pay(GameState st, Map<String, Integer> needs, int coins) {
        st.coins -= coins;
        needs.forEach((k, v) -> take(st, k, v));
    }

    // ---------------------------------------------------------------- Werkbank-Rezepte

    /** Ein Rezept: genau diese Zutaten auf die Werkbank legen und kombinieren. */
    public record Recipe(String id, Map<String, Integer> in, String out, int amount, String hint) { }

    public static final List<Recipe> RECIPES = List.of(
        new Recipe("bretter", need("B:HOLZ", 2), "B:BRETTER", 1, "Holz, das man glatt hobelt …"),
        new Recipe("ziegel", need("B:LEHM", 2, "B:STEIN", 1), "B:ZIEGEL", 2, "Lehm, gebrannt auf heißem Stein"),
        new Recipe("glas", need("B:STEIN", 1, "I:STERNKRISTALL", 1, "B:HOLZ", 1), "B:KRISTALLGLAS", 1, "Ein Kristall, geschmolzen im Holzfeuer auf Stein"),
        new Recipe("mondlaterne", need("B:KRISTALLGLAS", 1, "I:MONDBLUME", 1), "F:mondlaterne", 1, "Geheim: Licht einer Blume, eingefangen in Glas"),
        new Recipe("windspiel", need("I:FEDER", 1, "B:BRETTER", 1), "F:windspiel", 1, "Geheim: Etwas, das im Wind singt"),
        new Recipe("seerosenschale", need("I:SEEROSE", 2, "B:ZIEGEL", 1), "F:seerosenschale", 1, "Geheim: Ein Stück Teich fürs Haus")
    );

    /** Sucht das Rezept, das genau zu den Zutaten auf der Werkbank passt. */
    public static Recipe match(Map<String, Integer> bench) {
        for (Recipe r : RECIPES) if (r.in().equals(bench)) return r;
        return null;
    }

    // ---------------------------------------------------------------- Möbel

    public record Furniture(String id, String title, int coins, Map<String, Integer> needs, int minLevel, int cozy) { }

    public static final List<Furniture> FURNITURE = List.of(
        new Furniture("teppich", "Bunter Flickenteppich", 15, Map.of(), 0, 2),
        new Furniture("regal", "Bücherregal", 30, need("B:BRETTER", 3), 0, 3),
        new Furniture("schaukelstuhl", "Schaukelstuhl", 25, need("B:BRETTER", 2), 0, 3),
        new Furniture("pflanzen", "Topfpflanzen", 10, need("I:KROETENMOOS", 2), 0, 2),
        new Furniture("kamin", "Gemauerter Kamin", 25, need("B:ZIEGEL", 6), 1, 5),
        new Furniture("himmelbett", "Himmelbett", 40, need("B:BRETTER", 4), 1, 4),
        new Furniture("wandteppich", "Wandteppich mit Feenmuster", 35, Map.of(), 1, 3),
        new Furniture("lampe", "Kristalllampe", 35, need("B:KRISTALLGLAS", 1), 1, 3),
        new Furniture("sofa", "Kuschelsofa mit Kissen", 60, Map.of(), 2, 5),
        new Furniture("gemaelde", "Gemälde vom Dämmerwald", 45, Map.of(), 2, 3),
        new Furniture("trankregal", "Trankregal mit Fläschchen", 50, need("B:BRETTER", 2), 2, 3),
        new Furniture("harfe", "Elfenharfe", 80, Map.of(), 3, 4),
        new Furniture("aquarium", "Leuchtfisch-Aquarium", 90, need("B:KRISTALLGLAS", 2), 3, 5),
        new Furniture("teleskop", "Sternenteleskop", 120, need("B:KRISTALLGLAS", 2), 4, 5),
        new Furniture("kronleuchter", "Feenlicht-Kronleuchter", 150, need("B:KRISTALLGLAS", 3), 5, 6)
    );

    /** Selbst gebaute Schmuckstücke aus geheimen Rezepten. */
    public static final Map<String, String> SECRET = Map.of(
        "mondlaterne", "Mondlaterne", "windspiel", "Windspiel aus Greifenfedern", "seerosenschale", "Seerosenschale");

    public static String furnitureTitle(String id) {
        for (Furniture f : FURNITURE) if (f.id().equals(id)) return f.title();
        return SECRET.getOrDefault(id, id);
    }

    /** Gemütlichkeit: Ausbaustufe und Möbel zusammen. */
    public static int cozy(GameState st) {
        int c = st.homeLevel * 4;
        for (Furniture f : FURNITURE) if (st.furniture.contains(f.id())) c += f.cozy();
        for (String s : SECRET.keySet()) if (st.furniture.contains(s)) c += 4;
        return c;
    }

    // ---------------------------------------------------------------- Lage

    public static Vector3f door(int level) {
        float d = footprint(level)[1] + 0.9f;
        return new Vector3f(X + FastMath.sin(YAW) * d, WorldGen.height(X + FastMath.sin(YAW) * d, Z + FastMath.cos(YAW) * d), Z + FastMath.cos(YAW) * d);
    }

    /** Halbe Breite und halbe Tiefe des Hauses. */
    public static float[] footprint(int level) {
        return switch (level) {
            case 0 -> new float[]{1.8f, 1.6f};
            case 1 -> new float[]{2.2f, 2.0f};
            default -> new float[]{3.2f, 2.8f};
        };
    }

    public static float radius(int level) { return level >= 5 ? 5.2f : footprint(level)[0] + 0.6f; }

    // ---------------------------------------------------------------- 3D-Modell (außen)

    private static Material tex(String path, ColorRGBA tint, Mesh mesh, float su, float sv) {
        Material m = G3.texture(path, tint);
        if (mesh != null) mesh.scaleTextureCoordinates(new Vector2f(su, sv));
        return m;
    }

    private static Geometry box(Node n, String name, float hx, float hy, float hz, Material m, float x, float y, float z) {
        Geometry g = new Geometry(name, new Box(hx, hy, hz));
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        n.attachChild(g);
        return g;
    }

    private static Geometry texBox(Node n, String path, ColorRGBA tint, float hx, float hy, float hz, float x, float y, float z, float tile) {
        Box b = new Box(hx, hy, hz);
        Geometry g = new Geometry("tb", b);
        g.setMaterial(tex(path, tint, b, Math.max(1, hx * tile), Math.max(1, hy * tile)));
        g.setLocalTranslation(x, y, z);
        n.attachChild(g);
        return g;
    }

    private static final String BARK = "Models/Tree/BarkColor.jpg", BRICK = "Textures/Terrain/BrickWall/BrickWall.jpg",
        ROCK = "Textures/Terrain/Rocky/RockyTexture.jpg";

    /** Satteldach über einer Grundfläche (halbe Breite w, halbe Tiefe d) auf Höhe y. */
    private static void roof(Node n, float w, float d, float y, float pitch, Material m) {
        float slope = (float) Math.hypot(w + 0.4f, pitch);
        float ang = FastMath.atan2(pitch, w + 0.4f);
        for (int side = -1; side <= 1; side += 2) {
            Box rb = new Box(slope / 2, 0.09f, d + 0.45f);
            rb.scaleTextureCoordinates(new Vector2f(slope * 0.8f, (d + 0.45f) * 0.8f));
            Geometry r = new Geometry("roof", rb);
            r.setMaterial(m);
            r.setLocalTranslation(side * (w + 0.4f) / 2, y + pitch / 2, 0);
            n.attachChild(r);
            r.setLocalRotation(new Quaternion().fromAngleAxis(-side * ang, Vector3f.UNIT_Z));
        }
        // Giebeldreiecke
        Material gm = G3.lit(new ColorRGBA(0.5f, 0.36f, 0.22f, 1));
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 5; k++) {
                float f = (k + 0.5f) / 5f;
                box(n, "gable", w * (1 - f), pitch / 10f, 0.06f, gm, 0, y + pitch * f, s * d);
            }
        }
    }

    private static void window(Node n, float x, float y, float z, float yaw, float w, float h, boolean glass) {
        Node wn = new Node("window");
        Material frame = G3.lit(new ColorRGBA(0.32f, 0.2f, 0.1f, 1));
        ColorRGBA glow = glass ? new ColorRGBA(1f, 0.85f, 0.55f, 1) : new ColorRGBA(0.95f, 0.7f, 0.4f, 1);
        Geometry pane = box(wn, "pane", w / 2, h / 2, 0.02f, G3.litGlow(glow.mult(0.6f), glow.mult(glass ? 0.45f : 0.3f)), 0, 0, 0.03f);
        pane.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
        box(wn, "fr", w / 2 + 0.07f, 0.05f, 0.06f, frame, 0, h / 2, 0.05f);
        box(wn, "fr", w / 2 + 0.07f, 0.05f, 0.06f, frame, 0, -h / 2, 0.05f);
        box(wn, "fr", 0.05f, h / 2, 0.06f, frame, -w / 2, 0, 0.05f);
        box(wn, "fr", 0.05f, h / 2, 0.06f, frame, w / 2, 0, 0.05f);
        box(wn, "fr", 0.03f, h / 2, 0.06f, frame, 0, 0, 0.06f);
        box(wn, "fr", w / 2, 0.03f, 0.06f, frame, 0, 0, 0.06f);
        // Blumenkasten
        box(wn, "planter", w / 2 + 0.05f, 0.09f, 0.12f, G3.lit(new ColorRGBA(0.45f, 0.28f, 0.14f, 1)), 0, -h / 2 - 0.12f, 0.16f);
        ColorRGBA[] fl = {new ColorRGBA(1f, 0.4f, 0.55f, 1), new ColorRGBA(1f, 0.85f, 0.3f, 1), new ColorRGBA(0.6f, 0.55f, 1f, 1)};
        for (int i = 0; i < 4; i++) {
            Geometry f = new Geometry("flower", new Sphere(6, 6, 0.07f));
            f.setMaterial(G3.lit(fl[i % 3]));
            f.setLocalTranslation(-w / 2 + 0.12f + i * (w - 0.24f) / 3, -h / 2 - 0.0f, 0.18f);
            wn.attachChild(f);
        }
        wn.setLocalTranslation(x, y, z);
        wn.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        n.attachChild(wn);
    }

    private static void door(Node n, float z, float h) {
        Material wood = G3.lit(new ColorRGBA(0.42f, 0.24f, 0.11f, 1));
        box(n, "door", 0.5f, h / 2, 0.06f, wood, 0, h / 2, z + 0.04f);
        for (int i = -2; i <= 2; i++) box(n, "plank", 0.012f, h / 2 - 0.05f, 0.07f, G3.lit(new ColorRGBA(0.3f, 0.17f, 0.08f, 1)), i * 0.19f, h / 2, z + 0.05f);
        Geometry knob = new Geometry("knob", new Sphere(6, 8, 0.05f));
        Material brass = G3.lit(new ColorRGBA(0.9f, 0.75f, 0.35f, 1));
        brass.setColor("Specular", ColorRGBA.White);
        brass.setFloat("Shininess", 40);
        knob.setMaterial(brass);
        knob.setLocalTranslation(0.32f, h * 0.48f, z + 0.13f);
        n.attachChild(knob);
        box(n, "step", 0.75f, 0.08f, 0.35f, tex(ROCK, new ColorRGBA(0.75f, 0.72f, 0.68f, 1), null, 1, 1), 0, 0.05f, z + 0.35f);
    }

    private static void lantern(Node n, float x, float y, float z) {
        box(n, "post", 0.05f, y / 2, 0.05f, G3.lit(new ColorRGBA(0.12f, 0.1f, 0.09f, 1)), x, y / 2, z);
        Geometry l = new Geometry("lamp", new Sphere(8, 10, 0.16f));
        l.setMaterial(G3.emissive(new ColorRGBA(1f, 0.8f, 0.45f, 1)));
        l.setLocalTranslation(x, y + 0.12f, z);
        n.attachChild(l);
    }

    private static void chimney(Node n, float x, float z, float top) {
        texBox(n, ROCK, new ColorRGBA(0.7f, 0.68f, 0.65f, 1), 0.32f, top / 2, 0.32f, x, top / 2, z, 1.5f);
        ParticleSmoke.attach(n, new Vector3f(x, top + 0.2f, z));
    }

    /** Baut das Haus der gegebenen Stufe (lokal um den Bauplatz, Tür zeigt nach +z). */
    public static Node build(int level) {
        Node root = new Node("home");
        Node n = new Node("homeBody");
        root.attachChild(n);
        float[] fp = footprint(level);
        float w = fp[0], d = fp[1];
        Material roofMat = tex(BARK, new ColorRGBA(0.55f, 0.42f, 0.32f, 1), null, 1, 1);
        Material shingles = G3.phFresh("clay_roof_tiles", new ColorRGBA(0.95f, 0.82f, 0.78f, 1));
        float wallH;
        switch (level) {
            case 0 -> {
                wallH = 2.2f;
                logWalls(n, w, d, 0, wallH);
                roof(n, w, d, wallH, 1.3f, roofMat);
                door(n, d + 0.14f, 1.8f);
                window(n, w + 0.2f, 1.3f, 0, FastMath.HALF_PI, 0.6f, 0.5f, false);
                // Holzstapel neben der Hütte
                for (int i = 0; i < 6; i++) {
                    Geometry lg = new Geometry("log", new Cylinder(2, 8, 0.12f, 0.9f, true));
                    lg.setMaterial(tex(BARK, ColorRGBA.White, null, 1, 1));
                    lg.setLocalTranslation(-w - 0.4f, 0.13f + (i / 3) * 0.22f, -0.5f + (i % 3) * 0.26f + (i / 3) * 0.13f);
                    n.attachChild(lg);
                }
            }
            case 1 -> {
                wallH = 2.4f;
                texBox(n, ROCK, new ColorRGBA(0.78f, 0.76f, 0.72f, 1), w + 0.12f, 0.35f, d + 0.12f, 0, 0.35f, 0, 1.2f);
                logWalls(n, w, d, 0.7f, wallH);
                roof(n, w, d, 0.7f + wallH, 1.5f, roofMat);
                chimney(n, -w * 0.5f, -d * 0.4f, 0.7f + wallH + 2.0f);
                door(n, d + 0.16f, 1.9f);
                window(n, -w * 0.55f, 1.8f, d + 0.2f, 0, 0.7f, 0.6f, false);
                window(n, w + 0.2f, 1.8f, 0, FastMath.HALF_PI, 0.7f, 0.6f, false);
                window(n, -w - 0.2f, 1.8f, 0, -FastMath.HALF_PI, 0.7f, 0.6f, false);
            }
            case 2 -> {
                wallH = 2.6f;
                texBox(n, ROCK, new ColorRGBA(0.78f, 0.76f, 0.72f, 1), w + 0.12f, 0.35f, d + 0.12f, 0, 0.35f, 0, 1.2f);
                logWalls(n, w, d, 0.7f, wallH);
                roof(n, w, d, 0.7f + wallH, 1.8f, shingles);
                chimney(n, -w * 0.55f, -d * 0.3f, 0.7f + wallH + 2.3f);
                door(n, d + 0.16f, 2.0f);
                veranda(n, w, d);
                for (int s = -1; s <= 1; s += 2) window(n, s * w * 0.55f, 1.9f, d + 0.2f, 0, 0.8f, 0.7f, false);
                window(n, w + 0.2f, 1.9f, 0, FastMath.HALF_PI, 0.8f, 0.7f, false);
                window(n, -w - 0.2f, 1.9f, 0, -FastMath.HALF_PI, 0.8f, 0.7f, false);
            }
            default -> {
                // Fachwerk: unten Ziegel, oben helles Gefach mit dunklen Balken
                texBox(n, BRICK, new ColorRGBA(0.85f, 0.6f, 0.5f, 1), w, 1.4f, d, 0, 1.4f, 0, 1.0f);
                timber(n, w, d, 2.8f, 2.4f);
                roof(n, w + 0.2f, d, 5.2f, 2.2f, shingles);
                chimney(n, -w * 0.55f, -d * 0.3f, 8.2f);
                door(n, d, 2.1f);
                veranda(n, w, d);
                for (int s = -1; s <= 1; s += 2) {
                    window(n, s * w * 0.55f, 1.5f, d + 0.06f, 0, 0.85f, 0.75f, true);
                    window(n, s * w * 0.5f, 4.0f, d + 0.12f, 0, 0.75f, 0.7f, true);
                    window(n, s * (w + 0.06f), 1.5f, 0, s * FastMath.HALF_PI, 0.85f, 0.75f, true);
                    window(n, s * (w + 0.12f), 4.0f, 0, s * FastMath.HALF_PI, 0.75f, 0.7f, true);
                }
                if (level >= 4) tower(n, w, d);
                if (level >= 5) villa(root, w, d);
            }
        }
        G3.tangents(root);
        root.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive);
        root.setLocalTranslation(X, WorldGen.height(X, Z) - 0.05f, Z);
        root.setLocalRotation(new Quaternion().fromAngleAxis(YAW, Vector3f.UNIT_Y));
        return root;
    }

    /** Wände aus waagerechten Rundhölzern. */
    private static void logWalls(Node n, float w, float d, float y0, float h) {
        Material bark = tex(BARK, new ColorRGBA(0.85f, 0.72f, 0.58f, 1), null, 1, 1);
        int rows = (int) (h / 0.28f);
        for (int i = 0; i < rows; i++) {
            float y = y0 + 0.14f + i * 0.28f;
            for (int s = -1; s <= 1; s += 2) {
                Geometry a = new Geometry("log", new Cylinder(2, 10, 0.15f, 2 * w + 0.5f, true));
                a.setMaterial(bark);
                a.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
                a.setLocalTranslation(0, y, s * d + (i % 2) * 0.01f);
                n.attachChild(a);
                Geometry b = new Geometry("log", new Cylinder(2, 10, 0.15f, 2 * d + 0.5f, true));
                b.setMaterial(bark);
                b.setLocalTranslation(s * w, y + 0.14f, 0);
                n.attachChild(b);
            }
        }
        // Innenfüllung, damit keine Lücken zu sehen sind
        box(n, "core", w - 0.05f, h / 2, d - 0.05f, G3.lit(new ColorRGBA(0.3f, 0.2f, 0.12f, 1)), 0, y0 + h / 2, 0);
    }

    private static void timber(Node n, float w, float d, float y0, float h) {
        Box pb = new Box(w, h / 2, d);
        pb.scaleTextureCoordinates(new Vector2f(w, h / 2));
        Geometry pl = new Geometry("plaster", pb);
        pl.setMaterial(G3.phFresh("clay_plaster", new ColorRGBA(1f, 0.96f, 0.86f, 1)));
        pl.setLocalTranslation(0, y0 + h / 2, 0);
        n.attachChild(pl);
        Material beam = G3.lit(new ColorRGBA(0.26f, 0.16f, 0.09f, 1));
        for (int s = -1; s <= 1; s += 2) {
            box(n, "beam", w + 0.03f, 0.09f, 0.05f, beam, 0, y0 + 0.05f, s * (d + 0.02f));
            box(n, "beam", w + 0.03f, 0.09f, 0.05f, beam, 0, y0 + h - 0.05f, s * (d + 0.02f));
            box(n, "beam", 0.05f, 0.09f, d + 0.03f, beam, s * (w + 0.02f), y0 + 0.05f, 0);
            box(n, "beam", 0.05f, 0.09f, d + 0.03f, beam, s * (w + 0.02f), y0 + h - 0.05f, 0);
            for (float x = -w; x <= w + 0.01f; x += w / 2) box(n, "beam", 0.07f, h / 2, 0.05f, beam, x, y0 + h / 2, s * (d + 0.02f));
            for (float z = -d; z <= d + 0.01f; z += d) box(n, "beam", 0.05f, h / 2, 0.07f, beam, s * (w + 0.02f), y0 + h / 2, z);
            // Andreaskreuze
            for (int k = -1; k <= 1; k += 2) {
                Geometry x = box(n, "beam", 0.06f, h * 0.55f, 0.05f, beam, k * w * 0.25f, y0 + h / 2, s * (d + 0.03f));
                x.setLocalRotation(new Quaternion().fromAngleAxis(0.55f * k, Vector3f.UNIT_Z));
            }
        }
    }

    private static void veranda(Node n, float w, float d) {
        Material plank = G3.phFresh("brown_planks_05", ColorRGBA.White);
        Material post = G3.lit(new ColorRGBA(0.38f, 0.24f, 0.13f, 1));
        box(n, "deck", w + 0.3f, 0.08f, 0.8f, plank, 0, 0.18f, d + 0.85f);
        for (int s = -1; s <= 1; s += 2) {
            box(n, "post", 0.08f, 1.25f, 0.08f, post, s * (w + 0.2f), 1.3f, d + 1.55f);
            box(n, "rail", 0.04f, 0.04f, 0.75f, post, s * (w + 0.2f), 0.9f, d + 0.85f);
        }
        box(n, "rail", w * 0.32f, 0.04f, 0.04f, post, -w * 0.62f, 0.9f, d + 1.55f);
        box(n, "rail", w * 0.32f, 0.04f, 0.04f, post, w * 0.62f, 0.9f, d + 1.55f);
        Geometry canopy = box(n, "canopy", w + 0.4f, 0.06f, 0.95f, G3.phFresh("clay_roof_tiles", new ColorRGBA(0.95f, 0.82f, 0.78f, 1)), 0, 2.62f, d + 0.85f);
        canopy.setLocalRotation(new Quaternion().fromAngleAxis(0.18f, Vector3f.UNIT_X));
        // Bank und Laternen
        box(n, "bench", 0.7f, 0.05f, 0.2f, plank, -w * 0.6f, 0.62f, d + 0.4f);
        box(n, "benchBack", 0.7f, 0.2f, 0.03f, plank, -w * 0.6f, 0.9f, d + 0.22f);
        for (int s = -1; s <= 1; s += 2) box(n, "leg", 0.04f, 0.2f, 0.15f, post, -w * 0.6f + s * 0.6f, 0.4f, d + 0.4f);
        lantern(n, w + 0.6f, 1.5f, d + 2.0f);
        lantern(n, -w - 0.6f, 1.5f, d + 2.0f);
    }

    private static void tower(Node n, float w, float d) {
        float tx = w * 0.85f, tz = -d * 0.6f, r = 1.5f, h = 8.6f;
        Cylinder c = new Cylinder(2, 20, r, h, true);
        Geometry t = new Geometry("tower", c);
        t.setMaterial(tex(ROCK, new ColorRGBA(0.82f, 0.78f, 0.72f, 1), c, 4, 3));
        t.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        t.setLocalTranslation(tx, h / 2, tz);
        n.attachChild(t);
        Geometry cone = new Geometry("towerRoof", new Dome(Vector3f.ZERO, 2, 20, r + 0.35f, false));
        cone.setMaterial(G3.phFresh("clay_roof_tiles", new ColorRGBA(0.7f, 0.55f, 0.85f, 1)));
        cone.setLocalScale(1, 3.2f / (r + 0.35f), 1);
        cone.setLocalTranslation(tx, h, tz);
        n.attachChild(cone);
        // Sternenfenster rundum
        for (int i = 0; i < 4; i++) {
            float a = i * FastMath.HALF_PI + 0.4f;
            Geometry win = new Geometry("starWin", new Sphere(8, 10, 0.38f));
            win.setMaterial(G3.litGlow(new ColorRGBA(0.6f, 0.75f, 1f, 1), new ColorRGBA(0.35f, 0.5f, 0.9f, 1)));
            win.setLocalScale(1, 1, 0.15f);
            win.setLocalTranslation(tx + FastMath.sin(a) * r, h - 1.3f, tz + FastMath.cos(a) * r);
            win.setLocalRotation(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y));
            n.attachChild(win);
        }
        // Wetterfahne mit Greifenfedern
        box(n, "vanePole", 0.03f, 0.6f, 0.03f, G3.lit(new ColorRGBA(0.2f, 0.2f, 0.22f, 1)), tx, h + 3.4f, tz);
        Geometry feather = box(n, "vane", 0.04f, 0.35f, 0.02f, G3.litGlow(new ColorRGBA(1f, 0.75f, 0.4f, 1), new ColorRGBA(0.5f, 0.3f, 0.1f, 1)), tx + 0.25f, h + 3.8f, tz);
        feather.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI - 0.2f, Vector3f.UNIT_Z));
    }

    /** Stufe 5: ein uralter Baum wächst hinter dem Haus empor, dazu Garten und Feenlichter. */
    private static void villa(Node root, float w, float d) {
        try {
            com.jme3.scene.Spatial tree = G3.am.loadModel("Models/Tree/Tree.mesh.j3o");
            tree.setLocalScale(5.5f);
            tree.setLocalTranslation(-w * 0.2f, 0, -d - 1.6f);
            root.attachChild(tree);
        } catch (Exception e) {
            // Baum nicht ladbar – Villa bleibt trotzdem schön
        }
        ColorRGBA[] cols = {new ColorRGBA(0.75f, 0.85f, 1f, 1), new ColorRGBA(1f, 0.6f, 0.85f, 1), new ColorRGBA(1f, 0.9f, 0.5f, 1)};
        Random r = new Random(7);
        for (int i = 0; i < 26; i++) {
            float a = r.nextFloat() * FastMath.TWO_PI, rr = w + 2.5f + r.nextFloat() * 2.5f;
            float x = FastMath.sin(a) * rr, z = FastMath.cos(a) * rr;
            if (z > d && Math.abs(x) < 1.6f) continue; // Weg zur Tür frei
            ColorRGBA c = cols[i % 3];
            Geometry f = new Geometry("gardenFlower", new Sphere(6, 8, 0.12f));
            f.setMaterial(G3.litGlow(c, c.mult(0.4f)));
            f.setLocalTranslation(x, 0.35f, z);
            root.attachChild(f);
            Geometry s = new Geometry("stem", new Box(0.015f, 0.17f, 0.015f));
            s.setMaterial(G3.lit(new ColorRGBA(0.3f, 0.55f, 0.25f, 1)));
            s.setLocalTranslation(x, 0.17f, z);
            root.attachChild(s);
        }
        // Lichterkette am Dach
        for (int i = 0; i <= 12; i++) {
            float x = -w - 0.3f + i * (2 * w + 0.6f) / 12f;
            Geometry l = new Geometry("fairyLight", new Sphere(5, 6, 0.06f));
            l.setMaterial(G3.emissive(cols[i % 3]));
            l.setLocalTranslation(x, 5.0f - FastMath.sin(i / 12f * FastMath.PI) * 0.25f, d + 0.55f);
            root.attachChild(l);
        }
    }

    /** Kleiner Rauchfaden aus dem Schornstein. */
    private static final class ParticleSmoke {
        static void attach(Node n, Vector3f p) {
            com.jme3.effect.ParticleEmitter e = G3.particles("homeSmoke", 30, "Effects/Smoke/Smoke.png",
                new ColorRGBA(0.75f, 0.75f, 0.78f, 0.35f), new ColorRGBA(0.6f, 0.6f, 0.65f, 0), 0.3f, 1.4f, 2.5f, 4f);
            e.setImagesX(15);
            e.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            e.getParticleInfluencer().setInitialVelocity(new Vector3f(0.15f, 0.7f, 0));
            e.getParticleInfluencer().setVelocityVariation(0.25f);
            e.setParticlesPerSec(6);
            e.setLocalTranslation(p);
            n.attachChild(e);
        }
    }
}
