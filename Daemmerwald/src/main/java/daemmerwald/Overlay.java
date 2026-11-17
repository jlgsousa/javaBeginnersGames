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
    private final BufferedImage img = new BufferedImage(GamePanel.W, GamePanel.H, BufferedImage.TYPE_4BYTE_ABGR);
    private final ByteBuffer buf = BufferUtils.createByteBuffer(GamePanel.W * GamePanel.H * 4);
    private final Image image;
    private final Picture pic;

    public Overlay(Main app) {
        image = new Image(Image.Format.ABGR8, GamePanel.W, GamePanel.H, buf, ColorSpace.sRGB);
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
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        g.setComposite(AlphaComposite.SrcOver);
        Art.hints(g);
        core.render(g);
        g.dispose();
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        buf.clear();
        buf.put(data);
        buf.flip();
        image.setData(buf);
    }
}
