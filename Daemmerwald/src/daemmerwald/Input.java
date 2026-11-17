package daemmerwald;

import java.awt.event.*;

/** Sammelt Tastatur- und Mauszustand; wird einmal pro Frame abgefragt. */
public class Input implements KeyListener, MouseListener, MouseMotionListener {
    private final boolean[] down = new boolean[1024];
    private final boolean[] pressed = new boolean[1024];
    public double mouseX, mouseY;
    public boolean mouseDown, mouseClicked, mouseReleased;
    private double scale = 1, offX, offY;

    void setTransform(double scale, double offX, double offY) {
        this.scale = scale;
        this.offX = offX;
        this.offY = offY;
    }

    public boolean isDown(int... codes) {
        for (int c : codes) if (c >= 0 && c < down.length && down[c]) return true;
        return false;
    }

    public boolean wasPressed(int... codes) {
        for (int c : codes) if (c >= 0 && c < pressed.length && pressed[c]) return true;
        return false;
    }

    /** Bestätigen: Enter, Leertaste oder E. */
    public boolean confirm() {
        return wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_E);
    }

    void endFrame() {
        java.util.Arrays.fill(pressed, false);
        mouseClicked = false;
        mouseReleased = false;
    }

    private void pos(MouseEvent e) {
        mouseX = (e.getX() - offX) / scale;
        mouseY = (e.getY() - offY) / scale;
    }

    @Override public void keyPressed(KeyEvent e) {
        int c = e.getKeyCode();
        if (c >= 0 && c < down.length) {
            if (!down[c]) pressed[c] = true;
            down[c] = true;
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        int c = e.getKeyCode();
        if (c >= 0 && c < down.length) down[c] = false;
    }

    @Override public void keyTyped(KeyEvent e) { }

    @Override public void mousePressed(MouseEvent e) {
        pos(e);
        mouseDown = true;
        mouseClicked = true;
        e.getComponent().requestFocusInWindow();
    }

    @Override public void mouseReleased(MouseEvent e) {
        pos(e);
        mouseDown = false;
        mouseReleased = true;
    }

    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
    @Override public void mouseDragged(MouseEvent e) { pos(e); }
    @Override public void mouseMoved(MouseEvent e) { pos(e); }
}
