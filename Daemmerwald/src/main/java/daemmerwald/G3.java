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
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Additive);
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
            Path2D p = new Path2D.Double();
            p.moveTo(10, 246);
            p.curveTo(20, 120, 120, 10, 240, 16);
            p.curveTo(250, 110, 160, 200, 10, 246);
            g.setPaint(new GradientPaint(10, 246, new Color(255, 255, 255, 200), 240, 16, new Color(255, 255, 255, 40)));
            g.fill(p);
            g.setColor(new Color(255, 255, 255, 230));
            g.setStroke(new BasicStroke(2.2f));
            g.draw(p);
            g.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 6; i++) {
                g.draw(new QuadCurve2D.Double(10, 246, 80 + i * 18, 150 - i * 20, 120 + i * 22, 40 + i * 22));
            }
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
}
