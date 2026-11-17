package daemmerwald;

import java.awt.event.KeyEvent;

/**
 * Eingabezustand. Die Tasten-Codes sind AWT-VK-Codes (KeyEvent.VK_*),
 * die von {@link Main} aus jMonkeyEngine-Ereignissen übersetzt werden.
 * Mauskoordinaten liegen im virtuellen 1280x720-Raster.
 */
public class Input {
    private final boolean[] down = new boolean[1024];
    private final boolean[] pressed = new boolean[1024];
    public double mouseX, mouseY;
    public boolean mouseDown, mouseClicked, mouseReleased;
    public boolean rightDown;
    /** Mausbewegung seit dem letzten Frame (Pixel) – für die Kamera. */
    public double lookDX, lookDY;
    public double wheel;

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

    public void keyDown(int vk) {
        if (vk < 0 || vk >= down.length) return;
        if (!down[vk]) pressed[vk] = true;
        down[vk] = true;
    }

    public void keyUp(int vk) {
        if (vk >= 0 && vk < down.length) down[vk] = false;
    }

    public void mouseButton(boolean isDown) {
        if (isDown) { mouseDown = true; mouseClicked = true; }
        else { mouseDown = false; mouseReleased = true; }
    }

    public void releaseAll() {
        java.util.Arrays.fill(down, false);
        mouseDown = false;
        rightDown = false;
    }

    void endFrame() {
        java.util.Arrays.fill(pressed, false);
        mouseClicked = false;
        mouseReleased = false;
        lookDX = 0;
        lookDY = 0;
        wheel = 0;
    }
}
