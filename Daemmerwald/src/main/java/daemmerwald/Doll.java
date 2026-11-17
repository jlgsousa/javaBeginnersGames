package daemmerwald;

import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;

/**
 * Liebevoll gestaltete, animierte Puppen-Figur für Trolle und Feen:
 * große glänzende Augen, Wimpern, rosige Wangen, Lächeln, verschiedene Frisuren,
 * Kleider oder Hosen. Füße bei y=0, Blick nach +Z.
 */
public class Doll {
    public final Node root = new Node("doll");
    private final Node pelvis = new Node("pelvis"), torso = new Node("torso"), head = new Node("head");
    private final Node legL = new Node("legL"), legR = new Node("legR"), armL = new Node("armL"), armR = new Node("armR");
    private final Node lidL = new Node("lidL"), lidR = new Node("lidR"), hairSway = new Node("hairSway");
    private final float H, hipY, hr;
    private final boolean troll;
    private float phase, blinkT = 2, blink;

    private final Material skin, skinDark, cloth, clothDark, hairM, eyeWhite, iris, pupil, shine, blush, boots, accent, mouth;

    public Doll(Look l, float height) {
        this.H = height;
        this.troll = l.kind == Look.TROLL;
        ColorRGBA sk = G3.rgb(l.skin).multLocal(troll ? 1.18f : 1.06f);
        if (troll) sk.addLocal(new ColorRGBA(0.04f, 0.06f, 0.03f, 0));
        skin = mat(sk, 0.15f, 10);
        skinDark = mat(sk.mult(0.85f), 0.1f, 8);
        ColorRGBA cl = G3.rgb(l.cloth).multLocal(1.5f).addLocal(new ColorRGBA(0.06f, 0.06f, 0.06f, 0));
        cloth = mat(cl, 0.12f, 12);
        clothDark = mat(cl.mult(0.6f), 0.1f, 10);
        hairM = mat(G3.rgb(l.hair).multLocal(1.3f).addLocal(new ColorRGBA(0.04f, 0.03f, 0.02f, 0)), 0.45f, 30);
        eyeWhite = mat(new ColorRGBA(0.97f, 0.96f, 0.94f, 1), 0.6f, 60);
        iris = mat(G3.rgb(l.accent), 0.7f, 70);
        pupil = mat(new ColorRGBA(0.03f, 0.02f, 0.03f, 1), 0.9f, 90);
        shine = G3.emissive(new ColorRGBA(1, 1, 1, 1));
        blush = mat(new ColorRGBA(1f, 0.55f, 0.55f, 1), 0.05f, 4);
        boots = mat(new ColorRGBA(0.32f, 0.2f, 0.12f, 1), 0.3f, 20);
        accent = mat(G3.rgb(l.accent).multLocal(1.1f), 0.5f, 40);
        mouth = mat(new ColorRGBA(0.45f, 0.12f, 0.14f, 1), 0.3f, 20);

        // Proportionen
        float legLen = troll ? 0.27f * H : 0.36f * H;
        hipY = legLen;
        float torsoH = troll ? 0.33f * H : 0.25f * H;
        float torsoR = troll ? 0.19f * H : 0.095f * H;
        hr = troll ? 0.145f * H : 0.15f * H;
        float shoulderY = hipY + torsoH * 0.88f;
        float neckY = hipY + torsoH + hr * 0.75f;

        root.attachChild(pelvis);
        pelvis.setLocalTranslation(0, hipY, 0);
        // Beine
        float legR0 = troll ? 0.07f * H : 0.035f * H;
        for (int side = -1; side <= 1; side += 2) {
            Node leg = side < 0 ? legL : legR;
            leg.setLocalTranslation(side * torsoR * 0.5f, 0, 0);
            Material legMat = l.female ? skin : clothDark;
            Geometry thigh = cylinder(legR0, legR0 * 0.85f, legLen * 0.95f, legMat);
            thigh.setLocalTranslation(0, -legLen * 0.5f, 0);
            leg.attachChild(thigh);
            Geometry boot = sphere(legR0 * 1.35f, boots);
            boot.setLocalScale(1, 0.7f, 1.6f);
            boot.setLocalTranslation(0, -legLen + legR0 * 0.6f, legR0 * 0.5f);
            leg.attachChild(boot);
            if (l.female) {
                Geometry sock = cylinder(legR0 * 1.05f, legR0 * 1.05f, legLen * 0.3f, boots);
                sock.setLocalTranslation(0, -legLen * 0.82f, 0);
                leg.attachChild(sock);
            }
            pelvis.attachChild(leg);
        }
        // Rumpf
        pelvis.attachChild(torso);
        Geometry body = sphere(1, cloth);
        body.setLocalScale(torsoR, torsoH * 0.58f, torsoR * (troll ? 0.85f : 0.8f));
        body.setLocalTranslation(0, torsoH * 0.5f, 0);
        torso.attachChild(body);
        Geometry belt = cylinder(torsoR * 0.98f, torsoR * 0.98f, torsoH * 0.07f, accent);
        belt.setLocalScale(1, 1, troll ? 0.86f : 0.82f);
        belt.setLocalTranslation(0, torsoH * 0.22f, 0);
        torso.attachChild(belt);
        if (l.female) {
            Geometry skirt = cylinder(torsoR * 0.95f, torsoR * (troll ? 1.55f : 1.9f), legLen * 0.55f + torsoH * 0.2f, cloth);
            skirt.setLocalTranslation(0, torsoH * 0.18f - (legLen * 0.55f + torsoH * 0.2f) / 2, 0);
            torso.attachChild(skirt);
            Geometry hem = cylinder(torsoR * (troll ? 1.57f : 1.92f), torsoR * (troll ? 1.57f : 1.92f), H * 0.012f, accent);
            hem.setLocalTranslation(0, torsoH * 0.18f - (legLen * 0.55f + torsoH * 0.2f), 0);
            torso.attachChild(hem);
        } else {
            Geometry hips = sphere(1, clothDark);
            hips.setLocalScale(torsoR * 0.95f, torsoH * 0.22f, torsoR * 0.8f);
            hips.setLocalTranslation(0, torsoH * 0.05f, 0);
            torso.attachChild(hips);
        }
        // Kragen / Kette
        Geometry collar = new Geometry("collar", new Torus(16, 8, H * 0.012f, torsoR * 0.42f));
        collar.setMaterial(accent);
        collar.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        collar.setLocalTranslation(0, torsoH * 0.97f, 0);
        torso.attachChild(collar);
        // Arme
        float armLen = troll ? 0.3f * H : 0.27f * H, armR0 = troll ? 0.055f * H : 0.028f * H;
        for (int side = -1; side <= 1; side += 2) {
            Node arm = side < 0 ? armL : armR;
            arm.setLocalTranslation(side * (torsoR + armR0 * 0.6f), shoulderY - hipY, 0);
            arm.setLocalRotation(new Quaternion().fromAngleAxis(side * 0.12f, Vector3f.UNIT_Z));
            Geometry sleeve = cylinder(armR0 * 1.15f, armR0, armLen * 0.55f, cloth);
            sleeve.setLocalTranslation(0, -armLen * 0.28f, 0);
            arm.attachChild(sleeve);
            Geometry fore = cylinder(armR0 * 0.95f, armR0 * 0.85f, armLen * 0.5f, skin);
            fore.setLocalTranslation(0, -armLen * 0.72f, 0);
            arm.attachChild(fore);
            Geometry hand = sphere(armR0 * 1.25f, skin);
            hand.setLocalTranslation(0, -armLen, 0);
            arm.attachChild(hand);
            Geometry shoulder = sphere(armR0 * 1.3f, cloth);
            arm.attachChild(shoulder);
            torso.attachChild(arm);
        }
        // Kopf
        head.setLocalTranslation(0, neckY - hipY, 0);
        torso.attachChild(head);
        Geometry neck = cylinder(hr * 0.32f, hr * 0.36f, hr * 0.6f, skin);
        neck.setLocalTranslation(0, -hr * 0.85f, 0);
        head.attachChild(neck);
        Geometry skull = sphere(hr, skin);
        skull.setLocalScale(troll ? 1.08f : 1f, troll ? 0.95f : 1.02f, 1);
        head.attachChild(skull);
        if (troll) {
            Geometry jaw = sphere(hr * 0.75f, skin);
            jaw.setLocalScale(1.15f, 0.7f, 0.95f);
            jaw.setLocalTranslation(0, -hr * 0.42f, hr * 0.18f);
            head.attachChild(jaw);
        }
        // Augen
        float eyeR = hr * (troll ? 0.25f : 0.29f), eyeX = hr * 0.37f, eyeY = hr * 0.06f, eyeZ = hr * 0.86f;
        for (int side = -1; side <= 1; side += 2) {
            Node eye = new Node("eye");
            eye.setLocalTranslation(side * eyeX, eyeY, eyeZ);
            Geometry white = sphere(eyeR, eyeWhite);
            white.setLocalScale(1, 1.12f, 0.8f);
            eye.attachChild(white);
            Geometry ir = sphere(eyeR * 0.68f, iris);
            ir.setLocalScale(1, 1.08f, 0.5f);
            ir.setLocalTranslation(0, -eyeR * 0.06f, eyeR * 0.55f);
            eye.attachChild(ir);
            Geometry pu = sphere(eyeR * 0.36f, pupil);
            pu.setLocalScale(1, 1.1f, 0.5f);
            pu.setLocalTranslation(0, -eyeR * 0.06f, eyeR * 0.72f);
            eye.attachChild(pu);
            Geometry sh = sphere(eyeR * 0.15f, shine);
            sh.setLocalTranslation(eyeR * 0.22f, eyeR * 0.25f, eyeR * 0.84f);
            eye.attachChild(sh);
            Node lid = side < 0 ? lidL : lidR;
            Geometry lidG = sphere(eyeR * 1.08f, skin);
            lidG.setLocalScale(1, 1.15f, 0.88f);
            lid.attachChild(lidG);
            lid.setLocalScale(1, 0.01f, 1);
            lid.setLocalTranslation(0, eyeR * 0.5f, 0.01f);
            eye.attachChild(lid);
            // Augenbraue
            Geometry brow = box(eyeR * 0.9f, eyeR * 0.13f, eyeR * 0.15f, hairM);
            brow.setLocalTranslation(0, eyeR * 1.35f, eyeR * 0.2f);
            brow.setLocalRotation(new Quaternion().fromAngleAxis(side * -0.18f, Vector3f.UNIT_Z));
            eye.attachChild(brow);
            if (l.female) {
                for (int k = 0; k < 3; k++) {
                    Geometry lash = box(eyeR * 0.05f, eyeR * 0.28f, eyeR * 0.05f, pupil);
                    float a = side * (0.5f + k * 0.35f);
                    lash.setLocalTranslation(side * eyeR * (0.55f + k * 0.18f), eyeR * (0.85f - k * 0.18f), eyeR * 0.3f);
                    lash.setLocalRotation(new Quaternion().fromAngleAxis(-a, Vector3f.UNIT_Z));
                    eye.attachChild(lash);
                }
            }
            head.attachChild(eye);
            // Wangen
            if (l.female || !troll) {
                Geometry ch = sphere(hr * 0.17f, blush);
                ch.setLocalScale(1.2f, 0.7f, 0.3f);
                ch.setLocalTranslation(side * hr * 0.58f, -hr * 0.22f, hr * 0.78f);
                head.attachChild(ch);
            }
            // Ohren
            if (troll) {
                Geometry ear = cylinder(hr * 0.02f, hr * 0.22f, hr * 0.55f, skinDark);
                ear.setLocalRotation(new Quaternion().fromAngles(0, 0, side * -1.25f));
                ear.setLocalTranslation(side * hr * 1.12f, hr * 0.12f, 0);
                head.attachChild(ear);
            } else {
                Geometry ear = cylinder(hr * 0.01f, hr * 0.12f, hr * 0.62f, skin);
                ear.setLocalRotation(new Quaternion().fromAngles(-0.4f, 0, side * -0.95f));
                ear.setLocalTranslation(side * hr * 1.0f, hr * 0.3f, -hr * 0.1f);
                head.attachChild(ear);
            }
        }
        // Nase & Mund
        Geometry nose = sphere(troll ? hr * 0.24f : hr * 0.07f, troll ? skinDark : skin);
        if (troll) nose.setLocalScale(1, 0.9f, 1.1f);
        nose.setLocalTranslation(0, -hr * 0.12f, hr * (troll ? 1.02f : 0.98f));
        head.attachChild(nose);
        for (int side = -1; side <= 1; side += 2) {
            Geometry m = box(hr * 0.13f, hr * 0.03f, hr * 0.03f, mouth);
            m.setLocalTranslation(side * hr * 0.11f, -hr * (troll ? 0.42f : 0.4f), hr * (troll ? 0.96f : 0.9f));
            m.setLocalRotation(new Quaternion().fromAngleAxis(side * 0.32f, Vector3f.UNIT_Z));
            head.attachChild(m);
        }
        if (troll) {
            float tusk = l.female ? 0.09f : 0.15f;
            for (int side = -1; side <= 1; side += 2) {
                Geometry t = cylinder(hr * 0.005f, hr * 0.06f, hr * tusk * 1.5f, mat(new ColorRGBA(0.98f, 0.96f, 0.88f, 1), 0.5f, 40));
                t.setLocalTranslation(side * hr * 0.3f, -hr * 0.38f, hr * 0.9f);
                head.attachChild(t);
            }
        }
        buildHair(l);
        if (l.horns) {
            Material hornM = mat(new ColorRGBA(0.9f, 0.85f, 0.72f, 1), 0.4f, 30);
            for (int side = -1; side <= 1; side += 2) {
                Node horn = new Node("horn");
                for (int k = 0; k < 4; k++) {
                    Geometry seg = cylinder(hr * Math.max(0.02f, 0.17f - k * 0.05f), hr * (0.22f - k * 0.05f), hr * 0.42f, hornM);
                    seg.setLocalTranslation(side * hr * k * 0.12f, hr * (0.21f + k * 0.3f), -hr * k * 0.06f);
                    seg.getLocalRotation().multLocal(new Quaternion().fromAngleAxis(side * -0.28f * k, Vector3f.UNIT_Y));
                    horn.attachChild(seg);
                }
                horn.setLocalTranslation(side * hr * 0.55f, hr * 0.75f, 0);
                head.attachChild(horn);
            }
        }
        root.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
    }

