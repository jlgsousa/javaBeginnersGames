package daemmerwald;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.ArmatureMask;
import com.jme3.anim.SkinningControl;
import com.jme3.bounding.BoundingBox;
import com.jme3.effect.ParticleEmitter;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;
import com.jme3.texture.Texture;

/**
 * Eine animierte 3D-Figur (Spielfigur oder NPC), aufgebaut aus einem Look.
 * Der Knoten {@link #node} steht mit den Füßen im Ursprung und blickt nach +Z.
 */
public class Figure {
    public enum Anim { IDLE, RUN, CAST, JUMP }

    private static Spatial sinbadProto, ninjaProto;

    public final Node node = new Node("figure");
    public final Look look;
    public final float height;
    private final Node model = new Node("model");
    private AnimComposer ac;
    private Anim anim;
    private boolean troll;
    /** Realistische Mixamo-Figur (Idle/Walk/Run). */
    private boolean mixamo;
    private float lastSpeed, flyH, leanCur;
    private Node[] wings;
    private float t, castT, bob;
    public boolean hover;

    public boolean flying;
    private Node bigWings;

    public Figure(Look look) { this(look, Outfit.Accessory.NONE); }

    public Figure(Look look, Outfit.Accessory acc) {
        this.look = look;
        this.troll = look.kind == Look.TROLL;
        node.attachChild(model);
        switch (look.kind) {
            case Look.TROLL -> {
                height = look.female ? 2.4f : 2.7f;
                Spatial m = Mixamo.character(true);
                if (look.female && m != null) buildMixamo(m, new ColorRGBA(0.72f, 0.92f, 0.7f, 1));
                else buildTroll();
            }
            case Look.FAIRY -> {
                height = look.female ? 1.62f : 1.75f;
                Spatial m = Mixamo.character(look.female);
                if (m != null) {
                    buildMixamo(m, null);
                    buildFairyWings();
                } else buildFairy();
            }
            case Look.SHROOM -> { height = 3.4f; buildShroom(); }
            case Look.MOUSE -> { height = 1.35f; buildMouse(); }
            default -> { height = 2.1f; buildWraith(); }
        }
        accessory(acc);
        node.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        play(Anim.IDLE);
    }

    private void buildMouse() {
        Material fur = G3.lit(new ColorRGBA(0.55f, 0.5f, 0.47f, 1));
        Material pink = G3.lit(new ColorRGBA(0.95f, 0.65f, 0.68f, 1));
        Material black = G3.lit(new ColorRGBA(0.02f, 0.02f, 0.02f, 1));
        black.setColor("Specular", ColorRGBA.White);
        black.setFloat("Shininess", 60);
        Geometry body = G3.geom("mbody", new Sphere(16, 18, 0.36f), fur);
        body.setLocalScale(1, 1.3f, 0.95f);
        body.setLocalTranslation(0, 0.5f, 0);
        model.attachChild(body);
        Geometry apron = G3.geom("apron", new Box(0.24f, 0.26f, 0.02f), G3.lit(G3.rgb(look.cloth)));
        apron.setLocalTranslation(0, 0.45f, 0.33f);
        model.attachChild(apron);
        Geometry head = G3.geom("mhead", new Sphere(14, 16, 0.26f), fur);
        head.setLocalScale(1, 0.95f, 1.15f);
        head.setLocalTranslation(0, 1.02f, 0.05f);
        model.attachChild(head);
        Geometry snout = G3.geom("snout", new Sphere(10, 12, 0.12f), fur);
        snout.setLocalScale(1, 0.85f, 1.5f);
        snout.setLocalTranslation(0, 0.97f, 0.3f);
        model.attachChild(snout);
        Geometry nose = G3.geom("nose", new Sphere(8, 8, 0.045f), pink);
        nose.setLocalTranslation(0, 0.99f, 0.47f);
        model.attachChild(nose);
        for (int side = -1; side <= 1; side += 2) {
            Geometry ear = G3.geom("ear", new Sphere(12, 14, 0.2f), fur);
            ear.setLocalScale(1, 1, 0.25f);
            ear.setLocalTranslation(side * 0.22f, 1.3f, -0.02f);
            model.attachChild(ear);
            Geometry inner = G3.geom("earIn", new Sphere(10, 12, 0.15f), pink);
            inner.setLocalScale(1, 1, 0.2f);
            inner.setLocalTranslation(side * 0.22f, 1.3f, 0.03f);
            model.attachChild(inner);
            Geometry eye = G3.geom("eye", new Sphere(8, 8, 0.04f), black);
            eye.setLocalTranslation(side * 0.1f, 1.08f, 0.26f);
            model.attachChild(eye);
            Geometry foot = G3.geom("foot", new Sphere(8, 8, 0.1f), pink);
            foot.setLocalScale(1, 0.5f, 1.6f);
            foot.setLocalTranslation(side * 0.15f, 0.04f, 0.12f);
            model.attachChild(foot);
        }
        Node tail = new Node("tail");
        for (int i = 0; i < 8; i++) {
            Geometry seg = G3.geom("tailSeg", new Sphere(6, 6, 0.035f), pink);
            seg.setLocalTranslation(0, 0.25f + i * 0.04f + FastMath.sin(i * 0.5f) * 0.05f, -0.32f - i * 0.08f);
            tail.attachChild(seg);
        }
        model.attachChild(tail);
        mouseTail = tail;
    }

