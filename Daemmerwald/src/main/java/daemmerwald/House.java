package daemmerwald;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;
import com.jme3.texture.Texture;

import java.awt.Color;
import java.util.Random;

/**
 * Ein begehbares Zuhause: Baumhaus im hohlen Stamm oder Erdhöhle unter einem Hügel.
 * Darin wohnt eine Kreatur – lieb an hellen Orten, böse an dunklen.
 */
public class House {
    public final String id, owner, title;
    public final float x, z, yaw;
    public final boolean tree, evil;
    public final Look look;
    public final long seed;
    /** Rast-Café: hier kann man speichern. */
    public boolean cafe;

    public House(String id, String owner, String title, float x, float z, float yaw, boolean tree, boolean evil, Look look, long seed) {
        this.id = id;
        this.owner = owner;
        this.title = title;
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        this.tree = tree;
        this.evil = evil;
        this.look = look;
        this.seed = seed;
    }

    public float radius() { return tree ? 3.3f : 5.2f; }

    private static final String[] CAFE_NAMES = {"Teestube Mooskessel", "Café Zum Grünen Kessel", "Rastplatz Honigtau", "Café Wurzelwerk",
        "Teehaus Sternschnuppe", "Café Am Stillen Weiher", "Kaffeestube Kieselgrund", "Café Morgenrot"};

    /** Ein Rast-Café in der Wildnis (immer mit freundlicher Wirtin oder freundlichem Wirt). */
    public static House randomCafe(String id, float x, float z, Random r) {
        String name = CAFE_NAMES[r.nextInt(CAFE_NAMES.length)];
        int kind = r.nextInt(3);
        String host = kind == 0 ? NICE_TROLL[r.nextInt(NICE_TROLL.length)] : kind == 1 ? NICE_FAIRY[r.nextInt(NICE_FAIRY.length)] : NICE_MOUSE[r.nextInt(NICE_MOUSE.length)];
        House h = new House(id, host, name, x, z, r.nextFloat() * FastMath.TWO_PI, r.nextBoolean(), false, residentLook(r, false, kind), r.nextLong());
        h.cafe = true;
        return h;
    }

    /** Punkt vor der Tür (dort kann man klopfen). */
    public Vector3f door() {
        float d = radius() + 1.4f;
        float dx = FastMath.sin(yaw) * d, dz = FastMath.cos(yaw) * d;
        return new Vector3f(x + dx, WorldGen.height(x + dx, z + dz), z + dz);
    }

    // ---------------------------------------------------------------- Bewohner

    private static final String[] NICE_FAIRY = {"Mirabel Tautropfen", "Liss Holunder", "Fee Rosmarin", "Elva Glimmerflügel", "Nim Blütenstaub", "Sola Morgentau"};
    private static final String[] NICE_TROLL = {"Oma Knorz", "Brumli Moosbart", "Tante Kiesel", "Onkel Grummel", "Hilde Steinkuchen", "Bodo Wurzelfuß"};
    private static final String[] NICE_MOUSE = {"Onkel Krümel", "Frau Schnupper", "Pips Käserinde", "Mimi Nusskern"};
    private static final String[] EVIL = {"Grubenwicht Schnarr", "Witwe Nachtschatten", "Dornfratz", "Gierig Glimm", "Rabenkralle", "Moder-Mähre", "Schleichwurz"};

