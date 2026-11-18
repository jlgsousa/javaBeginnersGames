package daemmerwald;

import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** Menschliche Spielfiguren: färbt Haut, Haare und Kleidung der Mixamo-Frau pro Figur in der Textur um. */
public final class Humans {
    private Humans() { }

    /** Größe der männlichen Ready-Player-Me-Figur bei Maßstab 1 (Meter). */
    public static float maleBase = 1.82f;

    private static final Map<String, Texture> CACHE = new HashMap<>();

    /**
     * Michelle-Textur umfärben: Haut (braun) → Hautton, gelbe Hose → Hosenfarbe, graues Oberteil → Oberteilfarbe,
     * rote Schuhteile → Schuhfarbe, schwarze Haare → Haarfarbe.
     */
    public static Texture michelleTexture(Look look, Material m) {
        MatParam p = m.getParam("DiffuseMap");
        if (p == null) return null;
        String key = look.skin.getRGB() + ":" + look.cloth.getRGB() + ":" + look.accent.getRGB() + ":" + look.hair.getRGB() + ":" + look.wing.getRGB();
        Texture cached = CACHE.get(key);
        if (cached != null) return cached;
        Image src = ((Texture) p.getValue()).getImage();
        int w = src.getWidth(), h = src.getHeight();
        Image dst = new Image(Image.Format.RGBA8, w, h, BufferUtils.createByteBuffer(w * h * 4), src.getColorSpace());
        ImageRaster in = ImageRaster.create(src), out = ImageRaster.create(dst);
        float[] hsb = new float[3];
        float[] skinT = rgb(look.skin), topT = rgb(look.cloth), botT = rgb(look.accent), hairT = rgb(look.hair), shoeT = rgb(look.wing);
        float[] skinB = {150 / 255f, 100 / 255f, 72 / 255f};
        ColorRGBA c = new ColorRGBA();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                in.getPixel(x, y, c);
                int r = (int) (Math.min(1, c.r) * 255), g = (int) (Math.min(1, c.g) * 255), b = (int) (Math.min(1, c.b) * 255);
                Color.RGBtoHSB(r, g, b, hsb);
                float hue = hsb[0], sat = hsb[1], bri = hsb[2];
                float[] res = null;
                if (sat > 0.72f && bri > 0.35f && (hue < 0.035f || hue > 0.95f)) {
                    res = scaleTo(shoeT, bri / 0.8f);                       // rote Schuh-/Kleidungsdetails
                } else if (hue > 0.1f && hue < 0.19f && sat > 0.45f && bri > 0.45f) {
                    res = scaleTo(botT, bri / 0.85f);                       // gelbe Hose
                } else if (hue >= 0.02f && hue <= 0.11f && sat > 0.22f && sat < 0.78f && bri > 0.14f) {
                    res = new float[]{c.r * skinT[0] / skinB[0], c.g * skinT[1] / skinB[1], c.b * skinT[2] / skinB[2]}; // Haut
                } else if (sat < 0.2f && bri > 0.38f && bri < 0.9f) {
                    res = scaleTo(topT, bri / 0.62f);                       // graues Oberteil
                } else if (bri < 0.2f) {
                    float k = 0.35f + bri * 3.2f;                            // dunkle Haare
                    res = new float[]{hairT[0] * k, hairT[1] * k, hairT[2] * k};
                }
                if (res != null) c.set(clamp(res[0]), clamp(res[1]), clamp(res[2]), 1);
                out.setPixel(x, y, c);
            }
        }
        Texture2D t = new Texture2D(dst);
        t.setWrap(Texture.WrapMode.Repeat);
        t.setMinFilter(Texture.MinFilter.Trilinear);
        CACHE.put(key, t);
        return t;
    }

    private static float[] rgb(Color c) { return new float[]{c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f}; }

    private static float[] scaleTo(float[] t, float k) { return new float[]{t[0] * k, t[1] * k, t[2] * k}; }

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
}