    private Node mouseTail;

    private void accessory(Outfit.Accessory acc) {
        if (acc == null || acc == Outfit.Accessory.NONE) return;
        float h = height;
        float sw = troll ? 0.21f * h : 0.11f * h;
        ColorRGBA cloth = G3.rgb(look.cloth);
        Node a = new Node("accessory");
        switch (acc) {
            case PAULDRONS -> {
                Material metal = G3.lit(new ColorRGBA(0.6f, 0.62f, 0.68f, 1));
                metal.setColor("Specular", ColorRGBA.White);
                metal.setFloat("Shininess", 48);
                for (int side = -1; side <= 1; side += 2) {
                    Geometry g = G3.geom("pauldron", new Sphere(10, 12, h * 0.07f), metal);
                    g.setLocalScale(1.3f, 0.7f, 1.2f);
                    g.setLocalTranslation(side * sw, h * 0.8f, 0);
                    a.attachChild(g);
                }
            }
            case BIG_WINGS -> {
                bigWings = new Node("bigWings");
                Material wm = G3.additive(G3.wingTex(), cloth.mult(0.8f).addLocal(new ColorRGBA(0.15f, 0.15f, 0.25f, 0)), true);
                for (int side = -1; side <= 1; side += 2) {
                    Node wn = new Node("bw" + side);
                    Geometry q = G3.transparent(G3.geom("bigWing", new Quad(h * 0.9f, h * 0.9f), wm));
                    q.setLocalTranslation(0, -h * 0.25f, 0);
                    wn.attachChild(q);
                    wn.setUserData("side", side);
                    bigWings.attachChild(wn);
                }
                bigWings.setLocalTranslation(0, h * 0.7f, -h * 0.08f);
                a.attachChild(bigWings);
            }
            case SCARF -> {
                Geometry g = G3.geom("scarf", new Torus(16, 8, h * 0.025f, h * 0.075f), G3.lit(cloth.mult(1.4f)));
                g.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
                g.setLocalTranslation(0, h * 0.83f, 0.02f);
                a.attachChild(g);
            }
            case HALO -> {
                Geometry g = G3.geom("halo", new Torus(24, 6, h * 0.012f, h * 0.09f), G3.emissive(new ColorRGBA(1f, 0.92f, 0.6f, 1)));
                g.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
                g.setLocalTranslation(0, h * 1.07f, 0);
                a.attachChild(g);
            }
            case LANTERN -> {
                Geometry g = G3.geom("lanternOrb", new Sphere(10, 10, h * 0.04f), G3.emissive(new ColorRGBA(1f, 0.8f, 0.45f, 1)));
                g.setLocalTranslation(sw * 1.1f, h * 0.48f, 0.08f);
                a.attachChild(g);
            }
            case BUBBLE -> {
                Material glass = G3.lit(new ColorRGBA(0.6f, 0.85f, 1f, 0.25f));
                glass.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                glass.setColor("Specular", ColorRGBA.White);
                glass.setFloat("Shininess", 90);
                Geometry g = G3.transparent(G3.geom("bubble", new Sphere(16, 16, h * 0.13f), glass));
                g.setLocalTranslation(0, h * 0.91f, 0.02f);
                a.attachChild(g);
            }
            case HOOD -> {
                Geometry g = G3.geom("hood", new Dome(Vector3f.ZERO, 8, 16, h * 0.1f, false), G3.lit(cloth));
                g.setLocalScale(1.1f, 1.5f, 1.15f);
                g.setLocalTranslation(0, h * 0.9f, -0.02f);
                a.attachChild(g);
            }
            case CLOVER -> {
                Material green = G3.litGlow(new ColorRGBA(0.3f, 0.8f, 0.3f, 1), new ColorRGBA(0.1f, 0.4f, 0.1f, 1));
                for (int i = 0; i < 4; i++) {
                    Geometry g = G3.geom("leaf", new Sphere(6, 6, h * 0.018f), green);
                    float an = i * FastMath.HALF_PI;
                    g.setLocalTranslation(FastMath.cos(an) * h * 0.018f, h * 0.7f + FastMath.sin(an) * h * 0.018f, troll ? h * 0.15f : h * 0.07f);
                    a.attachChild(g);
                }
            }
            case SATCHEL -> {
                Geometry g = G3.geom("satchel", new Box(h * 0.06f, h * 0.05f, h * 0.025f), G3.lit(new ColorRGBA(0.4f, 0.26f, 0.15f, 1)));
                g.setLocalTranslation(-sw * 1.1f, h * 0.47f, 0.02f);
                a.attachChild(g);
            }
            case MONOCLE -> {
                Geometry g = G3.geom("monocle", new Torus(16, 6, h * 0.004f, h * 0.022f), G3.litGlow(new ColorRGBA(0.9f, 0.75f, 0.3f, 1), new ColorRGBA(0.2f, 0.15f, 0.05f, 1)));
                g.setLocalTranslation(h * 0.04f, h * 0.92f, troll ? h * 0.13f : h * 0.07f);
                a.attachChild(g);
            }
            case FLOWERS -> {
                ColorRGBA[] cs = {new ColorRGBA(1f, 0.8f, 0.3f, 1), new ColorRGBA(1f, 0.5f, 0.7f, 1), new ColorRGBA(0.6f, 0.7f, 1f, 1)};
                for (int i = 0; i < 9; i++) {
                    float an = i * FastMath.TWO_PI / 9;
                    Geometry g = G3.geom("flower", new Sphere(6, 6, h * 0.018f), G3.litGlow(cs[i % 3], cs[i % 3].mult(0.25f)));
                    g.setLocalTranslation(FastMath.cos(an) * h * 0.075f, h * 0.98f, FastMath.sin(an) * h * 0.075f);
                    a.attachChild(g);
                }
            }
            default -> { }
        }
        node.attachChild(a);
    }

