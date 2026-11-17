package daemmerwald;

import java.awt.Graphics2D;

/** Ein Bildschirm des Spiels (Titel, Welt, Minispiel ...). */
public interface Scene {
    void update(double dt);

    void render(Graphics2D g);

    default void onEnter() {
    }
}
