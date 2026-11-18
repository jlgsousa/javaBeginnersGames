package daemmerwald;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Magierkarten: 24 berühmte (erfundene) Hexen und Zauberer des Dämmerwalds. Sie liegen an schwer erreichbaren
 * Orten, in Geheimräumen und weit draußen – wer gründlich erkundet, füllt die Sammlung.
 */
public final class Cards {
    public enum Mage {
        AMALTHEA("Amalthea Brombeerweis", "Erfand den Tee, der niemals kalt wird."),
        TORVIN("Torvin Eisenhaupt", "Der erste Troll, der fliegen lernte – mit Flügeln aus Birkenrinde."),
        ELSABET("Elsabet Nachtwind", "Kartierte jeden Stern über dem Dämmerwald."),
        GUNDRAM("Gundram der Leise", "Flüsterte unruhige Bäume in den Schlaf."),
        MIRELLA("Mirella Glanzfeder", "Gründete das Feendorf Laternhain."),
        OSKAR("Oskar Moorbart", "Braute dreihundert Tränke an einem einzigen Tag."),
        FENJA("Fenja Sturmauge", "Zähmte den Nordwind und lieh ihn den Seglern."),
        BALDUIN("Balduin Kieselklang", "Baute die singenden Treppen von Sternenhall."),
        YLVA("Ylva Silberranke", "Konnte mit Pilzen sprechen – und sie antworteten."),
        KASIMIR("Kasimir Dämmerglut", "Erfand den Funkenstoß, den jeder Schüler zuerst lernt."),
        RHONA("Rhona Tausendtau", "Heilte den Mondsee, als er schwarz geworden war."),
        BRAKKA("Brakka Felsenherz", "Trollkönigin, die den Ersten Pakt unterschrieb."),
        LISELOTTE("Liselotte Spiegelgrund", "Sah sich selbst in hundert Jahren – und lächelte."),
        THEODOR("Theodor Uhrwerk", "Baute den ersten Uhrwerk-Käfer aus einer alten Taschenuhr."),
        SANNA("Sanna Morgenrot", "Schrieb das Rezeptbuch der Feen."),
        GRIMWALD("Grimwald Schattenlos", "Der Magier ohne Schatten. Niemand weiß, wo er ihn verlor."),
        ISOLDE("Isolde Wolkenreiterin", "Umrundete das Meer auf einer einzigen Wolke."),
        PILZHUT("Meister Pilzhut", "Das allererste Pilzorakel. Seine Sporen träumen noch."),
        NEREA("Nerea Perlmutt", "Fand die erste Mondperle – im Schnabel einer Ente."),
        ULRICH("Ulrich Dornenfest", "Lehrte die Dornenranken, Fremde zu erkennen."),
        WILMA("Wilma Wurzelweise", "Brachte Bäumen das Wandern bei. Einige wandern noch."),
        ARVID("Arvid Sternenhall", "Erbaute den Zauberpalast in einer einzigen Mondnacht."),
        CORVINA("Corvina die Erste", "Die erste Hüterin des Herzbaums."),
        NAMENLOS("Die namenlose Fee", "Niemand kennt ihren Namen. Vielleicht ist sie ganz in der Nähe.");

        public final String title, fact;

        Mage(String title, String fact) {
            this.title = title;
            this.fact = fact;
        }

        public boolean legendary() { return this == CORVINA || this == NAMENLOS || this == ARVID; }
    }

    /** Fundort einer Karte: Welt oder Palast (lokal). */
    public record Spot(Mage mage, boolean palace, Vector3f pos, String where) { }

    public final List<Spot> spots = new ArrayList<>();
    private final List<Geometry> geos = new ArrayList<>();
    private float t;

