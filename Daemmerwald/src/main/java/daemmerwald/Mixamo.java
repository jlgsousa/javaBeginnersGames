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
                long t0 = System.nanoTime();
                Spatial s = am.loadModel(f).clone();
                stripMorphs(s);
                boolean ok = retarget(s);
                if (Boolean.getBoolean("dw.timing")) System.out.println("Figur " + f + ": " + (System.nanoTime() - t0) / 1_000_000 + " ms");
                if (ok) { G3.toPhong(s); return s; }
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

    /**
     * Überträgt Idle/Walk/Run von Soldier.glb auf das Skelett der Figur.
     * Übertragen wird die Welt-Ausrichtung jedes Knochens; dabei werden unterschiedliche Ruhehaltungen
     * (T-Pose der Quelle, A-Pose der Figur) über die Knochenrichtungen ausgeglichen – sonst verdrehen sich die Arme.
     */
    private static boolean retarget(Spatial target) {
        // Der glTF-Lader verliert beim ersten Laden gelegentlich die Animationen – dann frisch nachladen.
        for (int i = 0; i < 4 && (animSource == null || composer(animSource) == null || composer(animSource).getAnimClip("Walk") == null); i++) {
            am.deleteFromCache(new com.jme3.asset.ModelKey("Soldier.glb"));
            animSource = am.loadModel("Soldier.glb");
        }
        AnimComposer src = composer(animSource), dst = composer(target);
        SkinningControl sk = skin(target);
        if (src == null || dst == null || sk == null) return false;
        Armature arm = sk.getArmature(), sarm = skin(animSource).getArmature();
        Quaternion conv = frame(arm).mult(frame(sarm).inverse());
        Quaternion convInv = conv.inverse();
        // Zuordnung Quelle -> Ziel
        java.util.Map<Joint, Joint> map = new java.util.LinkedHashMap<>();
        for (Joint sj : sarm.getJointList()) {
            String jn = sj.getName();
            Joint mj = arm.getJoint(jn);
            if (mj == null) mj = arm.getJoint(jn.replace("mixamorig:", "mixamorig"));
            if (mj == null) mj = arm.getJoint(jn.replace("mixamorig:", ""));
            if (mj != null) map.put(sj, mj);
        }
        // Ausgleich der Ruhehaltung je Knochen: dreht die (umgerechnete) Knochenrichtung der Quelle auf die des Ziels
        java.util.Map<Joint, Quaternion> align = new java.util.HashMap<>();
        for (java.util.Map.Entry<Joint, Joint> e : map.entrySet()) {
            Joint sj = e.getKey(), tj = e.getValue();
            Joint sc = null;
            for (Joint c : sj.getChildren()) if (map.containsKey(c)) { sc = c; break; }
            Quaternion q = new Quaternion();
            if (sc != null) {
                Vector3f ds = conv.mult(bindPos(sc).subtract(bindPos(sj)));
                Vector3f dt = bindPos(map.get(sc)).subtract(bindPos(tj));
                if (ds.lengthSquared() > 1e-8f && dt.lengthSquared() > 1e-8f && !sj.getName().endsWith("Hips")) q = arc(ds.normalize(), dt.normalize());
            }
            align.put(sj, q);
        }
        for (String name : new String[]{"Idle", "Walk", "Run"}) {
            AnimClip clip = src.getAnimClip(name);
            if (clip == null) return false;
            java.util.Map<Joint, TransformTrack> tracks = new java.util.HashMap<>();
            float[] times = null;
            for (AnimTrack tr : clip.getTracks()) {
                if (tr instanceof TransformTrack tt && tt.getTarget() instanceof Joint sj) {
                    tracks.put(sj, tt);
                    if (sj.getName().endsWith("Hips") || times == null) times = tt.getTimes();
                }
            }
            if (times == null) return false;
            int n = times.length;
            java.util.Map<Joint, Quaternion[]> outRot = new java.util.HashMap<>();
            java.util.Map<Joint, Vector3f[]> outTr = new java.util.HashMap<>();
            for (Joint tj : map.values()) { outRot.put(tj, new Quaternion[n]); outTr.put(tj, new Vector3f[n]); }
            Transform tmp = new Transform();
            for (int i = 0; i < n; i++) {
                double t = times[i];
                // Welt-Rotation der Quelle (animiert) über die Hierarchie
                java.util.Map<Joint, Quaternion> sw = new java.util.HashMap<>();
                java.util.Map<Joint, Vector3f> sLocT = new java.util.HashMap<>();
                for (Joint sj : ordered(sarm)) {
                    TransformTrack tt = tracks.get(sj);
                    Quaternion lr = sj.getInitialTransform().getRotation().clone();
                    Vector3f lt = sj.getInitialTransform().getTranslation().clone();
                    if (tt != null) {
                        tmp.loadIdentity();
                        tmp.setRotation(lr);
                        tmp.setTranslation(lt);
                        tt.getDataAtTime(t, tmp);
                        if (tt.getRotations() != null) lr = tmp.getRotation().clone();
                        if (tt.getTranslations() != null) lt = tmp.getTranslation().clone();
                    }
                    sLocT.put(sj, lt);
                    sw.put(sj, worldRot(sj, lr, sw));
                }
                // Ziel-Weltrotationen
                java.util.Map<Joint, Quaternion> tw = new java.util.HashMap<>();
                for (Joint tj : ordered(arm)) {
                    Joint sj = null;
                    for (java.util.Map.Entry<Joint, Joint> e : map.entrySet()) if (e.getValue() == tj) { sj = e.getKey(); break; }
                    Quaternion parentW = tj.getParent() != null ? tw.get(tj.getParent()) : new Quaternion();
                    if (parentW == null) parentW = bindWorld(tj.getParent());
                    Quaternion world;
                    if (sj != null) {
                        Quaternion delta = sw.get(sj).mult(bindWorld(sj).inverse());
                        world = conv.mult(delta).multLocal(convInv).multLocal(align.get(sj).inverse()).multLocal(bindWorld(tj));
                    } else {
                        world = parentW.mult(tj.getInitialTransform().getRotation());
                    }
                    tw.put(tj, world.normalizeLocal());
                    if (sj != null) {
                        outRot.get(tj)[i] = parentW.inverse().mult(world).normalizeLocal();
                        Transform sb = sj.getInitialTransform(), tb = tj.getInitialTransform();
                        float ratio = sb.getTranslation().length() > 1e-4f ? tb.getTranslation().length() / sb.getTranslation().length() : 1;
                        outTr.get(tj)[i] = sj.getName().endsWith("Hips")
                            ? tb.getTranslation().add(conv.mult(sLocT.get(sj).subtract(sb.getTranslation())).multLocal(ratio))
                            : tb.getTranslation().clone();
                    }
                }
            }
            List<AnimTrack> out = new ArrayList<>();
            for (Joint tj : map.values()) out.add(new TransformTrack(tj, times.clone(), outTr.get(tj), outRot.get(tj), null));
            AnimClip c = new AnimClip(name);
            c.setTracks(out.toArray(new AnimTrack[0]));
            dst.addAnimClip(c);
        }
        dst.setCurrentAction("Idle");
        return true;
    }

    private static Quaternion worldRot(Joint j, Quaternion local, java.util.Map<Joint, Quaternion> done) {
        if (j.getParent() == null) return local.clone();
        Quaternion pw = done.get(j.getParent());
        return (pw != null ? pw : bindWorld(j.getParent())).mult(local);
    }

    /** Gelenke so sortiert, dass Eltern vor ihren Kindern kommen. */
    private static List<Joint> ordered(Armature a) {
        List<Joint> out = new ArrayList<>();
        for (Joint r : a.getRoots()) collect(r, out);
        return out;
    }

    private static void collect(Joint j, List<Joint> out) {
        out.add(j);
        for (Joint c : j.getChildren()) collect(c, out);
    }

    /** Kürzeste Drehung, die a auf b dreht. */
    private static Quaternion arc(Vector3f a, Vector3f b) {
        float d = a.dot(b);
        if (d > 0.99999f) return new Quaternion();
        if (d < -0.99999f) {
            Vector3f ax = Vector3f.UNIT_X.cross(a);
            if (ax.lengthSquared() < 1e-6f) ax = Vector3f.UNIT_Y.cross(a);
            return new Quaternion().fromAngleAxis(com.jme3.math.FastMath.PI, ax.normalizeLocal());
        }
        Vector3f c = a.cross(b);
        return new Quaternion(c.x, c.y, c.z, 1 + d).normalizeLocal();
    }

    /** Körper-Koordinatensystem eines Skeletts in Ruhehaltung (seitlich, oben, vorn). */
    static Quaternion frame(Armature a) {
        Vector3f hips = bindPos(find(a, "Hips")), head = bindPos(find(a, "Head")), l = bindPos(find(a, "LeftArm")), r = bindPos(find(a, "RightArm"));
        Vector3f up = head.subtract(hips).normalizeLocal();
        Vector3f side = l.subtract(r).normalizeLocal();
        Vector3f fwd = side.cross(up).normalizeLocal();
        side = up.cross(fwd).normalizeLocal();
        Quaternion q = new Quaternion();
        q.fromAxes(side, up, fwd);
        return q;
    }

    static Joint find(Armature a, String n) {
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
