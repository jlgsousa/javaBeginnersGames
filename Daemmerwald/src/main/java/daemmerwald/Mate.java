package daemmerwald;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import java.awt.event.KeyEvent;

/**
 * Spieler 2 am geteilten Bildschirm.
 * Steuerung: Pfeiltasten (hoch/runter laufen, links/rechts drehen), rechte Strg springen/fliegen,
 * rechte Umschalt zaubern, Enter sprechen – oder ein Gamepad (Stick, A, B, X).
 * Taler, Gepäck und Missionen teilen sich beide Spieler.
 */
public class Mate {
    /** So weit darf sich Spieler 2 von Spieler 1 entfernen (die Welt wird um Spieler 1 geladen). */
    public static final float LEASH = 70;

    final GameState st;
    final World3D world;
    Figure fig;
    final Vector3f pos = new Vector3f();
    float yaw, camYaw, camPitch = 0.3f, velY, mana, moveSpeed;
    boolean onGround = true, flyingNow, castNow, useNow;
    float leashHint;
    private final Vector3f camPos = new Vector3f();
    private boolean camInit;

    Mate(GameState st, World3D world, Vector3f nearP1, float p1Yaw) {
        this.st = st;
        this.world = world;
        if (world.mate != null) world.mate.remove();
        world.mate = this;
        Vector3f side = new Vector3f(FastMath.cos(p1Yaw), 0, -FastMath.sin(p1Yaw)).multLocal(2.4f);
        pos.set(nearP1).addLocal(side);
        pos.y = world.height(pos.x, pos.z);
        yaw = camYaw = p1Yaw;
        mana = maxMana();
        fig = new Figure(st.hero2.look);
        fig.node.setLocalTranslation(pos);
        world.root.attachChild(fig.node);
    }

    float maxMana() { return (float) st.hero2.manaMax(); }

    boolean troll() { return st.hero2.isTroll(); }

    /** Logik: Bewegung und Tasten. frozen = Dialog/Menü offen. */
    void update(float dt, Input in, boolean frozen, Vector3f p1) {
        castNow = false;
        useNow = false;
        leashHint = Math.max(0, leashHint - dt);
        mana = Math.min(maxMana(), mana + (4 + st.hero2.macht * 1.2f) * dt);
        if (frozen) { idle(dt); return; }

        float turn = 0, fwd = 0;
        if (in.isDown(KeyEvent.VK_LEFT)) turn += 1;
        if (in.isDown(KeyEvent.VK_RIGHT)) turn -= 1;
        if (in.isDown(KeyEvent.VK_UP)) fwd += 1;
        if (in.isDown(KeyEvent.VK_DOWN)) fwd -= 1;
        turn -= in.padX;
        fwd += in.padY;
        turn = FastMath.clamp(turn, -1, 1);
        fwd = FastMath.clamp(fwd, -1, 1);
        yaw += turn * 2.6f * dt;

        float sp = (float) st.hero2.moveSpeed() / 36f;
        if (flyingNow) sp *= 1.6f;
        if (world.height(pos.x, pos.z) < WorldGen.WATER + 0.1f && onGround) sp *= 0.6f;
        moveSpeed = 0;
        if (Math.abs(fwd) > 0.05f) {
            float step = sp * dt * fwd * (fwd < 0 ? 0.55f : 1);
            float nx = pos.x + FastMath.sin(yaw) * step, nz = pos.z + FastMath.cos(yaw) * step;
            float ground = world.height(pos.x, pos.z);
            boolean air = !onGround && pos.y > ground + 0.6f;
            if (canGo(nx, pos.z, air, Math.abs(step))) pos.x = nx;
            if (canGo(pos.x, nz, air, Math.abs(step))) pos.z = nz;
            moveSpeed = sp * Math.abs(fwd);
        }
        // An der Leine: nicht zu weit von Spieler 1 entfernen
        float dx = pos.x - p1.x, dz = pos.z - p1.z, d = FastMath.sqrt(dx * dx + dz * dz);
        if (d > LEASH) {
            pos.x = p1.x + dx / d * LEASH;
            pos.z = p1.z + dz / d * LEASH;
            leashHint = 2.5f;
        }

        boolean jump = in.isDown(Input.P2_JUMP);
        flyingNow = false;
        float ground = world.height(pos.x, pos.z);
        if (st.hero2.isFairy() && jump && mana > 1 && pos.y < ground + 6f) {
            // Feen flattern und fliegen (kostet etwas Mana)
            velY = Math.min(velY + 16 * dt, 4.2f);
            mana -= 5 * dt;
            flyingNow = true;
        } else if (in.wasPressed(Input.P2_JUMP) && onGround) {
            velY = troll() ? 6.2f : 5f;
            onGround = false;
        }
        float grav = troll() ? 18 : (jump && st.hero2.isFairy() ? 3 : 12);
        velY -= grav * dt;
        pos.y += velY * dt;
        ground = world.height(pos.x, pos.z);
        if (pos.y <= ground) {
            pos.y = ground;
            velY = 0;
            onGround = true;
        } else {
            onGround = pos.y < ground + 0.05f;
        }

        if (in.wasPressed(Input.P2_CAST) && !fig.casting()) castNow = true;
        if (in.wasPressed(Input.P2_USE, KeyEvent.VK_ENTER)) useNow = true;
    }

    private void idle(float dt) {
        moveSpeed = 0;
        flyingNow = false;
        velY = Math.max(velY - 12 * dt, -20);
        pos.y = Math.max(world.height(pos.x, pos.z), pos.y + velY * dt);
        if (pos.y <= world.height(pos.x, pos.z) + 0.01f) { velY = 0; onGround = true; }
    }

    private boolean canGo(float x, float z, boolean air, float step) {
        if (air) return !world.chunks.blocked(x, z, 0.4f) || pos.y > world.height(x, z) + 8;
        float hNew = world.height(x, z), hCur = world.height(pos.x, pos.z);
        if ((hNew - hCur) / Math.max(0.001f, step) > 1.5f && hNew > hCur + 0.05f) return false;
        if (hNew < WorldGen.WATER - 1.3f) return false;
        return !world.blockedIgnoringWater(x, z, 0.45f);
    }

    /** Kamera folgt Spieler 2 von hinten. */
    void directCamera(Camera cam, float tpf) {
        camYaw += FastMath.normalize(yaw - camYaw, -FastMath.PI, FastMath.PI) * Math.min(1, tpf * 3.5f);
        Vector3f target = pos.add(0, fig.height * 0.85f, 0);
        float dist = troll() ? 6.8f : 4.8f;
        Vector3f back = new Vector3f(-FastMath.sin(camYaw) * FastMath.cos(camPitch), FastMath.sin(camPitch), -FastMath.cos(camYaw) * FastMath.cos(camPitch));
        Vector3f desired = target.add(back.mult(dist));
        float gh = world.height(desired.x, desired.z) + 0.6f;
        if (desired.y < gh) desired.y = gh;
        if (!camInit) { camPos.set(desired); camInit = true; }
        camPos.interpolateLocal(desired, Math.min(1, tpf * 8));
        cam.setLocation(camPos);
        cam.lookAt(target, Vector3f.UNIT_Y);
    }

    void update3D(float dt) {
        fig.node.setLocalTranslation(pos);
        fig.face(yaw, dt);
        fig.flying = flyingNow;
        fig.update(dt, moveSpeed, !onGround);
    }

    void remove() { fig.node.removeFromParent(); }
}
