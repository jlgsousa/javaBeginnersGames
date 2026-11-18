package daemmerwald;

import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static daemmerwald.Look.rgb;

/**
 * Regie für die Kapitel 3–7: der verbotene Wald mit Aschfell, das Dorf mit Hedda (Laternen, Dieb, Flugstunde),
 * die vergessene Bibliothek, die Ruinen mit dem Steinwächter und das Finale gegen Malrik.
 */
public final class Saga {
    final WorldScene ws;
    final GameState st;
    final World3D world;
    public final Combat combat;

    /** Hinweis und Aktion für [E] (wie im Palast). */
    String label;
    Runnable action;
    /** Laufende Begegnung: "waves", "fox", "thief", "flight", "golem", "shade" … */
    String enc;
    private float encT;
    private int wave;
    private Vector3f checkpoint;
    private float checkYaw;

    public static final Look HEDDA = Look.human(rgb(226, 192, 168), rgb(120, 62, 44), rgb(70, 52, 44), rgb(205, 205, 210), rgb(60, 42, 30), true, Look.BUN);
    public static final Look FENN = Look.human(rgb(198, 150, 112), rgb(52, 72, 52), rgb(62, 52, 40), rgb(150, 92, 40), rgb(40, 30, 22), false, Look.SHORT);
    public static final Look MALRIK = Look.human(rgb(206, 196, 192), rgb(32, 20, 48), rgb(24, 16, 34), rgb(16, 16, 22), rgb(20, 20, 26), false, Look.BEARD);

    // Orte draußen
    static final Vector3f GROVE = new Vector3f(WorldGen.GROVE[0], 0, WorldGen.GROVE[1]);
    static final Vector3f HEDDA_POS = new Vector3f(-84.5f, 0, -42.5f);
    static final Vector3f BROOM_POS = new Vector3f(-82.4f, 0, -43.6f);
    static final Vector3f FENN_POS = new Vector3f(-70, 0, -36);
    static final Vector3f[] LANTERNS = {new Vector3f(-79, 0, -46), new Vector3f(-88, 0, -47.5f), new Vector3f(-78.5f, 0, -36.5f), new Vector3f(-86.5f, 0, -35.5f)};
    static final Vector3f[] THIEF_ROUTE = {new Vector3f(-70, 0, -36), new Vector3f(-62, 0, -44), new Vector3f(-62, 0, -56), new Vector3f(-72, 0, -64),
        new Vector3f(-80, 0, -58), new Vector3f(-76, 0, -48), new Vector3f(-68, 0, -28)};
    static final Vector3f RUNE_SHELF = new Vector3f(-28.4f, 0, 4.5f);

    private Figure hedda, fenn;
    private final Geometry[] lanternFlames = new Geometry[4];
    private final PointLight[] lanternLights = new PointLight[4];
    private Node broom;
    private final List<Spatial> rings = new ArrayList<>();
    private int ringIdx;
    private Combat.Thief thief;
    private Combat.AncientFox fox;
    private Combat.Golem golem;
    private Combat.Shade shade;
    private final List<Combat.Sentry> sentries = new ArrayList<>();
    private int pillarStep;
    private float muralT, trapT, dartT, liftT = -1;
    private int liftStack = -1;
    private boolean blockMoving;
    private float blockT;
    boolean broomRide;

    Saga(WorldScene ws, Combat.Host host) {
        this.ws = ws;
        this.st = ws.st;
        this.world = ws.world;
        combat = new Combat(world, host);
        buildGrove();
        buildVillage();
    }

    Depths depths() { return world.palace.depths; }

    Vector3f dw(Vector3f local) { return world.palace.toWorld(local); }

    float maxHearts() { return 5 + (st.flags.contains("equip:umhang") ? 1 : 0); }

    // ================================================================ Bauten draußen

