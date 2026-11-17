package daemmerwald;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Random;

/**
 * Tag-Nacht-Zyklus: ein voller Tag dauert 15 Minuten.
 * Himmelskuppel mit Vertex-Farben (Dämmerungsglühen in Sonnenrichtung), Sonne, Mond und Sterne;
 * steuert Sonnen-/Mondlicht, Umgebungslicht und Nebelfarbe.
 */
public class DayNight {
    public static final float DAY_SECONDS = 15 * 60;

    /** Tageszeit 0..1 (0 = Mitternacht, 0.25 = Sonnenaufgang, 0.5 = Mittag, 0.75 = Sonnenuntergang). */
    public float time = 0.70f;

    public final DirectionalLight light = new DirectionalLight();
    public final AmbientLight ambient = new AmbientLight();
    public final Vector3f sunDir = new Vector3f(), moonDir = new Vector3f();
    public final ColorRGBA fogColor = new ColorRGBA();
    public float night, sunElev;
    /** Bewölkung 0..1 (vom Wetter gesetzt). */
    public float overcast;

    private final Node sky = new Node("sky");
    private final Geometry dome, stars, sun, moon;
    private final Mesh domeMesh;
    private final int rings = 24, segs = 48;
    private final Vector3f[] dirs;
    private final FloatBuffer colors;
    private final Material starMat;
    private final float radius = 800;
    private double accum;