    static Look residentLook(Random r, boolean evil, int kindHint) {
        int kind = kindHint >= 0 ? kindHint : r.nextInt(evil ? 2 : 3);
        int[][] skinsT = {{116, 140, 104}, {130, 120, 100}, {104, 128, 130}, {140, 120, 110}};
        int[][] skinsF = {{246, 226, 214}, {230, 205, 190}, {214, 222, 240}, {250, 236, 224}};
        int[][] cloth = {{170, 90, 120}, {80, 140, 110}, {90, 110, 170}, {200, 150, 80}, {150, 100, 170}, {190, 110, 80}};
        int[][] hair = {{230, 200, 120}, {120, 70, 40}, {60, 40, 30}, {200, 90, 60}, {220, 220, 230}, {80, 60, 110}};
        boolean female = r.nextBoolean();
        int style = female ? new int[]{Look.LONG, Look.BRAIDS, Look.BUN, Look.PONYTAIL}[r.nextInt(4)]
            : new int[]{Look.SHORT, Look.WILD, Look.BEARD}[r.nextInt(3)];
        if (evil) {
            int[] c = {50, 36, 60};
            int[] eye = {255, 60, 70};
            if (kind == 0) return Look.troll(new int[]{80, 96, 70}, c, eye, new int[]{30, 30, 30}, r.nextBoolean()).hair(female, Look.WILD);
            return Look.fairy(new int[]{190, 180, 200}, c, eye, new int[]{20, 15, 30}, new int[]{90, 40, 110}).hair(female, style);
        }
        if (kind == 2) {
            return new Look(Look.MOUSE, new Color(140, 128, 120), Look2.c(cloth[r.nextInt(cloth.length)]), new Color(255, 220, 230), Color.WHITE, Color.WHITE, false);
        }
        if (kind == 0) return Look.troll(skinsT[r.nextInt(skinsT.length)], cloth[r.nextInt(cloth.length)], new int[]{110, 190, 255}, hair[r.nextInt(hair.length)], r.nextInt(4) == 0).hair(female, style);
        return Look.fairy(skinsF[r.nextInt(skinsF.length)], cloth[r.nextInt(cloth.length)], new int[]{120, 200, 140}, hair[r.nextInt(hair.length)], new int[]{200, 230, 255}).hair(female, style);
    }

    /** Hilfsklasse für Farbumwandlung. */
    static final class Look2 {
        static Color c(int[] v) { return new Color(v[0], v[1], v[2]); }
    }

    /** Erzeugt ein Haus mit zufälligem Bewohner (für die endlose Welt). */
    public static House random(String id, float x, float z, Random r) {
        boolean evil = WorldGen.darkness(x, z) > 0.4f;
        boolean tree = r.nextBoolean();
        int kind = evil ? r.nextInt(2) : r.nextInt(3);
        String owner;
        if (evil) owner = EVIL[r.nextInt(EVIL.length)];
        else owner = kind == 0 ? NICE_TROLL[r.nextInt(NICE_TROLL.length)] : kind == 1 ? NICE_FAIRY[r.nextInt(NICE_FAIRY.length)] : NICE_MOUSE[r.nextInt(NICE_MOUSE.length)];
        String title = evil ? (tree ? "Verfluchter Hohlstamm" : "Dunkle Erdhöhle") : (tree ? "Baumhaus" : "Hügelhöhle");
        return new House(id, owner, title, x, z, r.nextFloat() * FastMath.TWO_PI, tree, evil, residentLook(r, evil, kind), r.nextLong());
    }

    // ---------------------------------------------------------------- Außenansicht

    private static Material bark, darkBark, grassMound, darkMound, door, frame, windowWarm, windowEvil, stone, thorn, leafMat;

