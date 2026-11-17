package daemmerwald;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Lebendige Fernsicht: springende Wale im Meer, Vogelschwärme, ein gleitender Himmelsrochen. */
public class Vista {
    public float rate = 1;
    private final Node root = new Node("vista");
    private final Random rnd = new Random();
    private final Node whale, manta;
    private final List<Node> birds = new ArrayList<>();
    private final Node mantaWingL, mantaWingR;
    private final List<ParticleEmitter> splashes = new ArrayList<>();
    private float whaleT = -1, birdsT = -1, mantaT = -1;
    private float nextWhale = 20, nextBirds = 12, nextManta = 50;
    private final Vector3f whaleStart = new Vector3f(), whaleDir = new Vector3f();
    private final Vector3f birdStart = new Vector3f(), birdDir = new Vector3f();
    private final Vector3f mantaStart = new Vector3f(), mantaDir = new Vector3f();
    private final Material mantaSpots;
    private final Node hawk = new Node("hawk");
    private final Node hawkL = new Node("hl"), hawkR = new Node("hr");
    private float hawkT;

    public Vista(Node world) {
        world.attachChild(root);
        // Wal
        whale = new Node("whale");
        Material skin = G3.lit(new ColorRGBA(0.16f, 0.2f, 0.26f, 1));
        skin.setColor("Specular", new ColorRGBA(0.5f, 0.5f, 0.55f, 1));
        skin.setFloat("Shininess", 30);
        Geometry body = G3.geom("whaleBody", new Sphere(16, 20, 1), skin);
        body.setLocalScale(2.2f, 1.9f, 10);
        whale.attachChild(body);
        Geometry belly = G3.geom("belly", new Sphere(12, 16, 1), G3.lit(new ColorRGBA(0.55f, 0.55f, 0.55f, 1)));
        belly.setLocalScale(1.7f, 1.2f, 8);
        belly.setLocalTranslation(0, -0.8f, 0.5f);
        whale.attachChild(belly);
        for (int side = -1; side <= 1; side += 2) {
            Geometry fluke = G3.geom("fluke", new Box(2.4f, 0.12f, 1.1f), skin);
            fluke.setLocalTranslation(side * 1.9f, 0, -10.5f);
            fluke.setLocalRotation(new Quaternion().fromAngleAxis(side * 0.3f, Vector3f.UNIT_Y));
            whale.attachChild(fluke);
            Geometry fin = G3.geom("fin", new Box(2.5f, 0.1f, 0.7f), skin);
            fin.setLocalTranslation(side * 2.8f, -0.6f, 4);
            fin.setLocalRotation(new Quaternion().fromAngleAxis(side * -0.3f, Vector3f.UNIT_Z));
            whale.attachChild(fin);
        }
        whale.setCullHint(Spatial.CullHint.Always);
        root.attachChild(whale);

        // Vögel
        Material bm = G3.lit(new ColorRGBA(0.08f, 0.08f, 0.09f, 1));
        for (int i = 0; i < 11; i++) {
            Node b = new Node("bird");
            Geometry bb = G3.geom("bbody", new Box(0.08f, 0.06f, 0.25f), bm);
            b.attachChild(bb);
            for (int side = -1; side <= 1; side += 2) {
                Node wn = new Node("wing" + side);
                Geometry w = G3.geom("bwing", new Box(0.45f, 0.015f, 0.12f), bm);
                w.setLocalTranslation(side * 0.45f, 0, 0);
                wn.attachChild(w);
                b.attachChild(wn);
            }
            b.setCullHint(Spatial.CullHint.Always);
            b.setShadowMode(RenderQueue.ShadowMode.Off);
            root.attachChild(b);
            birds.add(b);
        }

        // Himmelsrochen
        manta = new Node("manta");
        Material mm = G3.lit(new ColorRGBA(0.12f, 0.16f, 0.3f, 1));
        mm.setColor("Specular", new ColorRGBA(0.4f, 0.5f, 0.7f, 1));
        mm.setFloat("Shininess", 20);
        Geometry mb = G3.geom("mbody", new Sphere(10, 14, 1), mm);
        mb.setLocalScale(2.2f, 0.6f, 4.5f);
        manta.attachChild(mb);
        mantaSpots = G3.emissive(new ColorRGBA(0.4f, 0.8f, 1f, 1));
        mantaWingL = new Node("wl");
        mantaWingR = new Node("wr");
        for (int side = -1; side <= 1; side += 2) {
            Node wn = side < 0 ? mantaWingL : mantaWingR;
            Geometry w = G3.geom("mwing", new Sphere(8, 12, 1), mm);
            w.setLocalScale(6, 0.25f, 3);
            w.setLocalTranslation(side * 5.5f, 0, 0.5f);
            wn.attachChild(w);
            for (int k = 0; k < 4; k++) {
                Geometry s = G3.geom("spot", new Sphere(4, 5, 0.25f), mantaSpots);
                s.setLocalTranslation(side * (3 + k * 2f), 0.28f, 0.5f - k * 0.3f);
                wn.attachChild(s);
            }
            manta.attachChild(wn);
        }
        Geometry tail = G3.geom("tail", new Box(0.12f, 0.08f, 6), mm);
        tail.setLocalTranslation(0, 0, -9);
        manta.attachChild(tail);
        manta.setCullHint(Spatial.CullHint.Always);
        root.attachChild(manta);

        // Greifvogel, der tagsüber hoch oben kreist
        Material feather = G3.lit(new ColorRGBA(0.32f, 0.22f, 0.14f, 1));
        Material light = G3.lit(new ColorRGBA(0.85f, 0.8f, 0.7f, 1));
        Geometry hb = G3.geom("hawkBody", new Sphere(8, 10, 1), feather);
        hb.setLocalScale(0.18f, 0.16f, 0.55f);
        hawk.attachChild(hb);
        Geometry hh = G3.geom("hawkHead", new Sphere(8, 8, 0.13f), light);
        hh.setLocalTranslation(0, 0.05f, 0.52f);
        hawk.attachChild(hh);
        for (int side = -1; side <= 1; side += 2) {
            Node wn = side < 0 ? hawkL : hawkR;
            for (int k = 0; k < 3; k++) {
                Geometry seg = G3.geom("hawkWing", new Box(0.32f, 0.02f, 0.22f - k * 0.05f), feather);
                seg.setLocalTranslation(side * (0.32f + k * 0.6f), -k * 0.03f, -k * 0.06f);
                wn.attachChild(seg);
            }
            hawk.attachChild(wn);
        }
        Geometry hawkTail = G3.geom("hawkTail", new Box(0.18f, 0.02f, 0.25f), feather);
        hawkTail.setLocalTranslation(0, 0, -0.65f);
        hawk.attachChild(hawkTail);
        hawk.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(hawk);
    }

