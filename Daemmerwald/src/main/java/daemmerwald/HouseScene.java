package daemmerwald;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.*;
import com.jme3.scene.shape.*;
import com.jme3.texture.Texture;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Im Inneren eines Baumhauses oder einer Erdhöhle – bei lieben oder bösen Bewohnern. */
public class HouseScene implements Scene, Main.CameraDirector {
    private static final String[] NICE_GREET = {
        "Oh, Besuch! Komm rein, komm rein – putz dir nur die Füße ab, das Moos ist frisch gekämmt.",
        "Na so was, {name}! Ich hab gerade Wasser aufgesetzt. Setz dich ans Feuer.",
        "Herein, herein! Bei mir ist immer Platz für eine Tasse und ein Schwätzchen.",
        "Ach, wie schön! Weißt du, hier draußen kommt selten jemand vorbei. Erzähl, wie geht es dir?"
    };
    private static final String[] NICE_TALES = {
        "Mein Großvater hat einmal einen Stern im Mondsee gefangen. Er hat ihn wieder freigelassen – Sterne werden traurig in Eimern.",
        "Wenn du nachts drei Glühwürmchen in einer Reihe siehst, darfst du dir etwas wünschen. Aber nur etwas Kleines.",
        "Die Lichtfüchse sind eigentlich sehr schüchtern. Sie kommen nur zu Leuten, die leise lachen.",
        "Hinter den Bergen im Norden soll es einen Wald aus Glas geben. Ich war nie dort. Ich mag meinen Sessel.",
        "Weißt du, warum die Pilze im Pilzkreis leuchten? Sie erzählen sich Witze. Ganz leise.",
        "Früher habe ich Briefe an den Wind geschrieben. Manchmal kam eine Feder zurück. Das zählt als Antwort.",
        "Die Wale vor der Klippe singen nur bei Sonnenuntergang. Dann summt das ganze Café mit.",
        "Ich habe einmal mit einem Troll um die Wette gegähnt. Unentschieden. Wir haben beide verloren – und geschlafen."
    };
    private static final String[] EVIL_GREET = {
        "Sieh an, sieh an. Ein Wanderer, der sich verlaufen hat. Wie … appetitlich für meine Langeweile.",
        "Wer klopft an meine Tür? Niemand klopft an meine Tür. Außer Dummköpfe. Und du.",
        "Hihi! Frisches Mana, frisch gewaschen! Bleib doch, {name}. Für immer. Oder wenigstens für ein Spielchen.",
        "Du riechst nach Sonnenlicht. Wie ekelhaft. Lass uns sehen, ob du so mutig bist, wie du stinkst."
    };

    private final GamePanel game;
    private final WorldScene world;
    private final World3D w3;
    private final House house;
    private final Input in;
    private final Node interior = new Node("interior");
    private final List<PointLight> lights = new ArrayList<>();
    private final Vector3f base;
    private Figure resident, guest;
    private DialogBox dialog;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private boolean menu;
    private boolean teaHad;
    private BookReader book;
    private float savedT;
    private float t, lookYaw;
    private final Random rnd = new Random();

    public HouseScene(GamePanel game, WorldScene world, House house) {
        this.game = game;
        this.world = world;
        this.w3 = world.world;
        this.house = house;
        this.in = game.input;
        base = new Vector3f(house.x, WorldGen.height(house.x, house.z) + 300, house.z);
        build();
    }

    // ---------------------------------------------------------------- Aufbau des Innenraums

    private Material texLit(String path, ColorRGBA tint, float su, float sv, Mesh mesh) {
        Material m = G3.texture(path, tint);
        mesh.scaleTextureCoordinates(new Vector2f(su, sv));
        return m;
    }

    private Geometry add(Mesh mesh, Material m, float x, float y, float z) {
        Geometry g = new Geometry("i", mesh);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        interior.attachChild(g);
        return g;
    }

    private Geometry cylY(float r1, float r2, float h, int sides, Material m, float x, float y, float z) {
        Geometry g = add(new Cylinder(2, sides, r1, r2, h, true, false), m, x, y, z);
        g.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        return g;
    }