    public DayNight(Node root) {
        // Kuppel
        dirs = new Vector3f[(rings + 1) * (segs + 1)];
        float[] pos = new float[dirs.length * 3];
        int k = 0;
        for (int i = 0; i <= rings; i++) {
            double el = Math.PI / 2 - Math.PI * i / rings; // +90° .. -90°
            for (int j = 0; j <= segs; j++) {
                double az = 2 * Math.PI * j / segs;
                Vector3f d = new Vector3f((float) (Math.cos(el) * Math.cos(az)), (float) Math.sin(el), (float) (Math.cos(el) * Math.sin(az)));
                dirs[i * (segs + 1) + j] = d;
                pos[k++] = d.x * radius;
                pos[k++] = d.y * radius;
                pos[k++] = d.z * radius;
            }
        }
        short[] idx = new short[rings * segs * 6];
        int q = 0;
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segs; j++) {
                int a = i * (segs + 1) + j, b = a + segs + 1;
                idx[q++] = (short) a; idx[q++] = (short) b; idx[q++] = (short) (a + 1);
                idx[q++] = (short) (a + 1); idx[q++] = (short) b; idx[q++] = (short) (b + 1);
            }
        }
        domeMesh = new Mesh();
        domeMesh.setBuffer(VertexBuffer.Type.Position, 3, pos);
        domeMesh.setBuffer(VertexBuffer.Type.Index, 3, idx);
        colors = BufferUtils.createFloatBuffer(dirs.length * 4);
        domeMesh.setBuffer(VertexBuffer.Type.Color, 4, colors);
        domeMesh.updateBound();
        Material dm = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        dm.setBoolean("VertexColor", true);
        dm.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        dm.getAdditionalRenderState().setDepthWrite(false);
        dome = G3.geom("skyDome", domeMesh, dm);
        sky.attachChild(dome);

        // Sterne
        Random r = new Random(11);
        int n = 1800;
        float[] sp = new float[n * 3];
        float[] sc = new float[n * 4];
        for (int i = 0; i < n; i++) {
            Vector3f d;
            do {
                d = new Vector3f((float) r.nextGaussian(), (float) Math.abs(r.nextGaussian()) + 0.05f, (float) r.nextGaussian()).normalizeLocal();
            } while (d.y < 0.03f);
            sp[i * 3] = d.x * radius * 0.98f;
            sp[i * 3 + 1] = d.y * radius * 0.98f;
            sp[i * 3 + 2] = d.z * radius * 0.98f;
            float b = 0.35f + r.nextFloat() * r.nextFloat() * 0.65f;
            float tint = r.nextFloat();
            sc[i * 4] = b * (tint < 0.15f ? 1f : 0.85f);
            sc[i * 4 + 1] = b * 0.9f;
            sc[i * 4 + 2] = b * (tint > 0.8f ? 1f : 0.95f);
            sc[i * 4 + 3] = 1;
        }
        Mesh sm = new Mesh();
        sm.setMode(Mesh.Mode.Points);
        sm.setBuffer(VertexBuffer.Type.Position, 3, sp);
        sm.setBuffer(VertexBuffer.Type.Color, 4, sc);
        sm.updateBound();
        starMat = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        starMat.setBoolean("VertexColor", true);
        starMat.setFloat("PointSize", 2f);
        starMat.setColor("Color", new ColorRGBA(1, 1, 1, 1));
        starMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Additive);
        starMat.getAdditionalRenderState().setDepthWrite(false);
        stars = G3.geom("stars", sm, starMat);
        sky.attachChild(stars);

        sun = G3.geom("sun", new Quad(170, 170), G3.additive(G3.sunTex(), new ColorRGBA(1, 0.95f, 0.85f, 1), false));
        moon = G3.geom("moon", new Quad(70, 70), alphaMat(G3.moonTex()));
        sky.attachChild(sun);
        sky.attachChild(moon);

        for (Spatial s : sky.getChildren()) {
            s.setQueueBucket(RenderQueue.Bucket.Sky);
            s.setCullHint(Spatial.CullHint.Never);
            s.setShadowMode(RenderQueue.ShadowMode.Off);
        }
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        sky.setCullHint(Spatial.CullHint.Never);
        root.attachChild(sky);
        root.addLight(light);
        root.addLight(ambient);
        compute();
        updateDome();
    }

    private static Material alphaMat(com.jme3.texture.Texture t) {
        Material m = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", t);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        m.getAdditionalRenderState().setDepthWrite(false);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return m;
    }

    public String clock() {
        int minutes = (int) (time * 24 * 60);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    public String phaseName() {
        if (sunElev > 0.25f) return "Tag";
        if (sunElev > -0.08f) return time < 0.5f ? "Morgendämmerung" : "Abenddämmerung";
        return "Nacht";
    }

    private void compute() {
        double a = time * Math.PI * 2 - Math.PI / 2;
        // Sonne geht im Osten (+x) auf, steht mittags im Süden (+z) und geht im Westen unter
        sunDir.set((float) Math.cos(a), (float) Math.sin(a) * 0.92f, 0.38f).normalizeLocal();
        moonDir.set(-sunDir.x * 0.9f, -sunDir.y, -0.25f).normalizeLocal();
        sunElev = sunDir.y;
        night = 1 - G3.smooth(-0.16f, 0.12f, sunElev);
    }

    public void update(float tpf, Camera cam, float darkZone, float brightZone) {
        time = (time + tpf / DAY_SECONDS) % 1f;
        compute();
        sky.setLocalTranslation(cam.getLocation());

        float sunUp = G3.smooth(-0.04f, 0.18f, sunElev);
        float moonUp = G3.smooth(-0.02f, 0.15f, moonDir.y);
        float golden = (float) Math.exp(-Math.pow((sunElev - 0.04f) / 0.14f, 2));
        ColorRGBA sunCol = G3.lerp(new ColorRGBA(1.25f, 0.62f, 0.32f, 1), new ColorRGBA(1.25f, 1.18f, 1.05f, 1), G3.smooth(0.05f, 0.45f, sunElev));
        ColorRGBA moonCol = new ColorRGBA(0.30f, 0.36f, 0.58f, 1);
        if (sunElev > -0.02f) {
            light.setDirection(sunDir.negate());
            light.setColor(sunCol.mult(sunUp * (1 - 0.62f * overcast)));
        } else {
            light.setDirection(moonDir.negate());
            light.setColor(moonCol.mult(moonUp * (1 - darkZone * 0.5f) * (1 - 0.7f * overcast)));
        }
        ColorRGBA ambNight = new ColorRGBA(0.075f, 0.085f, 0.16f, 1);
        ColorRGBA ambDay = new ColorRGBA(0.36f, 0.39f, 0.47f, 1);
        ColorRGBA amb = G3.lerp(ambNight, ambDay, sunUp);
        amb.addLocal(new ColorRGBA(0.12f, 0.06f, 0.03f, 0).multLocal(golden));
        amb = G3.lerp(amb, new ColorRGBA(0.10f, 0.05f, 0.14f, 1), darkZone * 0.55f);
        amb.addLocal(new ColorRGBA(0.06f, 0.05f, 0.02f, 0).multLocal(brightZone));
        ambient.setColor(amb);

        // Nebel = Horizontfarbe abseits der Sonne
        ColorRGBA fog = horizon(new Vector3f(-sunDir.x, 0.02f, -sunDir.z).normalizeLocal(), sunUp, golden);
        fog = G3.lerp(fog, new ColorRGBA(0.12f, 0.06f, 0.16f, 1), darkZone * 0.6f);
        fogColor.set(fog);

        // Sonne & Mond als Scheiben
        place(sun, sunDir, cam);
        place(moon, moonDir, cam);
        sun.setCullHint(sunElev > -0.08f ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        moon.setCullHint(moonDir.y > -0.05f ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        ColorRGBA sc = G3.lerp(new ColorRGBA(1f, 0.5f, 0.22f, 1), new ColorRGBA(1f, 0.97f, 0.9f, 1), G3.smooth(0f, 0.3f, sunElev));
        sun.getMaterial().setColor("Color", sc);
        starMat.setColor("Color", new ColorRGBA(1, 1, 1, 1).multLocal(G3.smooth(0.45f, 0.95f, night)));
        stars.setCullHint(night > 0.4f ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        stars.setLocalRotation(new Quaternion().fromAngleAxis(time * FastMath.TWO_PI * 0.5f, new Vector3f(0.3f, 1, 0.1f).normalizeLocal()));

        accum += tpf;
        if (accum > 0.1) {
            accum = 0;
            updateDome();
        }
    }

    private void place(Geometry g, Vector3f dir, Camera cam) {
        Vector3f p = dir.mult(radius * 0.9f);
        g.setLocalTranslation(p);
        Quaternion q = new Quaternion();
        q.lookAt(dir.negate(), Vector3f.UNIT_Y);
        g.setLocalRotation(q);
        float w = ((Quad) g.getMesh()).getWidth();
        g.setLocalTranslation(p.add(q.mult(new Vector3f(-w / 2, -w / 2, 0))));
    }

    private ColorRGBA horizon(Vector3f d, float sunUp, float golden) {
        return skyColor(d, sunUp, golden);
    }

    /** Einfaches, künstlerisches Himmelsmodell (Zenit/Horizont-Verlauf + Glühen zur Sonne hin). */
    private ColorRGBA skyColor(Vector3f d, float sunUp, float golden) {
        ColorRGBA zenNight = new ColorRGBA(0.010f, 0.016f, 0.045f, 1), zenDay = new ColorRGBA(0.16f, 0.34f, 0.72f, 1);
        ColorRGBA horNight = new ColorRGBA(0.035f, 0.05f, 0.10f, 1), horDay = new ColorRGBA(0.62f, 0.74f, 0.88f, 1);
        float up = Math.max(d.y, 0);
        float hz = (float) Math.pow(1 - up, 3.2);
        ColorRGBA zen = G3.lerp(zenNight, zenDay, sunUp);
        ColorRGBA hor = G3.lerp(horNight, horDay, sunUp);
        ColorRGBA c = G3.lerp(zen, hor, hz);
        float toSun = Math.max(0, d.dot(sunDir));
        float twilight = (float) Math.exp(-Math.pow(sunElev / 0.22f, 2));
        float glowWide = (float) Math.pow(toSun, 3) * hz * twilight;
        float glowCore = (float) Math.pow(toSun, 24) * twilight;
        c.addLocal(new ColorRGBA(0.95f, 0.38f, 0.12f, 0).multLocal(glowWide * 0.9f));
        c.addLocal(new ColorRGBA(1.0f, 0.62f, 0.30f, 0).multLocal(glowCore * 0.8f));
        // rosa Gegendämmerung auf der sonnenabgewandten Seite
        float anti = Math.max(0, -d.dot(sunDir));
        c.addLocal(new ColorRGBA(0.28f, 0.12f, 0.22f, 0).multLocal(anti * hz * twilight * 0.5f));
        // Tagsüber: helle Aureole um die Sonne
        c.addLocal(new ColorRGBA(0.5f, 0.45f, 0.35f, 0).multLocal((float) Math.pow(toSun, 12) * sunUp * 0.5f));
        float lum = (c.r + c.g + c.b) / 3 * 0.9f;
        c = G3.lerp(c, new ColorRGBA(lum, lum * 1.02f, lum * 1.06f, 1), overcast * 0.8f);
        if (d.y < 0) c = G3.lerp(c, G3.lerp(new ColorRGBA(0.02f, 0.025f, 0.04f, 1), new ColorRGBA(0.3f, 0.34f, 0.38f, 1), sunUp), Math.min(1, -d.y * 4));
        c.a = 1;
        return c;
    }

    private void updateDome() {
        float sunUp = G3.smooth(-0.04f, 0.18f, sunElev);
        float golden = (float) Math.exp(-Math.pow((sunElev - 0.04f) / 0.14f, 2));
        colors.clear();
        for (Vector3f d : dirs) {
            ColorRGBA c = skyColor(d, sunUp, golden);
            colors.put(c.r).put(c.g).put(c.b).put(1f);
        }
        colors.flip();
        domeMesh.getBuffer(VertexBuffer.Type.Color).updateData(colors);
    }
}
