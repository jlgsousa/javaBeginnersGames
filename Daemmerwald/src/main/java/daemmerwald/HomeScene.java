package daemmerwald;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;
import com.jme3.texture.Texture;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;

/** Im eigenen Haus: Werkbank, Ausbau, Möbel, Ausruhen und Speichern. */
public class HomeScene implements Scene, Main.CameraDirector {
    private enum Mode { MENU, BENCH, BUILD, SHOP, TALK }

    private static final String[] BENCH_KEYS = {"B:HOLZ", "B:STEIN", "B:LEHM", "B:BRETTER", "B:ZIEGEL", "B:KRISTALLGLAS",
        "I:STERNKRISTALL", "I:MONDBLUME", "I:FEDER", "I:SEEROSE", "I:KROETENMOOS"};

    private final GamePanel game;
    private final WorldScene world;
    private final World3D w3;
    private final GameState st;
    private final Input in;
    private final Vector3f base;
    private Node interior = new Node("homeInterior");
    private final List<PointLight> lights = new ArrayList<>();
    private Figure guest, guest2;
    private Mode mode = Mode.MENU;
    private DialogBox dialog;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final Map<String, Integer> bench = new LinkedHashMap<>();
    private Home.Recipe lastRecipe;
    private String benchMsg = "";
    private Color benchMsgColor = Art.PARCH;
    private float t, lookYaw, savedT, flashT;
    private float W, D, H;

    public HomeScene(GamePanel game, WorldScene world) {
        this.game = game;
        this.world = world;
        this.w3 = world.world;
        this.st = game.state;
        this.in = game.input;
        base = new Vector3f(Home.X, WorldGen.height(Home.X, Home.Z) + 300, Home.Z);
        build();
    }

    // ================================================================ Innenraum

    private Material tex(String path, ColorRGBA tint, Mesh mesh, float su, float sv) {
        Material m = G3.lit(tint);
        Texture tx = G3.am.loadTexture(path);
        tx.setWrap(Texture.WrapMode.Repeat);
        m.setTexture("DiffuseMap", tx);
        if (mesh != null) mesh.scaleTextureCoordinates(new Vector2f(su, sv));
        return m;
    }

    private Geometry box(float hx, float hy, float hz, Material m, float x, float y, float z) {
        Geometry g = new Geometry("h", new Box(hx, hy, hz));
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        interior.attachChild(g);
        return g;
    }

    private Geometry texBox(String path, ColorRGBA tint, float hx, float hy, float hz, float x, float y, float z, float tile) {
        Box b = new Box(hx, hy, hz);
        Geometry g = new Geometry("h", b);
        g.setMaterial(tex(path, tint, b, Math.max(1, Math.max(hx, hz) * tile), Math.max(1, hy * tile)));
        g.setLocalTranslation(x, y, z);
        interior.attachChild(g);
        return g;
    }

    private Geometry cylY(float r1, float r2, float h, int sides, Material m, float x, float y, float z) {
        Geometry g = new Geometry("h", new Cylinder(2, sides, r1, r2, h, true, false));
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        interior.attachChild(g);
        return g;
    }

    private Geometry ball(float r, Material m, float x, float y, float z) {
        Geometry g = new Geometry("h", new Sphere(10, 12, r));
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        interior.attachChild(g);
        return g;
    }

    private boolean has(String id) { return st.furniture.contains(id); }

    private void build() {
        boolean attached = interior.getParent() != null;
        if (attached) {
            interior.removeFromParent();
            for (PointLight l : lights) w3.root.removeLight(l);
        }
        interior = new Node("homeInterior");
        lights.clear();
        int lv = st.homeLevel;
        W = lv == 0 ? 2.2f : lv == 1 ? 2.6f : 3.4f;
        D = lv == 0 ? 2.0f : lv == 1 ? 2.3f : 3.0f;
        H = lv <= 1 ? 2.6f : lv == 2 ? 3.0f : 3.3f;
        final String BARK = "Models/Tree/BarkColor.jpg", BRICK = "Textures/Terrain/BrickWall/BrickWall.jpg", ROCK = "Textures/Terrain/Rocky/RockyTexture.jpg";

        // Boden aus Dielen
        Material plank = tex(BARK, new ColorRGBA(1f, 0.86f, 0.7f, 1), null, 1, 1);
        for (float x = -W; x < W; x += 0.32f) {
            Box b = new Box(0.15f, 0.04f, D);
            b.scaleTextureCoordinates(new Vector2f(0.4f, 3));
            Geometry g = new Geometry("plank", b);
            g.setMaterial(plank);
            g.setLocalTranslation(x + 0.16f, -0.04f, 0);
            interior.attachChild(g);
        }
        // Wände: Holz (Stufe 0–2), unten Ziegel und oben Putz (ab Stufe 3)
        if (lv < 3) {
            ColorRGBA wt = new ColorRGBA(0.95f, 0.8f, 0.64f, 1);
            Material logM = tex(BARK, wt, null, 1, 1);
            for (float y = 0.14f; y < H; y += 0.28f) {
                for (int s = -1; s <= 1; s += 2) {
                    Geometry a = new Geometry("wlog", new Cylinder(2, 10, 0.15f, 2 * W, true));
                    a.setMaterial(logM);
                    a.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
                    a.setLocalTranslation(0, y, s * (D + 0.1f));
                    interior.attachChild(a);
                    Geometry b = new Geometry("wlog", new Cylinder(2, 10, 0.15f, 2 * D, true));
                    b.setMaterial(logM);
                    b.setLocalTranslation(s * (W + 0.1f), y, 0);
                    interior.attachChild(b);
                }
            }
        } else {
            for (int s = -1; s <= 1; s += 2) {
                texBox(BRICK, new ColorRGBA(0.9f, 0.65f, 0.55f, 1), W, 0.6f, 0.05f, 0, 0.6f, s * (D + 0.05f), 1.2f);
                texBox(BRICK, new ColorRGBA(0.9f, 0.65f, 0.55f, 1), 0.05f, 0.6f, D, s * (W + 0.05f), 0.6f, 0, 1.2f);
                box(W, (H - 1.2f) / 2, 0.05f, G3.lit(new ColorRGBA(0.74f, 0.68f, 0.58f, 1)), 0, 1.2f + (H - 1.2f) / 2, s * (D + 0.05f));
                box(0.05f, (H - 1.2f) / 2, D, G3.lit(new ColorRGBA(0.74f, 0.68f, 0.58f, 1)), s * (W + 0.05f), 1.2f + (H - 1.2f) / 2, 0);
            }
            Material beam = G3.lit(new ColorRGBA(0.3f, 0.19f, 0.1f, 1));
            for (float x = -W; x <= W + 0.01f; x += W / 2) box(0.07f, (H - 1.2f) / 2, 0.06f, beam, x, 1.2f + (H - 1.2f) / 2, -D + 0.01f);
        }
        // Decke mit Balken
        box(W + 0.2f, 0.05f, D + 0.2f, tex(BARK, new ColorRGBA(0.6f, 0.45f, 0.32f, 1), null, 1, 1), 0, H + 0.05f, 0);
        Material beamM = G3.lit(new ColorRGBA(0.32f, 0.2f, 0.11f, 1));
        for (float x = -W + 0.5f; x < W; x += 1.1f) box(0.08f, 0.1f, D, beamM, x, H - 0.1f, 0);

        // Fenster mit Tageslicht (hinten links)
        Geometry win = box(0.45f, 0.4f, 0.02f, G3.emissive(new ColorRGBA(0.85f, 0.92f, 1f, 1).mult(0.9f)), -W + 1.05f, 1.75f, -D + 0.01f);
        win.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
        box(0.48f, 0.03f, 0.04f, beamM, -W + 1.05f, 1.75f, -D + 0.03f);
        box(0.03f, 0.43f, 0.04f, beamM, -W + 1.05f, 1.75f, -D + 0.03f);

        Material wood = G3.lit(new ColorRGBA(0.5f, 0.33f, 0.18f, 1));
        Material darkWood = G3.lit(new ColorRGBA(0.32f, 0.2f, 0.1f, 1));

        // Werkbank (immer da) hinten rechts
        float bx = W - 0.75f, bz = -D + 0.45f;
        texBox(BARK, new ColorRGBA(1f, 0.85f, 0.7f, 1), 0.65f, 0.06f, 0.35f, bx, 0.86f, bz, 2);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) box(0.05f, 0.42f, 0.05f, darkWood, bx + sx * 0.55f, 0.42f, bz + sz * 0.27f);
        box(0.6f, 0.03f, 0.3f, wood, bx, 0.25f, bz);
        Geometry saw = box(0.25f, 0.06f, 0.005f, G3.lit(new ColorRGBA(0.75f, 0.76f, 0.8f, 1)), bx - 0.2f, 0.94f, bz + 0.05f);
        saw.setLocalRotation(new Quaternion().fromAngleAxis(0.3f, Vector3f.UNIT_Y));
        box(0.07f, 0.04f, 0.04f, darkWood, bx + 0.3f, 0.95f, bz);
        box(0.02f, 0.02f, 0.12f, G3.lit(new ColorRGBA(0.6f, 0.6f, 0.65f, 1)), bx + 0.3f, 0.95f, bz + 0.12f);
        // kleine Häufchen der Baustoffe auf der Werkbank
        if (st.mats(Baustoff.BRETTER) > 0) for (int i = 0; i < Math.min(4, st.mats(Baustoff.BRETTER)); i++)
            box(0.22f, 0.012f, 0.06f, tex(BARK, new ColorRGBA(1f, 0.85f, 0.65f, 1), null, 1, 1), bx + 0.25f, 0.94f + i * 0.025f, bz - 0.15f);
        if (st.mats(Baustoff.KRISTALLGLAS) > 0) {
            Geometry gl = box(0.08f, 0.1f, 0.01f, G3.litGlow(new ColorRGBA(0.7f, 0.9f, 1f, 1), new ColorRGBA(0.3f, 0.5f, 0.7f, 1)), bx - 0.45f, 1.02f, bz - 0.2f);
            gl.setLocalRotation(new Quaternion().fromAngleAxis(0.4f, Vector3f.UNIT_Y));
        }

