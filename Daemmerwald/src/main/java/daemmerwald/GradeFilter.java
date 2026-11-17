package daemmerwald;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/** Farbstimmung: warme Lichter, magisch getönte Schatten, kräftigere Farben, Vignette (MatDefs/Grade). */
public class GradeFilter extends Filter {
    private float saturation = 1.18f, contrast = 0.22f, vignette = 0.32f, exposure = 1.05f;
    private final Vector3f shadowTint = new Vector3f(0.02f, 0.005f, 0.045f), highTint = new Vector3f(0.05f, 0.025f, -0.02f);

    public GradeFilter() { super("Grade"); }

    @Override
    protected void initFilter(AssetManager manager, RenderManager rm, ViewPort vp, int w, int h) {
        material = new Material(manager, "MatDefs/Grade.j3md");
        apply();
    }

    @Override protected Material getMaterial() { return material; }

    private void apply() {
        if (material == null) return;
        material.setFloat("Saturation", saturation);
        material.setFloat("Contrast", contrast);
        material.setFloat("Vignette", vignette);
        material.setFloat("Exposure", exposure);
        material.setVector3("ShadowTint", shadowTint);
        material.setVector3("HighTint", highTint);
    }

    /** night 0..1, dark 0..1 (Welke), bright 0..1 (fröhliche Orte), indoors: warmes Innenlicht. */
    public void mood(float night, float dark, float bright, boolean indoors) {
        saturation = 1.12f + bright * 0.12f - dark * 0.25f + night * 0.05f;
        contrast = 0.2f + night * 0.08f;
        vignette = 0.28f + night * 0.18f + dark * 0.15f;
        exposure = 1.04f + night * 0.12f + (indoors ? 0.04f : 0);
        // Nacht: violett-türkise Schatten, Laternen golden; Tag: leicht warme Höhen
        shadowTint.set(0.015f + night * 0.02f, 0.006f + night * 0.012f, 0.03f + night * 0.05f + dark * 0.03f);
        highTint.set(0.05f + (indoors ? 0.03f : 0), 0.028f + (indoors ? 0.01f : 0), -0.02f - night * 0.01f);
        apply();
    }
}