    private void build() {
        Random r = new Random(house.seed);
        boolean evil = house.evil;
        float R = house.tree ? 5.6f : 6.6f, H = house.tree ? 4.4f : 3.6f;
        ColorRGBA tintWall = evil ? new ColorRGBA(0.42f, 0.34f, 0.45f, 1) : new ColorRGBA(1f, 0.9f, 0.78f, 1);
        // Boden
        Cylinder floorMesh = new Cylinder(2, 40, R, R, 0.1f, true, false);
        Geometry floor = add(floorMesh, texLit(house.tree ? "Models/Tree/BarkColor.jpg" : "Textures/Terrain/splat/dirt.jpg",
            evil ? new ColorRGBA(0.4f, 0.32f, 0.38f, 1) : new ColorRGBA(0.9f, 0.75f, 0.6f, 1), 6, 6, floorMesh), 0, 0, 0);
        floor.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        // Wände (Innenansicht)
        Cylinder wallMesh = new Cylinder(2, 40, R, R, H, false, true);
        Geometry wall = add(wallMesh, texLit(house.tree ? "Models/Tree/BarkColor.jpg" : "Textures/Terrain/splat/dirt.jpg", tintWall, 8, 2, wallMesh), 0, H / 2, 0);
        wall.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        Dome roofMesh = new Dome(Vector3f.ZERO, 10, 40, R, true);
        Geometry roof = add(roofMesh, texLit(house.tree ? "Models/Tree/BarkColor.jpg" : "Textures/Terrain/splat/dirt.jpg", tintWall.mult(0.8f), 6, 3, roofMesh), 0, H, 0);
        roof.setLocalScale(1, 0.55f, 1);
        // Balken (Höhle)
        Material wood = G3.lit(evil ? new ColorRGBA(0.22f, 0.16f, 0.2f, 1) : new ColorRGBA(0.45f, 0.3f, 0.18f, 1));
        if (!house.tree) {
            for (int i = 0; i < 6; i++) {
                float a = i * FastMath.TWO_PI / 6;
                Geometry b = add(new Box(0.12f, H / 2, 0.12f), wood, FastMath.sin(a) * (R - 0.15f), H / 2, FastMath.cos(a) * (R - 0.15f));
                b.setLocalRotation(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y));
            }
        } else {
            // Jahresringe als Treppe nach oben
            for (int i = 0; i < 9; i++) {
                float a = 2.4f + i * 0.32f;
                add(new Box(0.55f, 0.06f, 0.3f), wood, FastMath.sin(a) * (R - 0.6f), 0.35f + i * 0.42f, FastMath.cos(a) * (R - 0.6f))
                    .setLocalRotation(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y));
            }
        }
        // Teppich
        Material rug = G3.lit(evil ? new ColorRGBA(0.35f, 0.1f, 0.25f, 1) : new ColorRGBA(0.7f, 0.3f, 0.3f, 1));
        Geometry rg = cylY(2.2f, 2.2f, 0.02f, 32, rug, 0, 0.07f, 0.3f);
        Geometry rg2 = cylY(1.7f, 1.7f, 0.025f, 32, G3.lit(evil ? new ColorRGBA(0.2f, 0.08f, 0.25f, 1) : new ColorRGBA(0.95f, 0.75f, 0.35f, 1)), 0, 0.08f, 0.3f);
        // Tisch & Hocker (echte Modelle), Teeservice und Öllampe
        Spatial table = G3.model("round_wooden_table_01");
        table.setLocalScale(0.85f);
        table.setLocalTranslation(0.6f, 0, -1.2f);
        interior.attachChild(table);
        for (int k = 0; k < 2; k++) {
            Spatial stool = G3.model("wooden_stool_01");
            stool.setLocalScale(1.1f);
            stool.setLocalTranslation(0.6f + (k == 0 ? -1.15f : 1.15f), 0, -1.2f + (k == 0 ? 0.2f : -0.1f));
            interior.attachChild(stool);
        }
        Spatial tea = G3.model("tea_set_01");
        tea.setLocalScale(0.9f);
        tea.setLocalTranslation(0.6f, 0.86f, -1.2f);
        interior.attachChild(tea);
        if (!evil) {
            Spatial lamp = G3.model("vintage_oil_lamp");
            lamp.setLocalScale(0.55f);
            lamp.setLocalTranslation(1.05f, 0.86f, -1.45f);
            interior.attachChild(lamp);
        }
        Geometry pot = add(new Sphere(10, 12, 0.18f), G3.lit(evil ? new ColorRGBA(0.2f, 0.15f, 0.25f, 1) : new ColorRGBA(0.3f, 0.45f, 0.75f, 1)), -R + 1.0f, 0.18f, 1.6f);
        pot.setLocalScale(1, 0.85f, 1);
        // Bett
        Geometry bed = add(new Box(1.0f, 0.3f, 0.55f), wood, -R + 1.5f, 0.3f, -0.8f);
        Geometry blanket = add(new Box(0.95f, 0.08f, 0.5f), G3.lit(evil ? new ColorRGBA(0.25f, 0.12f, 0.3f, 1) : new ColorRGBA(0.5f, 0.65f, 0.9f, 1)), -R + 1.45f, 0.65f, -0.8f);
        Geometry pillow = add(new Sphere(8, 10, 0.3f), G3.lit(new ColorRGBA(0.95f, 0.92f, 0.85f, 1)), -R + 0.75f, 0.72f, -0.8f);
        pillow.setLocalScale(0.6f, 0.35f, 1.2f);
        // Regal mit leuchtenden Gläsern (echtes Bücherregal-Modell)
        Spatial shelf = G3.model("wooden_bookshelf_worn");
        shelf.setLocalTranslation(R - 0.45f, 0, -0.5f);
        shelf.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_Y));
        interior.attachChild(shelf);
        ColorRGBA[] jar = {new ColorRGBA(0.4f, 0.9f, 0.5f, 1), new ColorRGBA(0.9f, 0.6f, 0.2f, 1), new ColorRGBA(0.5f, 0.6f, 1f, 1), new ColorRGBA(1f, 0.4f, 0.7f, 1)};
        for (int s = 0; s < 3; s++) {
            for (int k = 0; k < 4; k++) {
                ColorRGBA c = evil ? new ColorRGBA(0.7f, 0.2f, 1f, 1) : jar[(s + k) % jar.length];
                cylY(0.07f, 0.07f, 0.18f, 10, G3.litGlow(c.mult(0.7f), c.mult(0.35f)), R - 0.45f, 0.5f + s * 0.62f, -0.95f + k * 0.3f);
            }
        }
        // Kamin bzw. Kessel
        float fx = -1.8f, fz = -R + 1.2f;
        if (evil) {
            Geometry cauldron = add(new Sphere(12, 14, 0.7f), G3.lit(new ColorRGBA(0.08f, 0.08f, 0.09f, 1)), fx, 0.55f, fz + 0.3f);
            cauldron.setLocalScale(1, 0.75f, 1);
            cylY(0.6f, 0.6f, 0.02f, 16, G3.emissive(new ColorRGBA(0.55f, 0.15f, 0.85f, 1)), fx, 0.95f, fz + 0.3f);
        } else {
            add(new Box(0.9f, 0.9f, 0.4f), G3.litTex("Textures/Terrain/Rocky/RockyTexture.jpg", new ColorRGBA(0.75f, 0.7f, 0.65f, 1), null), fx, 0.9f, fz);
        }
        ParticleEmitter fire = G3.particles("fire", 40, "Effects/Explosion/flame.png", evil ? new ColorRGBA(0.7f, 0.3f, 1f, 1) : new ColorRGBA(1f, 0.6f, 0.2f, 1),
            new ColorRGBA(1f, 0.2f, 0.05f, 0), 0.35f, 0.08f, 0.4f, 0.8f);
        fire.setImagesX(2);
        fire.setImagesY(2);
        fire.setSelectRandomImage(true);
        fire.setShape(new EmitterBoxShape(new Vector3f(-0.3f, 0, -0.1f), new Vector3f(0.3f, 0.1f, 0.1f)));
        fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.9f, 0));
        fire.getParticleInfluencer().setVelocityVariation(0.3f);
        fire.setParticlesPerSec(36);
        fire.setLocalTranslation(fx, evil ? 1.0f : 0.25f, fz + (evil ? 0.3f : 0.45f));
        interior.attachChild(fire);
        // Pflanzen / Spinnweben
        if (!evil) {
            Material leaves = G3.lit(new ColorRGBA(0.85f, 1f, 0.85f, 1));
            leaves.setTexture("DiffuseMap", G3.fernTex());
            leaves.setFloat("AlphaDiscardThreshold", 0.4f);
            leaves.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            for (int i = 0; i < 3; i++) {
                float a = 0.9f + i * 1.8f;
                float px = FastMath.sin(a) * (R - 0.8f), pz = FastMath.cos(a) * (R - 0.8f);
                cylY(0.25f, 0.18f, 0.45f, 12, G3.lit(new ColorRGBA(0.72f, 0.38f, 0.22f, 1)), px, 0.22f, pz);
                add(G3.crossQuads(1.3f, 1.1f), leaves, px, 0.4f, pz);
            }
            // Kräuter an der Decke
            for (int i = 0; i < 5; i++) {
                Geometry herb = add(new Sphere(6, 8, 0.15f), G3.lit(new ColorRGBA(0.4f, 0.6f, 0.3f, 1)), -1.5f + i * 0.75f, H - 0.4f, 1.5f);
                herb.setLocalScale(0.8f, 1.6f, 0.8f);
            }
        } else {
            Material web = G3.lit(new ColorRGBA(0.9f, 0.9f, 0.95f, 1));
            for (int i = 0; i < 10; i++) {
                float a = r.nextFloat() * FastMath.TWO_PI;
                Geometry w = add(new Box(0.005f, 0.6f, 0.005f), web, FastMath.sin(a) * (R - 0.3f), H - 0.4f, FastMath.cos(a) * (R - 0.3f));
                w.setLocalRotation(new Quaternion().fromAngles(r.nextFloat() - 0.5f, a, r.nextFloat() - 0.5f));
            }
            for (int i = 0; i < 6; i++) {
                float a = r.nextFloat() * FastMath.TWO_PI;
                add(new Sphere(6, 6, 0.04f), G3.emissive(new ColorRGBA(1f, 0.15f, 0.2f, 1)), FastMath.sin(a) * (R - 0.4f), 1.6f + r.nextFloat(), FastMath.cos(a) * (R - 0.4f));
            }
        }
        // Rundes Fenster (leuchtend)
        Geometry win = add(new Sphere(10, 14, 0.55f), G3.emissive(evil ? new ColorRGBA(0.5f, 0.2f, 0.7f, 1) : new ColorRGBA(1f, 0.92f, 0.7f, 1)), 0, 2.2f, R - 0.05f);
        win.setLocalScale(1, 1, 0.1f);
        // Tür (innen)
        Geometry d = add(new Cylinder(2, 24, 0.95f, 0.95f, 0.1f, true, false), G3.lit(new ColorRGBA(0.45f, 0.25f, 0.12f, 1)), 2.6f, 1.0f, R - 0.6f);
        d.setLocalRotation(new Quaternion().fromAngleAxis(0.45f, Vector3f.UNIT_Y));
        // Lichter
        ColorRGBA lc = evil ? new ColorRGBA(0.7f, 0.35f, 1f, 1) : new ColorRGBA(1f, 0.75f, 0.45f, 1);
        lights.add(new PointLight(base.add(0, H - 0.5f, 0), lc.mult(evil ? 1.1f : 1.6f), 14));
        lights.add(new PointLight(base.add(fx, 1.2f, fz + 0.8f), (evil ? new ColorRGBA(0.6f, 0.2f, 1f, 1) : new ColorRGBA(1f, 0.5f, 0.2f, 1)).mult(1.4f), 8));
        G3.tangents(interior);
        interior.setLocalTranslation(base);
        // Bewohner und Gast
        resident = new Figure(house.look);
        resident.node.setLocalTranslation(0.6f - 1.3f, 0, -1.2f);
        resident.node.setLocalRotation(new Quaternion().fromAngleAxis(0.6f, Vector3f.UNIT_Y));
        interior.attachChild(resident.node);
        guest = new Figure(game.state.outfit.apply(game.state.hero.look), game.state.outfit.accessory);
        guest.node.setLocalTranslation(-2.0f, 0, 1.4f);
        guest.node.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI - 0.2f, Vector3f.UNIT_Y));
        interior.attachChild(guest.node);
    }

    // ---------------------------------------------------------------- Ablauf

    @Override
    public void onEnter() {
        if (interior.getParent() == null) {
            w3.root.attachChild(interior);
            for (PointLight l : lights) w3.root.addLight(l);
            world.fig.node.setCullHint(Spatial.CullHint.Always);
            greet();
        }
    }

    private String key(String what) { return what + ":" + house.id; }

    public boolean evil() { return house.evil && !game.state.flags.contains(key("beaten")); }

    private void greet() {
        game.state.flags.add("visit:" + house.id);
        boolean tamed = game.state.flags.contains(key("beaten"));
        String[] pool = house.evil && !tamed ? EVIL_GREET : NICE_GREET;
        String first = house.evil && !tamed
            ? "*Die Tür knarrt. Drinnen riecht es nach kaltem Rauch und altem Zorn. Im Halbdunkel funkeln zwei Augen."
            : "*Die runde Tür schwingt auf. Drinnen ist es warm, es knistert, und irgendwo blubbert ein Teekessel.";
        String hello = pool[rnd.nextInt(pool.length)];
        if (house.cafe) {
            first = "*Ein Glöckchen klingelt über der Tür. Es duftet nach frischem Gebäck, Kräutertee und Holzfeuer.";
            hello = "Willkommen im " + house.title + "! Setz dich, ruh dich aus. Hier kannst du auch deine Reise festhalten – ich hab ein dickes Gästebuch.";
        }
        if (tamed) hello = "Du schon wieder. Na gut … setz dich. Aber fass nichts an. Und erzähl niemandem, dass ich nett war.";
        say(new String[]{first, hello}, this::openMenu);
    }

    private void say(String[] lines, Runnable after) {
        dialog = new DialogBox(house.owner, house.look, lines, game.state.hero);
        dialog.onFinish = after;
        menu = false;
    }

    private void openMenu() {
        labels.clear();
        actions.clear();
        GameState st = game.state;
        boolean tamed = st.flags.contains(key("beaten"));
        int day = (int) (st.playTime / DayNight.DAY_SECONDS);
        if (house.cafe) {
            option("Etwas zu trinken bestellen", () -> {
                world.mana = (float) st.hero.manaMax();
                say(new String[]{"*Ein dampfender Becher Kräutertee mit Honig. Dein Mana ist wieder voll, und deine Füße danken es dir."}, this::openMenu);
            });
            option("Ins Gästebuch eintragen (Spiel speichern)", () -> {
                Vector3f d = house.door();
                world.pos.set(d);
                world.saveNow(false);
                savedT = 4;
                say(new String[]{"*Du schreibst deinen Namen in das dicke Gästebuch. Dein Spiel ist gespeichert."}, this::openMenu);
            });
            option("Ein Buch aus dem Regal lesen", () -> { book = new BookReader(); menu = false; });
            option("Weiterziehen", this::leave);
            menu = true;
            return;
        }
        if (house.evil && !tamed) {
            option("Ein Duell! (Tastenkombos, Stufe " + st.stage(key("duel")) + ")", () -> challenge(new DuelGame(game, house.owner, house.look), "duel"));
            option("Eine Rätselwette", () -> challenge(new RiddleGame(game), "riddle"));
            option("Lieber schnell wieder gehen", this::leave);
        } else {
            if (!teaHad) option("Eine Tasse Tee trinken", () -> {
                teaHad = true;
                world.mana = (float) st.hero.manaMax();
                List<String> m = st.addXp(5);
                String[] l = {"*Der Tee schmeckt nach Honig, Rauch und einem Hauch Abenteuer. Dein Mana ist wieder voll."};
                for (String s : m) l = WorldScene.append(l, s);
                say(l, this::openMenu);
            });
            if (!st.flags.contains(key("gift" + day))) option("Hast du ein kleines Geschenk für mich?", () -> {
                st.flags.add(key("gift" + day));
                say(new String[]{gift(st)}, this::openMenu);
            });
            option("Erzähl mir etwas", () -> say(new String[]{NICE_TALES[rnd.nextInt(NICE_TALES.length)]}, this::openMenu));
            option("Spielen wir etwas? (Stufe " + st.stage(key("play")) + ")", () -> challenge(Story.randomGame(game), "play"));
            option("Auf Wiedersehen", this::leave);
        }
        menu = true;
    }

    private void option(String label, Runnable r) {
        labels.add(label);
        actions.add(r);
    }

    private String gift(GameState st) {
        int roll = rnd.nextInt(3);
        if (roll == 0) {
            Item it = Item.values()[rnd.nextInt(Item.values().length)];
            st.addItem(it, 2);
            return "Hier, nimm diese zwei " + it.title + ". Ich hab mehr, als in meine Gläser passt.";
        } else if (roll == 1) {
            int c = st.earn(20 + rnd.nextInt(25));
            return "Ein paar Glimmertaler – " + c + " Stück. Ich hab sie im Garten gefunden. Glaube ich.";
        }
        Potion p = Potion.values()[rnd.nextInt(Potion.values().length)];
        st.potions.merge(p, 1, Integer::sum);
        return "Ein " + p.title + ", selbst gebraut! Trink ihn, wenn du ihn brauchst – nicht vorher, sonst wirkt er nicht.";
    }

    private void challenge(MiniGame mg, String what) {
        GameState st = game.state;
        mg.level = st.stage(key(what));
        if (mg instanceof DuelGame) {
            Camera cam = game.app.getCamera();
            Vector3f a = guest.node.getWorldTranslation(), b = resident.node.getWorldTranslation();
            Vector3f mid = a.add(b).multLocal(0.5f);
            Vector3f d = b.subtract(a).setY(0).normalizeLocal();
            Vector3f side = new Vector3f(-d.z, 0, d.x);
            cam.setLocation(mid.add(side.mult(3.6f)).addLocal(0, 1.5f, 0));
            cam.lookAt(mid.add(0, 1.1f, 0), Vector3f.UNIT_Y);
            DuelGame.heroWorld = a.add(0, guest.height * 0.75f, 0);
            DuelGame.enemyWorld = b.add(0, resident.height * 0.75f, 0);
        }
        mg.onDone = r -> {
            game.setScene(this);
            in.releaseAll();
            if (r == MiniGame.Result.WIN) {
                st.stages.put(key(what), mg.level + 1);
                world.saveNow(false);
                savedT = 3;
                int coins = st.earn(house.evil ? 35 + 10 * mg.level : 15 + 5 * mg.level);
                List<String> m = st.addXp(mg.xpReward());
                String[] l;
                if (house.evil) {
                    st.flags.add(key("beaten"));
                    l = new String[]{"Grrr! Na schön, na schön! Du hast gewonnen. Nimm deine " + coins + " Taler und hör auf zu grinsen.",
                        "*Von nun an ist " + house.owner.split(" ")[0] + " ein kleines bisschen freundlicher zu dir."};
                } else {
                    l = new String[]{"Hihi, du bist gut! Hier, " + coins + " Glimmertaler als Preis."};
                }
                for (String s : m) l = WorldScene.append(l, s);
                say(l, this::openMenu);
            } else if (r == MiniGame.Result.LOSE) {
                if (house.evil) {
                    int take = Math.min(st.coins, 15);
                    st.coins -= take;
                    say(new String[]{"Hahaha! Verloren! Ich nehme " + take + " Glimmertaler als Andenken. Komm wieder, wenn du mehr hast."}, this::openMenu);
                } else {
                    say(new String[]{"Oh, knapp daneben! Macht nichts – beim Spielen gewinnt man immer etwas: gute Laune."}, this::openMenu);
                }
            } else {
                openMenu();
            }
        };
        game.setScene(mg);
    }

    private void leave() {
        interior.removeFromParent();
        for (PointLight l : lights) w3.root.removeLight(l);
        world.fig.node.setCullHint(Spatial.CullHint.Inherit);
        DuelGame.heroWorld = null;
        in.releaseAll();
        game.setScene(world);
    }

    @Override
    public void update(double dtd) {
        float dt = (float) dtd;
        t += dt;
        game.state.playTime += dt;
        resident.update(dt, 0, false);
        guest.update(dt, 0, false);
        savedT = Math.max(0, savedT - dt);
        if (book != null) {
            if (!book.update(in)) { book = null; openMenu(); }
            return;
        }
        if (dialog != null && !menu) {
            dialog.update(dt, in);
            if (dialog.done) {
                Runnable r = dialog.onFinish;
                dialog = null;
                if (r != null) r.run();
            }
            return;
        }
        if (menu) {
            lookYaw = FastMath.clamp(lookYaw + (in.isDown(KeyEvent.VK_LEFT, KeyEvent.VK_A) ? dt : 0) - (in.isDown(KeyEvent.VK_RIGHT, KeyEvent.VK_D) ? dt : 0), -0.8f, 0.8f);
            int pick = -1;
            for (int i = 0; i < labels.size() && i < 9; i++) if (in.wasPressed(KeyEvent.VK_1 + i)) pick = i;
            if (in.mouseClicked) for (int i = 0; i < labels.size(); i++) if (box(i).contains(in.mouseX, in.mouseY)) pick = i;
            if (in.wasPressed(KeyEvent.VK_ESCAPE)) pick = labels.size() - 1;
            if (pick >= 0) {
                menu = false;
                actions.get(pick).run();
            }
        }
    }

    private RoundRectangle2D box(int i) {
        return new RoundRectangle2D.Double(760, 300 + i * 52, 470, 44, 20, 20);
    }

    @Override
    public Vector3f focus() { return house.door(); }

    @Override
    public boolean indoors() { return true; }

    @Override
    public void directCamera(Camera cam, float tpf) {
        Vector3f eye = base.add(3.0f, 1.85f, 3.6f);
        Vector3f look = base.add(-1.0f, 1.15f, -0.8f);
        Vector3f dir = look.subtract(eye);
        dir = new Quaternion().fromAngleAxis(lookYaw, Vector3f.UNIT_Y).mult(dir);
        cam.setLocation(eye);
        cam.lookAt(eye.add(dir), Vector3f.UNIT_Y);
    }

    @Override
    public void render(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(640f, 360f, 900f, new float[]{0.5f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(10, 4, 0, 150)}));
        g.fillRect(0, 0, GamePanel.W, GamePanel.H);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
        Art.shadowCentered(g, house.title, 640, 46, house.evil ? new Color(220, 150, 255) : Art.GOLD);
        g.setFont(Art.ITALIC);
        Art.shadowCentered(g, (house.cafe ? "Bewirtet von " : house.evil ? "Hier haust " : "Zuhause von ") + house.owner, 640, 76, Art.PARCH);
        Art.ornament(g, 640, 92, 360, t);
        if (dialog != null) dialog.render(g, t);
        if (savedT > 0) {
            g.setFont(Art.UI);
            Art.shadowCentered(g, "✓ Spiel gespeichert", 640, 120, Art.alpha(Art.GOOD, (int) (Math.min(1, savedT) * 255)));
        }
        if (book != null) { book.render(g); return; }
        if (menu) {
            Art.panel(g, 740, 236, 510, 80 + labels.size() * 52);
            g.setFont(Art.H2);
            Art.shadowText(g, "Was möchtest du tun?", 770, 278, Art.GOLD);
            for (int i = 0; i < labels.size(); i++) {
                RoundRectangle2D b = box(i);
                boolean hover = b.contains(in.mouseX, in.mouseY);
                g.setColor(hover ? new Color(90, 64, 130, 220) : new Color(30, 22, 48, 200));
                g.fill(b);
                g.setColor(Art.alpha(Art.GOLD, hover ? 220 : 110));
                g.draw(b);
                g.setFont(Art.UI);
                Art.shadowText(g, (i + 1) + "   " + labels.get(i), b.getX() + 20, b.getY() + 28, hover ? Color.WHITE : Art.PARCH);
            }
            g.setFont(Art.SMALL);
            g.setColor(new Color(200, 195, 215));
            Art.str(g, "Klick oder Zahl wählen · A/D umsehen · Esc gehen", 770, (float) (300 + labels.size() * 52 + 10));
        }
    }
}