        // Bett: Strohbett oder Himmelbett (hinten links)
        float bedX = -W + 1.05f, bedZ = -D + 0.6f;
        if (has("himmelbett")) {
            box(1.0f, 0.22f, 0.55f, darkWood, bedX, 0.32f, bedZ);
            box(0.95f, 0.1f, 0.5f, G3.lit(new ColorRGBA(0.55f, 0.35f, 0.7f, 1)), bedX, 0.6f, bedZ);
            ball(0.3f, G3.lit(new ColorRGBA(0.97f, 0.94f, 0.88f, 1)), bedX - 0.7f, 0.72f, bedZ).setLocalScale(0.6f, 0.35f, 1.2f);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
                box(0.04f, 0.95f, 0.04f, darkWood, bedX + sx * 0.95f, 0.95f, bedZ + sz * 0.5f);
            Material canopy = G3.lit(new ColorRGBA(0.85f, 0.75f, 0.95f, 1));
            canopy.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            box(1.0f, 0.02f, 0.55f, canopy, bedX, 1.9f, bedZ);
            for (int s = -1; s <= 1; s += 2) box(0.01f, 0.35f, 0.5f, canopy, bedX + s * 0.98f, 1.55f, bedZ);
        } else {
            box(0.95f, 0.15f, 0.5f, G3.lit(new ColorRGBA(0.85f, 0.72f, 0.38f, 1)), bedX, 0.15f, bedZ);
            box(0.85f, 0.06f, 0.45f, G3.lit(new ColorRGBA(0.55f, 0.6f, 0.45f, 1)), bedX + 0.05f, 0.33f, bedZ);
            ball(0.25f, G3.lit(new ColorRGBA(0.9f, 0.85f, 0.75f, 1)), bedX - 0.65f, 0.38f, bedZ).setLocalScale(0.6f, 0.35f, 1.2f);
        }
        // Hocker (immer)
        cylY(0.22f, 0.22f, 0.06f, 14, wood, bx - 0.4f, 0.5f, bz + 0.75f);
        for (int i = 0; i < 3; i++) {
            float a = i * FastMath.TWO_PI / 3;
            box(0.025f, 0.24f, 0.025f, darkWood, bx - 0.4f + FastMath.sin(a) * 0.14f, 0.24f, bz + 0.75f + FastMath.cos(a) * 0.14f);
        }