    public Cards() {
        float vx = WorldGen.VILLAGE[0], vz = WorldGen.VILLAGE[1];
        world(Mage.MIRELLA, -72, -55, 5.6f, "Auf einem Baumhaus in Laternhain");
        world(Mage.TORVIN, -66, -42, 5.6f, "Auf dem zweiten Baumhaus in Laternhain");
        spots.add(new Spot(Mage.NEREA, false, new Vector3f(SpellPuzzles.LX + 1.5f, SpellPuzzles.ISLAND_Y + 0.9f, SpellPuzzles.LZ - 1.5f), "Auf der Mondsee-Insel"));
        world(Mage.BALDUIN, 128, -60, 1.2f, "Hoch am Rand des Steinbruchs");
        world(Mage.YLVA, -120, 96, 1.0f, "Im Pilzkreis");
        world(Mage.OSKAR, 8, 98, 1.0f, "Im Moor (dunkel!)");
        world(Mage.ULRICH, 134, 80, 1.0f, "In der Dornenschlucht");
        world(Mage.GRIMWALD, -132, -100, 1.0f, "Tief im Schattenhain");
        world(Mage.ISOLDE, 44, 200, 1.0f, "Beim Café auf der Klippe");
        world(Mage.CORVINA, 6, 22, 1.0f, "Hinter dem Herzbaum");
        world(Mage.KASIMIR, -20, -72, 1.0f, "Am Rand des Akademiehofs");
        world(Mage.RHONA, -152, -4, 1.0f, "Am Westufer des Mondsees");
        world(Mage.FENJA, -60, 186, 1.0f, "An der Klippe über dem Meer");
        world(Mage.WILMA, Home.X + 7, Home.Z - 7, 1.0f, "Im Garten hinter deinem Haus");
        world(Mage.SANNA, 320, -60, 1.0f, "Weit draußen im Osten der Wildnis");
        world(Mage.NAMENLOS, -280, 260, 1.0f, "Ganz weit draußen im Südwesten …");
        // Palast (lokale Koordinaten)
        palace(Mage.AMALTHEA, -11, 0, -17, "In einer Ecke des großen Saals");
        palace(Mage.ELSABET, 63, 22, 6, "In der geheimen Sternwarte");
        palace(Mage.LISELOTTE, 63, -9, 36, "In der Gruft unter dem Palast");
        palace(Mage.GUNDRAM, -29, 0, 9, "In der Bibliothek, hinter den Lesetischen");
        palace(Mage.THEODOR, 29, 0, -9, "Im Trankkeller, hinter den Kesseln");
        palace(Mage.PILZHUT, -28.5f, 0, -19, "In der verborgenen Schreibstube");
        palace(Mage.ARVID, 3, 0, -27, "In der Kammer hinter dem Kamin");
        palace(Mage.BRAKKA, 11, 0, 17.5f, "Im großen Saal, nahe dem Tor");
    }

    private void world(Mage m, float x, float z, float dy, String where) {
        spots.add(new Spot(m, false, new Vector3f(x, WorldGen.height(x, z) + dy, z), where));
    }

    private void palace(Mage m, float x, float y, float z, String where) {
        spots.add(new Spot(m, true, new Vector3f(x, y + 1.0f, z), where));
    }

    public static boolean has(GameState st, Mage m) { return st.flags.contains("card:" + m.name()); }

    public static int count(GameState st) {
        int n = 0;
        for (Mage m : Mage.values()) if (has(st, m)) n++;
        return n;
    }