    private void buildGrove() {
        Node n = new Node("grove");
        Material stoneM = G3.ph("mossy_rock", new ColorRGBA(0.7f, 0.72f, 0.78f, 1));
        for (int i = 0; i < 11; i++) {
            float a = i * FastMath.TWO_PI / 11 + 0.15f;
            float x = GROVE.x + FastMath.sin(a) * 24, z = GROVE.z + FastMath.cos(a) * 24;
            Geometry s = G3.geom("standingStone", G3.rock(300 + i, 1.2f), stoneM);
            s.setLocalScale(0.9f, 3.2f + (i % 3) * 0.6f, 0.7f);
            s.setLocalTranslation(x, WorldGen.height(x, z) + 1.5f, z);
            s.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            n.attachChild(s);
            world.addSolid(x, z, 1.1f);
            // Runen auf den Steinen glimmen violett
            Geometry rune = G3.geom("groveRune", new Sphere(6, 6, 0.16f), G3.emissive(new ColorRGBA(0.6f, 0.3f, 1f, 1)));
            rune.setLocalTranslation(x - FastMath.sin(a) * 0.8f, WorldGen.height(x, z) + 2.4f, z - FastMath.cos(a) * 0.8f);
            n.attachChild(rune);
        }
        // uralter, toter Baum in der Mitte hinten
        Geometry trunk = G3.geom("oldTree", new Cylinder(2, 14, 1.4f, 7, true), G3.ph("bark_brown_02", new ColorRGBA(0.6f, 0.55f, 0.6f, 1)));
        trunk.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        float tx = GROVE.x, tz = GROVE.z - 17;
        trunk.setLocalTranslation(tx, WorldGen.height(tx, tz) + 3.4f, tz);
        n.attachChild(trunk);
        world.addSolid(tx, tz, 1.6f);
        for (int i = 0; i < 6; i++) {
            Geometry br = G3.geom("branch", new Cylinder(2, 8, 0.35f, 5, true), trunk.getMaterial());
            float a = i * 1.05f;
            br.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI + 0.9f, a, 0));
            br.setLocalTranslation(tx + FastMath.sin(a) * 1.6f, WorldGen.height(tx, tz) + 7.2f, tz + FastMath.cos(a) * 1.6f);
            n.attachChild(br);
        }
        // Leuchtpilze und Nebel
        java.util.Random r = new java.util.Random(31);
        for (int i = 0; i < 30; i++) {
            float a = r.nextFloat() * 6.28f, d = 8 + r.nextFloat() * 16;
            float x = GROVE.x + FastMath.sin(a) * d, z = GROVE.z + FastMath.cos(a) * d;
            Geometry m = G3.geom("groveShroom", new Sphere(6, 8, 0.2f + r.nextFloat() * 0.15f), G3.emissive(new ColorRGBA(0.35f, 0.8f, 1f, 1).mult(0.7f)));
            m.setLocalScale(1, 0.5f, 1);
            m.setLocalTranslation(x, WorldGen.height(x, z) + 0.12f, z);
            n.attachChild(m);
        }
        com.jme3.effect.ParticleEmitter fog = G3.particles("groveFog", 70, "Effects/Smoke/Smoke.png",
            new ColorRGBA(0.35f, 0.3f, 0.5f, 0.22f), new ColorRGBA(0.2f, 0.15f, 0.3f, 0), 3f, 6f, 6f, 9f);
        fog.setImagesX(15);
        fog.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        fog.setShape(new com.jme3.effect.shapes.EmitterBoxShape(new Vector3f(-22, 0, -22), new Vector3f(22, 1, 22)));
        fog.getParticleInfluencer().setInitialVelocity(new Vector3f(0.2f, 0.05f, 0));
        fog.getParticleInfluencer().setVelocityVariation(1f);
        fog.setParticlesPerSec(8);
        fog.setLocalTranslation(GROVE.x, WorldGen.height(GROVE.x, GROVE.z) + 0.4f, GROVE.z);
        n.attachChild(fog);
        PointLight pl = new PointLight(new Vector3f(GROVE.x, WorldGen.height(GROVE.x, GROVE.z) + 5, GROVE.z), new ColorRGBA(0.55f, 0.35f, 1f, 1).mult(1.2f), 30);
        n.addLight(pl);
        world.root.attachChild(n);
    }

    private void buildVillage() {
        Node n = new Node("sagaVillage");
        hedda = new Figure(HEDDA);
        place(hedda.node, HEDDA_POS, -FastMath.HALF_PI);
        n.attachChild(hedda.node);
        world.addSolid(HEDDA_POS.x, HEDDA_POS.z, 0.5f);
        fenn = new Figure(FENN);
        place(fenn.node, FENN_POS, FastMath.PI);
        n.attachChild(fenn.node);
        // Besen an einem Ständer
        broom = new Node("broom");
        Geometry stick = G3.geom("broomStick", new Cylinder(2, 8, 0.04f, 1.9f, true), G3.lit(new ColorRGBA(0.45f, 0.3f, 0.16f, 1)));
        broom.attachChild(stick);
        Geometry bristles = G3.geom("broomBristles", new Cylinder(2, 10, 0.06f, 0.2f, 0.6f, true, false), G3.lit(new ColorRGBA(0.75f, 0.6f, 0.3f, 1)));
        bristles.setLocalTranslation(0, 0, -1.15f);
        broom.attachChild(bristles);
        broom.setLocalRotation(new Quaternion().fromAngleAxis(-1.2f, Vector3f.UNIT_X));
        broom.setLocalTranslation(BROOM_POS.x, WorldGen.height(BROOM_POS.x, BROOM_POS.z) + 0.9f, BROOM_POS.z);
        n.attachChild(broom);
        // vier kalte Laternen
        Material iron = G3.lit(new ColorRGBA(0.12f, 0.12f, 0.14f, 1));
        for (int i = 0; i < 4; i++) {
            Vector3f l = LANTERNS[i];
            float h = WorldGen.height(l.x, l.z);
            Geometry post = G3.geom("lanternPost", new Cylinder(2, 8, 0.08f, 2.6f, true), iron);
            post.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
            post.setLocalTranslation(l.x, h + 1.3f, l.z);
            n.attachChild(post);
            Spatial lamp = G3.model("Lantern_01");
            lamp.setLocalScale(1.4f);
            lamp.setLocalTranslation(l.x, h + 2.5f, l.z);
            n.attachChild(lamp);
            Geometry f = G3.geom("lanternFlame", new Sphere(8, 8, 0.13f), G3.emissive(new ColorRGBA(1f, 0.7f, 0.3f, 1)));
            f.setLocalTranslation(l.x, h + 2.75f, l.z);
            f.setCullHint(Spatial.CullHint.Always);
            n.attachChild(f);
            lanternFlames[i] = f;
            lanternLights[i] = new PointLight(new Vector3f(l.x, h + 2.9f, l.z), new ColorRGBA(1f, 0.7f, 0.35f, 1).mult(1.4f), 9);
            world.addSolid(l.x, l.z, 0.3f);
        }
        world.root.attachChild(n);
    }

    private void place(Spatial s, Vector3f p, float yaw) {
        s.setLocalTranslation(p.x, world.height(p.x, p.z), p.z);
        s.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
    }

    /** Spielstand anwenden (Laternen, Bibliothek, Ruinen, Portale). */
    void apply() {
        for (int i = 0; i < 4; i++) setLantern(i, st.flags.contains("lantern:" + i));
        boolean fennGone = st.flags.contains("q4:dieb") || Chapters.chapter(st) != 4;
        fenn.node.setCullHint(fennGone ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        Depths d = depths();
        int lit = 0;
        for (int i = 0; i < 3; i++) if (st.flags.contains("lib:candle" + i)) lit++;
        for (int i = 0; i < 3; i++) d.candleFlames[i].setCullHint(st.flags.contains("lib:candle" + i) ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        d.setLibraryLit(lit >= 3);
        for (int i = 0; i < 3; i++) {
            boolean placed = st.flags.contains("lib:stack" + i);
            Vector3f at = placed ? Depths.BRIDGE_SLOT[i] : Depths.BOOKSTACK[i];
            d.bookStacks[i].setLocalTranslation(at.x, at.y + (placed ? -0.33f : 0.35f), at.z);
            if (placed) d.bookStacks[i].setLocalScale(1.25f, 1, 1.15f);
        }
        if (st.flags.contains("lib:stack0") && st.flags.contains("lib:stack1") && st.flags.contains("lib:stack2")) d.openBridge();
        boolean solved = st.flags.contains("lib:pillars");
        d.bookOfFive.setCullHint(solved ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        for (int i = 0; i < 4; i++) d.pillarRunes[i].getMaterial().setColor("Color", solved ? new ColorRGBA(1f, 0.8f, 0.35f, 1) : new ColorRGBA(0.5f, 0.45f, 0.6f, 1));
        d.libPortal.setCullHint(Chapters.chapter(st) >= 6 ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        boolean plate = st.flags.contains("ruin:plate");
        d.blockOnPlate(plate);
        d.openGate(plate);
        d.heartPortal.setCullHint(Chapters.chapter(st) >= 7 ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        for (int i = 0; i < 5; i++) d.lightBrazier(i, st.flags.contains("finale"));
        combat.maxHearts = maxHearts();
        combat.hearts = Math.min(combat.hearts, combat.maxHearts);
    }

    private void setLantern(int i, boolean on) {
        lanternFlames[i].setCullHint(on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        world.root.removeLight(lanternLights[i]);
        if (on) world.root.addLight(lanternLights[i]);
    }

    // ================================================================ Ziele

    static String[] objective(GameState st, String head, int c, int s) {
        return switch (c) {
            case 3 -> switch (s) {
                case 0 -> new String[]{head, "Lerne Protego (Schutzschild)", "Zauberunterricht bei Professorin Funkelstein"};
                case 1 -> new String[]{head, "Finde den Hain der Alten im verbotenen Wald", "Nordwestlich, hinter dem Schattenhain – dem Pfad folgen"};
                case 2 -> new String[]{head, "Überstehe den Angriff im Hain", "Flatterer: Arcanum · Dornen: Ignis · Irrgeister: erst Lumen · Q zaubern, R wechseln"};
                case 3 -> new String[]{head, "Befreie Aschfell, den Uralten", "Ausweichen · Kristalle zerschlagen · Protego wirft Kugeln zurück · Lumen"};
                default -> new String[]{head, "Kapitel abgeschlossen", ""};
            };
            case 4 -> switch (s) {
                case 0 -> new String[]{head, "Sprich mit Hedda Wendelin in Laternhain", "Das Feendorf am Laternbach, westlich deines Hauses"};
                case 1 -> new String[]{head, "Hilf Hedda: " + villageLeft(st), "Laternen: Ignis · Dieb: fangen oder Levitaris · Flugstunde: Besen bei Hedda"};
                default -> new String[]{head, "Bring Hedda die gute Nachricht", "Laternhain"};
            };
            case 5 -> switch (s) {
                case 0 -> new String[]{head, "Öffne das Runenregal in der Palastbibliothek", "Westwand der Bibliothek – mit Heddas Runenschlüssel"};
                case 1 -> new String[]{head, "Entzünde die drei Leuchter", "Es ist stockdunkel: Ignis auf die kalten Leuchter"};
                case 2 -> new String[]{head, "Baue eine Brücke über die Schlucht", "Levitaris auf die drei Bücherstapel"};
                case 3 -> new String[]{head, "Löse das Rätsel der Runensäulen", "Lumen auf das Wandbild zeigt die Reihenfolge · Arcanum auf die Säulen · Meide die Wächteraugen (Lumen blendet sie)"};
                case 4 -> new String[]{head, "Lies das Buch der Fünf", "Auf dem Pult an der Nordwand"};
                default -> new String[]{head, "Kapitel abgeschlossen", ""};
            };
            case 6 -> switch (s) {
                case 0 -> new String[]{head, "Steige hinab in die alten Ruinen", "Der violette Torbogen in der Nordostecke der vergessenen Bibliothek"};
                case 1 -> new String[]{head, "Überwinde die Fallen der Ruinen", "Flammen: Takt abwarten · Pfeile: Protego · Gitter: Levitaris auf den Steinblock"};
                case 2 -> new String[]{head, "Besiege den Steinwächter", "Schockwelle: springen · Levitaris auf Trümmersteine · dann Arcanum aufs Runenherz"};
                default -> new String[]{head, "Kapitel abgeschlossen", ""};
            };
            case 7 -> switch (s) {
                case 0 -> new String[]{head, "Hinab zum Schlafenden", "Der Torbogen hinter dem Steinwächter"};
                case 1 -> new String[]{head, "Halte Malrik auf!", "Protego · Diener · Gründerfeuer (Ignis) · Sternenblitz (Lumen, dann Arcanum)"};
                default -> new String[]{head, "Sternenhall ist gerettet", ""};
            };
            default -> new String[]{"Epilog · Sternenhall ist erwacht", "Frei spielen", "Karten, Raritäten, Freunde, Duelle – und die Akademie öffnet wieder ihre Tore"};
        };
    }

    private static String villageLeft(GameState st) {
        List<String> l = new ArrayList<>();
        if (!st.flags.contains("q4:laternen")) l.add("Laternen");
        if (!st.flags.contains("q4:dieb")) l.add("Dieb");
        if (!st.flags.contains("q4:flug")) l.add("Flugstunde");
        return l.isEmpty() ? "alles erledigt!" : String.join(" · ", l);
    }

    // ================================================================ Takt

    void update(float dt, boolean usePressed) {
        label = null;
        action = null;
        combat.maxHearts = maxHearts();
        combat.regen = st.flags.contains("equip:amulett") ? 0.8f : 0.35f;
        combat.update(dt);
        encT += dt;
        hedda.update(dt, 0, false);
        if (fenn.node.getCullHint() != Spatial.CullHint.Always && thief == null) fenn.update(dt, 0, false);
        muralT = Math.max(0, muralT - dt);
        if (muralT > 0 || depths().muralGlyphs != null) {
            float a = muralT > 0 ? Math.min(1, muralT) : 0.04f;
            depths().muralGlyphs.getMaterial().setColor("Color", new ColorRGBA(1, 1, 1, a));
        }
        if (combat.hearts <= 0) { knockout(); return; }
        int c = Chapters.chapter(st), s = Chapters.step(st);
        Vector3f pos = ws.pos;
        if (c == 3) chapter3(s, pos);
        else if (c == 4) chapter4(s, pos, dt);
        updateDepths(c, s, pos, dt);
        if ("thief".equals(enc)) updateThief();
        if ("flight".equals(enc)) updateFlight(dt);
        if ("waves".equals(enc)) updateWaves();
        if (enc != null && enc.equals("fox") && fox != null && fox.dead && fox.phase < 2) { /* befreit: Dialog läuft */ }
        if ("shade".equals(enc) && shade != null && shade.phase == 1 && encT > 2 && combat.alive(null) == 1) shade.summonOver();
        if (!ws.world.inPalace && Chapters.chapter(st) >= 4 && hedda.node.getParent() != null) {
            float d = horiz(pos, HEDDA_POS);
            if (d < 3.2f && label == null) {
                label = "[E] Mit Hedda Wendelin sprechen";
                action = this::talkHedda;
            }
        }
    }

    static float horiz(Vector3f a, Vector3f b) { return FastMath.sqrt(FastMath.sqr(a.x - b.x) + FastMath.sqr(a.z - b.z)); }

    private void setCheckpoint(Vector3f p, float yaw) { checkpoint = p.clone(); checkYaw = yaw; }

    private void knockout() {
        combat.hearts = combat.maxHearts;
        String e = enc;
        endEncounter();
        Vector3f back = checkpoint != null ? checkpoint : ws.pos.clone();
        ws.teleportTo(back, checkYaw);
        ws.say(null, null, new String[]{"*Dir wird schwarz vor Augen … Als du wieder zu dir kommst, bist du ein Stück zurück.",
            "*Atme durch – und versuch es noch einmal. (Protego hält jeden Treffer ab!)"}, () -> restart(e));
    }

    private void restart(String e) {
        if (e == null) return;
        switch (e) {
            case "waves", "fox" -> { Chapters.set(st, 3, 1); }
            case "golem" -> startGolem();
            case "shade" -> startShade();
            case "thief" -> { }
            default -> { }
        }
    }

    void endEncounter() {
        combat.clear();
        enc = null;
        fox = null;
        golem = null;
        shade = null;
        thief = null;
        sentries.clear();
        broomRide = false;
        for (Spatial r : rings) r.removeFromParent();
        rings.clear();
    }

    public boolean bossFight() { return combat.boss != null && !combat.boss.dead; }

    // ================================================================ Kapitel 3: Der verbotene Wald

    private void chapter3(int s, Vector3f pos) {
        if (s == 0 && st.spells.contains(Spell.SCHILD)) {
            ws.say(null, null, new String[]{"*Protego schimmert um deine Hände. Professorin Funkelstein nickt dir zu:",
                "*„Die Tafel der Gründer spricht vom Wald. Hinter dem Schattenhain, im Nordwesten, liegt der Hain der Alten. Dorthin geht niemand mehr.“",
                "*„Nimm Protego mit. Und vergiss nicht: Jeder Zauber hat seine Stärke. Arcanum, Ignis, Lumen – du wirst alle brauchen.“"}, null);
            ws.advance(3, 1);
            return;
        }
        float d = horiz(pos, GROVE);
        if (s == 1 && d < 26 && !world.inPalace) {
            Vector3f back = GROVE.add(new Vector3f(pos.x - GROVE.x, 0, pos.z - GROVE.z).normalizeLocal().mult(25));
            back.y = world.height(back.x, back.z);
            setCheckpoint(back, FastMath.atan2(GROVE.x - back.x, GROVE.z - back.z));
            ws.say(null, null, new String[]{"*Die Bäume stehen hier so dicht, dass kein Licht durchdringt. Ein Kreis aus alten Steinen glimmt violett.",
                "*Etwas raschelt in den Wipfeln. Dann noch etwas. Die Schatten haben dich bemerkt!",
                "*Halte durch! Schattenflatterer: Arcanum · Dornenwürger: nur Ignis · Irrgeister: erst mit Lumen sichtbar machen. Und Protego (R wechseln, Q zaubern) hält jeden Treffer ab."},
                () -> { ws.advance(3, 2); startWaves(); });
            return;
        }
        if ((s == 2 || s == 3) && enc == null && d < 30 && !world.inPalace && ws.dialog == null) {
            if (s == 2) startWaves(); else startFox();
        }
        if ((s == 2 || s == 3) && enc != null && d > 48) {
            endEncounter();
            Chapters.set(st, 3, 1);
            ws.floatText("Du bist aus dem Hain geflohen …", pos.add(0, 2.4f, 0), new Color(220, 190, 255));
        }
    }

    void startWaves() {
        endEncounter();
        enc = "waves";
        wave = 0;
        encT = 0;
        spawnWave();
    }

    private Vector3f ringPos(Vector3f c, float r, float a) {
        float x = c.x + FastMath.sin(a) * r, z = c.z + FastMath.cos(a) * r;
        return new Vector3f(x, world.height(x, z), z);
    }

    private void spawnWave() {
        wave++;
        float base = FastMath.nextRandomFloat() * 6;
        switch (wave) {
            case 1 -> { for (int i = 0; i < 4; i++) combat.add(combat.new Bat(ringPos(GROVE, 14, base + i * 1.6f))); }
            case 2 -> {
                for (int i = 0; i < 2; i++) combat.add(combat.new Plant(ringPos(GROVE, 10, base + i * 3.1f)));
                for (int i = 0; i < 2; i++) combat.add(combat.new Bat(ringPos(GROVE, 15, base + 1.5f + i * 3.1f)));
            }
            default -> {
                for (int i = 0; i < 3; i++) combat.add(combat.new Ghost(ringPos(GROVE, 13, base + i * 2.1f)));
                combat.add(combat.new Plant(ringPos(GROVE, 6, base + 1)));
            }
        }
        ws.bigText("Welle " + wave + " / 3");
        if (ws.game.app.music != null) ws.game.app.music.sfx("roar", 1.6f);
    }

    private void updateWaves() {
        if (combat.alive(null) > 0) { encT = 0; return; }
        if (encT < 2.2f) return;
        if (wave < 3) { spawnWave(); return; }
        enc = null;
        ws.say(null, null, new String[]{"*Stille. Die letzten Schatten zerfallen zu Asche.",
            "*Doch dann bebt der Boden. Zwischen den Steinen erhebt sich etwas Riesiges – ein Fuchs, so groß wie ein Haus, das Fell grau wie Asche.",
            "*Drei schwarze Kristalle kreisen über seinem Rücken. Seine Augen glühen rot. Er erkennt dich nicht."},
            () -> { ws.advance(3, 3); startFox(); });
    }

    void startFox() {
        endEncounter();
        enc = "fox";
        Vector3f at = GROVE.add(0, 0, -8);
        at.y = world.height(at.x, at.z);
        fox = combat.add(combat.new AncientFox(at));
        combat.boss = fox;
        combat.bossHint = "Er springt – weich aus! Ist er benommen: Arcanum oder Ignis auf die Schattenkristalle.";
        fox.onPhase2 = () -> {
            for (int i = 0; i < 3; i++) combat.add(combat.new Ghost(ringPos(GROVE, 12, i * 2.1f)));
        };
        fox.onCleansed = this::foxCleansed;
        if (ws.game.app.music != null) ws.game.app.music.sfx("roar", 0.8f);
    }

    private void foxCleansed() {
        world.burst(fox.center(), new ColorRGBA(1f, 0.95f, 0.75f, 1), 200);
        st.flags.add("combo:" + Combat.Combo.STERNENBLITZ.name());
        st.earn(200);
        List<String> l = new ArrayList<>(List.of(
            "*Das Licht frisst die Schatten aus seinem Fell. Darunter ist es silbern, wie Mondlicht auf Schnee.",
            "Aschfell: „Mensch … Du hast mich zurückgeholt. Ich war Wächter dieses Waldes, als Sternenhall noch ein Hügel war.“",
            "Aschfell: „Es waren nicht fünf Gründer. Es waren sechs. Der sechste hieß Malrik. Er wollte die Kraft unter dem Schloss für sich.“",
            "Aschfell: „Die Fünf haben ihn verbannt und seinen Namen ausgelöscht. Doch Schatten vergessen nicht. Er war es, der mich verdorben hat.“",
            "Aschfell: „Geh nach Laternhain, ins Feendorf. Dort lebt Hedda Wendelin – die letzte Nachfahrin der Fünf. Sie hütet einen Schlüssel.“",
            "Aschfell: „Und nimm dies: Wenn du Lumen und gleich danach Arcanum sprichst, verbinden sie sich zum Sternenblitz.“",
            "*✦ Neue Zauber-Kombination: Sternenblitz (Lumen → Arcanum innerhalb von 2 Sekunden) ✦",
            "*Kapitel 3 abgeschlossen! Belohnung: 200 Glimmertaler."));
        l.addAll(st.addXp(150));
        ws.say("Aschfell", null, l.toArray(new String[0]), () -> {
            endEncounter();
            ws.advance(4, 0);
        });
    }

    // ================================================================ Kapitel 4: Das Dorf

    private void chapter4(int s, Vector3f pos, float dt) {
        if (world.inPalace) return;
        // Dieb wartet am Ostrand des Dorfes
        if (s == 1 && !st.flags.contains("q4:dieb") && enc == null && horiz(pos, FENN_POS) < 3.4f) {
            label = "[E] Den Fremden mit der gestohlenen Laterne stellen";
            action = this::startThief;
        }
        if (s == 1 && !st.flags.contains("q4:flug") && enc == null && horiz(pos, BROOM_POS) < 2.6f) {
            label = "[E] Flugstunde auf Heddas Besen";
            action = this::startFlight;
        }
    }

    private void talkHedda() {
        int c = Chapters.chapter(st), s = Chapters.step(st);
        if (c == 4 && s == 0) {
            ws.say("Hedda Wendelin", HEDDA, new String[]{
                "Ein Kind aus Sternenhall? Hier? … Du riechst nach Wald. Und nach altem Licht. Du warst bei Aschfell.",
                "Ja, ich bin eine Wendelin. Meine Urururgroßmutter war eine der Fünf. Und ja – ich habe den Runenschlüssel.",
                "Aber ich gebe ihn nicht irgendwem. Zeig mir, dass du Laternhain helfen kannst:",
                "Erstens: Die vier Laternen am Platz sind erloschen. Ohne sie finden die Feen nachts nicht heim. Ignis!",
                "Zweitens: Ein Fremder hat meine Sternlaterne gestohlen. Er lungert am Ostrand herum. Fang ihn!",
                "Drittens: Wer in die Tiefe will, muss fliegen können. Nimm meinen Besen – zehn Ringe, bevor die Kerze abbrennt."
            }, () -> ws.advance(4, 1));
            return;
        }
        if (c == 4 && s == 1) {
            boolean done = st.flags.contains("q4:laternen") && st.flags.contains("q4:dieb") && st.flags.contains("q4:flug");
            if (!done) {
                ws.say("Hedda Wendelin", HEDDA, new String[]{"Noch offen: " + villageLeft(st) + ".",
                    "Laternen mit Ignis anzünden · den Dieb am Ostrand fangen (Levitaris oder Arcanum halten ihn auf) · Flugstunde am Besen neben mir."}, null);
                return;
            }
            st.flags.add("runenschluessel");
            st.flags.add("equip:umhang");
            st.earn(180);
            List<String> l = new ArrayList<>(List.of(
                "Hedda Wendelin: Du hast es geschafft. Laternhain leuchtet wieder – und meine Sternlaterne ist zurück.",
                "Hedda Wendelin: Hier ist der Runenschlüssel. In der Bibliothek von Sternenhall gibt es ein Regal ohne Bücher. Dort passt er hinein.",
                "Hedda Wendelin: Und nimm diesen Umhang. Er hat meiner Familie gehört. Er hält mehr aus, als man denkt.",
                "*✦ Ausrüstung: Wächterumhang (+1 Herz) · Schlüssel: Runenschlüssel ✦",
                "*Kapitel 4 abgeschlossen! Belohnung: 180 Glimmertaler."));
            l.addAll(st.addXp(150));
            ws.say("Hedda Wendelin", HEDDA, l.toArray(new String[0]), () -> ws.advance(5, 0));
            return;
        }
        String[] chat = {
            "Die Feen sagen, in Sternenhall brennt wieder Licht. Das ist dein Werk, nicht wahr?",
            "Meine Urururgroßmutter schrieb: „Das Herz unter dem Schloss ist kein Ungeheuer. Es ist das Licht, das den Wald am Leben hält.“",
            "Pass auf dich auf, Kind. Malrik war klug. Klüger als die Fünf zusammen."};
        ws.say("Hedda Wendelin", HEDDA, new String[]{chat[(int) (st.playTime / 60) % chat.length]}, null);
    }

    private void startThief() {
        endEncounter();
        enc = "thief";
        encT = 0;
        Vector3f[] route = new Vector3f[THIEF_ROUTE.length];
        for (int i = 0; i < route.length; i++) route[i] = world.ground(THIEF_ROUTE[i].x, THIEF_ROUTE[i].z);
        fenn.node.setCullHint(Spatial.CullHint.Always);
        thief = combat.add(combat.new Thief(FENN, route));
        setCheckpoint(ws.pos, 0);
        ws.say("Fenn", FENN, new String[]{"Was willst du? Die Laterne? Die hab ich … gefunden. Fang mich doch!"}, null);
    }

    private void updateThief() {
        if (thief == null) return;
        float d = horiz(ws.pos, thief.pos);
        if (thief.caught) {
            st.flags.add("q4:dieb");
            st.earn(40);
            endEncounter();
            ws.say("Fenn", FENN, new String[]{"Schon gut, schon gut! Hier ist die Laterne. Ein Mann im schwarzen Mantel hat mir Gold dafür versprochen …",
                "Er sagte, die Laterne der Wendelins dürfe nie wieder leuchten. Unheimlicher Kerl. Ich hab mich nicht getraut, Nein zu sagen.",
                "*Du hast Heddas Sternlaterne zurück! (+40 Taler)"}, () -> apply());
            return;
        }
        if (encT > 75 || d > 45) {
            endEncounter();
            apply();
            fenn.node.setCullHint(Spatial.CullHint.Inherit);
            ws.say(null, null, new String[]{"*Er ist entwischt … aber Fenn kommt immer wieder an den Ostrand zurück. Versuch es noch einmal!",
                "*Tipp: Levitaris oder Arcanum (Q) halten ihn ein paar Sekunden fest."}, null);
        } else {
            label = "Fang den Dieb!  " + (int) (75 - encT) + " s" + (thief.stun > 0 ? "  – er hängt fest, schnell!" : "");
        }
    }

    // ---------------------------------------------------------------- Flugstunde

    void startFlight() {
        endEncounter();
        enc = "flight";
        encT = 0;
        ringIdx = 0;
        broomRide = true;
        Vector3f c = new Vector3f(-84, 0, -44);
        Material m = G3.emissive(new ColorRGBA(1f, 0.8f, 0.35f, 1));
        for (int i = 0; i < 10; i++) {
            float a = i * FastMath.TWO_PI / 10 + 0.3f;
            float r = 22 + (i % 3) * 6;
            float x = c.x + FastMath.sin(a) * r, z = c.z + FastMath.cos(a) * r;
            float y = Math.max(world.height(x, z), WorldGen.WATER) + 7 + (i % 4) * 2.5f;
            Geometry ring = G3.geom("flightRing", new Torus(28, 8, 0.18f, 2.4f), m.clone());
            ring.setLocalTranslation(x, y, z);
            ring.setLocalRotation(new Quaternion().fromAngleAxis(a + FastMath.HALF_PI, Vector3f.UNIT_Y));
            ring.setShadowMode(RenderQueue.ShadowMode.Off);
            world.root.attachChild(ring);
            rings.add(ring);
        }
        ws.pos.y += 1.5f;
        ws.say("Hedda Wendelin", HEDDA, new String[]{"Aufsitzen! W fliegt vorwärts, die Maus lenkt, Leertaste steigt, Umschalt sinkt.",
            "Zehn goldene Ringe, der Reihe nach. Der nächste leuchtet am hellsten. Die Kerze brennt 100 Sekunden – los!"}, null);
    }

    private void updateFlight(float dt) {
        if (ws.dialog != null) { encT = 0; }
        for (int i = 0; i < rings.size(); i++) {
            Geometry r = (Geometry) rings.get(i);
            float a = i == ringIdx ? 0.8f + 0.2f * FastMath.sin(ws.treasureTime() * 6) : i < ringIdx ? 0.12f : 0.35f;
            r.getMaterial().setColor("Color", new ColorRGBA(1f, 0.8f, 0.35f, 1).mult(a));
            r.getMaterial().setColor("GlowColor", new ColorRGBA(1f, 0.8f, 0.35f, 1).mult(a));
        }
        if (ringIdx < rings.size() && rings.get(ringIdx).getLocalTranslation().distance(ws.pos.add(0, 0.8f, 0)) < 3.2f) {
            world.burst(rings.get(ringIdx).getLocalTranslation(), new ColorRGBA(1f, 0.85f, 0.4f, 1), 50);
            if (ws.game.app.music != null) ws.game.app.music.sfx("chime", 1 + ringIdx * 0.06f);
            ringIdx++;
        }
        if (ringIdx >= rings.size()) {
            st.flags.add("q4:flug");
            st.earn(40);
            endEncounter();
            ws.say("Hedda Wendelin", HEDDA, new String[]{"Hervorragend! Du fliegst, als hättest du nie etwas anderes getan. (+40 Taler)"}, null);
            return;
        }
        if (encT > 100) {
            endEncounter();
            ws.say("Hedda Wendelin", HEDDA, new String[]{"Die Kerze ist abgebrannt. Lande erst mal … und dann gleich noch einmal!"}, null);
            return;
        }
        label = "Flugstunde: Ring " + (ringIdx + 1) + "/10 · " + (int) (100 - encT) + " s  (W vor · Maus lenkt · Leertaste hoch · Umschalt runter)";
    }

    // ================================================================ Unter dem Schloss (Kapitel 5–7)

    private void updateDepths(int c, int s, Vector3f pos, float dt) {
        if (!world.inPalace) return;
        Palace pal = world.palace;
        Depths d = depths();
        // Runenregal in der Palastbibliothek
        if (st.flags.contains("runenschluessel") && dw(RUNE_SHELF).distance(pos) < 2.6f) {
            label = "[E] Den Runenschlüssel ins leere Regal stecken – hinab in die vergessene Bibliothek";
            action = () -> {
                if (ws.game.app.music != null) ws.game.app.music.sfx("door", 0.8f);
                ws.teleportTo(dw(Depths.LIB_ARRIVE), FastMath.PI);
                if (c == 5 && s == 0) {
                    ws.say(null, null, new String[]{"*Das Regal schwingt zur Seite. Eine Wendeltreppe führt hinab, tiefer und tiefer …",
                        "*Unten: Dunkelheit. Es riecht nach Staub und Pergament. Irgendwo vor dir stehen drei kalte Leuchter.",
                        "*Bring Licht – aber mit Feuer, nicht mit Lumen. Die Leuchter wollen brennen. (Ignis)"}, null);
                    ws.advance(5, 1);
                }
            };
            return;
        }
        if (dw(Depths.LIB_BACK).distance(pos) < 2.2f) {
            label = "[E] Die Wendeltreppe hinauf in die Palastbibliothek";
            action = () -> ws.teleportTo(dw(new Vector3f(-26, 0, 4.5f)), FastMath.HALF_PI);
            return;
        }
        if (c >= 6 && dw(Depths.TO_RUINS).distance(pos) < 2.6f) {
            label = "[E] Hinab in die alten Ruinen";
            action = () -> {
                if (ws.game.app.music != null) ws.game.app.music.sfx("door", 0.6f);
                ws.teleportTo(dw(Depths.RUIN_ARRIVE), FastMath.PI);
                if (c == 6 && s == 0) {
                    ws.say(null, null, new String[]{"*Die Ruinen von Ur-Sternenhall – die erste Akademie, lange bevor das Schloss darüber gebaut wurde.",
                        "*Am Boden: rußgeschwärzte Düsen. An der Wand: kleine Löcher. Wer hier herunterkam, sollte nicht weiterkommen.",
                        "*Flammen kommen im Takt – warte die Pause ab. Gegen Pfeile hilft Protego."}, null);
                    ws.advance(6, 1);
                }
            };
            return;
        }
        if (dw(Depths.RUIN_BACK).distance(pos) < 2.2f) {
            label = "[E] Zurück hinauf in die vergessene Bibliothek";
            action = () -> ws.teleportTo(dw(Depths.TO_RUINS.add(-2, 0, 2)), 0);
            return;
        }
        if (c >= 7 && dw(Depths.TO_HEART).distance(pos) < 2.6f) {
            label = "[E] Hinab zum Schlafenden";
            action = () -> {
                ws.teleportTo(dw(Depths.HEART_ARRIVE), FastMath.PI);
                if (c == 7 && s == 0) meetMalrik();
            };
            return;
        }
        if (dw(new Vector3f(210, Depths.HY, 19.3f)).distance(pos) < 2.2f && enc == null) {
            label = "[E] Zurück in die Halle des Steinwächters";
            action = () -> ws.teleportTo(dw(Depths.TO_HEART.add(0, 0, 3)), 0);
            return;
        }
        if (c == 5) library(s, pos, d, dt);
        if (c == 6) ruins(s, pos, d, dt);
        if (c == 7 && s == 1 && enc == null && pal.roomAt(pos.x, pos.z) == d.heart) startShade();
        if (c == 7 && s == 1 && "shade".equals(enc) && shade != null && shade.phase == 2) {
            int lit = 0;
            for (int i = 0; i < 5; i++) if (d.braziers[i].getCullHint() != Spatial.CullHint.Always) lit++;
            if (lit >= 5) shade.firesDone();
        }
    }

    // ---------------------------------------------------------------- Die vergessene Bibliothek

    private void library(int s, Vector3f pos, Depths d, float dt) {
        // schwebender Bücherstapel auf dem Weg zur Brücke
        if (liftStack >= 0) {
            liftT += dt;
            Vector3f from = dw(Depths.BOOKSTACK[liftStack]), to = dw(Depths.BRIDGE_SLOT[liftStack]);
            float k = Math.min(1, liftT / 1.6f);
            Vector3f p = from.clone().interpolateLocal(to, k);
            p.y += FastMath.sin(k * FastMath.PI) * 2.5f;
            d.bookStacks[liftStack].setLocalTranslation(world.palace.toLocal(p).addLocal(0, 0.35f - k * 0.68f, 0));
            if (k >= 1) {
                st.flags.add("lib:stack" + liftStack);
                liftStack = -1;
                apply();
                int n = 0;
                for (int i = 0; i < 3; i++) if (st.flags.contains("lib:stack" + i)) n++;
                if (n >= 3 && s == 2) {
                    ws.say(null, null, new String[]{"*Die drei Stapel schließen die Lücke. Eine Brücke aus Büchern – sie hält!",
                        "*Auf der anderen Seite: vier Säulen mit Runen. Und zwei schwebende Augen, die langsam ihre Runden ziehen.",
                        "*Ein Wandbild an der Nordwand ist kaum zu erkennen. Vielleicht zeigt Licht mehr …",
                        "*Lass dich nicht von den Augen sehen! Lumen blendet sie für ein paar Sekunden."}, null);
                    ws.advance(5, 3);
                }
            }
        }
        if (s == 3) {
            if (sentries.isEmpty() && enc == null) spawnSentries(d);
            if (world.palace.roomAt(pos.x, pos.z) == d.libS && !sentries.isEmpty()) { combat.clear(); sentries.clear(); enc = null; }
        }
        if (s == 4 && dw(Depths.LECTERN).distance(pos) < 2.4f) {
            label = "[E] Das Buch der Fünf aufschlagen";
            action = this::readBookOfFive;
        }
    }

    private void spawnSentries(Depths d) {
        if (world.palace.roomAt(ws.pos.x, ws.pos.z) != d.libN) return;
        enc = "sneak";
        Vector3f[][] routes = {
            {dw(new Vector3f(104, Depths.LY, -6)), dw(new Vector3f(132, Depths.LY, -6))},
            {dw(new Vector3f(126, Depths.LY, -21)), dw(new Vector3f(110, Depths.LY, -21)), dw(new Vector3f(110, Depths.LY, -13)), dw(new Vector3f(126, Depths.LY, -13))}};
        for (Vector3f[] r : routes) {
            Combat.Sentry se = combat.add(combat.new Sentry(r, 2.4f));
            se.onSpot = this::spotted;
            sentries.add(se);
        }
    }

    private void spotted() {
        if (ws.dialog != null) return;
        pillarStep = 0;
        for (int i = 0; i < 4; i++) depths().pillarRunes[i].getMaterial().setColor("Color", new ColorRGBA(0.5f, 0.45f, 0.6f, 1));
        if (ws.game.app.music != null) ws.game.app.music.sfx("roar", 1.8f);
        ws.say(null, null, new String[]{"*Das Auge hat dich gesehen! Ein greller Blitz – und du stehst wieder am Ende der Bücherbrücke.",
            "*Die Runen der Säulen sind erloschen. Blende die Augen mit Lumen, bevor sie dich erfassen."}, null);
        ws.teleportTo(dw(Depths.LIB_NORTH_SAFE), FastMath.PI);
    }

    private void readBookOfFive() {
        st.flags.add("combo:" + Combat.Combo.FEUERKOMET.name());
        st.flags.add("equip:stab");
        st.earn(220);
        List<String> l = new ArrayList<>(List.of(
            "*Das Buch der Fünf. Die Seiten sind aus dünnem Silber, die Schrift wandert, während du liest:",
            "*„Unter Sternenhall schläft das Sternenherz – das Licht, das den Wald nährt. Wir haben es nicht gefangen. Wir haben es versteckt.“",
            "*„Malrik wollte es aufbrechen, um nie zu altern. Wir versiegelten ihn in den Schatten. Doch die Siegel hängen an der Akademie.“",
            "*„Erlischt Sternenhall, erwacht Malrik. Wird Sternenhall wieder lebendig, erwacht er ebenfalls – und sucht jemanden, der ihm die Siegel bricht.“",
            "*Dir wird kalt. Der Brief ohne Absender. Das wandernde Symbol. Die Siegel am Gemälde. Hast du sie … für ihn gebrochen?",
            "*Im Buch liegt ein Stab aus Sternholz und eine Seite mit einer Zeichnung: Feuer, das schwebt.",
            "*✦ Ausrüstung: Sternenstab (größere Reichweite) · Neue Kombination: Feuerkomet (Ignis → Levitaris) ✦",
            "*In der Nordostecke öffnet sich ein violetter Torbogen. Er führt tiefer hinab.",
            "*Kapitel 5 abgeschlossen! Belohnung: 220 Glimmertaler."));
        l.addAll(st.addXp(180));
        endEncounter();
        ws.say(null, null, l.toArray(new String[0]), () -> ws.advance(6, 0));
    }

    // ---------------------------------------------------------------- Die alten Ruinen

    private void ruins(int s, Vector3f pos, Depths d, float dt) {
        Palace.Room here = world.palace.roomAt(pos.x, pos.z);
        Vector3f lp = world.palace.toLocal(pos);
        // Flammenfallen im Takt (1,4 s an, 1,6 s aus, versetzt)
        trapT += dt;
        for (int r = 0; r < Depths.FLAME_ROWS.length; r++) {
            float ph = (trapT + r * 1.0f) % 3f;
            boolean on = ph < 1.4f;
            for (Geometry g : d.flameJets.get(r)) {
                g.setCullHint(on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
                if (on) g.setLocalScale(1 + FastMath.sin(trapT * 20 + r) * 0.1f, 1, 1);
            }
            if (on && here == d.corridor && Math.abs(lp.z - Depths.FLAME_ROWS[r]) < 0.9f) combat.strike(1, pos);
        }
        // Pfeile aus der Westwand
        dartT -= dt;
        if (dartT <= 0 && here == d.corridor && lp.z < Depths.DART_Z1 + 6 && lp.z > Depths.DART_Z0 - 6) {
            dartT = 1.1f;
            float z = Depths.DART_Z0 + FastMath.nextRandomFloat() * (Depths.DART_Z1 - Depths.DART_Z0);
            Vector3f from = dw(new Vector3f(150.4f, Depths.RY + 1.2f, z));
            combat.shoot(null, from, from.add(10, 0, (lp.z - z) * 0.3f), 13, 0.5f, new ColorRGBA(0.85f, 0.75f, 0.55f, 1), 0.07f, false);
        }
        // Steinblock auf die Druckplatte (Levitaris)
        if (blockMoving) {
            blockT += dt;
            float k = Math.min(1, blockT / 1.4f);
            Vector3f p = Depths.BLOCK.clone().interpolateLocal(Depths.PLATE, k);
            d.blockGeo.setLocalTranslation(p.x, Depths.RY + 0.7f + FastMath.sin(k * FastMath.PI) * 2, p.z);
            if (k >= 1) {
                blockMoving = false;
                st.flags.add("ruin:plate");
                apply();
                if (ws.game.app.music != null) ws.game.app.music.sfx("door", 0.5f);
                ws.floatText("Klack! Das Fallgitter hebt sich.", pos.add(0, 2.2f, 0), Art.GOLD);
            }
        }
        if (s == 1) {
            if (lp.z > 10) setCheckpoint(dw(Depths.RUIN_ARRIVE), FastMath.PI);
            else if (lp.z < -36 && here == d.corridor) setCheckpoint(dw(new Vector3f(154, Depths.RY, -40)), FastMath.PI);
            if (here == d.arena && lp.z < -64) {
                setCheckpoint(dw(new Vector3f(154, Depths.RY, -52)), FastMath.PI);
                ws.say(null, null, new String[]{"*Eine riesige Halle. In der Mitte steht eine Statue aus Fels – und bewegt sich.",
                    "*Ein Runenherz glimmt in ihrer Brust. Der Steinwächter von Ur-Sternenhall. Er hat seit tausend Jahren niemanden durchgelassen.",
                    "*Spring über seine Schockwellen. Heb mit Levitaris einen der Trümmersteine – er schleudert sich von selbst auf ihn."},
                    () -> { ws.advance(6, 2); startGolem(); });
            }
        }
        if (s == 2 && enc == null && here == d.arena && ws.dialog == null) startGolem();
    }

    void startGolem() {
        endEncounter();
        enc = "golem";
        golem = combat.add(combat.new Golem(dw(Depths.ARENA_C)));
        combat.boss = golem;
        combat.bossHint = "Schockwelle: springen (Leertaste) · Levitaris auf einen Trümmerstein · dann Arcanum/Ignis aufs Runenherz";
        for (Vector3f dp : Depths.DEBRIS) combat.addProp(dw(dp).addLocal(0, 0.7f, 0), 0.8f);
        golem.onDefeat = this::golemDefeated;
        setCheckpoint(dw(new Vector3f(154, Depths.RY, -52)), FastMath.PI);
        if (ws.game.app.music != null) ws.game.app.music.sfx("roar", 0.5f);
    }

    private void golemDefeated() {
        world.burst(golem.center(), new ColorRGBA(1f, 0.7f, 0.3f, 1), 200);
        st.flags.add("combo:" + Combat.Combo.SPIEGELSTOSS.name());
        st.flags.add("equip:amulett");
        st.earn(260);
        List<String> l = new ArrayList<>(List.of(
            "*Das Runenherz zerspringt. Der Steinwächter sinkt auf die Knie – und spricht, mit einer Stimme wie Mahlsteine:",
            "Steinwächter: „Endlich … Ich sollte jeden aufhalten, der zum Herzen will. Auch die, die es retten wollen. Verzeih.“",
            "Steinwächter: „Er ist schon unten. Der Namenlose. Er wartet auf dich – weil du die Siegel für ihn gebrochen hast.“",
            "Steinwächter: „Nimm mein Amulett. Und denk daran: Ein Schild kann auch zuschlagen.“",
            "*✦ Ausrüstung: Eulenamulett (Herzen heilen schneller) · Neue Kombination: Spiegelstoß (Protego → Arcanum) ✦",
            "*Hinter ihm öffnet sich ein Torbogen. Von unten dringt ein blaues Licht herauf – und ein Herzschlag, so tief, dass der Boden zittert.",
            "*Kapitel 6 abgeschlossen! Belohnung: 260 Glimmertaler."));
        l.addAll(st.addXp(220));
        ws.say(null, null, l.toArray(new String[0]), () -> { endEncounter(); ws.advance(7, 0); apply(); });
    }

    // ---------------------------------------------------------------- Das Erwachen

    private void meetMalrik() {
        Vector3f at = dw(Depths.SHADE_SPOTS[0]);
        world.burst(at.add(0, 1.5f, 0), new ColorRGBA(0.5f, 0.2f, 0.9f, 1), 120);
        ws.say("Malrik", MALRIK, new String[]{
            "Da bist du ja. Pünktlich, wie es in meinem Brief stand.",
            "Ja – der Brief war von mir. Das Symbol, das dich führte, war meins. Die Siegel am Gemälde? Du hast sie für mich gebrochen. Danke.",
            "Fünfhundert Jahre war ich ein Schatten in den Mauern. Fünf Narren haben mich ausgelöscht, weil ich nicht sterben wollte.",
            "Dort oben schwebt das Sternenherz. Wer es bricht, lebt ewig. Der Wald? Der stirbt. Die Akademie? Die stirbt. Ich nicht.",
            "*Du hebst deinen Stab. Hinter dir flackern fünf erloschene Feuerschalen – die Feuer der Gründer.",
            "Du willst mich aufhalten? Mit den fünf Kinderzaubern, die ich dir habe beibringen lassen? Gut. Zeig sie mir."
        }, () -> { ws.advance(7, 1); startShade(); });
    }

    void startShade() {
        endEncounter();
        enc = "shade";
        encT = 0;
        Vector3f[] spots = new Vector3f[Depths.SHADE_SPOTS.length];
        for (int i = 0; i < spots.length; i++) spots[i] = dw(Depths.SHADE_SPOTS[i]);
        for (int i = 0; i < 5; i++) depths().lightBrazier(i, false);
        shade = combat.add(combat.new Shade(MALRIK, spots));
        combat.boss = shade;
        combat.bossHint = "Sein Schild schluckt jeden Zauber – wirf seine Schattenkugeln mit Protego zurück!";
        shade.onSummon = () -> {
            encT = 0;
            Vector3f c = dw(Depths.HEART_C);
            combat.add(combat.new Bat(ringPos(c, 9, 0.5f)));
            combat.add(combat.new Bat(ringPos(c, 9, 3.6f)));
            combat.add(combat.new Ghost(ringPos(c, 8, 1.9f)));
            combat.add(combat.new Ghost(ringPos(c, 8, 4.6f)));
            combat.add(combat.new Plant(ringPos(c, 5, 2.8f)));
            ws.bigText("Malriks Diener!");
        };
        shade.onBraziers = () -> ws.bigText("Entzünde die fünf Gründerfeuer!");
        shade.onDefeat = this::finale;
        setCheckpoint(dw(Depths.HEART_ARRIVE), FastMath.PI);
        if (ws.game.app.music != null) ws.game.app.music.sfx("roar", 0.9f);
    }

    private void finale() {
        world.burst(shade.center(), new ColorRGBA(1f, 0.95f, 0.75f, 1), 300);
        st.flags.add("finale");
        st.earn(500);
        st.addTreasure(Treasure.HERZSAMEN, 1);
        List<String> l = new ArrayList<>(List.of(
            "*Das Sternenlicht trifft Malrik mitten ins Herz. Er schreit nicht. Er sieht nur überrascht aus – und sehr, sehr alt.",
            "Malrik: „Fünf Kinderzauber …“ – dann zerfällt er zu Staub, und der Staub zu Licht.",
            "*Über dir springen die Ketten des Sternenherzens. Es erwacht – und es ist kein Ungeheuer. Es singt.",
            "*Fünf Gestalten erscheinen in den Gründerfeuern: die Fünf, durchsichtig wie Glas. Die Frau mit Heddas Augen lächelt dir zu.",
            "Die Fünf: „Du hast getan, was wir nicht konnten. Du hast ihn nicht eingesperrt. Du hast ihn erlöst.“",
            "Die Fünf: „Sternenhall gehört jetzt dir – und allen, die nach dir kommen. Öffne die Tore. Lass die Kinder zurückkehren.“",
            "*Oben im Schloss gehen alle Kerzen gleichzeitig an. Im Wald erwachen die Laternen. In Laternhain tanzen die Feen.",
            "*✦ Kapitel 7 abgeschlossen – Das Erwachen! Belohnung: 500 Glimmertaler und der Samen des Herzbaums ✦",
            "*Danke fürs Spielen! Die Welt bleibt offen: Sammle alle Karten und Raritäten, triff deine Freunde, übe deine Zauber."));
        l.addAll(st.addXp(400));
        for (int i = 0; i < 5; i++) depths().lightBrazier(i, true);
        ws.say(null, null, l.toArray(new String[0]), () -> { endEncounter(); ws.advance(8, 0); });
    }

    // ================================================================ Zauber treffen etwas

    /** @return true, wenn die Geschichte den Zauber verbraucht hat. */
    boolean spell(Vector3f at, Spell spell, Combat.Combo combo, float power, Combat.Foe homing) {
        boolean hit = combat.spellHit(at, spell, combo, power, homing);
        Depths d = depths();
        int c = Chapters.chapter(st), s = Chapters.step(st);
        boolean fire = spell == Spell.FLAMME || combo == Combat.Combo.FEUERKOMET;
        // Laternen in Laternhain
        if (fire && !world.inPalace) {
            for (int i = 0; i < 4; i++) {
                if (st.flags.contains("lantern:" + i) || horiz(at, LANTERNS[i]) > 3.2f) continue;
                st.flags.add("lantern:" + i);
                setLantern(i, true);
                world.burst(LANTERNS[i].add(0, world.height(LANTERNS[i].x, LANTERNS[i].z) + 2.8f, 0), new ColorRGBA(1f, 0.7f, 0.3f, 1), 40);
                int n = 0;
                for (int k = 0; k < 4; k++) if (st.flags.contains("lantern:" + k)) n++;
                ws.floatText("Laterne entzündet (" + n + "/4)", at.add(0, 1.5f, 0), Art.GOLD);
                if (n >= 4 && !st.flags.contains("q4:laternen")) {
                    st.flags.add("q4:laternen");
                    st.earn(30);
                    ws.say(null, null, new String[]{"*Alle vier Laternen brennen! Kleine Lichter schwirren heran – die Feen finden wieder heim. (+30 Taler)"}, null);
                }
                return true;
            }
        }
        if (!world.inPalace) return hit;
        // Leuchter der vergessenen Bibliothek
        if (fire && c == 5) {
            for (int i = 0; i < 3; i++) {
                if (st.flags.contains("lib:candle" + i) || horiz(at, dw(Depths.CANDELABRA[i])) > 3) continue;
                st.flags.add("lib:candle" + i);
                world.burst(dw(Depths.CANDELABRA[i]).addLocal(0, 2.7f, 0), new ColorRGBA(1f, 0.6f, 0.2f, 1), 50);
                apply();
                int n = 0;
                for (int k = 0; k < 3; k++) if (st.flags.contains("lib:candle" + k)) n++;
                if (n >= 3 && s == 1) {
                    ws.say(null, null, new String[]{"*Die Flammen springen von Leuchter zu Leuchter, die Halle wird hell – und du bleibst wie angewurzelt stehen.",
                        "*Mitten durch die Bibliothek klafft eine Schlucht. Unten wabert violetter Nebel. Kein Boden zu sehen.",
                        "*Neben dir: drei schwere Bücherstapel. Vielleicht kann man mit ihnen eine Brücke bauen … (Levitaris)"}, null);
                    ws.advance(5, 2);
                } else ws.floatText("Leuchter entzündet (" + n + "/3)", at.add(0, 1.5f, 0), Art.GOLD);
                return true;
            }
        }
        if (spell == Spell.SCHWEBE && c == 5 && s >= 2 && liftStack < 0) {
            for (int i = 0; i < 3; i++) {
                if (st.flags.contains("lib:stack" + i) || horiz(at, dw(Depths.BOOKSTACK[i])) > 3) continue;
                liftStack = i;
                liftT = 0;
                if (ws.game.app.music != null) ws.game.app.music.sfx("whoosh", 0.7f);
                return true;
            }
        }
        if (spell == Spell.LICHT && horiz(at, dw(Depths.MURAL)) < 6 && c == 5) {
            muralT = 22;
            ws.floatText("Lumen zeigt die Reihenfolge der Runen!", at.add(0, 1, 0), Spell.LICHT.color);
            return true;
        }
        if (c == 5 && s == 3 && (spell == Spell.FUNKE || combo != null)) {
            for (int i = 0; i < 4; i++) {
                if (horiz(at, dw(Depths.PILLAR[i])) > 2.4f) continue;
                if (Depths.ORDER[pillarStep] == i) {
                    d.pillarRunes[i].getMaterial().setColor("Color", new ColorRGBA(1f, 0.8f, 0.35f, 1));
                    world.burst(dw(Depths.PILLAR[i]).addLocal(0, 1.6f, 0), new ColorRGBA(1f, 0.8f, 0.4f, 1), 40);
                    if (ws.game.app.music != null) ws.game.app.music.sfx("chime", 1 + pillarStep * 0.12f);
                    pillarStep++;
                    if (pillarStep >= 4) {
                        st.flags.add("lib:pillars");
                        endEncounter();
                        apply();
                        ws.say(null, null, new String[]{"*Die vier Runen leuchten golden. Die Wächteraugen schließen sich und sinken zu Boden.",
                            "*Auf dem Pult an der Nordwand erscheint ein Buch mit silbernem Einband."}, null);
                        ws.advance(5, 4);
                    }
                } else {
                    pillarStep = 0;
                    for (int k = 0; k < 4; k++) d.pillarRunes[k].getMaterial().setColor("Color", new ColorRGBA(0.5f, 0.45f, 0.6f, 1));
                    ws.floatText("Falsche Reihenfolge – die Runen erlöschen. (Lumen auf das Wandbild!)", at.add(0, 1.6f, 0), new Color(255, 140, 140));
                }
                return true;
            }
        }
        if (spell == Spell.SCHWEBE && c == 6 && !st.flags.contains("ruin:plate") && !blockMoving && horiz(at, dw(Depths.BLOCK)) < 3) {
            blockMoving = true;
            blockT = 0;
            if (ws.game.app.music != null) ws.game.app.music.sfx("whoosh", 0.6f);
            return true;
        }
        if (fire && "shade".equals(enc) && shade != null && shade.phase == 2) {
            for (int i = 0; i < 5; i++) {
                if (d.braziers[i].getCullHint() != Spatial.CullHint.Always || horiz(at, dw(Depths.BRAZIERS[i])) > 3.2f) continue;
                d.lightBrazier(i, true);
                world.burst(dw(Depths.BRAZIERS[i]).addLocal(0, 2, 0), new ColorRGBA(1f, 0.85f, 0.45f, 1), 60);
                ws.floatText("Ein Gründerfeuer brennt!", at.add(0, 1.5f, 0), Art.GOLD);
                return true;
            }
        }
        return hit;
    }

    /** Zauber-Ziel für die Zielhilfe: Gegner, sonst Rätselobjekte in der Nähe. */
    Vector3f aimPoint(Vector3f from, float yaw, float reach, Spell spell) {
        List<Vector3f> cands = new ArrayList<>();
        int c = Chapters.chapter(st);
        if (!world.inPalace) {
            if (spell == Spell.FLAMME) for (int i = 0; i < 4; i++) if (!st.flags.contains("lantern:" + i)) cands.add(LANTERNS[i].add(0, world.height(LANTERNS[i].x, LANTERNS[i].z) + 2.6f, 0));
        } else {
            Depths d = depths();
            if (spell == Spell.FLAMME && c == 5) for (int i = 0; i < 3; i++) if (!st.flags.contains("lib:candle" + i)) cands.add(dw(Depths.CANDELABRA[i]).addLocal(0, 2.5f, 0));
            if (spell == Spell.SCHWEBE && c == 5) for (int i = 0; i < 3; i++) if (!st.flags.contains("lib:stack" + i)) cands.add(dw(Depths.BOOKSTACK[i]).addLocal(0, 0.6f, 0));
            if (spell == Spell.LICHT && c == 5) cands.add(dw(Depths.MURAL));
            if (spell == Spell.FUNKE && c == 5) for (int i = 0; i < 4; i++) cands.add(dw(Depths.PILLAR[i]).addLocal(0, 1.6f, 0));
            if (spell == Spell.SCHWEBE && c == 6 && !st.flags.contains("ruin:plate")) cands.add(dw(Depths.BLOCK).addLocal(0, 0.7f, 0));
            if (spell == Spell.FLAMME && "shade".equals(enc)) for (int i = 0; i < 5; i++) if (d.braziers[i].getCullHint() == Spatial.CullHint.Always) cands.add(dw(Depths.BRAZIERS[i]).addLocal(0, 1.8f, 0));
        }
        if (spell == Spell.SCHWEBE) for (Combat.Prop p : combat.props) if (p.state == 0) cands.add(p.pos.clone());
        Vector3f best = null;
        float bs = 1e9f;
        for (Vector3f v : cands) {
            Vector3f dd = v.subtract(from).setY(0);
            float dist = dd.length();
            if (dist > reach + 5 || dist < 0.3f) continue;
            float ang = FastMath.abs(FastMath.normalize(FastMath.atan2(dd.x, dd.z) - yaw, -FastMath.PI, FastMath.PI));
            if (ang > 0.7f) continue;
            float sc = dist + ang * 8;
            if (sc < bs) { bs = sc; best = v; }
        }
        return best;
    }
}