    private static Spatial sinbad() {
        if (sinbadProto == null) sinbadProto = G3.am.loadModel("Models/Sinbad/Sinbad.mesh.xml");
        return sinbadProto.clone();
    }

    private static Spatial ninja() {
        if (ninjaProto == null) ninjaProto = G3.am.loadModel("Models/Ninja/Ninja.mesh.xml");
        return ninjaProto.clone();
    }

    /** Skaliert ein Modell auf die gewünschte Höhe und stellt die Füße auf y=0. */
    private void fit(Spatial s, float h, float yaw) {
        s.updateModelBound();
        s.updateGeometricState();
        BoundingBox bb = (BoundingBox) s.getWorldBound();
        float sc = h / (bb.getYExtent() * 2);
        s.setLocalScale(sc);
        s.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        Vector3f c = bb.getCenter();
        s.setLocalTranslation(-c.x * sc, -(c.y - bb.getYExtent()) * sc, -c.z * sc);
        model.attachChild(s);
        ac = findComposer(s);
    }

    private static AnimComposer findComposer(Spatial s) {
        AnimComposer[] out = new AnimComposer[1];
        s.depthFirstTraversal(sp -> {
            if (out[0] == null && sp.getControl(AnimComposer.class) != null) out[0] = sp.getControl(AnimComposer.class);
        });
        return out[0];
    }