    private static synchronized void initMats() {
        if (bark != null) return;
        bark = G3.litTex("Models/Tree/BarkColor.jpg", new ColorRGBA(0.75f, 0.62f, 0.5f, 1), null);
        darkBark = G3.litTex("Models/Tree/BarkColor.jpg", new ColorRGBA(0.3f, 0.24f, 0.32f, 1), null);
        darkBark.setColor("GlowColor", new ColorRGBA(0.08f, 0.01f, 0.12f, 1));
        grassMound = G3.litTex("Textures/Terrain/splat/grass.jpg", new ColorRGBA(0.9f, 1f, 0.8f, 1), null);
        darkMound = G3.litTex("Textures/Terrain/splat/dirt.jpg", new ColorRGBA(0.45f, 0.38f, 0.45f, 1), null);
        door = G3.lit(new ColorRGBA(0.45f, 0.25f, 0.12f, 1));
        frame = G3.lit(new ColorRGBA(0.6f, 0.5f, 0.35f, 1));
        windowWarm = G3.emissive(new ColorRGBA(1f, 0.78f, 0.42f, 1));
        windowEvil = G3.emissive(new ColorRGBA(0.85f, 0.25f, 1f, 1));
        stone = G3.litTex("Textures/Terrain/Rocky/RockyTexture.jpg", new ColorRGBA(0.8f, 0.78f, 0.75f, 1), null);
        thorn = G3.litGlow(new ColorRGBA(0.12f, 0.05f, 0.14f, 1), new ColorRGBA(0.35f, 0.08f, 0.5f, 1));
        leafMat = G3.lit(new ColorRGBA(0.85f, 1.05f, 0.75f, 1));
        leafMat.setTexture("DiffuseMap", G3.am.loadTexture("Models/Tree/Leaves.png"));
        leafMat.setFloat("AlphaDiscardThreshold", 0.5f);
        leafMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        for (Material m : new Material[]{bark, darkBark, grassMound, darkMound, stone}) {
            Texture t = (Texture) m.getParam("DiffuseMap").getValue();
            t.setWrap(Texture.WrapMode.Repeat);
        }
    }

    private static Geometry cyl(float r1, float r2, float h, int sides, Material m) {
        Geometry g = new Geometry("c", new Cylinder(2, sides, r1, r2, h, true, false));
        g.setMaterial(m);
        g.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        return g;
    }

