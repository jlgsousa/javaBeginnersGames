package daemmerwald;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.ui.Picture;
import com.jme3.util.BufferUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;

/** Zeichnet die Java2D-Oberfläche (Menüs, HUD, Dialoge, Prüfungen) in eine Textur über der 3D-Szene. */
public class Overlay {
    private final BufferedImage img;
    private final ByteBuffer buf;
    private final Image image;
    private final Picture pic;
    /** Pixelgröße der Oberfläche: so groß wie das Fenster (höchstens 1920 breit), damit Schrift scharf bleibt. */
    private final int iw, ih;

    public Overlay(Main app) {
        int cw = app.getCamera().getWidth(), ch = app.getCamera().getHeight();
        float sc = Math.max(1f, Math.min(1920f, cw) / GamePanel.W);
        iw = Math.round(GamePanel.W * sc);
        ih = Math.round(GamePanel.H * sc);
        img = new BufferedImage(iw, ih, BufferedImage.TYPE_4BYTE_ABGR);
        buf = BufferUtils.createByteBuffer(iw * ih * 4);
        image = new Image(Image.Format.ABGR8, iw, ih, buf, ColorSpace.sRGB);
        Texture2D tex = new Texture2D(image);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        // Gui.j3md wird im OpenGL-3.2-Kernprofil nicht korrekt gezeichnet – Unshaded funktioniert überall.
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", tex);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        pic = new Picture("overlay");
        pic.setMaterial(m);
        pic.setQueueBucket(RenderQueue.Bucket.Gui);
        pic.setCullHint(com.jme3.scene.Spatial.CullHint.Never);
        resize(app.getCamera().getWidth(), app.getCamera().getHeight());
        app.getGuiNode().attachChild(pic);
    }

    public void resize(int w, int h) {
        // Bildzeile 0 liegt oben, OpenGL zählt von unten – daher gespiegelt.
        pic.setLocalScale(w, -h, 1);
        pic.setLocalTranslation(0, h, 0);
    }

    public void render(GamePanel core) {
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, iw, ih);
        g.setComposite(AlphaComposite.SrcOver);
        g.scale(iw / (double) GamePanel.W, ih / (double) GamePanel.H);
        Art.hints(g);
        core.render(g);
        if (Main.errorMsg != null) {
            g.setColor(new java.awt.Color(120, 20, 30, 220));
            g.fillRect(0, GamePanel.H - 30, GamePanel.W, 30);
            g.setColor(java.awt.Color.WHITE);
            g.setFont(Art.SMALL);
            g.drawString(Main.errorMsg, 14, GamePanel.H - 10);
        }
        g.dispose();
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        buf.clear();
        buf.put(data);
        buf.flip();
        image.setData(buf);
    }
}
