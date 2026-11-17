package daemmerwald;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Kleines Leben rund um die Spielfigur: Schmetterlinge, Libellen und fallendes Laub. */
public class Ambience {
    private final Node root = new Node("ambience");
    private final List<Flyer> flyers = new ArrayList<>();
    private final ParticleEmitter leaves;
    private final Random rnd = new Random();
    private final List<Pixie> pixies = new ArrayList<>();

    /** Kleine leuchtende Feen, die in Schwärmen umherschwirren und eine Funkenspur ziehen. */
    private class Pixie {
        final Node node = new Node("pixie");
        final Node wl = new Node("pwl"), wr = new Node("pwr");
        final ParticleEmitter trail;
        final Vector3f pos = new Vector3f();
        float t = rnd.nextFloat() * 20, phase = rnd.nextFloat() * 6;
        int slot;

        Pixie(int slot) {
            this.slot = slot;
            ColorRGBA[] cols = {new ColorRGBA(0.7f, 0.9f, 1f, 1), new ColorRGBA(1f, 0.8f, 0.95f, 1), new ColorRGBA(0.85f, 1f, 0.75f, 1), new ColorRGBA(1f, 0.92f, 0.6f, 1)};
            ColorRGBA c = cols[slot % cols.length];
            Geometry core = G3.geom("pcore", new com.jme3.scene.shape.Sphere(8, 8, 0.05f), G3.emissive(c));
            core.setLocalScale(0.7f, 1.6f, 0.7f);
            node.attachChild(core);
            Material wm = G3.additive(G3.wingTex(), c.mult(0.5f), false);
            for (int side = -1; side <= 1; side += 2) {
                Geometry q = G3.transparent(G3.geom("pw", new Quad(0.16f, 0.16f), wm));
                if (side < 0) q.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y));
                (side < 0 ? wl : wr).attachChild(q);
            }
            wl.setLocalTranslation(0, 0.03f, -0.02f);
            wr.setLocalTranslation(0, 0.03f, -0.02f);
            node.attachChild(wl);
            node.attachChild(wr);
            trail = G3.particles("pixieTrail", 30, null, c.clone().setAlpha(0.9f), new ColorRGBA(c.r, c.g, c.b, 0), 0.06f, 0.01f, 0.6f, 1.2f);
            trail.setParticlesPerSec(18);
            trail.setShape(new com.jme3.effect.shapes.EmitterSphereShape(Vector3f.ZERO, 0.02f));
            trail.getParticleInfluencer().setVelocityVariation(0.4f);
            trail.setInWorldSpace(true);
            node.attachChild(trail);
            node.setCullHint(Spatial.CullHint.Always);
            root.attachChild(node);
        }

        void update(float dt, Vector3f center, boolean on) {
            t += dt;
            node.setCullHint(on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            trail.setParticlesPerSec(on ? 18 : 0);
            if (!on) return;
            float a = t * (0.6f + slot * 0.05f) + phase;
            Vector3f target = center.add(FastMath.cos(a) * (3 + slot % 3), 1.6f + FastMath.sin(t * 1.7f + slot) * 0.8f, FastMath.sin(a * 1.3f) * (3 + slot % 4));
            Vector3f d = target.subtract(pos);
            if (d.length() > 25) pos.set(target);
            pos.addLocal(d.multLocal(Math.min(1, dt * 2.5f)));
            node.setLocalTranslation(pos);
            Vector3f dir = new Vector3f(-FastMath.sin(a), 0, FastMath.cos(a * 1.3f));
            node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.atan2(dir.x, dir.z), Vector3f.UNIT_Y));
            float flap = FastMath.sin(t * 40) * 0.6f;
            wl.setLocalRotation(new Quaternion().fromAngleAxis(flap, Vector3f.UNIT_Z));
            wr.setLocalRotation(new Quaternion().fromAngleAxis(-flap, Vector3f.UNIT_Z));
        }
    }

    private final Vector3f swarmCenter = new Vector3f();
    private float swarmT;

    private class Flyer {
        final Node node = new Node("flyer");
        final Node wl = new Node("wl"), wr = new Node("wr");
        final boolean dragonfly;
        final Vector3f pos = new Vector3f(), home = new Vector3f();
        float t = rnd.nextFloat() * 10, speed;
        boolean active;

        Flyer(boolean dragonfly) {
            this.dragonfly = dragonfly;
            ColorRGBA[] cols = {new ColorRGBA(1f, 0.65f, 0.15f, 1), new ColorRGBA(0.35f, 0.6f, 1f, 1), new ColorRGBA(1f, 0.95f, 0.5f, 1),
                new ColorRGBA(0.95f, 0.45f, 0.75f, 1), new ColorRGBA(1, 1, 1, 1)};
            ColorRGBA c = dragonfly ? new ColorRGBA(0.6f, 0.85f, 1f, 1) : cols[rnd.nextInt(cols.length)];
            Material m = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap", G3.wingTex());
            m.setColor("Color", dragonfly ? c.mult(0.6f).setAlpha(0.6f) : c);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            float s = dragonfly ? 0.09f : 0.13f;
            Geometry ql = G3.transparent(G3.geom("w", new Quad(s, s), m));
            Geometry qr = G3.transparent(G3.geom("w", new Quad(s, s), m));
            ql.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y));
            wl.attachChild(ql);
            wr.attachChild(qr);
            node.attachChild(wl);
            node.attachChild(wr);
            Geometry body = G3.geom("body", new com.jme3.scene.shape.Box(0.008f, 0.008f, dragonfly ? 0.07f : 0.03f),
                G3.lit(dragonfly ? new ColorRGBA(0.1f, 0.5f, 0.6f, 1) : new ColorRGBA(0.1f, 0.08f, 0.06f, 1)));
            node.attachChild(body);
            node.setCullHint(Spatial.CullHint.Always);
            root.attachChild(node);
            speed = dragonfly ? 4 : 1.2f;
        }

        void update(float dt, Vector3f player, boolean allowed) {
            t += dt;
            if (!allowed) {
                active = false;
                node.setCullHint(Spatial.CullHint.Always);
                return;
            }
            if (!active || pos.distance(player) > 30) {
                // Neuen Platz in der Nähe suchen
                for (int i = 0; i < 6; i++) {
                    float a = rnd.nextFloat() * FastMath.TWO_PI, d = 5 + rnd.nextFloat() * 18;
                    float x = player.x + FastMath.cos(a) * d, z = player.z + FastMath.sin(a) * d;
                    float h = WorldGen.height(x, z);
                    boolean water = h < WorldGen.WATER + 0.3f && h > WorldGen.WATER - 4;
                    if (dragonfly != water) continue;
                    if (!dragonfly && (h < WorldGen.WATER + 0.3f || WorldGen.forest(x, z, h, 0) > 0.6f)) continue;
                    home.set(x, Math.max(h, WorldGen.WATER) + (dragonfly ? 0.6f : 0.7f), z);
                    pos.set(home);
                    active = true;
                    node.setCullHint(Spatial.CullHint.Inherit);
                    break;
                }
                if (!active) { node.setCullHint(Spatial.CullHint.Always); return; }
            }
            Vector3f target = home.add(FastMath.sin(t * 0.7f) * 2.5f, FastMath.sin(t * 1.3f) * 0.5f + 0.4f, FastMath.cos(t * 0.53f) * 2.5f);
            if (dragonfly && ((int) (t * 1.5f)) % 3 == 0) target.addLocal(FastMath.sin(t * 9) * 0.3f, 0, 0);
            Vector3f dir = target.subtract(pos);
            Vector3f step = dir.mult(Math.min(1, dt * speed));
            pos.addLocal(step);
            node.setLocalTranslation(pos);
            if (step.lengthSquared() > 1e-6f) node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.atan2(step.x, step.z), Vector3f.UNIT_Y));
            float flap = dragonfly ? FastMath.sin(t * 60) * 0.25f : FastMath.sin(t * 14) * 0.9f;
            wl.setLocalRotation(new Quaternion().fromAngleAxis(flap, Vector3f.UNIT_Z));
            wr.setLocalRotation(new Quaternion().fromAngleAxis(-flap, Vector3f.UNIT_Z));
        }
    }

    public Ambience(Node world) {
        world.attachChild(root);
        for (int i = 0; i < 12; i++) flyers.add(new Flyer(false));
        for (int i = 0; i < 4; i++) flyers.add(new Flyer(true));
        for (int i = 0; i < 9; i++) pixies.add(new Pixie(i));
        leaves = new ParticleEmitter("fallingLeaves", com.jme3.effect.ParticleMesh.Type.Triangle, 120);
        Material lm = new Material(G3.am, "Common/MatDefs/Misc/Particle.j3md");
        lm.setTexture("Texture", G3.leafTex());
        lm.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        leaves.setMaterial(lm);
        leaves.setImagesX(1);
        leaves.setImagesY(1);
        leaves.setStartColor(new ColorRGBA(0.55f, 0.65f, 0.25f, 1));
        leaves.setEndColor(new ColorRGBA(0.75f, 0.45f, 0.15f, 0.9f));
        leaves.setStartSize(0.09f);
        leaves.setEndSize(0.08f);
        leaves.setLowLife(6);
        leaves.setHighLife(10);
        leaves.setGravity(0, 0.25f, 0);
        leaves.setRotateSpeed(2.5f);
        leaves.setRandomAngle(true);
        leaves.setShape(new EmitterBoxShape(new Vector3f(-22, 5, -22), new Vector3f(22, 11, 22)));
        leaves.getParticleInfluencer().setInitialVelocity(new Vector3f(0.4f, -0.2f, 0.15f));
        leaves.getParticleInfluencer().setVelocityVariation(0.6f);
        leaves.setInWorldSpace(true);
        leaves.setShadowMode(RenderQueue.ShadowMode.Off);
        leaves.setQueueBucket(RenderQueue.Bucket.Transparent);
        root.attachChild(leaves);
    }

    public void update(float dt, Vector3f player, DayNight dn, Weather w) {
        boolean day = dn.night < 0.45f && w.rain < 0.3f;
        for (Flyer f : flyers) f.update(dt, player, day);
        // Feenschwarm: in der Dämmerung, nachts und an hellen Orten
        float bright = WorldGen.brightness(player.x, player.z);
        boolean pixiesOn = (dn.night > 0.3f || bright > 0.35f) && w.rain < 0.5f && WorldGen.darkness(player.x, player.z) < 0.4f;
        swarmT -= dt;
        if (swarmT <= 0 || swarmCenter.distance(player) > 40) {
            float a = rnd.nextFloat() * FastMath.TWO_PI, d = 8 + rnd.nextFloat() * 10;
            swarmCenter.set(player.x + FastMath.cos(a) * d, 0, player.z + FastMath.sin(a) * d);
            swarmCenter.y = Math.max(WorldGen.height(swarmCenter.x, swarmCenter.z), WorldGen.WATER);
            swarmT = 25 + rnd.nextFloat() * 20;
        }
        for (Pixie p : pixies) p.update(dt, swarmCenter, pixiesOn);
        float h = WorldGen.height(player.x, player.z);
        float forest = WorldGen.forest(player.x, player.z, h, 0);
        leaves.setLocalTranslation(player.x, h, player.z);
        leaves.setParticlesPerSec(forest * 7 + w.rain * 3);
    }
}