    private static SkinningControl findSkin(Spatial s) {
        SkinningControl[] out = new SkinningControl[1];
        s.depthFirstTraversal(sp -> {
            if (out[0] == null && sp.getControl(SkinningControl.class) != null) out[0] = sp.getControl(SkinningControl.class);
        });
        return out[0];
    }

    // ---------------------------------------------------------------- Aufbau

    private void buildTroll() {
        Spatial s = sinbad();
        ColorRGBA skin = G3.rgb(look.skin).multLocal(1.7f), cloth = G3.rgb(look.cloth).multLocal(1.8f);
        s.depthFirstTraversal(sp -> {
            if (sp instanceof Geometry g) {
                Material m = g.getMaterial().clone();
                String tex = String.valueOf(m.getParam("DiffuseMap"));
                if (tex.contains("body")) tint(m, skin);
                else if (tex.contains("clothes")) tint(m, cloth);
                g.setMaterial(m);
                if (g.getName().toLowerCase().contains("sword")) g.removeFromParent();
            }
        });
        fit(s, height, 0);
        SkinningControl sc = findSkin(s);
        if (ac != null && sc != null) {
            ac.makeLayer("top", ArmatureMask.createMask(sc.getArmature(), "Waist"));
            if (look.horns) {
                Node head = sc.getAttachmentsNode("Head");
                Material hm = G3.lit(new ColorRGBA(0.82f, 0.78f, 0.66f, 1));
                for (int side = -1; side <= 1; side += 2) {
                    Geometry horn = G3.geom("horn", new Cylinder(4, 10, 0.32f, 0.02f, 1.9f, true, false), hm);
                    horn.setLocalTranslation(side * 0.9f, 1.25f, 0.1f);
                    horn.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI + 0.35f, 0, side * -0.6f));
                    head.attachChild(horn);
                }
            }
        }
    }

    private static void tint(Material m, ColorRGBA c) {
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", c);
        m.setColor("Ambient", c.mult(0.8f));
    }

    /** Realistische Mixamo-Figur, optional getönt (z. B. Trollhaut). */
    private void buildMixamo(Spatial s, ColorRGBA tint) {
        mixamo = true;
        troll = false;
        if (tint != null) {
            s.depthFirstTraversal(sp -> {
                if (sp instanceof Geometry g) {
                    Material m = g.getMaterial().clone();
                    if (m.getMaterialDef().getMaterialParam("BaseColor") != null) m.setColor("BaseColor", tint);
                    else if (m.getMaterialDef().getMaterialParam("Diffuse") != null) { m.setBoolean("UseMaterialColors", true); m.setColor("Diffuse", tint); }
                    g.setMaterial(m);
                }
            });
        }
        mixamoFit(s);
        if (look.horns) {
            SkinningControl sc = Mixamo.skin(s);
            Node head = sc != null ? sc.getAttachmentsNode(sc.getArmature().getJoint("mixamorig:Head") != null ? "mixamorig:Head" : "mixamorigHead") : null;
            if (head != null) {
                float u = 1f / s.getLocalScale().y * height / 100f;
                Material hm = G3.lit(new ColorRGBA(0.82f, 0.78f, 0.66f, 1));
                for (int side = -1; side <= 1; side += 2) {
                    Geometry horn = G3.geom("horn", new Cylinder(4, 10, 6f * u, 0.4f * u, 26f * u, true, false), hm);
                    horn.setLocalTranslation(side * 7f * u, 16f * u, 0);
                    horn.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI + 0.3f, 0, side * -0.5f));
                    head.attachChild(horn);
                }
            }
        }
    }

    /** Skaliert eine Mixamo-Figur. Mixamo-Exporte sind (bei Maßstab 1) etwa 1,62 m groß, die Füße stehen auf y = 0. */
    private void mixamoFit(Spatial s) {
        float k = height / 1.62f;
        s.setLocalScale(k);
        model.attachChild(s);
        ac = Mixamo.composer(s);
    }

    private static com.jme3.anim.Joint joint(com.jme3.anim.Armature a, String n) {
        com.jme3.anim.Joint j = a.getJoint("mixamorig:" + n);
        return j != null ? j : a.getJoint("mixamorig" + n);
    }

    private void buildFairyWings() {
        wings = new Node[4];
        Material wm = G3.additive(G3.wingTex(), G3.rgb(look.wing).multLocal(0.22f), false);
        for (int i = 0; i < 4; i++) {
            Node w = new Node("wing");
            boolean left = i % 2 == 0, lower = i >= 2;
            float size = lower ? 0.62f : 0.95f;
            Geometry q = G3.transparent(G3.geom("wq", new Quad(size, size), wm));
            q.setLocalTranslation(0, lower ? -size * 0.9f : -0.05f, 0);
            if (lower) q.setLocalRotation(new Quaternion().fromAngleAxis(-0.6f, Vector3f.UNIT_Z));
            w.attachChild(q);
            w.setLocalTranslation(0, height * 0.72f, -0.12f);
            w.setUserData("left", left);
            node.attachChild(w);
            wings[i] = w;
        }
    }

    private void buildFairy() {
        Spatial s = ninja();
        // Natürliche Haut und Kleidung statt Leuchten: Kleidungsfarbe auf die Fototextur gelegt
        Material m = G3.lit(G3.rgb(look.cloth).multLocal(0.9f).addLocal(new ColorRGBA(0.35f, 0.33f, 0.32f, 0)));
        Texture t = G3.am.loadTexture("Models/Ninja/Ninja.jpg");
        m.setTexture("DiffuseMap", t);
        m.setColor("Specular", new ColorRGBA(0.18f, 0.18f, 0.18f, 1));
        m.setFloat("Shininess", 16);
        s.setMaterial(m);
        fit(s, height, FastMath.PI);
        // Flügel
        wings = new Node[4];
        Material wm = G3.additive(G3.wingTex(), G3.rgb(look.wing).multLocal(0.22f), false);
        for (int i = 0; i < 4; i++) {
            Node w = new Node("wing");
            boolean left = i % 2 == 0, lower = i >= 2;
            float size = lower ? 0.62f : 0.95f;
            Geometry q = G3.transparent(G3.geom("wq", new Quad(size, size), wm));
            q.setLocalTranslation(0, lower ? -size * 0.9f : -0.05f, 0);
            if (lower) q.setLocalRotation(new Quaternion().fromAngleAxis(-0.6f, Vector3f.UNIT_Z));
            w.attachChild(q);
            w.setLocalTranslation(0, height * 0.72f, -0.12f);
            w.setUserData("left", left);
            node.attachChild(w);
            wings[i] = w;
        }
        ParticleEmitter sp = G3.particles("sparkle", 30, null, G3.rgb(look.accent).setAlpha(0.9f), new ColorRGBA(1, 1, 1, 0), 0.06f, 0.01f, 1.2f, 2.2f);
        sp.setParticlesPerSec(10);
        sp.setShape(new com.jme3.effect.shapes.EmitterSphereShape(new Vector3f(0, height * 0.55f, 0), 0.45f));
        sp.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.25f, 0));
        sp.getParticleInfluencer().setVelocityVariation(0.6f);
        sp.setInWorldSpace(true);
        node.attachChild(sp);
    }

    private void buildWraith() {
        Spatial s = ninja();
        Material m = G3.lit(new ColorRGBA(0.03f, 0.025f, 0.04f, 0.82f));
        m.setColor("GlowColor", G3.rgb(look.accent).multLocal(0.07f));
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        s.setMaterial(m);
        s.setQueueBucket(RenderQueue.Bucket.Transparent);
        fit(s, height, FastMath.PI);
        Material eye = G3.emissive(G3.rgb(look.accent).multLocal(1.4f));
        for (int side = -1; side <= 1; side += 2) {
            Geometry e = G3.geom("eye", new Sphere(6, 6, 0.035f), eye);
            e.setLocalTranslation(side * 0.07f, height * 0.9f, 0.16f);
            node.attachChild(e);
        }
        ParticleEmitter smoke = G3.particles("shadowSmoke", 40, "Effects/Smoke/Smoke.png",
            new ColorRGBA(0.05f, 0.02f, 0.08f, 0.55f), new ColorRGBA(0.02f, 0, 0.03f, 0), 0.6f, 1.6f, 1.5f, 2.6f);
        smoke.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        smoke.setImagesX(15);
        smoke.setParticlesPerSec(14);
        smoke.setShape(new com.jme3.effect.shapes.EmitterSphereShape(new Vector3f(0, 0.8f, 0), 0.5f));
        smoke.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.5f, 0));
        smoke.getParticleInfluencer().setVelocityVariation(0.5f);
        smoke.setInWorldSpace(true);
        node.attachChild(smoke);
    }

    private void buildShroom() {
        Material stem = G3.lit(new ColorRGBA(0.86f, 0.82f, 0.72f, 1));
        Geometry st = G3.geom("stem", new Cylinder(6, 18, 0.55f, 0.75f, 2.5f, true, false), stem);
        st.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        st.setLocalTranslation(0, 1.25f, 0);
        model.attachChild(st);
        Material cap = G3.litGlow(G3.rgb(look.cloth).multLocal(1.4f), G3.rgb(look.cloth).multLocal(0.25f));
        Geometry cp = G3.geom("cap", new Dome(Vector3f.ZERO, 10, 24, 1.9f, false), cap);
        cp.setLocalScale(1, 0.62f, 1);
        cp.setLocalTranslation(0, 2.35f, 0);
        model.attachChild(cp);
        Material spot = G3.emissive(new ColorRGBA(1f, 0.95f, 0.75f, 1));
        for (int i = 0; i < 11; i++) {
            float a = i * 2.4f, r = 0.5f + (i % 3) * 0.45f;
            Geometry s = G3.geom("spot", new Sphere(5, 6, 0.12f + (i % 2) * 0.06f), spot);
            float y = 2.35f + 1.18f * (float) Math.sqrt(Math.max(0, 1 - (r / 1.9f) * (r / 1.9f)));
            s.setLocalTranslation(FastMath.cos(a) * r, y, FastMath.sin(a) * r);
            model.attachChild(s);
        }
        Material eye = G3.emissive(G3.rgb(look.accent));
        for (int side = -1; side <= 1; side += 2) {
            Geometry e = G3.geom("eye", new Sphere(6, 6, 0.08f), eye);
            e.setLocalTranslation(side * 0.2f, 1.6f, 0.62f);
            model.attachChild(e);
        }
    }

    // ---------------------------------------------------------------- Animation

    public void play(Anim a) {
        if (a == anim || ac == null) { anim = a; return; }
        anim = a;
        try {
            if (mixamo) {
                switch (a) {
                    case IDLE, CAST -> ac.setCurrentAction("Idle");
                    case RUN -> ac.setCurrentAction(look.kind == Look.FAIRY ? "Idle" : lastSpeed > 5.5f ? "Run" : "Walk");
                    case JUMP -> ac.setCurrentAction("Run");
                }
            } else if (troll) {
                switch (a) {
                    case IDLE -> { ac.setCurrentAction("IdleBase"); ac.setCurrentAction("IdleTop", "top"); }
                    case RUN -> { ac.setCurrentAction("RunBase"); ac.setCurrentAction("RunTop", "top"); }
                    case CAST -> { ac.setCurrentAction("IdleBase"); ac.setCurrentAction("SliceVertical", "top"); }
                    case JUMP -> { ac.setCurrentAction("JumpLoop"); ac.setCurrentAction("JumpLoop", "top"); }
                }
            } else {
                switch (a) {
                    case IDLE -> ac.setCurrentAction(look.kind == Look.WRAITH ? "Idle2" : "Idle3");
                    case RUN -> ac.setCurrentAction("Walk");
                    case CAST -> ac.setCurrentAction("Attack3");
                    case JUMP -> ac.setCurrentAction("JumpNoHeight");
                }
            }
        } catch (RuntimeException ignored) {
            // fehlende Animation: Figur bleibt in der letzten Pose
        }
    }

    public void cast() {
        castT = troll ? 0.75f : 0.8f;
        play(Anim.CAST);
    }

    public boolean casting() { return castT > 0; }

    /** speed: Laufgeschwindigkeit in m/s (0 = steht). */
    public void update(float tpf, float speed, boolean airborne) {
        t += tpf;
        if (mixamo && anim == Anim.RUN && (lastSpeed > 5.5f) != (speed > 5.5f)) anim = null;
        lastSpeed = speed;
        if (castT > 0) {
            castT -= tpf;
            if (castT <= 0) anim = null;
        }
        if (castT <= 0) {
            if (airborne && troll) play(Anim.JUMP);
            else play(speed > 0.5f ? Anim.RUN : Anim.IDLE);
        }
        if (ac != null) {
            float base = troll ? speed / 5.2f : mixamo ? (speed > 5.5f ? speed / 6f : speed / 2.2f) : speed / 2.6f;
            ac.setGlobalSpeed(castT > 0 ? 1.3f : (speed > 0.5f ? Math.max(0.6f, Math.min(1.8f, base)) : 1f));
        }
        if (look.kind == Look.FAIRY) {
            // Feen fliegen: schweben, beim Fortbewegen höher und nach vorn geneigt gleitend
            float target = speed > 0.5f || airborne || flying ? 0.85f : 0.35f;
            flyH += (target - flyH) * Math.min(1, tpf * 3);
            bob = FastMath.sin(t * 2.2f) * 0.08f + flyH;
            model.setLocalTranslation(0, bob, 0);
            float lean = Math.min(1, speed / 5f) * 0.38f;
            leanCur += (lean - leanCur) * Math.min(1, tpf * 4);
            model.setLocalRotation(new Quaternion().fromAngleAxis(leanCur, Vector3f.UNIT_X));
        } else if (look.kind == Look.WRAITH || hover) {
            bob = FastMath.sin(t * 2.2f) * 0.08f + 0.12f;
            model.setLocalTranslation(0, bob, 0);
        }
        if (bigWings != null) {
            float flap = flying ? FastMath.sin(t * 7f) * 0.55f : FastMath.sin(t * 1.5f) * 0.05f - 0.45f;
            for (Spatial sp : bigWings.getChildren()) {
                int side = sp.getUserData("side");
                sp.setLocalRotation(new Quaternion().fromAngles(0, side < 0 ? FastMath.PI - 0.25f - flap : 0.25f + flap, 0.1f));
            }
            bigWings.setLocalTranslation(0, height * 0.7f + bob, -height * 0.08f);
        }
        if (mouseTail != null) mouseTail.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.sin(t * 2.5f) * 0.3f, Vector3f.UNIT_Y));
        if (wings != null) {
            float flap = FastMath.sin(t * (speed > 0.5f || airborne ? 22f : 9f)) * 0.5f + 0.3f;
            for (Node w : wings) {
                boolean left = w.getUserData("left");
                w.setLocalTranslation(0, height * 0.72f + bob, -0.12f);
                w.setLocalRotation(new Quaternion().fromAngles(0, (left ? 1 : -1) * (FastMath.PI - 0.35f - flap), 0.15f));
            }
        }
    }

    /** Dreht die Figur weich in Blickrichtung yaw (Bogenmaß, 0 = +Z). */
    public void face(float yaw, float tpf) {
        float cur = node.getLocalRotation().toAngles(null)[1];
        float d = yaw - cur;
        while (d > FastMath.PI) d -= FastMath.TWO_PI;
        while (d < -FastMath.PI) d += FastMath.TWO_PI;
        cur += d * Math.min(1, tpf * 10);
        node.setLocalRotation(new Quaternion().fromAngleAxis(cur, Vector3f.UNIT_Y));
    }
}