    private void buildHair(Look l) {
        head.attachChild(hairSway);
        if (l.hairStyle != Look.BEARD || !troll) {
            Geometry cap = new Geometry("hairCap", new Dome(Vector3f.ZERO, 10, 20, hr * 1.08f, false));
            cap.setMaterial(hairM);
            cap.setLocalRotation(new Quaternion().fromAngleAxis(-0.42f, Vector3f.UNIT_X));
            cap.setLocalTranslation(0, hr * 0.05f, -hr * 0.04f);
            head.attachChild(cap);
            // Pony
            for (int k = -2; k <= 2; k++) {
                Geometry f = sphere(hr * 0.2f, hairM);
                f.setLocalScale(1.1f, 0.7f, 0.6f);
                f.setLocalTranslation(k * hr * 0.2f, hr * (0.62f - Math.abs(k) * 0.04f), hr * (0.72f - Math.abs(k) * 0.05f));
                head.attachChild(f);
            }
        }
        switch (l.hairStyle) {
            case Look.LONG -> {
                Geometry back = sphere(1, hairM);
                back.setLocalScale(hr * 0.98f, hr * 1.55f, hr * 0.55f);
                back.setLocalTranslation(0, -hr * 0.6f, -hr * 0.55f);
                hairSway.attachChild(back);
                for (int side = -1; side <= 1; side += 2) {
                    Geometry lock = sphere(1, hairM);
                    lock.setLocalScale(hr * 0.28f, hr * 1.05f, hr * 0.3f);
                    lock.setLocalTranslation(side * hr * 0.85f, -hr * 0.45f, hr * 0.05f);
                    hairSway.attachChild(lock);
                }
            }
            case Look.BRAIDS -> {
                for (int side = -1; side <= 1; side += 2) {
                    for (int k = 0; k < 6; k++) {
                        Geometry b = sphere(hr * (0.2f - k * 0.012f), hairM);
                        b.setLocalTranslation(side * hr * (0.95f + k * 0.03f), -hr * (0.2f + k * 0.26f), -hr * 0.05f + k * hr * 0.03f);
                        hairSway.attachChild(b);
                    }
                    Geometry ribbon = box(hr * 0.16f, hr * 0.06f, hr * 0.06f, accent);
                    ribbon.setLocalTranslation(side * hr * 1.13f, -hr * 1.55f, hr * 0.12f);
                    hairSway.attachChild(ribbon);
                }
            }
            case Look.BUN -> {
                Geometry bun = sphere(hr * 0.45f, hairM);
                bun.setLocalTranslation(0, hr * 0.85f, -hr * 0.45f);
                head.attachChild(bun);
                Geometry pin = cylinder(hr * 0.03f, hr * 0.03f, hr * 0.9f, accent);
                pin.setLocalRotation(new Quaternion().fromAngleAxis(0.9f, Vector3f.UNIT_Z));
                pin.setLocalTranslation(0, hr * 0.9f, -hr * 0.45f);
                head.attachChild(pin);
            }
            case Look.PONYTAIL -> {
                for (int k = 0; k < 7; k++) {
                    Geometry p = sphere(hr * (0.3f - k * 0.025f), hairM);
                    p.setLocalTranslation(0, hr * (0.55f - k * 0.27f), -hr * (1.0f + k * 0.07f));
                    hairSway.attachChild(p);
                }
                Geometry ribbon = new Geometry("ribbon", new Torus(12, 6, hr * 0.05f, hr * 0.22f));
                ribbon.setMaterial(accent);
                ribbon.setLocalTranslation(0, hr * 0.55f, -hr * 0.98f);
                hairSway.attachChild(ribbon);
            }
            case Look.WILD -> {
                for (int k = 0; k < 9; k++) {
                    float a = -1.4f + k * 0.35f;
                    Geometry sp = cylinder(hr * 0.01f, hr * 0.16f, hr * 0.55f, hairM);
                    sp.setLocalRotation(new Quaternion().fromAngles(-0.6f + (k % 3) * 0.3f, 0, a));
                    sp.setLocalTranslation(-FastMath.sin(a) * hr * 0.8f, FastMath.cos(a) * hr * 0.85f, -hr * 0.15f);
                    hairSway.attachChild(sp);
                }
            }
            case Look.BEARD -> {
                Geometry beard = sphere(1, hairM);
                beard.setLocalScale(hr * 0.72f, hr * 0.62f, hr * 0.45f);
                beard.setLocalTranslation(0, -hr * 0.78f, hr * 0.55f);
                head.attachChild(beard);
                for (int side = -1; side <= 1; side += 2) {
                    Geometry mus = sphere(1, hairM);
                    mus.setLocalScale(hr * 0.28f, hr * 0.1f, hr * 0.12f);
                    mus.setLocalTranslation(side * hr * 0.2f, -hr * 0.3f, hr * 0.98f);
                    mus.setLocalRotation(new Quaternion().fromAngleAxis(side * -0.3f, Vector3f.UNIT_Z));
                    head.attachChild(mus);
                }
                if (troll) {
                    Geometry tuft = sphere(hr * 0.35f, hairM);
                    tuft.setLocalScale(1.4f, 0.6f, 1.2f);
                    tuft.setLocalTranslation(0, hr * 0.92f, -hr * 0.1f);
                    head.attachChild(tuft);
                }
            }
            default -> {
                Geometry tuft = sphere(hr * 0.3f, hairM);
                tuft.setLocalScale(1.3f, 0.7f, 1);
                tuft.setLocalTranslation(hr * 0.2f, hr * 0.95f, hr * 0.1f);
                head.attachChild(tuft);
            }
        }
    }