        if (has("teppich")) {
            cylY(1.3f, 1.3f, 0.015f, 32, G3.lit(new ColorRGBA(0.75f, 0.3f, 0.28f, 1)), 0, 0.01f, 0.25f).setLocalScale(1.25f, 0.85f, 1);
            cylY(1.0f, 1.0f, 0.018f, 32, G3.lit(new ColorRGBA(0.95f, 0.78f, 0.35f, 1)), 0, 0.012f, 0.25f).setLocalScale(1.25f, 0.85f, 1);
            cylY(0.6f, 0.6f, 0.02f, 32, G3.lit(new ColorRGBA(0.3f, 0.45f, 0.75f, 1)), 0, 0.014f, 0.25f).setLocalScale(1.25f, 0.85f, 1);
        }
        if (has("regal")) {
            float rz = 0.9f * Math.min(1, D / 3f);
            box(0.2f, 1.0f, 0.55f, wood, -W + 0.22f, 1.0f, rz);
            ColorRGBA[] bc = {new ColorRGBA(0.6f, 0.15f, 0.15f, 1), new ColorRGBA(0.15f, 0.3f, 0.55f, 1), new ColorRGBA(0.2f, 0.45f, 0.25f, 1), new ColorRGBA(0.7f, 0.55f, 0.2f, 1)};
            Random r = new Random(3);
            for (int sh = 0; sh < 3; sh++) {
                box(0.21f, 0.015f, 0.55f, darkWood, -W + 0.24f, 0.45f + sh * 0.6f, rz);
                float z = rz - 0.5f;
                while (z < rz + 0.48f) {
                    float bw = 0.03f + r.nextFloat() * 0.03f, bh = 0.16f + r.nextFloat() * 0.08f;
                    box(0.13f, bh, bw, G3.lit(bc[r.nextInt(bc.length)]), -W + 0.26f, 0.47f + sh * 0.6f + bh, z + bw);
                    z += bw * 2 + 0.008f;
                }
            }
        }
        if (has("schaukelstuhl")) {
            float sx = -0.6f, sz = 0.9f;
            Node chair = new Node("chair");
            Material cw = G3.lit(new ColorRGBA(0.45f, 0.28f, 0.15f, 1));
            Geometry seat = new Geometry("seat", new Box(0.28f, 0.03f, 0.28f));
            seat.setMaterial(cw);
            seat.setLocalTranslation(0, 0.45f, 0);
            chair.attachChild(seat);
            Geometry back = new Geometry("back", new Box(0.28f, 0.35f, 0.03f));
            back.setMaterial(cw);
            back.setLocalTranslation(0, 0.8f, 0.27f);
            back.setLocalRotation(new Quaternion().fromAngleAxis(-0.15f, Vector3f.UNIT_X));
            chair.attachChild(back);
            for (int s = -1; s <= 1; s += 2) {
                Geometry rocker = new Geometry("rocker", new Box(0.025f, 0.03f, 0.42f));
                rocker.setMaterial(cw);
                rocker.setLocalTranslation(s * 0.25f, 0.04f, 0);
                chair.attachChild(rocker);
                Geometry leg = new Geometry("leg", new Box(0.025f, 0.2f, 0.025f));
                leg.setMaterial(cw);
                leg.setLocalTranslation(s * 0.25f, 0.25f, 0.12f);
                chair.attachChild(leg);
            }
            chair.setLocalTranslation(sx, 0, sz);
            chair.setLocalRotation(new Quaternion().fromAngleAxis(2.6f, Vector3f.UNIT_Y));
            chair.setUserData("rock", 1);
            interior.attachChild(chair);
        }
        if (has("pflanzen")) {
            Material leaves = G3.lit(new ColorRGBA(0.85f, 1f, 0.85f, 1));
            leaves.setTexture("DiffuseMap", G3.fernTex());
            leaves.setFloat("AlphaDiscardThreshold", 0.4f);
            leaves.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            float[][] ps = {{-W + 0.4f, D - 0.4f}, {W - 0.3f, -D + 1.25f}, {-0.9f * Math.min(1, W / 3f) + 0.1f, -D + 0.3f}};
            for (float[] p : ps) {
                cylY(0.2f, 0.15f, 0.36f, 12, G3.lit(new ColorRGBA(0.72f, 0.38f, 0.22f, 1)), p[0], 0.18f, p[1]);
                Geometry f = new Geometry("fern", G3.crossQuads(1.0f, 0.9f));
                f.setMaterial(leaves);
                f.setLocalTranslation(p[0], 0.32f, p[1]);
                interior.attachChild(f);
            }
        }
        float fireX = 0.2f, fireZ = -D + 0.3f;
        if (has("kamin")) {
            texBox(BRICK, new ColorRGBA(0.85f, 0.55f, 0.45f, 1), 0.65f, 0.65f, 0.3f, fireX, 0.65f, fireZ, 2);
            box(0.42f, 0.36f, 0.05f, G3.lit(new ColorRGBA(0.04f, 0.03f, 0.03f, 1)), fireX, 0.4f, fireZ + 0.27f);
            texBox(ROCK, new ColorRGBA(0.8f, 0.78f, 0.74f, 1), 0.75f, 0.05f, 0.38f, fireX, 1.33f, fireZ + 0.04f, 2);
            texBox(BRICK, new ColorRGBA(0.85f, 0.55f, 0.45f, 1), 0.35f, (H - 1.38f) / 2, 0.22f, fireX, 1.38f + (H - 1.38f) / 2, fireZ - 0.05f, 2);
            ParticleEmitter fire = G3.particles("homeFire", 40, "Effects/Explosion/flame.png", new ColorRGBA(1f, 0.6f, 0.2f, 1),
                new ColorRGBA(1f, 0.2f, 0.05f, 0), 0.28f, 0.06f, 0.35f, 0.7f);
            fire.setImagesX(2);
            fire.setImagesY(2);
            fire.setSelectRandomImage(true);
            fire.setShape(new EmitterBoxShape(new Vector3f(-0.25f, 0, -0.05f), new Vector3f(0.25f, 0.05f, 0.05f)));
            fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.7f, 0));
            fire.getParticleInfluencer().setVelocityVariation(0.3f);
            fire.setParticlesPerSec(34);
            fire.setLocalTranslation(fireX, 0.1f, fireZ + 0.15f);
            interior.attachChild(fire);
            for (int i = 0; i < 3; i++) {
                Geometry lg = new Geometry("firelog", new Cylinder(2, 8, 0.06f, 0.5f, true));
                lg.setMaterial(tex(BARK, ColorRGBA.White, null, 1, 1));
                lg.setLocalTranslation(fireX - 0.1f + i * 0.1f, 0.08f, fireZ + 0.12f);
                lg.setLocalRotation(new Quaternion().fromAngleAxis(0.3f * (i - 1), Vector3f.UNIT_Y));
                interior.attachChild(lg);
            }
            lights.add(new PointLight(base.add(fireX, 0.8f, fireZ + 0.9f), new ColorRGBA(1f, 0.5f, 0.2f, 1).mult(1.6f), 7));
        }
        if (has("wandteppich")) {
            Material tap = G3.lit(new ColorRGBA(0.35f, 0.2f, 0.5f, 1));
            box(0.02f, 0.45f, 0.6f, tap, -W + 0.03f, 2.0f, -0.4f * Math.min(1, D / 3f) - 0.3f);
            box(0.025f, 0.3f, 0.42f, G3.litGlow(new ColorRGBA(0.9f, 0.75f, 0.35f, 1), new ColorRGBA(0.15f, 0.1f, 0.02f, 1)), -W + 0.04f, 2.0f, -0.4f * Math.min(1, D / 3f) - 0.3f);
            ball(0.12f, G3.litGlow(new ColorRGBA(0.6f, 0.9f, 1f, 1), new ColorRGBA(0.2f, 0.4f, 0.6f, 1)), -W + 0.06f, 2.05f, -0.4f * Math.min(1, D / 3f) - 0.3f).setLocalScale(0.3f, 1.4f, 1);
        }
        if (has("lampe") && !has("kronleuchter")) {
            box(0.01f, 0.25f, 0.01f, darkWood, 0, H - 0.25f, 0.2f);
            ball(0.1f, G3.emissive(new ColorRGBA(0.75f, 0.9f, 1f, 1).mult(0.8f)), 0, H - 0.6f, 0.2f);
            lights.add(new PointLight(base.add(0, H - 0.7f, 0.2f), new ColorRGBA(0.75f, 0.85f, 1f, 1).mult(0.8f), 8));
        }
        if (has("sofa")) {
            Node sofa = new Node("sofa");
            Material fab = G3.lit(new ColorRGBA(0.3f, 0.45f, 0.4f, 1));
            Geometry s1 = new Geometry("s", new Box(0.9f, 0.22f, 0.4f)); s1.setMaterial(fab); s1.setLocalTranslation(0, 0.32f, 0); sofa.attachChild(s1);
            Geometry s2 = new Geometry("s", new Box(0.9f, 0.3f, 0.1f)); s2.setMaterial(fab); s2.setLocalTranslation(0, 0.75f, 0.32f); sofa.attachChild(s2);
            for (int s = -1; s <= 1; s += 2) {
                Geometry arm = new Geometry("s", new Box(0.1f, 0.22f, 0.4f)); arm.setMaterial(fab); arm.setLocalTranslation(s * 0.9f, 0.55f, 0); sofa.attachChild(arm);
                Geometry pil = new Geometry("p", new Sphere(8, 10, 0.2f)); pil.setMaterial(G3.lit(s < 0 ? new ColorRGBA(0.95f, 0.75f, 0.35f, 1) : new ColorRGBA(0.85f, 0.4f, 0.45f, 1)));
                pil.setLocalScale(1, 1, 0.45f); pil.setLocalTranslation(s * 0.55f, 0.72f, 0.18f); pil.setLocalRotation(new Quaternion().fromAngleAxis(0.2f * s, Vector3f.UNIT_Z)); sofa.attachChild(pil);
            }
            sofa.setLocalTranslation(-0.9f, 0, D - 0.9f);
            interior.attachChild(sofa);
        }
        if (has("gemaelde")) {
            box(0.55f, 0.4f, 0.03f, darkWood, W - 0.75f, 2.0f, -D + 0.03f);
            Geometry pic = box(0.48f, 0.33f, 0.01f, G3.lit(new ColorRGBA(1, 1, 1, 1)), W - 0.75f, 2.0f, -D + 0.07f);
            Material pm = G3.lit(ColorRGBA.White);
            pm.setTexture("DiffuseMap", paintingTex());
            pic.setMaterial(pm);
        }
        if (has("trankregal")) {
            float tz = -0.6f * Math.min(1, D / 3f);
            box(0.15f, 0.02f, 0.5f, wood, -W + 0.17f, 0.9f, tz);
            box(0.15f, 0.02f, 0.5f, wood, -W + 0.17f, 1.3f, tz);
            ColorRGBA[] pc = {new ColorRGBA(0.4f, 0.9f, 0.5f, 1), new ColorRGBA(0.9f, 0.4f, 0.8f, 1), new ColorRGBA(0.4f, 0.6f, 1f, 1), new ColorRGBA(1f, 0.8f, 0.3f, 1)};
            for (int sh = 0; sh < 2; sh++) for (int k = 0; k < 5; k++) {
                ColorRGBA c = pc[(k + sh) % pc.length];
                cylY(0.05f, 0.05f, 0.16f, 10, G3.litGlow(c.mult(0.7f), c.mult(0.4f)), -W + 0.17f, 1.0f + sh * 0.4f, tz - 0.4f + k * 0.2f);
            }
        }
        if (has("harfe")) {
            Node harp = new Node("harp");
            Material gold = G3.lit(new ColorRGBA(0.9f, 0.72f, 0.3f, 1));
            gold.setColor("Specular", ColorRGBA.White);
            gold.setFloat("Shininess", 50);
            Geometry fr = new Geometry("f", new Torus(20, 6, 0.04f, 0.55f)); fr.setMaterial(gold); fr.setLocalScale(0.7f, 1.3f, 1); fr.setLocalTranslation(0, 0.95f, 0); harp.attachChild(fr);
            for (int i = -3; i <= 3; i++) {
                Geometry s = new Geometry("str", new Box(0.004f, 0.55f - Math.abs(i) * 0.06f, 0.004f));
                s.setMaterial(G3.litGlow(new ColorRGBA(1f, 0.95f, 0.8f, 1), new ColorRGBA(0.3f, 0.25f, 0.1f, 1)));
                s.setLocalTranslation(i * 0.05f, 0.95f, 0);
                harp.attachChild(s);
            }
            harp.setLocalTranslation(W - 0.6f, 0, 0.4f);
            harp.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
            interior.attachChild(harp);
        }
        if (has("aquarium")) {
            float ax = -W + 0.45f, az = D - 0.75f;
            box(0.3f, 0.35f, 0.02f, darkWood, ax, 0.35f, az);
            Material glass = G3.lit(new ColorRGBA(0.5f, 0.8f, 1f, 0.35f));
            glass.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            Geometry tank = box(0.32f, 0.25f, 0.5f, glass, ax + 0.05f, 0.95f, az - 0.1f);
            tank.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
            box(0.3f, 0.35f, 0.48f, darkWood, ax + 0.05f, 0.35f, az - 0.1f);
            ColorRGBA[] fc = {new ColorRGBA(1f, 0.5f, 0.2f, 1), new ColorRGBA(0.4f, 1f, 0.9f, 1), new ColorRGBA(1f, 0.4f, 0.8f, 1)};
            for (int i = 0; i < 5; i++) {
                Geometry fish = ball(0.04f, G3.emissive(fc[i % 3]), ax + 0.05f, 0.9f + (i % 3) * 0.08f, az - 0.35f + i * 0.12f);
                fish.setLocalScale(0.6f, 0.6f, 1.6f);
                fish.setUserData("fish", i);
            }
            lights.add(new PointLight(base.add(ax + 0.4f, 1f, az), new ColorRGBA(0.3f, 0.8f, 1f, 1).mult(0.8f), 3));
        }
        if (has("teleskop")) {
            Material brass = G3.lit(new ColorRGBA(0.85f, 0.65f, 0.3f, 1));
            brass.setColor("Specular", ColorRGBA.White);
            brass.setFloat("Shininess", 60);
            float tx = 0.9f, tz = D - 0.9f;
            for (int i = 0; i < 3; i++) {
                float a = i * FastMath.TWO_PI / 3;
                Geometry leg = box(0.02f, 0.6f, 0.02f, darkWood, tx + FastMath.sin(a) * 0.2f, 0.58f, tz + FastMath.cos(a) * 0.2f);
                leg.setLocalRotation(new Quaternion().fromAngles(FastMath.cos(a) * 0.25f, 0, -FastMath.sin(a) * 0.25f));
            }
            Geometry tube = new Geometry("tube", new Cylinder(2, 14, 0.07f, 0.11f, 1.0f, true, false));
            tube.setMaterial(brass);
            tube.setLocalTranslation(tx, 1.3f, tz);
            tube.setLocalRotation(new Quaternion().fromAngles(-0.7f, 0.6f, 0));
            interior.attachChild(tube);
        }
        if (has("kronleuchter")) {
            Material gold = G3.lit(new ColorRGBA(0.9f, 0.75f, 0.35f, 1));
            Geometry ring = new Geometry("ring", new Torus(24, 6, 0.03f, 0.55f));
            ring.setMaterial(gold);
            ring.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
            ring.setLocalTranslation(0, H - 0.7f, 0.2f);
            interior.attachChild(ring);
            ColorRGBA[] lc = {new ColorRGBA(0.75f, 0.85f, 1f, 1), new ColorRGBA(1f, 0.7f, 0.9f, 1), new ColorRGBA(1f, 0.9f, 0.6f, 1)};
            for (int i = 0; i < 9; i++) {
                float a = i * FastMath.TWO_PI / 9;
                ball(0.06f, G3.emissive(lc[i % 3]), FastMath.sin(a) * 0.55f, H - 0.62f, 0.2f + FastMath.cos(a) * 0.55f);
            }
            for (int i = 0; i < 3; i++) box(0.006f, 0.35f, 0.006f, gold, FastMath.sin(i * 2.1f) * 0.5f, H - 0.35f, 0.2f + FastMath.cos(i * 2.1f) * 0.5f);
            lights.add(new PointLight(base.add(0, H - 0.8f, 0.2f), new ColorRGBA(1f, 0.92f, 0.85f, 1).mult(1.1f), 11));
        }
        // Geheime Schmuckstücke
        if (has("mondlaterne")) {
            box(0.12f, 0.16f, 0.12f, G3.litGlow(new ColorRGBA(0.75f, 0.85f, 1f, 1), new ColorRGBA(0.45f, 0.6f, 1f, 1)), -W + 2.35f, 0.16f, -D + 0.35f);
            lights.add(new PointLight(base.add(-W + 2.35f, 0.5f, -D + 0.6f), new ColorRGBA(0.5f, 0.6f, 1f, 1).mult(0.9f), 3.5f));
        }
        if (has("windspiel")) {
            for (int i = 0; i < 5; i++) {
                Geometry f = box(0.02f, 0.18f, 0.005f, G3.litGlow(new ColorRGBA(1f, 0.75f, 0.4f, 1), new ColorRGBA(0.3f, 0.15f, 0.05f, 1)), -W + 0.6f + i * 0.08f, H - 0.55f - (i % 2) * 0.1f, D - 0.6f);
                f.setUserData("chime", i);
            }
            box(0.25f, 0.01f, 0.01f, darkWood, -W + 0.76f, H - 0.35f, D - 0.6f);
        }
        if (has("seerosenschale")) {
            cylY(0.32f, 0.22f, 0.12f, 18, G3.lit(new ColorRGBA(0.8f, 0.45f, 0.35f, 1)), 0.7f, 0.06f, 0.7f);
            cylY(0.28f, 0.28f, 0.01f, 18, G3.lit(new ColorRGBA(0.2f, 0.45f, 0.55f, 1)), 0.7f, 0.12f, 0.7f);
            ball(0.07f, G3.litGlow(new ColorRGBA(1f, 0.7f, 0.85f, 1), new ColorRGBA(0.3f, 0.1f, 0.2f, 1)), 0.75f, 0.14f, 0.7f).setLocalScale(1, 0.5f, 1);
        }
        // Stufe 4/5: Treppe zum Turmzimmer, Baumstamm
        if (lv >= 4) {
            for (int i = 0; i < 7; i++) box(0.4f, 0.05f, 0.18f, wood, W - 0.45f, 0.3f + i * 0.4f, D - 0.4f - i * 0.36f).setLocalScale(1, 1, 1);
        }
        if (lv >= 5) {
            Cylinder trunk = new Cylinder(2, 16, 0.7f, H + 0.2f, true);
            Geometry tr = new Geometry("trunk", trunk);
            tr.setMaterial(tex(BARK, new ColorRGBA(0.85f, 0.75f, 0.65f, 1), trunk, 3, 2));
            tr.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
            tr.setLocalTranslation(-W + 0.9f, H / 2, 0.2f);
            interior.attachChild(tr);
            for (int i = 0; i < 6; i++) {
                float a = i * 1.1f;
                ball(0.05f, G3.emissive(new ColorRGBA(0.6f, 1f, 0.7f, 1)), -W + 0.9f + FastMath.sin(a) * 0.72f, 0.6f + i * 0.35f, 0.2f + FastMath.cos(a) * 0.72f);
            }
        }

        // Grundlicht
        lights.add(new PointLight(base.add(0, H - 0.4f, 0.4f), new ColorRGBA(1f, 0.8f, 0.58f, 1).mult(0.85f), 9));
        lights.add(new PointLight(base.add(-W + 1.05f, 1.6f, -D + 1.2f), new ColorRGBA(0.75f, 0.85f, 1f, 1).mult(0.4f), 5));
        interior.setLocalTranslation(base);

        guest = new Figure(st.outfit.apply(st.hero.look), st.outfit.accessory);
        guest.node.setLocalTranslation(0.5f, 0, 0.6f);
        guest.node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI + 0.6f, Vector3f.UNIT_Y));
        interior.attachChild(guest.node);
        if (world.p2 != null) {
            guest2 = new Figure(st.hero2.look);
            guest2.node.setLocalTranslation(1.3f, 0, 0.1f);
            guest2.node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI + 0.9f, Vector3f.UNIT_Y));
            interior.attachChild(guest2.node);
        } else guest2 = null;
        if (attached) {
            w3.root.attachChild(interior);
            for (PointLight l : lights) w3.root.addLight(l);
        }
    }

    /** Kleines gemaltes Waldbild für das Gemälde. */
    private static Texture paintingTex() {
        int w = 128, h = 96;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(250, 170, 110), 0, h * 0.6f, new Color(120, 90, 160)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 230, 150));
        g.fillOval(78, 28, 22, 22);
        Random r = new Random(5);
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(w), th = 30 + r.nextInt(30);
            g.setColor(new Color(30 + r.nextInt(30), 60 + r.nextInt(40), 50 + r.nextInt(30)));
            g.fillPolygon(new int[]{x - 9, x, x + 9}, new int[]{h - 12, h - 12 - th, h - 12}, 3);
        }
        g.setColor(new Color(40, 60, 45));
        g.fillRect(0, h - 14, w, 14);
        g.dispose();
        return new com.jme3.texture.Texture2D(new com.jme3.texture.plugins.AWTLoader().load(img, true));
    }

    // ================================================================ Ablauf

    @Override
    public void onEnter() {
        if (interior.getParent() == null) {
            w3.root.attachChild(interior);
            for (PointLight l : lights) w3.root.addLight(l);
            world.fig.node.setCullHint(Spatial.CullHint.Always);
            if (world.p2 != null) world.p2.fig.node.setCullHint(Spatial.CullHint.Always);
            Home.Level lv = Home.LEVELS.get(st.homeLevel);
            say(new String[]{"*Zuhause: " + lv.title() + ". " + lv.text() + "  Gemütlichkeit: " + Home.cozy(st) + "."}, this::openMenu);
        }
    }

    private void say(String[] lines, Runnable after) {
        dialog = new DialogBox(st.displayName(), st.hero.look, lines, st.hero);
        dialog.onFinish = after;
        mode = Mode.TALK;
    }

    /** Für die Bildschirmfoto-Tour: direkt ins Menü bzw. an die Werkbank. */
    void debugMode(String m) {
        dialog = null;
        openMenu();
        if (m.equals("bench")) actions.get(0).run();
        if (m.equals("build")) mode = Mode.BUILD;
        if (m.equals("shop")) mode = Mode.SHOP;
    }

    private void option(String label, Runnable r) {
        labels.add(label);
        actions.add(r);
    }

    private void openMenu() {
        labels.clear();
        actions.clear();
        option("Werkbank: Baustoffe kombinieren", () -> { mode = Mode.BENCH; bench.clear(); benchMsg = "Leg Zutaten auf die Werkbank und kombiniere sie!"; benchMsgColor = Art.PARCH; });
        if (st.homeLevel + 1 < Home.LEVELS.size()) option("Haus ausbauen: " + Home.LEVELS.get(st.homeLevel + 1).title(), () -> mode = Mode.BUILD);
        else option("Dein Haus ist fertig – eine echte Baumvilla!", () -> say(new String[]{"*Du lehnst dich zurück. Größer geht es nicht mehr – aber schöner immer."}, this::openMenu));
        option("Einrichten: Möbel kaufen", () -> mode = Mode.SHOP);
        option("Am Feuer ausruhen", this::rest);
        option("Ins Tagebuch schreiben (Spiel speichern)", () -> {
            world.pos.set(Home.door(st.homeLevel));
            world.saveNow(false);
            savedT = 4;
            say(new String[]{"*Du schreibst ein paar Zeilen in dein Tagebuch. Dein Spiel ist gespeichert."}, this::openMenu);
        });
        option("Hinausgehen", this::leave);
        mode = Mode.MENU;
    }

    private void rest() {
        world.mana = (float) st.hero.manaMax();
        if (world.p2 != null) world.p2.mana = world.p2.maxMana();
        int day = (int) (st.playTime / DayNight.DAY_SECONDS);
        String key = "homeRest:" + day;
        List<String> lines = new ArrayList<>();
        lines.add("*Du machst es dir gemütlich. Dein Mana ist wieder voll.");
        if (!st.flags.contains(key)) {
            st.flags.add(key);
            int xp = 5 + Home.cozy(st);
            lines.add("*Zuhause ist es am schönsten: +" + xp + " Erfahrung (Gemütlichkeit " + Home.cozy(st) + ").");
            lines.addAll(st.addXp(xp));
        } else {
            lines.add("*Heute hast du dich schon ausgeruht. Morgen gibt es wieder einen Gemütlichkeits-Bonus.");
        }
        say(lines.toArray(new String[0]), this::openMenu);
    }

    private void leave() {
        interior.removeFromParent();
        for (PointLight l : lights) w3.root.removeLight(l);
        world.fig.node.setCullHint(Spatial.CullHint.Inherit);
        if (world.p2 != null) world.p2.fig.node.setCullHint(Spatial.CullHint.Inherit);
        in.releaseAll();
        game.setScene(world);
    }

    // ---------------------------------------------------------------- Werkbank

    private RoundRectangle2D tile(int i) { return new RoundRectangle2D.Double(60 + (i % 2) * 250, 150 + (i / 2) * 58, 236, 48, 18, 18); }

    private RoundRectangle2D benchBtn(int i) {
        if (i == 4) return new RoundRectangle2D.Double(1100, 590, 130, 44, 22, 22);
        double[] x = {56, 196, 316, 436}, w = {132, 112, 112, 112};
        return new RoundRectangle2D.Double(x[i], 596, w[i], 44, 22, 22);
    }

    private int onBench(String k) { return bench.getOrDefault(k, 0); }

    private void combine() {
        if (bench.isEmpty()) { benchMsg = "Die Werkbank ist leer."; benchMsgColor = Art.PARCH; return; }
        Home.Recipe r = Home.match(bench);
        if (r == null) {
            benchMsg = "Nichts passiert … diese Mischung ergibt nichts. Die Zutaten bleiben dir.";
            benchMsgColor = Art.DANGER;
            bench.clear();
            return;
        }
        bench.forEach((k, v) -> Home.take(st, k, v));
        bench.clear();
        produce(r);
        lastRecipe = r;
    }

    private void produce(Home.Recipe r) {
        boolean fresh = st.recipes.add(r.id());
        if (r.out().startsWith("F:")) {
            String id = r.out().substring(2);
            st.furniture.add(id);
            benchMsg = "✦ Geheimrezept! Du hast " + Home.furnitureTitle(id) + " gebaut – es steht jetzt in deinem Haus.";
            build();
        } else {
            Home.give(st, r.out(), r.amount());
            benchMsg = (fresh ? "✦ Neues Rezept entdeckt! " : "") + "+" + r.amount() + " " + Home.title(r.out());
            if (r.out().equals("B:BRETTER") || r.out().equals("B:KRISTALLGLAS")) build();
        }
        benchMsgColor = Art.GOOD;
        flashT = 1;
        if (game.app.music != null) game.app.music.sfx("chime");
        if (fresh) st.addXp(4);
    }

    private void repeat(int times) {
        if (lastRecipe == null) { benchMsg = "Kombiniere zuerst etwas, dann kannst du es wiederholen."; benchMsgColor = Art.PARCH; return; }
        int made = 0;
        for (int i = 0; i < times; i++) {
            boolean ok = true;
            for (Map.Entry<String, Integer> e : lastRecipe.in().entrySet()) if (Home.have(st, e.getKey()) < e.getValue()) ok = false;
            if (!ok || lastRecipe.out().startsWith("F:")) break;
            lastRecipe.in().forEach((k, v) -> Home.take(st, k, v));
            Home.give(st, lastRecipe.out(), lastRecipe.amount());
            made++;
        }
        if (made == 0) { benchMsg = "Dafür fehlen dir Zutaten."; benchMsgColor = Art.DANGER; return; }
        benchMsg = made + "× kombiniert: +" + made * lastRecipe.amount() + " " + Home.title(lastRecipe.out());
        benchMsgColor = Art.GOOD;
        flashT = 1;
        if (game.app.music != null) game.app.music.sfx("chime");
        build();
    }

    private void updateBench() {
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { openMenu(); return; }
        if (in.wasPressed(KeyEvent.VK_ENTER)) combine();
        if (in.wasPressed(KeyEvent.VK_BACK_SPACE)) { bench.clear(); benchMsg = "Werkbank geleert."; benchMsgColor = Art.PARCH; }
        if (in.wasPressed(KeyEvent.VK_R)) repeat(1);
        if (in.wasPressed(KeyEvent.VK_F)) repeat(5);
        if (!in.mouseClicked) return;
        for (int i = 0; i < BENCH_KEYS.length; i++) {
            if (!tile(i).contains(in.mouseX, in.mouseY)) continue;
            String k = BENCH_KEYS[i];
            if (Home.have(st, k) > onBench(k)) bench.merge(k, 1, Integer::sum);
            else { benchMsg = "Du hast nicht mehr " + Home.title(k) + "."; benchMsgColor = Art.DANGER; }
        }
        Runnable[] btn = {this::combine, () -> { bench.clear(); benchMsg = "Werkbank geleert."; benchMsgColor = Art.PARCH; }, () -> repeat(1), () -> repeat(5), this::openMenu};
        for (int i = 0; i < btn.length; i++) if (benchBtn(i).contains(in.mouseX, in.mouseY)) { btn[i].run(); return; }
    }

    private void drawBench(Graphics2D g) {
        Art.panel(g, 36, 96, 540, 580);
        g.setFont(Art.H2);
        Art.shadowText(g, "Deine Vorräte", 60, 134, Art.GOLD);
        g.setFont(Art.SMALL);
        Art.shadowText(g, "Klick legt eine Zutat auf die Werkbank", 292, 131, new Color(200, 195, 220));
        for (int i = 0; i < BENCH_KEYS.length; i++) {
            String k = BENCH_KEYS[i];
            RoundRectangle2D r = tile(i);
            int left = Home.have(st, k) - onBench(k);
            boolean hover = r.contains(in.mouseX, in.mouseY);
            g.setColor(hover ? new Color(80, 60, 110, 230) : new Color(24, 18, 38, 210));
            g.fill(r);
            g.setColor(Art.alpha(Home.color(k), left > 0 ? 200 : 70));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(r);
            Art.dot(g, r.getX() + 22, r.getCenterY(), 9, Home.color(k), left > 0 ? 255 : 90);
            g.setFont(Art.UI);
            Art.shadowText(g, Home.title(k), r.getX() + 40, r.getCenterY() + 6, left > 0 ? Art.PARCH : new Color(130, 125, 145));
            Art.shadowText(g, "×" + left, r.getX() + 190, r.getCenterY() + 6, left > 0 ? Color.WHITE : new Color(130, 125, 145));
        }
        // Werkbank-Fläche
        double wy = 488;
        g.setColor(new Color(80, 52, 28, 230));
        g.fill(new RoundRectangle2D.Double(60, wy, 486, 92, 20, 20));
        g.setColor(Art.alpha(Art.GOLD, flashT > 0 ? (int) (120 + 135 * flashT) : 120));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new RoundRectangle2D.Double(60, wy, 486, 92, 20, 20));
        g.setFont(Art.SMALL);
        Art.shadowText(g, "WERKBANK", 76, wy + 20, Art.alpha(Art.GOLD, 200));
        double x = 80;
        if (bench.isEmpty()) {
            g.setFont(Art.ITALIC);
            Art.shadowText(g, "… noch leer", 80, wy + 58, new Color(200, 180, 150));
        }
        for (Map.Entry<String, Integer> e : bench.entrySet()) {
            Art.glow(g, x + 22, wy + 52, 30, Home.color(e.getKey()), 70);
            Art.dot(g, x + 22, wy + 52, 14, Home.color(e.getKey()), 255);
            g.setFont(Art.UI);
            Art.shadowCentered(g, e.getValue() + "", x + 22, wy + 58, Color.BLACK);
            g.setFont(Art.SMALL);
            Art.shadowCentered(g, Home.title(e.getKey()), x + 22, wy + 84, Art.PARCH);
            x += 96;
            if (x < 520 && bench.size() > 1) {
                g.setFont(Art.UI);
                Art.shadowCentered(g, "+", x - 26, wy + 58, Art.GOLD);
            }
        }
        String[] bl = {"Kombinieren", "Leeren", "Nochmal R", "5× F", "Zurück Esc"};
        for (int i = 0; i < bl.length; i++) {
            RoundRectangle2D b = benchBtn(i);
            boolean hover = b.contains(in.mouseX, in.mouseY);
            g.setColor(i == 0 ? (hover ? new Color(140, 100, 40) : new Color(110, 76, 30)) : hover ? new Color(90, 64, 130, 230) : new Color(40, 30, 64, 220));
            g.fill(b);
            g.setColor(Art.alpha(Art.GOLD, 180));
            g.draw(b);
            g.setFont(Art.UI);
            Art.shadowCentered(g, bl[i], b.getCenterX(), b.getCenterY() + 6, Color.WHITE);
        }
        g.setFont(Art.UI);
        Art.shadowText(g, benchMsg, 60, 668, benchMsgColor);
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 185, 210));
        g.drawString("Enter kombiniert · Rücktaste leert", 900, 558);

        // Rezeptbuch
        Art.panel(g, 870, 96, 380, 480);
        g.setFont(Art.H2);
        Art.shadowText(g, "Rezeptbuch", 894, 134, Art.GOLD);
        double y = 172;
        for (Home.Recipe r : Home.RECIPES) {
            boolean known = st.recipes.contains(r.id());
            boolean secret = r.out().startsWith("F:");
            String out = secret ? (known ? Home.furnitureTitle(r.out().substring(2)) : "??? (Geheimrezept)") : r.amount() + " " + Home.title(r.out());
            g.setFont(Art.UI);
            Art.shadowText(g, "→ " + out, 894, y, known ? Art.GOOD : secret ? new Color(220, 170, 255) : Art.PARCH);
            g.setFont(Art.SMALL);
            g.setColor(new Color(195, 190, 215));
            StringBuilder sb = new StringBuilder();
            if (known) r.in().forEach((k, v) -> sb.append(sb.length() > 0 ? " + " : "").append(v).append(" ").append(Home.title(k)));
            Art.drawWrapped(g, known ? sb.toString() : "Tipp: " + r.hint(), 900, y + 20, 330, 16);
            y += 66;
        }
    }

    // ---------------------------------------------------------------- Ausbau

    private RoundRectangle2D buildBtn() { return new RoundRectangle2D.Double(500, 560, 280, 54, 26, 26); }

    private void updateBuild() {
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { openMenu(); return; }
        if (st.homeLevel + 1 >= Home.LEVELS.size()) { openMenu(); return; }
        Home.Level nx = Home.LEVELS.get(st.homeLevel + 1);
        boolean ok = Home.canAfford(st, nx.needs(), nx.coins());
        if (ok && (in.wasPressed(KeyEvent.VK_ENTER) || (in.mouseClicked && buildBtn().contains(in.mouseX, in.mouseY)))) {
            Home.pay(st, nx.needs(), nx.coins());
            st.homeLevel++;
            w3.setHomeLevel(st.homeLevel);
            List<String> lines = new ArrayList<>();
            lines.add("*Es hämmert, sägt und klopft – Feen tragen Bretter, ein Troll stemmt Steine. Stunden später …");
            lines.add("*Dein Zuhause ist jetzt: " + nx.title() + "! " + nx.text());
            lines.addAll(st.addXp(30 + st.homeLevel * 10));
            build();
            world.pos.set(Home.door(st.homeLevel));
            world.saveNow(false);
            savedT = 4;
            flashT = 1.5f;
            if (game.app.music != null) game.app.music.sfx("win");
            say(lines.toArray(new String[0]), this::openMenu);
        } else if (in.mouseClicked && buildBtn().contains(in.mouseX, in.mouseY)) {
            flashT = 0.6f;
        }
    }

    private void drawBuild(Graphics2D g) {
        Home.Level nx = Home.LEVELS.get(Math.min(st.homeLevel + 1, Home.LEVELS.size() - 1));
        Art.panel(g, 340, 110, 600, 530);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 30));
        Art.shadowCentered(g, "Ausbau: " + nx.title(), 640, 160, Art.GOLD);
        Art.ornament(g, 640, 180, 360, t);
        g.setFont(Art.ITALIC);
        g.setColor(Art.PARCH);
        Art.drawWrapped(g, nx.text(), 380, 214, 520, 22);
        // Stufenleiste
        for (int i = 0; i < Home.LEVELS.size(); i++) {
            double cx = 440 + i * 80;
            boolean done = i <= st.homeLevel, next = i == st.homeLevel + 1;
            Art.dot(g, cx, 270, next ? 11 : 8, done ? Art.GOLD : next ? new Color(200, 230, 255) : new Color(90, 85, 110), 255);
            if (i > 0) {
                g.setColor(done ? Art.GOLD : new Color(90, 85, 110));
                g.drawLine((int) cx - 70, 270, (int) cx - 12, 270);
            }
        }
        g.setFont(Art.UI);
        Art.shadowText(g, "Du brauchst:", 380, 320, Art.GOLD);
        double y = 354;
        for (Map.Entry<String, Integer> e : nx.needs().entrySet()) {
            int have = Home.have(st, e.getKey());
            boolean ok = have >= e.getValue();
            Art.dot(g, 396, y - 6, 7, Home.color(e.getKey()), 255);
            Art.shadowText(g, e.getValue() + "  " + Home.title(e.getKey()), 414, y, ok ? Art.PARCH : new Color(230, 160, 160));
            Art.shadowText(g, (ok ? "✓ " : "") + "du hast " + have, 720, y, ok ? Art.GOOD : Art.DANGER);
            y += 32;
        }
        boolean okC = st.coins >= nx.coins();
        Art.dot(g, 396, y - 6, 7, Art.GOLD, 255);
        Art.shadowText(g, nx.coins() + "  Glimmertaler", 414, y, okC ? Art.PARCH : new Color(230, 160, 160));
        Art.shadowText(g, (okC ? "✓ " : "") + "du hast " + st.coins, 720, y, okC ? Art.GOOD : Art.DANGER);
        boolean ok = Home.canAfford(st, nx.needs(), nx.coins());
        RoundRectangle2D b = buildBtn();
        g.setColor(ok ? (b.contains(in.mouseX, in.mouseY) ? new Color(150, 110, 40) : new Color(120, 86, 30)) : new Color(50, 44, 60, 220));
        g.fill(b);
        g.setColor(Art.alpha(flashT > 0 && !ok ? Art.DANGER : Art.GOLD, 200));
        g.draw(b);
        Art.shadowCentered(g, ok ? "Jetzt ausbauen!  ⏎" : "Noch nicht genug", 640, 594, ok ? Color.WHITE : new Color(170, 165, 185));
        g.setFont(Art.SMALL);
        g.setColor(new Color(195, 190, 215));
        Art.centered(g, "Bretter, Ziegel und Kristallglas entstehen an der Werkbank · Esc zurück", 640, 630);
    }

    // ---------------------------------------------------------------- Möbel

    private RoundRectangle2D shopRow(int i) { return new RoundRectangle2D.Double(300, 150 + i * 34, 680, 31, 12, 12); }

    private void updateShop() {
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { openMenu(); return; }
        if (!in.mouseClicked) return;
        for (int i = 0; i < Home.FURNITURE.size(); i++) {
            if (!shopRow(i).contains(in.mouseX, in.mouseY)) continue;
            Home.Furniture f = Home.FURNITURE.get(i);
            if (st.furniture.contains(f.id())) return;
            if (st.homeLevel < f.minLevel()) { flashT = 0.6f; return; }
            if (!Home.canAfford(st, f.needs(), f.coins())) { flashT = 0.6f; return; }
            Home.pay(st, f.needs(), f.coins());
            st.furniture.add(f.id());
            build();
            if (game.app.music != null) game.app.music.sfx("chime");
            say(new String[]{"*" + f.title() + " – wunderschön! Es steht jetzt in deinem Haus. Gemütlichkeit: " + Home.cozy(st) + "."}, () -> mode = Mode.SHOP);
            return;
        }
    }

    private void drawShop(Graphics2D g) {
        Art.panel(g, 270, 80, 740, 640);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
        Art.shadowCentered(g, "Einrichten", 640, 122, Art.GOLD);
        for (int i = 0; i < Home.FURNITURE.size(); i++) {
            Home.Furniture f = Home.FURNITURE.get(i);
            RoundRectangle2D r = shopRow(i);
            boolean own = st.furniture.contains(f.id()), locked = st.homeLevel < f.minLevel();
            boolean ok = !own && !locked && Home.canAfford(st, f.needs(), f.coins());
            boolean hover = r.contains(in.mouseX, in.mouseY);
            g.setColor(own ? new Color(30, 60, 40, 200) : hover && ok ? new Color(90, 64, 130, 220) : new Color(24, 18, 38, 200));
            g.fill(r);
            g.setFont(Art.UI);
            Color c = own ? Art.GOOD : locked ? new Color(120, 115, 135) : ok ? Color.WHITE : Art.PARCH;
            Art.shadowText(g, (own ? "✓ " : "") + f.title(), r.getX() + 14, r.getY() + 22, c);
            g.setFont(Art.SMALL);
            String cost;
            if (own) cost = "steht schon bei dir";
            else if (locked) cost = "ab Ausbaustufe " + f.minLevel() + " (" + Home.LEVELS.get(f.minLevel()).title() + ")";
            else {
                StringBuilder sb = new StringBuilder(f.coins() + " Taler");
                f.needs().forEach((k, v) -> sb.append(" + ").append(v).append(" ").append(Home.title(k)));
                cost = sb.toString();
            }
            g.setColor(own ? new Color(160, 220, 170) : locked ? new Color(120, 115, 135) : ok ? new Color(255, 225, 150) : new Color(230, 150, 150));
            g.drawString(cost, (float) r.getX() + 340, (float) r.getY() + 21);
        }
        g.setFont(Art.UI);
        Art.shadowText(g, st.coins + " Glimmertaler  ·  Gemütlichkeit " + Home.cozy(st), 300, 700, Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(new Color(195, 190, 215));
        g.drawString("Klick kauft · Esc zurück", 840, 700);
    }

    // ================================================================ Update / Darstellung

    @Override
    public void update(double dtd) {
        float dt = (float) dtd;
        t += dt;
        st.playTime += dt;
        savedT = Math.max(0, savedT - dt);
        flashT = Math.max(0, flashT - dt);
        guest.update(dt, 0, false);
        if (guest2 != null) guest2.update(dt, 0, false);
        animate();
        switch (mode) {
            case TALK -> {
                if (dialog != null) {
                    dialog.update(dt, in);
                    if (dialog.done) {
                        Runnable r = dialog.onFinish;
                        dialog = null;
                        if (r != null) r.run();
                        else openMenu();
                    }
                }
            }
            case BENCH -> updateBench();
            case BUILD -> updateBuild();
            case SHOP -> updateShop();
            case MENU -> {
                lookYaw = FastMath.clamp(lookYaw + (in.isDown(KeyEvent.VK_LEFT, KeyEvent.VK_A) ? dt : 0) - (in.isDown(KeyEvent.VK_RIGHT, KeyEvent.VK_D) ? dt : 0), -0.7f, 0.7f);
                int pick = -1;
                for (int i = 0; i < labels.size() && i < 9; i++) if (in.wasPressed(KeyEvent.VK_1 + i)) pick = i;
                if (in.mouseClicked) for (int i = 0; i < labels.size(); i++) if (menuBox(i).contains(in.mouseX, in.mouseY)) pick = i;
                if (in.wasPressed(KeyEvent.VK_ESCAPE)) pick = labels.size() - 1;
                if (pick >= 0) actions.get(pick).run();
            }
        }
    }

    /** Kleine Bewegungen: Schaukelstuhl, Fische, Windspiel. */
    private void animate() {
        for (Spatial s : interior.getChildren()) {
            if (s.getUserData("rock") != null) s.setLocalRotation(new Quaternion().fromAngles(FastMath.sin(t * 1.4f) * 0.08f, 2.6f, 0));
            Integer fish = s.getUserData("fish");
            if (fish != null) {
                Vector3f p = s.getLocalTranslation();
                s.setLocalTranslation(p.x + FastMath.sin(t * 1.5f + fish) * 0.002f, p.y + FastMath.cos(t * 2 + fish) * 0.001f, p.z);
            }
            Integer chime = s.getUserData("chime");
            if (chime != null) s.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.sin(t * 2.2f + chime) * 0.25f, Vector3f.UNIT_Z));
        }
    }

    private RoundRectangle2D menuBox(int i) { return new RoundRectangle2D.Double(760, 280 + i * 52, 470, 44, 20, 20); }

    @Override
    public Vector3f focus() { return Home.door(st.homeLevel); }

    @Override
    public boolean indoors() { return true; }

    @Override
    public void directCamera(Camera cam, float tpf) {
        Vector3f eye = base.add(W - 0.45f, Math.min(H - 0.35f, 1.95f), D - 0.35f);
        Vector3f look = base.add(-W * 0.35f, 0.95f, -D * 0.45f);
        Vector3f dir = new Quaternion().fromAngleAxis(lookYaw, Vector3f.UNIT_Y).mult(look.subtract(eye));
        cam.setLocation(eye);
        cam.lookAt(eye.add(dir), Vector3f.UNIT_Y);
    }

    @Override
    public void render(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(640f, 360f, 900f, new float[]{0.5f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(10, 4, 0, 150)}));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        if (mode != Mode.BENCH && mode != Mode.SHOP) {
            g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
            Art.shadowCentered(g, "Zuhause · " + Home.LEVELS.get(st.homeLevel).title(), 640, 46, Art.GOLD);
            g.setFont(Art.ITALIC);
            Art.shadowCentered(g, "Gemütlichkeit " + Home.cozy(st) + "  ·  " + st.coins + " Glimmertaler", 640, 76, Art.PARCH);
            Art.ornament(g, 640, 92, 360, t);
        }
        if (savedT > 0) {
            g.setFont(Art.UI);
            Art.shadowCentered(g, "✓ Spiel gespeichert", 640, 120, Art.alpha(Art.GOOD, (int) (Math.min(1, savedT) * 255)));
        }
        switch (mode) {
            case TALK -> { if (dialog != null) dialog.render(g, t); }
            case BENCH -> drawBench(g);
            case BUILD -> drawBuild(g);
            case SHOP -> drawShop(g);
            case MENU -> {
                Art.panel(g, 740, 216, 510, 80 + labels.size() * 52);
                g.setFont(Art.H2);
                Art.shadowText(g, "Was möchtest du tun?", 770, 258, Art.GOLD);
                for (int i = 0; i < labels.size(); i++) {
                    RoundRectangle2D b = menuBox(i);
                    boolean hover = b.contains(in.mouseX, in.mouseY);
                    g.setColor(hover ? new Color(90, 64, 130, 220) : new Color(30, 22, 48, 200));
                    g.fill(b);
                    g.setColor(Art.alpha(Art.GOLD, hover ? 220 : 110));
                    g.draw(b);
                    g.setFont(Art.UI);
                    Art.shadowText(g, (i + 1) + "   " + labels.get(i), b.getX() + 20, b.getY() + 28, hover ? Color.WHITE : Art.PARCH);
                }
                g.setFont(Art.SMALL);
                g.setColor(new Color(200, 195, 215));
                g.drawString("Klick oder Zahl wählen · A/D umsehen · Esc gehen", 770, (float) (280 + labels.size() * 52 + 10));
            }
        }
    }
}
