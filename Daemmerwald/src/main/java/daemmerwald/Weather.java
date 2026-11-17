package daemmerwald;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import com.jme3.util.BufferUtils;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Random;

/** Wolken, Regen und Regenbogen. Wechselt von selbst zwischen Sonne, Bewölkung und Regen. */
public class Weather {
    public float cover = 0.25f, rain = 0, rainbow = 0;
    private float targetCover = 0.25f, targetRain = 0, timer = 70;
    /** Im Café wechselt das Wetter schneller, damit man mehr zu sehen bekommt. */
    public float speed = 1;
    public String forced;

    private final Random rnd = new Random();
    private final Geometry clouds, rainbowGeo, rainGeo;
    private final Material cloudMat, rainbowMat, rainMat;
    private final Mesh rainMesh;
    private final int drops = 2400;
    private final float[] dx = new float[drops], dy = new float[drops], dz = new float[drops];
    private final FloatBuffer rainPos = BufferUtils.createFloatBuffer(drops * 6);
    private float wind;

    public Weather(Node root) {
        // Wolkendecke
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < 512; x++) {
                double n = 0, amp = 0.5, f = 1.0 / 128, norm = 0;
                for (int o = 0; o < 5; o++) {
                    // kachelbar: Rauschen auf einem Torus
                    double ax = x * f, ay = y * f;
                    int period = (int) Math.round(512 * f);
                    n += tileNoise(ax, ay, period) * amp;
                    norm += amp;
                    amp *= 0.5;
                    f *= 2;
                }
                n /= norm;
                double a = Math.max(0, Math.min(1, (n - 0.38) * 2.6));
                int shade = (int) (215 + 40 * n);
                img.setRGB(x, y, ((int) (a * 255) << 24) | (Math.min(255, shade) << 16) | (Math.min(255, shade) << 8) | Math.min(255, shade + 8));
            }
        }
        com.jme3.texture.Texture2D ct = G3.tex(img);
        ct.setWrap(com.jme3.texture.Texture.WrapMode.Repeat);
        Quad q = new Quad(4000, 4000);
        q.scaleTextureCoordinates(new Vector2f(8, 8));
        cloudMat = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        cloudMat.setTexture("ColorMap", ct);
        cloudMat.setColor("Color", ColorRGBA.White);
        cloudMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        cloudMat.getAdditionalRenderState().setDepthWrite(false);
        cloudMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        clouds = G3.transparent(G3.geom("clouds", q, cloudMat));
        clouds.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        root.attachChild(clouds);

        // Regenbogen: Ring mit 42° Winkelradius
        BufferedImage rb = new BufferedImage(64, 4, BufferedImage.TYPE_INT_ARGB);
        java.awt.Color[] spec = {new java.awt.Color(120, 60, 200), new java.awt.Color(60, 90, 255), new java.awt.Color(60, 200, 120),
            new java.awt.Color(250, 240, 80), new java.awt.Color(255, 150, 40), new java.awt.Color(240, 50, 40)};
        for (int x = 0; x < 64; x++) {
            double t = x / 63.0 * (spec.length - 1);
            int i = (int) Math.min(spec.length - 2, t);
            double f = t - i;
            java.awt.Color a = spec[i], b = spec[i + 1];
            double edge = Math.sin(Math.PI * x / 63.0);
            int al = (int) (edge * 200);
            int c = (al << 24) | ((int) (a.getRed() + (b.getRed() - a.getRed()) * f) << 16)
                | ((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f) << 8) | (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f);
            for (int y = 0; y < 4; y++) rb.setRGB(x, y, c);
        }
        rainbowMat = G3.additive(G3.tex(rb), new ColorRGBA(1, 1, 1, 1), false);
        rainbowGeo = G3.geom("rainbow", ring(580, 42, 3.2f, 64), rainbowMat);
        rainbowGeo.setQueueBucket(RenderQueue.Bucket.Sky);
        rainbowGeo.setShadowMode(RenderQueue.ShadowMode.Off);
        rainbowGeo.setCullHint(Spatial.CullHint.Always);
        root.attachChild(rainbowGeo);

        // Regen als Linien
        rainMesh = new Mesh();
        rainMesh.setMode(Mesh.Mode.Lines);
        rainMesh.setBuffer(VertexBuffer.Type.Position, 3, rainPos);
        rainMat = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
        rainMat.setColor("Color", new ColorRGBA(0.75f, 0.8f, 0.9f, 0.32f));
        rainMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        rainMat.getAdditionalRenderState().setDepthWrite(false);
        rainGeo = G3.transparent(G3.geom("rain", rainMesh, rainMat));
        rainGeo.setCullHint(Spatial.CullHint.Never);
        root.attachChild(rainGeo);
        for (int i = 0; i < drops; i++) { dx[i] = rnd.nextFloat() * 70 - 35; dy[i] = rnd.nextFloat() * 30; dz[i] = rnd.nextFloat() * 70 - 35; }
    }

    private static double tileNoise(double x, double y, int period) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int x0 = Math.floorMod(ix, period), x1 = Math.floorMod(ix + 1, period), y0 = Math.floorMod(iy, period), y1 = Math.floorMod(iy + 1, period);
        double a = G3.hash(x0, y0 + 77), b = G3.hash(x1, y0 + 77), c = G3.hash(x0, y1 + 77), d = G3.hash(x1, y1 + 77);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    private static Mesh ring(float dist, float angleDeg, float widthDeg, int segs) {
        float rIn = dist * FastMath.tan((angleDeg - widthDeg / 2) * FastMath.DEG_TO_RAD);
        float rOut = dist * FastMath.tan((angleDeg + widthDeg / 2) * FastMath.DEG_TO_RAD);
        float[] pos = new float[(segs + 1) * 2 * 3];
        float[] uv = new float[(segs + 1) * 2 * 2];
        short[] idx = new short[segs * 6];
        for (int i = 0; i <= segs; i++) {
            float a = FastMath.PI * i / segs;
            pos[i * 6] = FastMath.cos(a) * rIn; pos[i * 6 + 1] = FastMath.sin(a) * rIn; pos[i * 6 + 2] = 0;
            pos[i * 6 + 3] = FastMath.cos(a) * rOut; pos[i * 6 + 4] = FastMath.sin(a) * rOut; pos[i * 6 + 5] = 0;
            uv[i * 4] = 0; uv[i * 4 + 1] = 0.5f; uv[i * 4 + 2] = 1; uv[i * 4 + 3] = 0.5f;
        }
        for (int i = 0; i < segs; i++) {
            int a = i * 2;
            idx[i * 6] = (short) a; idx[i * 6 + 1] = (short) (a + 1); idx[i * 6 + 2] = (short) (a + 2);
            idx[i * 6 + 3] = (short) (a + 2); idx[i * 6 + 4] = (short) (a + 1); idx[i * 6 + 5] = (short) (a + 3);
        }
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();
        return m;
    }

    public String describe() {
        if (rain > 0.4f) return "Regen";
        if (rainbow > 0.2f) return "Regenbogen";
        if (cover > 0.6f) return "Bewölkt";
        return cover > 0.3f ? "Heiter" : "Klar";
    }

    public void update(float tpf, Camera cam, DayNight dn, boolean indoors) {
        timer -= tpf * speed;
        if (forced != null) {
            switch (forced) {
                case "rain" -> { targetCover = 0.95f; targetRain = 1; }
                case "clear" -> { targetCover = 0.15f; targetRain = 0; }
                default -> { targetCover = 0.6f; targetRain = 0; }
            }
        } else if (timer <= 0) {
            float r = rnd.nextFloat();
            if (r < 0.5f) { targetCover = 0.1f + rnd.nextFloat() * 0.25f; targetRain = 0; }
            else if (r < 0.75f) { targetCover = 0.55f + rnd.nextFloat() * 0.3f; targetRain = 0; }
            else { targetCover = 0.95f; targetRain = 0.6f + rnd.nextFloat() * 0.4f; }
            timer = 60 + rnd.nextFloat() * 120;
        }
        float k = Math.min(1, tpf * 0.08f * speed);
        float before = rain;
        cover += (targetCover - cover) * k;
        rain += (Math.min(targetRain, cover) - rain) * k * 1.4f;
        if (before > 0.35f && rain <= 0.35f && targetRain == 0) rainbow = 1;
        rainbow = Math.max(0, rainbow - tpf * speed / 70f);
        wind += tpf * 6;

        Vector3f c = cam.getLocation();
        float period = 500;
        float ox = (float) (Math.floor(c.x / period) * period) + wind % period;
        float oz = (float) (Math.floor(c.z / period) * period);
        clouds.setLocalTranslation(ox - 2000, c.y + 320, oz - 2000);
        float light = 0.25f + 0.75f * (1 - dn.night);
        ColorRGBA cc = G3.lerp(new ColorRGBA(light, light * 0.98f, light * 0.96f, 1),
            new ColorRGBA(light * 0.55f, light * 0.57f, light * 0.62f, 1), rain);
        if (dn.sunElev > -0.1f && dn.sunElev < 0.2f) cc.addLocal(new ColorRGBA(0.35f, 0.15f, 0.05f, 0).multLocal(1 - Math.abs(dn.sunElev - 0.05f) / 0.15f));
        cc.a = Math.min(1, 0.15f + cover * 0.95f);
        cloudMat.setColor("Color", cc);

        // Regenbogen gegenüber der Sonne
        float sunE = dn.sunDir.y;
        boolean showBow = rainbow > 0.01f && sunE > 0.02f && sunE < 0.6f;
        rainbowGeo.setCullHint(showBow ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (showBow) {
            Vector3f anti = dn.sunDir.negate();
            Vector3f flat = new Vector3f(anti.x, 0, anti.z).normalizeLocal();
            Vector3f center = c.add(anti.mult(580));
            rainbowGeo.setLocalTranslation(center);
            Quaternion q = new Quaternion();
            q.lookAt(flat.negate(), Vector3f.UNIT_Y);
            rainbowGeo.setLocalRotation(q);
            float a = Math.min(1, rainbow * 2.5f) * (1 - cover * 0.4f) * 0.55f;
            rainbowMat.setColor("Color", new ColorRGBA(a, a, a, 1));
        }

        // Regen
        boolean showRain = rain > 0.03f;
        Vector3f rc = c;
        if (indoors) {
            Vector3f fd = cam.getDirection().clone().setY(0).normalizeLocal();
            rc = c.add(fd.mult(42));
        }
        rainGeo.setCullHint(showRain ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (showRain) {
            int active = (int) (drops * Math.min(1, rain));
            rainPos.clear();
            for (int i = 0; i < drops; i++) {
                dy[i] -= tpf * 24;
                if (dy[i] < -6) { dy[i] = 24 + rnd.nextFloat() * 6; dx[i] = rnd.nextFloat() * 70 - 35; dz[i] = rnd.nextFloat() * 70 - 35; }
                float x = rc.x + dx[i], y = c.y + dy[i], z = rc.z + dz[i];
                if (i >= active) { y = -9999; }
                rainPos.put(x).put(y).put(z).put(x + 0.15f).put(y - 0.9f).put(z);
            }
            rainPos.flip();
            rainMesh.getBuffer(VertexBuffer.Type.Position).updateData(rainPos);
            rainMesh.updateBound();
            rainMat.setColor("Color", new ColorRGBA(0.7f, 0.75f, 0.85f, 0.18f + 0.22f * (1 - dn.night)));
        }
    }
}
