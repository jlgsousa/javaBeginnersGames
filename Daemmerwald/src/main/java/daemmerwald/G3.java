package daemmerwald;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.util.BufferUtils;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Kleine Helfer für Materialien, prozedurale Texturen, Meshes und Partikel. */
public final class G3 {
    private G3() { }

    static AssetManager am;
    private static Texture2D glowTex, wingTex, grassTex, flowerTex, moonTex, sunTex;

    // ---------------------------------------------------------------- Rauschen

    static double hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    static double vnoise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    static double fbm(double x, double y, int oct) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += vnoise(x, y) * amp;
            norm += amp;
            amp *= 0.5;
            x *= 2.03;
            y *= 2.03;
        }
        return sum / norm;
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static ColorRGBA rgb(Color c) {
        return new ColorRGBA(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1);
    }

    static ColorRGBA lerp(ColorRGBA a, ColorRGBA b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new ColorRGBA(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, a.a + (b.a - a.a) * t);
    }

    // ---------------------------------------------------------------- Materialien

    static Material lit(ColorRGBA diffuse) {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", diffuse);
        m.setColor("Ambient", diffuse);
        m.setColor("Specular", new ColorRGBA(0.08f, 0.08f, 0.08f, 1));
        m.setFloat("Shininess", 12f);
        return m;
    }

    static Material litTex(String path, ColorRGBA tint, String normal) {
        if (upgrade(path) != null) return phFresh(upgrade(path), tint);
        Material m = lit(tint);
        Texture t = am.loadTexture(path);
        t.setWrap(Texture.WrapMode.Repeat);
        m.setTexture("DiffuseMap", t);
        if (normal != null) {
            Texture n = am.loadTexture(normal);
            n.setWrap(Texture.WrapMode.Repeat);
            m.setTexture("NormalMap", n);
        }
        return m;
    }

    // ---------------------------------------------------------------- Poly-Haven-Assets (CC0)

    private static final java.util.Map<String, Material> PH_MATS = new java.util.HashMap<>();
    private static final java.util.Map<String, Spatial> PH_MODELS = new java.util.HashMap<>();

    /**
     * Fotorealistische Oberfläche (Farbe + Normalen) aus assets/polyhaven/textures.
     * Die Geometrie braucht Tangenten (siehe {@link #tangents}), damit die Normalenkarte wirkt.
     */
    static Material ph(String name, ColorRGBA tint) {
        String key = name + tint;
        Material cached = PH_MATS.get(key);
        if (cached != null) return cached;
        Material m = lit(tint);
        m.setColor("Ambient", tint.mult(0.9f));
        Texture t = am.loadTexture("polyhaven/textures/" + name + "/" + name + "_diff.jpg");
        t.setWrap(Texture.WrapMode.Repeat);
        m.setTexture("DiffuseMap", t);
        if (!World3D.lowGraphics) {
            Texture n = am.loadTexture("polyhaven/textures/" + name + "/" + name + "_nor.jpg");
            n.setWrap(Texture.WrapMode.Repeat);
            m.setTexture("NormalMap", n);
        }
        PH_MATS.put(key, m);
        return m;
    }

    static Material ph(String name) { return ph(name, ColorRGBA.White); }

    /** Erzeugt Tangenten für alle Formen mit Normalenkarte (sonst wirkt das Relief nicht). */
    static void tangents(Spatial s) {
        if (World3D.lowGraphics || s == null) return;
        s.depthFirstTraversal(sp -> {
            if (!(sp instanceof Geometry g) || sp instanceof ParticleEmitter || g.getMaterial() == null) return;
            Mesh m = g.getMesh();
            if (m.getBuffer(VertexBuffer.Type.Tangent) != null || m.getBuffer(VertexBuffer.Type.TexCoord) == null) return;
            if (g.getMaterial().getParam("NormalMap") == null) return;
            try {
                com.jme3.util.mikktspace.MikktspaceTangentGenerator.generate(g);
            } catch (Exception ignored) {
                // Form ohne passende Daten – dann eben ohne Relief
            }
        });
    }

    /** Alte Engine-Beispieltexturen → fotorealistische Poly-Haven-Texturen. */
    static String upgrade(String path) {
        return switch (path) {
            case "Models/Tree/BarkColor.jpg" -> "bark_brown_02";
            case "Textures/Terrain/Rocky/RockyTexture.jpg", "Textures/Terrain/BrickWall/BrickWall.jpg" -> "mossy_stone_wall";
            case "Textures/Terrain/splat/dirt.jpg" -> "clay_plaster";
            case "Textures/Terrain/PBR/Rock035_1K_Color.jpg" -> "mossy_rock";
            default -> null;
        };
    }

    /** Eigenes (nicht geteiltes) Material mit Farbe + Normalenkarte von Poly Haven. */
    static Material phFresh(String name, ColorRGBA tint) {
        Material m = lit(tint);
        m.setColor("Ambient", tint.mult(0.9f));
        Texture t = am.loadTexture("polyhaven/textures/" + name + "/" + name + "_diff.jpg");
        t.setWrap(Texture.WrapMode.Repeat);
        m.setTexture("DiffuseMap", t);
        if (!World3D.lowGraphics) {
            Texture n = am.loadTexture("polyhaven/textures/" + name + "/" + name + "_nor.jpg");
            n.setWrap(Texture.WrapMode.Repeat);
            m.setTexture("NormalMap", n);
        }
        return m;
    }

    /** Textur-Material: nutzt automatisch die realistische Variante, falls vorhanden. */
    static Material texture(String path, ColorRGBA tint) {
        String up = upgrade(path);
        if (up != null) return phFresh(up, tint);
        Material m = lit(tint);
        Texture t = am.loadTexture(path);
        t.setWrap(Texture.WrapMode.Repeat);
        m.setTexture("DiffuseMap", t);
        return m;
    }

    /** Lädt ein Poly-Haven-Modell (glTF) einmal und liefert Kopien mit klassischer Beleuchtung. */
    static Spatial model(String name) {
        Spatial s = PH_MODELS.get(name);
        if (s == null) {
            s = am.loadModel("polyhaven/models/" + name + "/" + name + ".gltf");
            toPhong(s);
            tangents(s);
            PH_MODELS.put(name, s);
        }
        return s.clone();
    }

    private static Spatial fox;
    private static boolean foxFailed;

    /** Animiertes Fuchsmodell (assets/models/Fox.glb) – Kopie, oder null, falls nicht ladbar. */
    static Spatial foxModel() {
        if (foxFailed) return null;
        try {
            if (fox == null) {
                fox = am.loadModel("models/Fox.glb");
                toPhong(fox);
            }
            return fox.clone();
        } catch (Exception e) {
            foxFailed = true;
            System.err.println("Fuchsmodell nicht ladbar: " + e.getMessage());
            return null;
        }
    }

    /** Ersetzt PBR-Materialien durch klassisches Licht (passt zur übrigen Beleuchtung, braucht keine Umgebungssonde). */
    static void toPhong(Spatial s) {
        s.depthFirstTraversal(sp -> {
            if (!(sp instanceof Geometry g) || g.getMaterial() == null) return;
            Material pbr = g.getMaterial();
            if (pbr.getMaterialDef().getMaterialParam("BaseColor") == null) return;
            Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
            m.setName(pbr.getName());
            com.jme3.material.MatParam base = pbr.getParam("BaseColorMap"), nrm = pbr.getParam("NormalMap"), col = pbr.getParam("BaseColor"),
                em = pbr.getParam("EmissiveMap"), emc = pbr.getParam("Emissive");
            ColorRGBA c = col != null ? (ColorRGBA) col.getValue() : ColorRGBA.White;
            m.setBoolean("UseMaterialColors", true);
            m.setColor("Diffuse", c);
            m.setColor("Ambient", c.mult(0.9f));
            m.setColor("Specular", new ColorRGBA(0.12f, 0.12f, 0.12f, 1));
            m.setFloat("Shininess", 14);
            if (base != null) m.setTexture("DiffuseMap", (Texture) base.getValue());
            if (nrm != null && !World3D.lowGraphics) m.setTexture("NormalMap", (Texture) nrm.getValue());
            if (em != null) m.setTexture("GlowMap", (Texture) em.getValue());
            else if (emc != null && ((ColorRGBA) emc.getValue()).r + ((ColorRGBA) emc.getValue()).g > 0.1f) m.setColor("GlowColor", (ColorRGBA) emc.getValue());
            RenderState.BlendMode bm = pbr.getAdditionalRenderState().getBlendMode();
            if (bm != RenderState.BlendMode.Off || pbr.getParam("AlphaDiscardThreshold") != null) {
                m.setFloat("AlphaDiscardThreshold", 0.45f);
                m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            } else {
                m.getAdditionalRenderState().setFaceCullMode(pbr.getAdditionalRenderState().getFaceCullMode());
            }
            g.setMaterial(m);
        });
    }

    static Material litGlow(ColorRGBA diffuse, ColorRGBA glow) {
        Material m = lit(diffuse);
        m.setColor("GlowColor", glow);
        return m;
    }

    static Material emissive(ColorRGBA c) {
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", c);
        m.setColor("GlowColor", c);
        return m;
    }

    static Material additive(Texture tex, ColorRGBA c, boolean glow) {
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", tex);
        m.setColor("Color", c);
        if (glow) m.setColor("GlowColor", c);
        // AlphaAdditive beachtet die Transparenz der Textur – sonst leuchtet das ganze Rechteck
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        m.getAdditionalRenderState().setDepthWrite(false);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return m;
    }

    static Geometry geom(String name, Mesh mesh, Material mat) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(mat);
        return g;
    }

    static Geometry transparent(Geometry g) {
        g.setQueueBucket(RenderQueue.Bucket.Transparent);
        g.setShadowMode(RenderQueue.ShadowMode.Off);
        return g;
    }

    // ---------------------------------------------------------------- Texturen

    static Texture2D tex(BufferedImage img) {
        Texture2D t = new Texture2D(new AWTLoader().load(img, true));
        t.setWrap(Texture.WrapMode.EdgeClamp);
        return t;
    }

    static Texture2D glowTex() {
        if (glowTex == null) {
            BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setPaint(new RadialGradientPaint(64, 64, 64, new float[]{0f, 0.25f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(255, 255, 255, 110), new Color(255, 255, 255, 0)}));
            g.fillRect(0, 0, 128, 128);
            g.dispose();
            glowTex = tex(img);
        }
        return glowTex;
    }

    static Texture2D wingTex() {
        if (wingTex == null) {
            BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Libellenflügel: schillernder Verlauf, Längsadern und feines Zellnetz
            Path2D p = new Path2D.Double();
            p.moveTo(10, 246);
            p.curveTo(20, 120, 120, 10, 240, 16);
            p.curveTo(250, 110, 160, 200, 10, 246);
            g.setPaint(new LinearGradientPaint(10, 246, 240, 16, new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(230, 240, 255, 150), new Color(190, 235, 230, 90), new Color(240, 200, 240, 70), new Color(255, 255, 255, 40)}));
            g.fill(p);
            g.setClip(p);
            g.setStroke(new BasicStroke(1.6f));
            g.setColor(new Color(255, 255, 255, 170));
            for (int i = 0; i < 7; i++) g.draw(new QuadCurve2D.Double(10, 246, 70 + i * 18, 150 - i * 20, 110 + i * 22, 30 + i * 24));
            g.setStroke(new BasicStroke(0.7f));
            g.setColor(new Color(255, 255, 255, 95));
            Random r = new Random(17);
            for (int i = 0; i < 260; i++) {
                double x = 20 + r.nextDouble() * 220, y = 20 + r.nextDouble() * 220;
                double a = r.nextDouble() * Math.PI;
                g.draw(new Line2D.Double(x, y, x + Math.cos(a) * 9, y + Math.sin(a) * 9));
            }
            g.setClip(null);
            g.setColor(new Color(255, 255, 255, 220));
            g.setStroke(new BasicStroke(2.2f));
            g.draw(p);
            g.setColor(new Color(60, 50, 70, 200));
            g.fill(new Ellipse2D.Double(196, 30, 14, 9));
            g.dispose();
            wingTex = tex(img);
        }
        return wingTex;
    }

    static Texture2D grassTex() {
        if (grassTex == null) {
            BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Random r = new Random(4);
            for (int i = 0; i < 46; i++) {
                double x = 8 + r.nextDouble() * 112, h = 50 + r.nextDouble() * 76, lean = (r.nextDouble() - 0.5) * 40;
                int gr = 90 + r.nextInt(70);
                g.setPaint(new GradientPaint((float) x, 128, new Color(40, gr - 30, 25), (float) x, (float) (128 - h), new Color(120, gr + 50, 60)));
                Path2D b = new Path2D.Double();
                b.moveTo(x - 2.2, 128);
                b.quadTo(x + lean * 0.3, 128 - h * 0.5, x + lean, 128 - h);
                b.quadTo(x + lean * 0.3 + 1, 128 - h * 0.5, x + 2.2, 128);
                b.closePath();
                g.fill(b);
            }
            g.dispose();
            grassTex = tex(img);
        }
        return grassTex;
    }

    static Texture2D flowerTex() {
        if (flowerTex == null) {
            BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Random r = new Random(9);
            Color[] cols = {new Color(255, 220, 120), new Color(250, 160, 200), new Color(170, 200, 255), new Color(255, 255, 240)};
            for (int i = 0; i < 9; i++) {
                double x = 14 + r.nextDouble() * 100, h = 40 + r.nextDouble() * 70;
                g.setColor(new Color(60, 120, 50));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Line2D.Double(x, 128, x + (r.nextDouble() - 0.5) * 10, 128 - h));
                g.setColor(cols[r.nextInt(cols.length)]);
                double cx = x, cy = 128 - h;
                for (int k = 0; k < 5; k++) {
                    double a = k * Math.PI * 2 / 5;
                    g.fill(new Ellipse2D.Double(cx + Math.cos(a) * 5 - 4, cy + Math.sin(a) * 5 - 4, 8, 8));
                }
                g.setColor(new Color(255, 240, 160));
                g.fill(new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));
            }
            g.dispose();
            flowerTex = tex(img);
        }
        return flowerTex;
    }

    static Texture2D moonTex() {
        if (moonTex == null) {
            BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new RadialGradientPaint(128, 128, 128, new float[]{0f, 0.36f, 0.42f, 1f},
                new Color[]{new Color(255, 255, 255, 0), new Color(200, 210, 255, 0), new Color(200, 210, 255, 70), new Color(200, 210, 255, 0)}));
            g.fillRect(0, 0, 256, 256);
            g.setPaint(new RadialGradientPaint(118, 116, 52, new float[]{0f, 1f}, new Color[]{new Color(250, 248, 236), new Color(205, 204, 196)}));
            g.fill(new Ellipse2D.Double(80, 80, 96, 96));
            Random r = new Random(2);
            for (int i = 0; i < 14; i++) {
                double x = 92 + r.nextDouble() * 64, y = 92 + r.nextDouble() * 64, s = 4 + r.nextDouble() * 14;
                if (Math.hypot(x - 128, y - 128) + s / 2 > 46) continue;
                g.setColor(new Color(160, 160, 158, 110));
                g.fill(new Ellipse2D.Double(x - s / 2, y - s / 2, s, s));
            }
            g.dispose();
            moonTex = tex(img);
        }
        return moonTex;
    }

    static Texture2D sunTex() {
        if (sunTex == null) {
            BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setPaint(new RadialGradientPaint(128, 128, 128, new float[]{0f, 0.12f, 0.18f, 0.45f, 1f},
                new Color[]{new Color(255, 255, 250, 255), new Color(255, 250, 230, 255), new Color(255, 230, 180, 160),
                    new Color(255, 200, 140, 40), new Color(255, 180, 120, 0)}));
            g.fillRect(0, 0, 256, 256);
            g.dispose();
            sunTex = tex(img);
        }
        return sunTex;
    }

    private static Texture2D fernTex, reedTex, needleTex, lilyTex, leafTex;

    /** Farnwedel: mehrere gefiederte Blätter, die aus einem Punkt wachsen. */
    static Texture2D fernTex() {
        if (fernTex == null) {
            BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Random r = new Random(12);
            for (int f = 0; f < 7; f++) {
                double ang = -Math.PI / 2 + (f - 3) * 0.32 + (r.nextDouble() - 0.5) * 0.1;
                double len = 150 + r.nextDouble() * 80;
                int gr = 110 + r.nextInt(50);
                for (int k = 0; k < 26; k++) {
                    double t = k / 26.0, bend = t * t * 0.6 * Math.signum(f - 3);
                    double x = 128 + Math.cos(ang + bend) * len * t, y = 252 + Math.sin(ang + bend) * len * t;
                    double leaf = (1 - t) * 26 + 4;
                    g.setColor(new Color(30 + r.nextInt(20), gr - (int) (t * 30), 30, 255));
                    for (int side = -1; side <= 1; side += 2) {
                        double la = ang + bend + side * 1.2;
                        g.setStroke(new BasicStroke((float) (3.2 - t * 2)));
                        g.draw(new Line2D.Double(x, y, x + Math.cos(la) * leaf, y + Math.sin(la) * leaf));
                    }
                }
                g.setColor(new Color(60, 90, 40));
                g.setStroke(new BasicStroke(2f));
                g.draw(new QuadCurve2D.Double(128, 252, 128 + Math.cos(ang) * len * 0.5, 252 + Math.sin(ang) * len * 0.5,
                    128 + Math.cos(ang + 0.6 * Math.signum(f - 3)) * len, 252 + Math.sin(ang + 0.6 * Math.signum(f - 3)) * len));
            }
            g.dispose();
            fernTex = tex(img);
        }
        return fernTex;
    }

    static Texture2D reedTex() {
        if (reedTex == null) {
            BufferedImage img = new BufferedImage(128, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Random r = new Random(3);
            for (int i = 0; i < 22; i++) {
                double x = 8 + r.nextDouble() * 112, h = 140 + r.nextDouble() * 110, lean = (r.nextDouble() - 0.5) * 30;
                g.setColor(new Color(90 + r.nextInt(40), 120 + r.nextInt(30), 60));
                g.setStroke(new BasicStroke(2.2f));
                g.draw(new QuadCurve2D.Double(x, 256, x + lean * 0.3, 256 - h * 0.5, x + lean, 256 - h));
                if (r.nextDouble() < 0.45) {
                    g.setColor(new Color(110, 70, 40));
                    g.fill(new Ellipse2D.Double(x + lean - 3.5, 256 - h - 4, 7, 26));
                }
            }
            g.dispose();
            reedTex = tex(img);
        }
        return reedTex;
    }

    /** Nadelwerk für Tannen: dunkle Nadeln mit ausgefranstem unterem Rand. */
    static Texture2D needleTex() {
        if (needleTex == null) {
            BufferedImage img = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Random r = new Random(8);
            g.setColor(new Color(18, 42, 30));
            g.fillRect(0, 0, 256, 96);
            for (int i = 0; i < 2600; i++) {
                double x = r.nextDouble() * 256, y = r.nextDouble() * 120;
                int gr = 40 + r.nextInt(55);
                g.setColor(new Color(15 + r.nextInt(25), gr, 25 + r.nextInt(25)));
                g.setStroke(new BasicStroke(1.3f));
                g.draw(new Line2D.Double(x, y, x + (r.nextDouble() - 0.5) * 6, y + 5 + r.nextDouble() * 8));
            }
            g.dispose();
            needleTex = tex(img);
            needleTex.setWrap(Texture.WrapMode.Repeat);
        }
        return needleTex;
    }

    static Texture2D lilyTex() {
        if (lilyTex == null) {
            BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Arc2D pad = new Arc2D.Double(8, 8, 112, 112, 20, 330, Arc2D.PIE);
            g.setPaint(new RadialGradientPaint(64, 64, 60, new float[]{0f, 1f}, new Color[]{new Color(90, 150, 70), new Color(50, 100, 45)}));
            g.fill(pad);
            g.setColor(new Color(110, 170, 90, 140));
            for (int k = 0; k < 9; k++) {
                double a = Math.toRadians(30 + k * 36);
                g.draw(new Line2D.Double(64, 64, 64 + Math.cos(a) * 54, 64 - Math.sin(a) * 54));
            }
            g.dispose();
            lilyTex = tex(img);
        }
        return lilyTex;
    }

    /** Einzelnes Blatt (für fallendes Laub). */
    static Texture2D leafTex() {
        if (leafTex == null) {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Path2D p = new Path2D.Double();
            p.moveTo(32, 4);
            p.curveTo(56, 18, 54, 46, 32, 60);
            p.curveTo(10, 46, 8, 18, 32, 4);
            g.setColor(Color.WHITE);
            g.fill(p);
            g.dispose();
            leafTex = tex(img);
        }
        return leafTex;
    }

    // ---------------------------------------------------------------- Meshes

    /** Zwei gekreuzte Quads (für Gras und Blumen), Fuß bei (0,0,0). */
    static Mesh crossQuads(float w, float h) {
        float hw = w / 2;
        float[] pos = {
            -hw, 0, 0, hw, 0, 0, hw, h, 0, -hw, h, 0,
            0, 0, -hw, 0, 0, hw, 0, h, hw, 0, h, -hw};
        float[] nor = {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0};
        float[] uv = {0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1};
        short[] idx = {0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7};
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.Normal, 3, nor);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();
        return m;
    }

    /** Unregelmäßiger Felsbrocken: verformte Kugel. */
    static Mesh rock(long seed, float size) {
        int rings = 10, segs = 14;
        Random r = new Random(seed);
        double ox = r.nextDouble() * 100, oy = r.nextDouble() * 100;
        float[] pos = new float[(rings + 1) * (segs + 1) * 3];
        float[] uv = new float[(rings + 1) * (segs + 1) * 2];
        int k = 0, u = 0;
        for (int i = 0; i <= rings; i++) {
            double th = Math.PI * i / rings;
            for (int j = 0; j <= segs; j++) {
                double ph = 2 * Math.PI * (j % segs) / segs;
                double x = Math.sin(th) * Math.cos(ph), y = Math.cos(th), z = Math.sin(th) * Math.sin(ph);
                double n = 0.72 + 0.5 * fbm(ox + x * 1.6 + 3, oy + z * 1.6 + y * 1.3, 3);
                double flat = y < -0.2 ? 0.55 : 1.0;
                pos[k++] = (float) (x * n * size * 1.15);
                pos[k++] = (float) (y * n * size * 0.75 * flat);
                pos[k++] = (float) (z * n * size);
                uv[u++] = (float) j / segs * 2;
                uv[u++] = (float) i / rings;
            }
        }
        short[] idx = new short[rings * segs * 6];
        int q = 0;
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segs; j++) {
                int a = i * (segs + 1) + j, b = a + segs + 1;
                idx[q++] = (short) a; idx[q++] = (short) (a + 1); idx[q++] = (short) b;
                idx[q++] = (short) b; idx[q++] = (short) (a + 1); idx[q++] = (short) (b + 1);
            }
        }
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.setBuffer(VertexBuffer.Type.Normal, 3, computeNormals(pos, idx));
        m.updateBound();
        return m;
    }

    static float[] computeNormals(float[] pos, short[] idx) {
        float[] n = new float[pos.length];
        for (int i = 0; i < idx.length; i += 3) {
            int a = idx[i] * 3, b = idx[i + 1] * 3, c = idx[i + 2] * 3;
            Vector3f va = new Vector3f(pos[a], pos[a + 1], pos[a + 2]);
            Vector3f e1 = new Vector3f(pos[b], pos[b + 1], pos[b + 2]).subtractLocal(va);
            Vector3f e2 = new Vector3f(pos[c], pos[c + 1], pos[c + 2]).subtractLocal(va);
            Vector3f f = e1.cross(e2);
            for (int v : new int[]{a, b, c}) { n[v] += f.x; n[v + 1] += f.y; n[v + 2] += f.z; }
        }
        for (int i = 0; i < n.length; i += 3) {
            float l = (float) Math.sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2]);
            if (l > 0) { n[i] /= l; n[i + 1] /= l; n[i + 2] /= l; }
        }
        return n;
    }

    // ---------------------------------------------------------------- Partikel

    static ParticleEmitter particles(String name, int count, String texture, ColorRGBA start, ColorRGBA end,
                                     float startSize, float endSize, float lowLife, float highLife) {
        ParticleEmitter e = new ParticleEmitter(name, ParticleMesh.Type.Triangle, count);
        Material m = new Material(am, "Common/MatDefs/Misc/Particle.j3md");
        Texture t = texture == null ? glowTex() : am.loadTexture(texture);
        m.setTexture("Texture", t);
        // Nur leuchtende Partikel (Lichtfunken, Feuer) dürfen glühen – Rauch und Nebel nicht
        if (texture == null || texture.contains("flame")) m.setTexture("GlowMap", t);
        e.setMaterial(m);
        e.setImagesX(1);
        e.setImagesY(1);
        e.setStartColor(start);
        e.setEndColor(end);
        e.setStartSize(startSize);
        e.setEndSize(endSize);
        e.setLowLife(lowLife);
        e.setHighLife(highLife);
        e.setGravity(0, 0, 0);
        e.setShape(new EmitterSphereShape(Vector3f.ZERO, 1f));
        e.setShadowMode(RenderQueue.ShadowMode.Off);
        e.setQueueBucket(RenderQueue.Bucket.Transparent);
        return e;
    }

    static java.nio.FloatBuffer floats(float[] f) { return BufferUtils.createFloatBuffer(f); }

    /** Tanne: Stamm + gestaffelte Nadelkegel (Höhe ≈ 1, wird skaliert). */
    static Mesh pine() {
        java.util.List<float[]> pos = new java.util.ArrayList<>(), uv = new java.util.ArrayList<>();
        java.util.List<Integer> idx = new java.util.ArrayList<>();
        int seg = 10;
        int layers = 7;
        for (int L = 0; L < layers; L++) {
            float yb = 0.18f + L * 0.115f, yt = yb + 0.24f;
            float rb = 0.34f * (1 - L / (float) (layers + 0.6f)) + 0.04f;
            int base = pos.size();
            pos.add(new float[]{0, yt, 0});
            uv.add(new float[]{0.5f, 0f});
            for (int i = 0; i <= seg; i++) {
                double a = Math.PI * 2 * i / seg + L * 0.4;
                float rr = rb * (0.9f + (float) ((i * 7 + L * 3) % 5) * 0.05f);
                pos.add(new float[]{(float) Math.cos(a) * rr, yb + ((i + L) % 2) * 0.02f, (float) Math.sin(a) * rr});
                uv.add(new float[]{(float) i / seg * 2, 1f});
            }
            for (int i = 0; i < seg; i++) { idx.add(base); idx.add(base + 2 + i); idx.add(base + 1 + i); }
        }
        // Stamm
        int base = pos.size();
        for (int i = 0; i <= 6; i++) {
            double a = Math.PI * 2 * i / 6;
            pos.add(new float[]{(float) Math.cos(a) * 0.025f, 0, (float) Math.sin(a) * 0.025f});
            pos.add(new float[]{(float) Math.cos(a) * 0.012f, 0.4f, (float) Math.sin(a) * 0.012f});
            uv.add(new float[]{0.02f, 0.99f});
            uv.add(new float[]{0.02f, 0.99f});
        }
        for (int i = 0; i < 6; i++) {
            int a = base + i * 2;
            idx.add(a); idx.add(a + 1); idx.add(a + 2);
            idx.add(a + 2); idx.add(a + 1); idx.add(a + 3);
        }
        float[] p = new float[pos.size() * 3], t = new float[uv.size() * 2];
        for (int i = 0; i < pos.size(); i++) { System.arraycopy(pos.get(i), 0, p, i * 3, 3); System.arraycopy(uv.get(i), 0, t, i * 2, 2); }
        short[] ix = new short[idx.size()];
        for (int i = 0; i < ix.length; i++) ix[i] = (short) (int) idx.get(i);
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, p);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, t);
        m.setBuffer(VertexBuffer.Type.Index, 3, ix);
        float[] n = computeNormals(p, ix);
        for (int i = 0; i < n.length; i += 3) { n[i + 1] = Math.abs(n[i + 1]) * 0.6f + 0.4f; }
        m.setBuffer(VertexBuffer.Type.Normal, 3, n);
        m.updateBound();
        return m;
    }

    /** Liegender, bemooster Baumstamm entlang der x-Achse. */
    static Mesh log(float len, float r) {
        com.jme3.scene.shape.Cylinder c = new com.jme3.scene.shape.Cylinder(2, 10, r, r * 0.85f, len, true, false);
        c.scaleTextureCoordinates(new com.jme3.math.Vector2f(2, len / 2));
        return c;
    }

    private static boolean isInside(Spatial sp, Spatial anc) {
        for (Spatial p = sp; p != null; p = p.getParent()) if (p == anc) return true;
        return false;
    }

    /** Unbewegliche Teile eines Knotens zusammenfassen (viel weniger Zeichenaufrufe). Partikel bleiben einzeln. */
    static void batch(com.jme3.scene.Node n) { batch(n, java.util.Set.of()); }

    /** Wie batch, lässt aber die angegebenen (beweglichen) Teile einzeln. */
    static void batch(com.jme3.scene.Node n, java.util.Set<Spatial> dynamic) {
        java.util.List<Spatial> keep = new java.util.ArrayList<>();
        n.breadthFirstTraversal(sp -> {
            if (sp == n) return;
            for (Spatial k : keep) if (isInside(sp, k)) return;
            if (sp instanceof ParticleEmitter || sp.getControl(com.jme3.anim.AnimComposer.class) != null || dynamic.contains(sp)) keep.add(sp);
        });
        java.util.List<com.jme3.math.Transform> rel = new java.util.ArrayList<>();
        for (Spatial sp : keep) {
            com.jme3.math.Transform t = sp.getLocalTransform().clone();
            for (com.jme3.scene.Node p = sp.getParent(); p != null && p != n; p = p.getParent()) t.combineWithParent(p.getLocalTransform());
            rel.add(t);
            sp.removeFromParent();
        }
        jme3tools.optimize.GeometryBatchFactory.optimize(n);
        for (int k = 0; k < keep.size(); k++) {
            keep.get(k).setLocalTransform(rel.get(k));
            n.attachChild(keep.get(k));
        }
    }
}