    private void splash(Vector3f p) {
        ParticleEmitter e = G3.particles("splash", 80, "Effects/Smoke/Smoke.png", new ColorRGBA(0.9f, 0.95f, 1f, 0.6f), new ColorRGBA(1, 1, 1, 0), 1.5f, 4f, 1.2f, 2.4f);
        e.setImagesX(15);
        e.getMaterial().getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        e.setShape(new EmitterSphereShape(Vector3f.ZERO, 2.5f));
        e.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 9, 0));
        e.getParticleInfluencer().setVelocityVariation(0.5f);
        e.setGravity(0, 9, 0);
        e.setParticlesPerSec(0);
        e.setLocalTranslation(p.x, WorldGen.WATER, p.z);
        root.attachChild(e);
        e.emitAllParticles();
        e.setUserData("life", 3f);
        splashes.add(e);
    }

    /** Sucht eine Meeresstelle in Blickrichtung. */
    private boolean seaSpot(Camera cam, Vector3f out) {
        Vector3f dir = cam.getDirection().clone();
        dir.y = 0;
        dir.normalizeLocal();
        for (int i = 0; i < 20; i++) {
            float a = (rnd.nextFloat() - 0.5f) * 1.1f, d = 200 + rnd.nextFloat() * 260;
            Vector3f d2 = new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y).mult(dir);
            Vector3f p = cam.getLocation().add(d2.mult(d));
            if (WorldGen.sea(p.x, p.z) > 0.97 && WorldGen.height(p.x, p.z) < -8) {
                out.set(p);
                return true;
            }
        }
        return false;
    }

    public void update(float tpf, Camera cam, DayNight dn) {
        float k = tpf * rate;
        nextWhale -= k;
        nextBirds -= k;
        nextManta -= k;
        Vector3f c = cam.getLocation();

        // Greifvogel kreist über dem Spieler (tagsüber)
        hawkT += tpf;
        boolean hawkOn = dn.night < 0.5f;
        hawk.setCullHint(hawkOn ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (hawkOn) {
            float a = hawkT * 0.16f;
            Vector3f hp = new Vector3f(c.x + FastMath.cos(a) * 55, c.y + 48 + FastMath.sin(hawkT * 0.3f) * 6, c.z + FastMath.sin(a) * 55);
            hawk.setLocalTranslation(hp);
            Vector3f dir = new Vector3f(-FastMath.sin(a), 0, FastMath.cos(a));
            Quaternion q = new Quaternion();
            q.lookAt(dir, Vector3f.UNIT_Y);
            hawk.setLocalRotation(q.mult(new Quaternion().fromAngleAxis(0.35f, Vector3f.UNIT_Z)));
            float flap = (hawkT % 9) < 1.5f ? FastMath.sin(hawkT * 9) * 0.45f : 0.06f;
            hawkL.setLocalRotation(new Quaternion().fromAngleAxis(flap, Vector3f.UNIT_Z));
            hawkR.setLocalRotation(new Quaternion().fromAngleAxis(-flap, Vector3f.UNIT_Z));
        }

        // Wal
        if (whaleT < 0 && nextWhale <= 0) {
            nextWhale = 30 + rnd.nextFloat() * 50;
            if (seaSpot(cam, whaleStart)) {
                whaleT = 0;
                float a = rnd.nextFloat() * FastMath.TWO_PI;
                whaleDir.set(FastMath.cos(a), 0, FastMath.sin(a));
                whale.setCullHint(Spatial.CullHint.Inherit);
                splash(whaleStart);
            }
        }
        if (whaleT >= 0) {
            whaleT += tpf;
            float dur = 5.5f, f = whaleT / dur;
            Vector3f p = whaleStart.add(whaleDir.mult(f * 26));
            float y = WorldGen.WATER - 8 + FastMath.sin(f * FastMath.PI) * 15;
            whale.setLocalTranslation(p.x, y, p.z);
            Quaternion yaw = new Quaternion();
            yaw.lookAt(whaleDir, Vector3f.UNIT_Y);
            float pitch = -FastMath.cos(f * FastMath.PI) * 0.9f;
            whale.setLocalRotation(yaw.mult(new Quaternion().fromAngleAxis(pitch, Vector3f.UNIT_X)));
            whale.setLocalRotation(whale.getLocalRotation().mult(new Quaternion().fromAngleAxis(f * 1.6f, Vector3f.UNIT_Z)));
            if (whaleT > dur * 0.8f && whale.getUserData("splashed") == null) {
                whale.setUserData("splashed", true);
                splash(p);
            }
            if (whaleT > dur) {
                whaleT = -1;
                whale.setUserData("splashed", null);
                whale.setCullHint(Spatial.CullHint.Always);
            }
        }

        // Vögel (tagsüber)
        if (birdsT < 0 && nextBirds <= 0 && dn.night < 0.6f) {
            nextBirds = 20 + rnd.nextFloat() * 35;
            Vector3f fwd = cam.getDirection().clone().setY(0).normalizeLocal();
            Vector3f side = fwd.cross(Vector3f.UNIT_Y).normalizeLocal();
            float dist = 60 + rnd.nextFloat() * 120;
            int s = rnd.nextBoolean() ? 1 : -1;
            birdStart.set(c.add(fwd.mult(dist)).add(side.mult(-s * 160)));
            birdStart.y = c.y + 18 + rnd.nextFloat() * 30;
            birdDir.set(side.mult(s)).addLocal(fwd.mult((rnd.nextFloat() - 0.5f) * 0.4f)).normalizeLocal();
            birdsT = 0;
            for (Node b : birds) b.setCullHint(Spatial.CullHint.Inherit);
        }
        if (birdsT >= 0) {
            birdsT += tpf;
            Vector3f right = birdDir.cross(Vector3f.UNIT_Y).normalizeLocal();
            for (int i = 0; i < birds.size(); i++) {
                Node b = birds.get(i);
                int row = (i + 1) / 2, sideSign = i % 2 == 0 ? 1 : -1;
                Vector3f off = birdDir.mult(-row * 2.2f).add(right.mult(sideSign * row * 2.0f));
                Vector3f p = birdStart.add(birdDir.mult(birdsT * 13)).add(off);
                p.y += FastMath.sin(birdsT * 0.8f + i) * 0.8f;
                b.setLocalTranslation(p);
                Quaternion q = new Quaternion();
                q.lookAt(birdDir, Vector3f.UNIT_Y);
                b.setLocalRotation(q);
                float flap = FastMath.sin(birdsT * 9 + i * 0.7f) * 0.6f;
                b.getChild("wing-1").setLocalRotation(new Quaternion().fromAngleAxis(-flap, Vector3f.UNIT_Z));
                b.getChild("wing1").setLocalRotation(new Quaternion().fromAngleAxis(flap, Vector3f.UNIT_Z));
            }
            if (birdsT > 26) {
                birdsT = -1;
                for (Node b : birds) b.setCullHint(Spatial.CullHint.Always);
            }
        }

        // Himmelsrochen
        if (mantaT < 0 && nextManta <= 0) {
            nextManta = 70 + rnd.nextFloat() * 80;
            Vector3f fwd = cam.getDirection().clone().setY(0).normalizeLocal();
            Vector3f side = fwd.cross(Vector3f.UNIT_Y).normalizeLocal();
            int s = rnd.nextBoolean() ? 1 : -1;
            mantaStart.set(c.add(fwd.mult(170 + rnd.nextFloat() * 120)).add(side.mult(-s * 260)));
            mantaStart.y = c.y + 45 + rnd.nextFloat() * 40;
            mantaDir.set(side.mult(s)).normalizeLocal();
            mantaT = 0;
            manta.setCullHint(Spatial.CullHint.Inherit);
        }
        if (mantaT >= 0) {
            mantaT += tpf;
            Vector3f p = mantaStart.add(mantaDir.mult(mantaT * 10));
            p.y += FastMath.sin(mantaT * 0.4f) * 4;
            manta.setLocalTranslation(p);
            Quaternion q = new Quaternion();
            q.lookAt(mantaDir, Vector3f.UNIT_Y);
            manta.setLocalRotation(q.mult(new Quaternion().fromAngleAxis(FastMath.sin(mantaT * 0.5f) * 0.15f, Vector3f.UNIT_Z)));
            float flap = FastMath.sin(mantaT * 1.6f) * 0.35f;
            mantaWingL.setLocalRotation(new Quaternion().fromAngleAxis(-flap, Vector3f.UNIT_Z));
            mantaWingR.setLocalRotation(new Quaternion().fromAngleAxis(flap, Vector3f.UNIT_Z));
            mantaSpots.setColor("Color", new ColorRGBA(0.4f, 0.8f, 1f, 1).multLocal(0.3f + 0.7f * dn.night));
            mantaSpots.setColor("GlowColor", new ColorRGBA(0.4f, 0.8f, 1f, 1).multLocal(0.1f + 0.9f * dn.night));
            if (mantaT > 55) {
                mantaT = -1;
                manta.setCullHint(Spatial.CullHint.Always);
            }
        }

        for (int i = splashes.size() - 1; i >= 0; i--) {
            ParticleEmitter e = splashes.get(i);
            float life = (Float) e.getUserData("life") - tpf;
            e.setUserData("life", life);
            if (life <= 0) { e.removeFromParent(); splashes.remove(i); }
        }
    }
}
