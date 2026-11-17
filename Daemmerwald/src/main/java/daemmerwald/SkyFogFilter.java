package daemmerwald;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/** Distanznebel, der den Himmel sichtbar lässt (eigener Shader in MatDefs/SkyFog). */
public class SkyFogFilter extends Filter {
    private ColorRGBA color = new ColorRGBA(0.1f, 0.1f, 0.15f, 1);
    private float density = 0.012f, skyHaze = 0.15f;
    private final Vector2f nearFar = new Vector2f(0.5f, 1000f);

    public SkyFogFilter() { super("SkyFog"); }

    @Override protected boolean isRequiresDepthTexture() { return true; }

    @Override
    protected void initFilter(AssetManager manager, RenderManager rm, ViewPort vp, int w, int h) {
        material = new Material(manager, "MatDefs/SkyFog.j3md");
        nearFar.set(vp.getCamera().getFrustumNear(), vp.getCamera().getFrustumFar());
        apply();
    }

    @Override protected Material getMaterial() { return material; }

    private void apply() {
        if (material == null) return;
        material.setColor("FogColor", color);
        material.setFloat("FogDensity", density);
        material.setVector2("NearFar", nearFar);
        material.setFloat("SkyHaze", skyHaze);
    }

    public void set(ColorRGBA c, float density, float skyHaze) {
        this.color.set(c);
        this.density = density;
        this.skyHaze = skyHaze;
        apply();
    }
}
