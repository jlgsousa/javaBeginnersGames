package daemmerwald;

import com.jme3.anim.*;
import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Realistische Figuren im Mixamo-Format (glTF/GLB).
 * <p>
 * Die Dateien werden NICHT mit dem Spiel verteilt (Mixamo-Lizenz), sondern beim ersten Start einmalig auf den
 * eigenen Rechner geladen (~/.daemmerwald/modelle) – oder man legt eigene Mixamo-Figuren als frau.glb / mann.glb dort ab.
 * Die Bewegungen (Stehen, Gehen, Rennen) stammen aus Soldier.glb und werden auf jede Mixamo-Figur übertragen.
 */
public final class Mixamo {
    private Mixamo() { }

    public static final Path DIR = Paths.get(System.getProperty("user.home"), ".daemmerwald", "modelle");
    private static final String BASE = "https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/models/gltf/";
    private static final String[] FILES = {"Michelle.glb", "Soldier.glb", "readyplayer.glb"};
    public static volatile String status = "";
    private static Spatial animSource;
    private static AssetManager am;

    /** Registriert den Modellordner und lädt fehlende Dateien im Hintergrund. */
    public static void init(AssetManager assets) {
        am = assets;
        try {
            Files.createDirectories(DIR);
        } catch (Exception ignored) { }
        am.registerLocator(DIR.toString(), FileLocator.class);
        boolean missing = false;
        for (String f : FILES) if (!Files.exists(DIR.resolve(f))) missing = true;
        if (!missing || Boolean.getBoolean("dw.offline")) return;
        Thread t = new Thread(() -> {
            for (String f : FILES) {
                Path target = DIR.resolve(f);
                if (Files.exists(target)) continue;
                status = "Lade realistische Figuren (einmalig): " + f + " …";
                try (InputStream in = URI.create(BASE + (f.equals("readyplayer.glb") ? "readyplayer.me.glb" : f)).toURL().openStream()) {
                    Path tmp = DIR.resolve(f + ".part");
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    if (f.equals("readyplayer.glb")) {
                        GlbFix.deinterleave(tmp, target);
                        Files.deleteIfExists(tmp);
                    } else Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    status = "Realistische Figuren konnten nicht geladen werden (offline?) – es werden die Ersatzmodelle benutzt.";
                    return;
                }
            }
            status = "Realistische Figuren bereit.";
        }, "mixamo-download");
        t.setDaemon(true);
        t.start();
    }

    /** Lädt eine weibliche bzw. männliche Mixamo-Figur (eigene Datei hat Vorrang) – oder null. */
    public static Spatial character(boolean female) {
        String own = female ? "frau.glb" : "mann.glb";
        String fallback = female ? "Michelle.glb" : "readyplayer.glb";
        for (String f : new String[]{own, fallback}) {
            if (f == null || !Files.exists(DIR.resolve(f)) || !Files.exists(DIR.resolve("Soldier.glb"))) continue;
            try {
                Spatial s = am.loadModel(f).clone();
                stripMorphs(s);
                if (retarget(s)) { G3.toPhong(s); return s; }
            } catch (Exception e) {
                System.err.println("Mixamo-Figur " + f + " nicht lesbar: " + e.getMessage());
            }
        }
        return null;
    }

    /** Gesichts-Blendshapes entfernen (zu viele für die Engine – die Figur würde sonst zersplittern). */
    static void stripMorphs(Spatial s) {
        s.depthFirstTraversal(sp -> {
            while (sp.getNumControls() > 0 && sp.getControl(com.jme3.anim.MorphControl.class) != null)
                sp.removeControl(com.jme3.anim.MorphControl.class);
            if (sp instanceof com.jme3.scene.Geometry g) {
                com.jme3.scene.Mesh m = g.getMesh();
                while (m.hasMorphTargets()) m.removeMorphTarget(0);
                g.setMorphState(null);
            }
        });
    }

    static AnimComposer composer(Spatial s) {
        AnimComposer[] o = new AnimComposer[1];
        s.depthFirstTraversal(sp -> { if (o[0] == null && sp.getControl(AnimComposer.class) != null) o[0] = sp.getControl(AnimComposer.class); });
        return o[0];
    }

    static SkinningControl skin(Spatial s) {
        SkinningControl[] o = new SkinningControl[1];
        s.depthFirstTraversal(sp -> { if (o[0] == null && sp.getControl(SkinningControl.class) != null) o[0] = sp.getControl(SkinningControl.class); });
        return o[0];
    }