    /** Baut die Außenansicht. Darf in Hintergrund-Threads aufgerufen werden. */
    public Node buildExterior() {
        initMats();
        Node n = new Node("house_" + id);
        float y = WorldGen.height(x, z);
        Random r = new Random(seed);
        Node body = new Node("body");
        float doorZ;
        if (tree) {
            Geometry trunk = cyl(3.2f, 2.5f, 9, 24, evil ? darkBark : bark);
            ((Cylinder) trunk.getMesh()).scaleTextureCoordinates(new Vector2f(6, 3));
            trunk.setLocalTranslation(0, 4.2f, 0);
            body.attachChild(trunk);
            for (int i = 0; i < 6; i++) {
                float a = i * FastMath.TWO_PI / 6 + 0.3f;
                Geometry root = cyl(0.25f, 0.9f, 3.4f, 8, evil ? darkBark : bark);
                root.setLocalRotation(new Quaternion().fromAngles(0, a, 0).mult(new Quaternion().fromAngles(-FastMath.HALF_PI + 0.75f, 0, 0)));
                root.setLocalTranslation(FastMath.sin(a) * 3.1f, 0.6f, FastMath.cos(a) * 3.1f);
                body.attachChild(root);
            }
            if (!evil) {
                for (int i = 0; i < 9; i++) {
                    Geometry crown = new Geometry("crown", G3.crossQuads(6.5f, 5.5f));
                    crown.setMaterial(leafMat);
                    float a = i * 0.7f;
                    crown.setLocalTranslation(FastMath.cos(a) * (i % 3), 7.5f + (i % 4) * 0.8f, FastMath.sin(a) * (i % 3));
                    crown.setLocalRotation(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y));
                    body.attachChild(crown);
                }
            } else {
                for (int i = 0; i < 7; i++) {
                    float a = i * 0.9f;
                    Geometry br = cyl(0.04f, 0.28f, 3.5f, 6, darkBark);
                    br.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI + 0.7f, a, 0));
                    br.setLocalTranslation(FastMath.sin(a) * 1.8f, 9.5f, FastMath.cos(a) * 1.8f);
                    body.attachChild(br);
                }
            }
            doorZ = 3.15f;
            // Balkon & Fenster oben
            Geometry win2 = new Geometry("win", new Sphere(8, 12, 0.45f));
            win2.setMaterial(evil ? windowEvil : windowWarm);
            win2.setLocalScale(1, 1, 0.2f);
            win2.setLocalTranslation(1.2f, 5.2f, 2.85f);
            body.attachChild(win2);
        } else {
            Geometry mound = new Geometry("mound", new Dome(Vector3f.ZERO, 12, 28, 5.2f, false));
            mound.setMaterial(evil ? darkMound : grassMound);
            ((Dome) mound.getMesh()).scaleTextureCoordinates(new Vector2f(4, 4));
            mound.setLocalScale(1, 0.62f, 1);
            mound.setLocalTranslation(0, -0.2f, 0);
            body.attachChild(mound);
            Geometry chimney = cyl(0.35f, 0.42f, 1.8f, 10, stone);
            chimney.setLocalTranslation(-1.6f, 3.3f, -1.2f);
            body.attachChild(chimney);
            ParticleEmitter smoke = G3.particles("smoke", 30, "Effects/Smoke/Smoke.png", new ColorRGBA(0.75f, 0.75f, 0.78f, 0.3f), new ColorRGBA(0.6f, 0.6f, 0.65f, 0), 0.5f, 2.2f, 3f, 5f);
            smoke.setImagesX(15);
            smoke.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            smoke.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.2f));
            smoke.getParticleInfluencer().setInitialVelocity(new Vector3f(0.2f, 0.9f, 0));
            smoke.setParticlesPerSec(4);
            smoke.setLocalTranslation(-1.6f, 4.3f, -1.2f);
            body.attachChild(smoke);
            doorZ = 4.15f;
        }
        // Runde Tür mit Rahmen, Fenstern und Laterne
        Geometry d = new Geometry("door", new Cylinder(2, 24, 0.95f, 0.95f, 0.14f, true, false));
        d.setMaterial(evil ? darkBark : door);
        d.setLocalTranslation(0, 1.0f, doorZ);
        body.attachChild(d);
        Geometry fr = new Geometry("frame", new Torus(24, 8, 0.12f, 1.0f));
        fr.setMaterial(evil ? thorn : frame);
        fr.setLocalTranslation(0, 1.0f, doorZ + 0.05f);
        body.attachChild(fr);
        Geometry knob = new Geometry("knob", new Sphere(8, 8, 0.07f));
        knob.setMaterial(G3.litGlow(new ColorRGBA(0.9f, 0.75f, 0.3f, 1), new ColorRGBA(0.2f, 0.15f, 0.02f, 1)));
        knob.setLocalTranslation(0, 1.0f, doorZ + 0.12f);
        body.attachChild(knob);
        for (int side = -1; side <= 1; side += 2) {
            Geometry w = new Geometry("win", new Sphere(8, 12, 0.36f));
            w.setMaterial(evil ? windowEvil : windowWarm);
            w.setLocalScale(1, 1, 0.2f);
            w.setLocalTranslation(side * 1.9f, 1.5f, doorZ - (tree ? 0.55f : 0.9f));
            body.attachChild(w);
            Geometry wf = new Geometry("winFrame", new Torus(16, 6, 0.06f, 0.38f));
            wf.setMaterial(evil ? thorn : frame);
            wf.setLocalTranslation(side * 1.9f, 1.5f, doorZ - (tree ? 0.5f : 0.85f));
            body.attachChild(wf);
        }
        Geometry lamp = new Geometry("lamp", new Sphere(8, 8, 0.16f));
        lamp.setMaterial(evil ? windowEvil : windowWarm);
        lamp.setLocalTranslation(1.25f, 2.1f, doorZ + 0.25f);
        body.attachChild(lamp);
        if (evil) {
            for (int i = 0; i < 12; i++) {
                float a = r.nextFloat() * FastMath.TWO_PI, rr = radius() + 0.4f + r.nextFloat();
                Geometry sp = cyl(0.18f, 0.01f, 1.4f + r.nextFloat() * 1.6f, 5, thorn);
                sp.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI + (r.nextFloat() - 0.5f) * 0.8f, a, 0));
                sp.setLocalTranslation(FastMath.sin(a) * rr, 0.6f, FastMath.cos(a) * rr);
                if (Math.abs(a - FastMath.PI * 2) > 0.5f && Math.abs(a) > 0.5f) body.attachChild(sp);
            }
        } else {
            // Blumen & Briefkasten
            Material fm = G3.lit(ColorRGBA.White);
            fm.setTexture("DiffuseMap", G3.flowerTex());
            fm.setFloat("AlphaDiscardThreshold", 0.4f);
            fm.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            for (int i = 0; i < 8; i++) {
                Geometry fl = new Geometry("fl", G3.crossQuads(0.9f, 0.6f));
                fl.setMaterial(fm);
                float a = -1.2f + i * 0.33f;
                fl.setLocalTranslation(FastMath.sin(a) * (radius() + 0.6f), 0, FastMath.cos(a) * (radius() + 0.6f));
                body.attachChild(fl);
            }
            Geometry post = cyl(0.05f, 0.05f, 1.1f, 6, door);
            post.setLocalTranslation(-2.2f, 0.55f, doorZ + 1.3f);
            body.attachChild(post);
            Geometry box = new Geometry("mailbox", new Box(0.2f, 0.15f, 0.25f));
            box.setMaterial(G3.lit(new ColorRGBA(0.75f, 0.25f, 0.2f, 1)));
            box.setLocalTranslation(-2.2f, 1.2f, doorZ + 1.3f);
            body.attachChild(box);
        }
        if (cafe) cafeDecor(body, doorZ, r);
        body.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        n.attachChild(body);
        n.setLocalTranslation(x, y, z);
        n.setShadowMode(ShadowMode.CastAndReceive);
        return n;
    }

    /** Schild mit Namen, Laternen und Tische vor der Tür. */
    private void cafeDecor(Node body, float doorZ, Random r) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(512, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(70, 42, 22));
        g.fillRoundRect(0, 0, 512, 128, 30, 30);
        g.setColor(new Color(230, 190, 110));
        g.setStroke(new java.awt.BasicStroke(5));
        g.drawRoundRect(6, 6, 500, 116, 26, 26);
        g.setFont(new java.awt.Font("Serif", java.awt.Font.BOLD | java.awt.Font.ITALIC, title.length() > 18 ? 40 : 50));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(title, 256 - fm.stringWidth(title) / 2, 80);
        g.dispose();
        Material sm = G3.lit(ColorRGBA.White);
        sm.setTexture("DiffuseMap", G3.tex(img));
        sm.setColor("GlowColor", new ColorRGBA(0.12f, 0.08f, 0.03f, 1));
        Geometry sign = new Geometry("cafeSign", new Quad(2.8f, 0.7f));
        sign.setMaterial(sm);
        sign.setLocalTranslation(-1.4f, 2.35f, doorZ + 0.2f);
        body.attachChild(sign);
        Material wood = G3.lit(new ColorRGBA(0.5f, 0.33f, 0.2f, 1));
        Material cloth = G3.lit(new ColorRGBA(0.85f, 0.3f, 0.28f, 1));
        for (int side = -1; side <= 1; side += 2) {
            float tx = side * 2.6f, tz = doorZ + 2.6f;
            Geometry top = cyl(0.6f, 0.6f, 0.06f, 18, wood);
            top.setLocalTranslation(tx, 0.78f, tz);
            body.attachChild(top);
            Geometry leg = cyl(0.06f, 0.06f, 0.78f, 8, wood);
            leg.setLocalTranslation(tx, 0.39f, tz);
            body.attachChild(leg);
            Geometry pole = cyl(0.03f, 0.03f, 2.2f, 6, wood);
            pole.setLocalTranslation(tx, 1.1f, tz);
            body.attachChild(pole);
            Geometry shade = new Geometry("parasol", new Dome(Vector3f.ZERO, 4, 12, 1.1f, false));
            shade.setMaterial(cloth);
            shade.setLocalScale(1, 0.4f, 1);
            shade.setLocalTranslation(tx, 2.15f, tz);
            body.attachChild(shade);
            Geometry lamp = new Geometry("lamp", new Sphere(8, 8, 0.14f));
            lamp.setMaterial(windowWarm);
            lamp.setLocalTranslation(side * 1.5f, 2.4f, doorZ + 0.3f);
            body.attachChild(lamp);
        }
    }

    // ---------------------------------------------------------------- Feste Häuser der Story-Region

    public static java.util.List<House> storyHouses() {
        java.util.List<House> l = new java.util.ArrayList<>();
        Random r = new Random(77);
        l.add(new House("holunder", "Mirabel Tautropfen", "Baumhaus am Wiesenrand", 92, -78, 2.4f, true, false,
            Look.fairy(Look.rgb(246, 228, 216), Look.rgb(200, 110, 150), Look.rgb(120, 200, 140), Look.rgb(240, 200, 120), Look.rgb(255, 210, 240)).hair(true, Look.LONG), 1));
        l.add(new House("knorz", "Oma Knorz", "Hügelhöhle beim Steinbruch", 96, -2, -2.2f, false, false,
            Look.troll(Look.rgb(126, 140, 110), Look.rgb(150, 100, 160), Look.rgb(110, 190, 255), Look.rgb(225, 225, 230), false).hair(true, Look.BUN), 2));
        l.add(new House("kruemel", "Onkel Krümel", "Mäusehöhle am Akademiehof", -34, -36, 0.9f, false, false,
            new Look(Look.MOUSE, new Color(140, 128, 120), new Color(90, 130, 180), new Color(255, 220, 230), Color.WHITE, Color.WHITE, false), 3));
        l.add(new House("lamellis", "Familie Lamellis", "Pilzhaus am Pilzkreis", -96, 112, 2.6f, true, false,
            new Look(Look.SHROOM, new Color(220, 210, 185), new Color(70, 110, 180), new Color(200, 230, 255), Color.WHITE, Color.WHITE, false), 4));
        l.add(new House("nachtschatten", "Witwe Nachtschatten", "Verfluchter Hohlstamm", -146, -74, 1.2f, true, true,
            Look.fairy(Look.rgb(190, 180, 205), Look.rgb(40, 30, 55), Look.rgb(255, 60, 90), Look.rgb(25, 20, 35), Look.rgb(90, 40, 110)).hair(true, Look.LONG), 5));
        l.add(new House("schnarr", "Grubenwicht Schnarr", "Dunkle Erdhöhle im Moor", -26, 112, 0.5f, false, true,
            Look.troll(Look.rgb(80, 96, 70), Look.rgb(60, 40, 50), Look.rgb(255, 70, 70), Look.rgb(30, 30, 30), true).hair(false, Look.WILD), 6));
        l.add(new House("fluestern", "Der Flüsternde", "Schwarzer Stamm an der Welke", 34, 44, -2.6f, true, true,
            new Look(Look.WRAITH, Color.BLACK, new Color(30, 20, 40), new Color(255, 90, 120), Color.BLACK, Color.BLACK, false), 7));
        House c1 = new House("mooskessel", "Wirtin Hilde Steinkuchen", "Teestube Mooskessel", -70, 40, 1.9f, false, false,
            Look.troll(Look.rgb(130, 140, 112), Look.rgb(160, 110, 80), Look.rgb(110, 190, 255), Look.rgb(150, 90, 50), false).hair(true, Look.BRAIDS), 8);
        c1.cafe = true;
        l.add(c1);
        House c2 = new House("sternschnuppe", "Wirt Nim Blütenstaub", "Teehaus Sternschnuppe", 150, -70, -1.6f, true, false,
            Look.fairy(Look.rgb(240, 225, 215), Look.rgb(90, 120, 170), Look.rgb(150, 200, 255), Look.rgb(200, 170, 90), Look.rgb(200, 230, 255)).hair(false, Look.SHORT), 9);
        c2.cafe = true;
        l.add(c2);
        return l;
    }
}