    // ---------------------------------------------------------------- Bausteine

    private static Material mat(ColorRGBA c, float spec, float shin) {
        Material m = G3.lit(c);
        m.setColor("Specular", new ColorRGBA(spec, spec, spec, 1));
        m.setFloat("Shininess", shin);
        return m;
    }

    private static Geometry sphere(float r, Material m) {
        Geometry g = new Geometry("s", new Sphere(14, 16, r));
        g.setMaterial(m);
        return g;
    }

    private static Geometry box(float x, float y, float z, Material m) {
        Geometry g = new Geometry("b", new Box(x, y, z));
        g.setMaterial(m);
        return g;
    }

    /** Senkrechter Zylinder (Achse = y), zentriert: rTop oben, rBottom unten. */
    private static Geometry cylinder(float rTop, float rBottom, float h, Material m) {
        Geometry g = new Geometry("c", new Cylinder(2, 14, rTop, rBottom, h, true, false));
        g.setMaterial(m);
        g.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        return g;
    }

    // ---------------------------------------------------------------- Animation

    public void animate(float dt, float t, float speed, boolean airborne, boolean casting) {
        float amp = Math.min(1, speed / (troll ? 4.5f : 3.5f));
        phase += dt * (troll ? 7.5f : 9.5f) * Math.max(0.2f, amp);
        float swing = FastMath.sin(phase) * 0.75f * amp;
        if (airborne) {
            legL.setLocalRotation(new Quaternion().fromAngleAxis(-0.5f, Vector3f.UNIT_X));
            legR.setLocalRotation(new Quaternion().fromAngleAxis(-0.2f, Vector3f.UNIT_X));
        } else {
            legL.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X));
            legR.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_X));
        }
        float bob = Math.abs(FastMath.sin(phase)) * 0.03f * H * amp;
        pelvis.setLocalTranslation(0, hipY + bob, 0);
        float breathe = 1 + FastMath.sin(t * 2.1f) * 0.018f;
        torso.setLocalScale(1, breathe, 1);
        torso.setLocalRotation(new Quaternion().fromAngleAxis(0.08f * amp + (casting ? -0.08f : 0), Vector3f.UNIT_X));
        if (casting) {
            armR.setLocalRotation(new Quaternion().fromAngles(-2.3f, 0, -0.15f));
            armL.setLocalRotation(new Quaternion().fromAngles(-0.9f, 0, 0.2f));
        } else {
            float idle = FastMath.sin(t * 1.3f) * 0.05f;
            armL.setLocalRotation(new Quaternion().fromAngles(-swing * 0.8f + idle, 0, -0.12f - (airborne ? 0.6f : 0)));
            armR.setLocalRotation(new Quaternion().fromAngles(swing * 0.8f - idle, 0, 0.12f + (airborne ? 0.6f : 0)));
        }
        head.setLocalRotation(new Quaternion().fromAngles(FastMath.sin(t * 0.6f) * 0.05f, FastMath.sin(t * 0.37f) * 0.12f * (1 - amp), FastMath.sin(t * 0.8f) * 0.06f));
        hairSway.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.sin(phase) * 0.06f * amp + FastMath.sin(t * 1.1f) * 0.02f, Vector3f.UNIT_X));
        // Blinzeln
        blinkT -= dt;
        if (blinkT <= 0) { blink = 0.16f; blinkT = 2.5f + (float) Math.random() * 3.5f; }
        if (blink > 0) blink -= dt;
        float lid = blink > 0 ? FastMath.sin((0.16f - blink) / 0.16f * FastMath.PI) : 0.01f;
        lidL.setLocalScale(1, Math.max(0.01f, lid), 1);
        lidR.setLocalScale(1, Math.max(0.01f, lid), 1);
    }
}