    /** Überträgt Idle/Walk/Run von Soldier.glb auf das Skelett der Figur (relativ zur Ruhepose). */
    private static boolean retarget(Spatial target) {
        // Der glTF-Lader verliert beim ersten Laden gelegentlich die Animationen – dann frisch nachladen.
        for (int i = 0; i < 4 && (animSource == null || composer(animSource) == null || composer(animSource).getAnimClip("Walk") == null); i++) {
            am.deleteFromCache(new com.jme3.asset.ModelKey("Soldier.glb"));
            animSource = am.loadModel("Soldier.glb");
        }
        AnimComposer src = composer(animSource), dst = composer(target);
        SkinningControl sk = skin(target);
        if (src == null || dst == null || sk == null) return false;
        Armature arm = sk.getArmature();
        // Grunddrehung der beiden Skelette (z. B. ein gedrehter "Armature"-Knoten in der Datei) ausgleichen
        // M: dreht den Skelettraum der Quelle in den des Ziels (aus Körperachsen: Hüfte→Kopf, rechte→linke Schulter)
        Quaternion conv = frame(sk.getArmature()).mult(frame(skin(animSource).getArmature()).inverse());
        Quaternion convInv = conv.inverse();
        for (String name : new String[]{"Idle", "Walk", "Run"}) {
            AnimClip clip = src.getAnimClip(name);
            if (clip == null) return false;
            List<AnimTrack> out = new ArrayList<>();
            for (AnimTrack tr : clip.getTracks()) {
                if (!(tr instanceof TransformTrack tt) || !(tt.getTarget() instanceof Joint sj)) continue;
                String jn = sj.getName();
                Joint mj = arm.getJoint(jn);
                if (mj == null) mj = arm.getJoint(jn.replace("mixamorig:", "mixamorig"));
                if (mj == null) mj = arm.getJoint(jn.replace("mixamorig:", ""));
                if (mj == null) continue;
                Transform sb = sj.getInitialTransform(), tb = mj.getInitialTransform();
                Quaternion ws = bindWorld(sj), wt = bindWorld(mj);
                Quaternion wsp = sj.getParent() != null ? bindWorld(sj.getParent()) : new Quaternion();
                Quaternion wtpInv = (mj.getParent() != null ? bindWorld(mj.getParent()) : new Quaternion()).inverse();
                Quaternion wsInv = ws.inverse();
                Quaternion[] srq = tt.getRotations();
                Vector3f[] srt = tt.getTranslations();
                int n = tt.getTimes().length;
                Quaternion[] rots = new Quaternion[n];
                Vector3f[] trs = new Vector3f[n];
                float ratio = sb.getTranslation().length() > 1e-4f ? tb.getTranslation().length() / sb.getTranslation().length() : 1;
                for (int i = 0; i < n; i++) {
                    Quaternion r = srq != null ? srq[i] : sb.getRotation();
                    // Rotation über die Ruhepose im Weltraum übertragen: inv(Wt_parent) * Ws_parent * r * inv(Ws) * Wt
                    // Änderung D = Wsp*r*inv(Ws) im Quellraum; Ziel x = inv(Wtp)*M*D*inv(M)*Wt
                    Quaternion d = wsp.mult(r).multLocal(wsInv);
                    rots[i] = wtpInv.mult(conv).multLocal(d).multLocal(convInv).multLocal(wt).normalizeLocal();
                    trs[i] = jn.endsWith("Hips") && srt != null
                        ? tb.getTranslation().add(conv.mult(srt[i].subtract(sb.getTranslation())).multLocal(ratio))
                        : tb.getTranslation().clone();
                }
                out.add(new TransformTrack(mj, tt.getTimes(), trs, rots, null));
            }
            AnimClip c = new AnimClip(name);
            c.setTracks(out.toArray(new AnimTrack[0]));
            dst.addAnimClip(c);
        }
        dst.setCurrentAction("Idle");
        return true;
    }

    /** Körper-Koordinatensystem eines Skeletts in Ruhehaltung (seitlich, oben, vorn). */
    private static Quaternion frame(Armature a) {
        Vector3f hips = bindPos(find(a, "Hips")), head = bindPos(find(a, "Head")), l = bindPos(find(a, "LeftArm")), r = bindPos(find(a, "RightArm"));
        Vector3f up = head.subtract(hips).normalizeLocal();
        Vector3f side = l.subtract(r).normalizeLocal();
        Vector3f fwd = side.cross(up).normalizeLocal();
        side = up.cross(fwd).normalizeLocal();
        Quaternion q = new Quaternion();
        q.fromAxes(side, up, fwd);
        return q;
    }

    private static Joint find(Armature a, String n) {
        Joint j = a.getJoint("mixamorig:" + n);
        if (j == null) j = a.getJoint("mixamorig" + n);
        if (j == null) j = a.getJoint(n);
        return j;
    }

    private static Vector3f bindPos(Joint j) {
        if (j == null) return new Vector3f();
        Transform t = j.getInitialTransform().clone();
        for (Joint p = j.getParent(); p != null; p = p.getParent()) t.combineWithParent(p.getInitialTransform());
        return t.getTranslation();
    }

    private static Quaternion bindWorld(Joint j) {
        Quaternion q = j.getInitialTransform().getRotation().clone();
        for (Joint p = j.getParent(); p != null; p = p.getParent()) q = p.getInitialTransform().getRotation().mult(q);
        return q;
    }

}
