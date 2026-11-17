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
        float h = WorldGen.height(player.x, player.z);
        float forest = WorldGen.forest(player.x, player.z, h, 0);
        leaves.setLocalTranslation(player.x, h, player.z);
        leaves.setParticlesPerSec(forest * 7 + w.rain * 3);
    }
}