    /** Kartenbild (für Welt und Sammlung). */
    public static BufferedImage image(Mage m, boolean found) {
        int w = 128, h = 180;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color gold = m.legendary() ? new Color(255, 200, 80) : new Color(225, 190, 120);
        g.setColor(new Color(40, 26, 60));
        g.fillRoundRect(0, 0, w, h, 16, 16);
        g.setPaint(new GradientPaint(0, 0, new Color(70, 50, 110), 0, h, new Color(25, 18, 40)));
        g.fillRoundRect(8, 8, w - 16, h - 16, 10, 10);
        g.setColor(gold);
        g.setStroke(new BasicStroke(4));
        g.drawRoundRect(4, 4, w - 8, h - 8, 14, 14);
        if (found) {
            // Silhouette mit Spitzhut
            int hue = m.ordinal() * 37;
            g.setColor(new Color(Color.HSBtoRGB(hue / 360f, 0.35f, 0.9f)));
            g.fillOval(44, 52, 40, 40);
            g.fillRoundRect(34, 92, 60, 50, 24, 24);
            g.setColor(new Color(Color.HSBtoRGB(hue / 360f, 0.6f, 0.45f)));
            g.fillPolygon(new int[]{38, 64, 90}, new int[]{62, 14, 62}, 3);
            g.setColor(Color.WHITE);
            for (int i = 0; i < 6; i++) g.fillOval(18 + (i * 23) % 92, 20 + (i * 41) % 60, 3, 3);
            g.setColor(gold);
            g.setFont(new Font("Serif", Font.BOLD, 13));
            String[] parts = m.title.split(" ", 2);
            FontMetrics fm = g.getFontMetrics();
            Art.str(g, parts[0], w / 2 - fm.stringWidth(parts[0]) / 2, 158);
            if (parts.length > 1) {
                g.setFont(new Font("Serif", Font.PLAIN, 11));
                fm = g.getFontMetrics();
                Art.str(g, parts[1], w / 2 - fm.stringWidth(parts[1]) / 2, 172);
            }
        } else {
            g.setColor(new Color(140, 120, 180));
            g.setFont(new Font("Serif", Font.BOLD, 60));
            Art.str(g, "?", 48, 110);
        }
        g.dispose();
        return img;
    }

    /** Karten in die Welt bzw. in den Palast setzen (nur noch nicht gefundene). */
    public void attach(Node worldRoot, Palace palace, GameState st) {
        if (attached) return;
        attached = true;
        for (Spot s : spots) {
            Material m = new Material(G3.am, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap", G3.tex(image(s.mage(), true)));
            m.setColor("GlowColor", s.mage().legendary() ? new ColorRGBA(0.6f, 0.45f, 0.1f, 1) : new ColorRGBA(0.35f, 0.25f, 0.45f, 1));
            m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            Geometry g = new Geometry("card", new Quad(0.5f, 0.7f));
            g.setMaterial(m);
            g.setQueueBucket(RenderQueue.Bucket.Transparent);
            g.setShadowMode(RenderQueue.ShadowMode.Off);
            Node holder = new Node("cardHolder");
            holder.attachChild(g);
            g.setLocalTranslation(-0.25f, -0.35f, 0);
            holder.setLocalTranslation(s.pos());
            (s.palace() ? palace.interior : worldRoot).attachChild(holder);
            geos.add(g);
        }
    }

    /** Karte aufheben, wenn man nahe genug ist. Liefert die gefundene Karte oder null. */
    public Spot pickup(GameState st, Vector3f player, boolean inPalace, Palace palace) {
        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i);
            if (s.palace() != inPalace || has(st, s.mage())) continue;
            Vector3f wp = s.palace() ? palace.toWorld(s.pos()) : s.pos();
            if (wp.distance(player.add(0, 1.0f, 0)) < 1.7f) {
                st.flags.add("card:" + s.mage().name());
                Geometry g = geos.get(i);
                if (g != null && g.getParent() != null) g.getParent().removeFromParent();
                return s;
            }
        }
        return null;
    }

    private boolean attached;

    public boolean attached() { return attached; }

    public void update(float dt, GameState st) {
        t += dt;
        for (int i = 0; i < geos.size(); i++) {
            Geometry g = geos.get(i);
            if (g == null || g.getParent() == null) continue;
            g.setCullHint(has(st, spots.get(i).mage()) ? com.jme3.scene.Spatial.CullHint.Always : com.jme3.scene.Spatial.CullHint.Inherit);
            g.getParent().setLocalRotation(new Quaternion().fromAngleAxis(t * 1.8f, Vector3f.UNIT_Y));
            g.setLocalTranslation(-0.25f, -0.35f + FastMath.sin(t * 2.4f) * 0.08f, 0);
        }
    }
}
