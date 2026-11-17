package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static daemmerwald.WorldMap.*;

/** Die erkundbare Welt: Akademie, Dämmerwald, Herzbaum. */
public class WorldScene implements Scene {
    private static final int SW = GamePanel.W, SH = GamePanel.H;

    private final GamePanel game;
    private final Input in;
    private final GameState st;
    private final WorldMap map = new WorldMap(1337);
    private final List<Npc> npcs = Story.createNpcs();
    private final Random rnd = new Random();

    private double px, py;
    private int fx = 0, fy = 1, flip = 1;
    private boolean moving;
    private double animT, mana, lightBoost, castAnim;
    private Npc near;
    private DialogBox dialog;
    private boolean showMap, paused;
    private double camX, camY;

    private final List<Creature> creatures = new ArrayList<>();
    private double spawnT, hurtT, invT, companionT, foxMana;
    private double compX, compY;

    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatText> texts = new ArrayList<>();
    private final double[][] flies = new double[70][3];

    private final BufferedImage[] trees = new BufferedImage[4];
    private final BufferedImage deadTree = Art.makeDeadTree();
    private final BufferedImage[] rocks = {Art.makeRock(0), Art.makeRock(1), Art.makeRock(2)};
    private final BufferedImage shelf = Art.makeShelf();
    private BufferedImage minimap;

    private static class Particle {
        double x, y, vx, vy, life, max, size;
        Color c;
    }

    /** Wesen des Waldes. WOLF/MOTH = dunkel, FOX = freundlich. */
    private static class Creature {
        static final int WOLF = 0, MOTH = 1, FOX = 2;
        int type;
        double x, y, vx, vy, t, cool;
        boolean dark() { return type != FOX; }
    }

    private static class FloatText {
        String s;
        double x, y, life;
        Color c;
    }

    public WorldScene(GamePanel game) {
        this.game = game;
        this.in = game.input;
        this.st = game.state;
        for (int i = 0; i < trees.length; i++) trees[i] = Art.makeTree(i);
        px = COURTYARD[0] * TS + 16;
        py = (COURTYARD[1] - 2) * TS + 20;
        mana = st.hero.manaMax();
        compX = px;
        compY = py;
        for (double[] f : flies) {
            f[0] = px + (rnd.nextDouble() - 0.5) * SW;
            f[1] = py + (rnd.nextDouble() - 0.5) * SH;
            f[2] = rnd.nextDouble() * 10;
        }
        rebuildMinimap();
    }

    @Override
    public void onEnter() {
        if (!st.flags.contains("intro")) {
            st.flags.add("intro");
            say(null, null, new String[]{
                "*Die Dämmerung legt sich über Wurzelhain. Irgendwo im Wald ächzt ein Baum, als würde er im Schlaf sprechen.",
                "*Im Hof der Akademie wartet Borga auf dich. Sprich sie mit [E] an."
            }, null);
        }
    }

    private void say(String name, Look look, String[] lines, Runnable after) {
        dialog = new DialogBox(name, look, lines, st.hero);
        dialog.onFinish = after;
    }

    // ================================================================ Update

    @Override
    public void update(double dt) {
        animT += dt;
        st.playTime += dt;
        updateEffects(dt);
        updateCamera();

        if (dialog != null) {
            dialog.update(dt, in);
            if (dialog.done) {
                Runnable r = dialog.onFinish;
                dialog = null;
                if (r != null) r.run();
            }
            moving = false;
            return;
        }
        if (paused) {
            if (in.wasPressed(KeyEvent.VK_ESCAPE)) paused = false;
            if (in.wasPressed(KeyEvent.VK_Q)) {
                game.state = new GameState();
                game.setScene(new TitleScene(game));
            }
            return;
        }
        if (in.wasPressed(KeyEvent.VK_ESCAPE)) { paused = true; showMap = false; return; }
        if (in.wasPressed(KeyEvent.VK_M)) showMap = !showMap;

        // Bewegung
        double dx = 0, dy = 0;
        if (in.isDown(KeyEvent.VK_A, KeyEvent.VK_LEFT)) dx -= 1;
        if (in.isDown(KeyEvent.VK_D, KeyEvent.VK_RIGHT)) dx += 1;
        if (in.isDown(KeyEvent.VK_W, KeyEvent.VK_UP)) dy -= 1;
        if (in.isDown(KeyEvent.VK_S, KeyEvent.VK_DOWN)) dy += 1;
        moving = dx != 0 || dy != 0;
        if (moving) {
            double len = Math.hypot(dx, dy);
            dx /= len;
            dy /= len;
            fx = (int) Math.signum(dx);
            fy = (int) Math.signum(dy);
            if (dx != 0) flip = dx < 0 ? -1 : 1;
            double sp = st.hero.moveSpeed();
            if (st.has(Ability.SPRINT) && in.isDown(KeyEvent.VK_SHIFT)) sp *= 1.55;
            if (map.get((int) (px / TS), (int) (py / TS)) == SWAMP) sp *= 0.65;
            double nx = px + dx * sp * dt;
            if (!blocked(nx, py)) px = nx;
            double ny = py + dy * sp * dt;
            if (!blocked(px, ny)) py = ny;
        }

        double max = st.hero.manaMax();
        mana = Math.min(max, mana + (4 + st.hero.macht * 1.2) * dt * (st.has(Ability.MOND) ? 2 : 1));
        updateCreatures(dt);
        lightBoost = Math.max(0, lightBoost - dt);
        castAnim = Math.max(0, castAnim - dt);

        if (in.wasPressed(KeyEvent.VK_SPACE)) castSpell();

        near = null;
        double best = 1e9;
        for (Npc n : npcs) {
            double d = Math.hypot(n.x - px, n.y - py);
            if (d < n.radius && d < best) { best = d; near = n; }
        }
        if (near != null && in.wasPressed(KeyEvent.VK_E, KeyEvent.VK_ENTER)) interact(near);

        updateCamera();
    }

    private void updateCamera() {
        camX = Math.max(0, Math.min(W * TS - SW, px - SW / 2.0));
        camY = Math.max(0, Math.min(H * TS - SH, py - 30 - SH / 2.0));
    }

    private boolean blocked(double x, double y) {
        double hw = 9, top = 8;
        return map.solidAtPixel(x - hw, y - top) || map.solidAtPixel(x + hw, y - top)
            || map.solidAtPixel(x - hw, y) || map.solidAtPixel(x + hw, y);
    }

    private void castSpell() {
        double cost = 22;
        if (mana < cost) {
            floatText("Zu wenig Mana", px, py - 80, Art.DANGER);
            return;
        }
        mana -= cost;
        castAnim = 0.5;
        lightBoost = 3;
        double reach = st.has(Ability.LANZE) ? 90 : 44;
        double cx = px + fx * reach, cy = py - 12 + fy * reach;
        if (fx == 0 && fy == 0) cy += reach;
        double banishR = st.has(Ability.LANZE) ? 190 : 120;
        for (Iterator<Creature> it = creatures.iterator(); it.hasNext(); ) {
            Creature c = it.next();
            if (c.dark() && (Math.hypot(c.x - cx, c.y - cy) < banishR || Math.hypot(c.x - px, c.y - py) < banishR * 0.8)) {
                banish(c);
                it.remove();
            }
        }
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 40 + rnd.nextDouble() * 160;
            spawn(cx, cy, Math.cos(a) * s, Math.sin(a) * s, 0.7, Art.MAGIC, 2.5);
        }
        int cleared = 0;
        int tx0 = (int) (cx / TS), ty0 = (int) (cy / TS);
        for (int x = tx0 - 3; x <= tx0 + 3; x++) {
            for (int y = ty0 - 3; y <= ty0 + 3; y++) {
                if (map.get(x, y) != BLIGHT) continue;
                double d = Math.hypot(x * TS + 16 - cx, y * TS + 16 - cy);
                if (d < TS * 2.7) {
                    map.t[x][y] = GRASS;
                    cleared++;
                    for (int i = 0; i < 12; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, s = 30 + rnd.nextDouble() * 90;
                        spawn(x * TS + 16, y * TS + 16, Math.cos(a) * s, Math.sin(a) * s - 40, 1.2, Art.BLIGHT, 3);
                    }
                }
            }
        }
        if (cleared > 0) {
            rebuildMinimap();
            floatText("Die Welke weicht zurück", px, py - 90, Art.MAGIC);
        }
    }

    private void interact(Npc n) {
        if (n.id.equals("heart")) {
            if (!st.allShards()) {
                say(null, null, Story.heartNotReady(st.count()), null);
            } else if (!st.flags.contains("ended")) {
                say("Corvinas Echo", Story.CORVINA, Story.HEART_READY, () -> startMinigame(n));
            } else {
                String[] l = st.flags.contains("freed") ? Story.HEART_FREED : Story.HEART_RENEWED;
                say("Corvinas Echo", Story.CORVINA, l, () -> startMinigame(n));
            }
            return;
        }
        if (n.id.equals("rektor")) {
            say(n.name, n.look, st.count() >= 3 ? n.after : n.before, null);
            return;
        }
        if (n.game == null) {
            say(n.name, n.look, n.before, null);
        } else if (n.shard != null && st.has(n.shard)) {
            say(n.name, n.look, append(n.after,
                "*Du kannst die Prüfung erneut ablegen – Stufe " + st.stage(n.id) + ". (Mit [Esc] im Prüfungsfenster lehnst du ab.)"),
                () -> startMinigame(n));
        } else if (n.shard == null && st.stage(n.id) > 1) {
            say(n.name, n.look, n.after, () -> startMinigame(n));
        } else {
            say(n.name, n.look, n.before, () -> startMinigame(n));
        }
    }

    private void startMinigame(Npc n) {
        MiniGame mg = n.game.apply(game);
        mg.level = st.stage(n.id);
        if (n.id.equals("aurin")) mg.xpMult = 1.5;
        mg.onDone = r -> {
            game.setScene(this);
            if (r == MiniGame.Result.WIN) {
                int lvl = mg.level;
                st.stages.put(n.id, lvl + 1);
                st.gamesWon++;
                List<String> lvlMsgs = st.addXp(mg.xpReward());
                floatText("+" + mg.xpReward() + " EP", px, py - 130, Art.GOLD);
                if (n.id.equals("heart") && !st.flags.contains("ended")) {
                    game.setScene(new EndingScene(game, this));
                    pendingLines = lvlMsgs;
                    return;
                }
                boolean firstShard = n.shard != null && !st.has(n.shard);
                Color fxc = n.shard != null ? n.shard.color : Art.GOLD;
                for (int i = 0; i < 60; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 50 + rnd.nextDouble() * 150;
                    spawn(px, py - 30, Math.cos(a) * s, Math.sin(a) * s, 1.4, fxc, 3);
                }
                String[] lines;
                if (firstShard) {
                    st.give(n.shard);
                    if (n.note != null) st.notes.add(n.note);
                    floatText(n.shard.title + " erhalten!", px, py - 100, n.shard.color);
                    lines = n.win;
                    if (st.allShards()) {
                        lines = append(lines, "*Alle sechs Splitter sind dein. Sie ziehen dich zum Herzbaum, in die Mitte des Waldes.");
                    }
                } else if (n.shard != null) {
                    lines = new String[]{"Stufe " + lvl + " gemeistert. Die nächste wird härter. Komm wieder, wann immer du willst."};
                } else {
                    lines = n.win;
                }
                for (String m : lvlMsgs) lines = append(lines, m);
                say(n.name, n.look, lines, null);
            } else if (r == MiniGame.Result.LOSE) {
                say(n.name, n.look, n.lose, null);
            }
        };
        game.setScene(mg);
    }

    private List<String> pendingLines;

    /** Wird von der EndingScene aufgerufen, wenn das Spiel danach endlos weitergeht. */
    public void afterEnding(boolean freed) {
        st.flags.add("ended");
        if (freed) st.flags.add("freed");
        String[] l = {
            "*Die Geschichte des Paktes ist erzählt – aber der Dämmerwald schläft nie.",
            "*Alle Prüfungen kannst du immer wieder ablegen, jedes Mal eine Stufe schwerer. Vex wartet im Schattenhain, Aurin auf der Lichtwiese.",
            "*Und in den dunklen Ecken des Waldes regt sich noch immer, was die Welke geboren hat."
        };
        if (pendingLines != null) for (String m : pendingLines) l = append(l, m);
        pendingLines = null;
        say(null, null, l, null);
    }

    private static String[] append(String[] a, String s) {
        String[] r = java.util.Arrays.copyOf(a, a.length + 1);
        r[a.length] = s;
        return r;
    }

    private void spawn(double x, double y, double vx, double vy, double life, Color c, double size) {
        Particle p = new Particle();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy; p.life = p.max = life; p.c = c; p.size = size;
        particles.add(p);
    }

    private void floatText(String s, double x, double y, Color c) {
        FloatText f = new FloatText();
        f.s = s; f.x = x; f.y = y; f.life = 2.2; f.c = c;
        texts.add(f);
    }

    private void updateEffects(double dt) {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) { it.remove(); continue; }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 0.96;
            p.vy *= 0.96;
        }
        for (Iterator<FloatText> it = texts.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.life -= dt;
            f.y -= 22 * dt;
            if (f.life <= 0) it.remove();
        }
        for (double[] f : flies) {
            f[2] += dt;
            f[0] += Math.sin(f[2] * 0.7 + f[1] * 0.01) * 18 * dt;
            f[1] += Math.cos(f[2] * 0.5 + f[0] * 0.01) * 14 * dt;
            if (f[0] < camX - 60 || f[0] > camX + SW + 60 || f[1] < camY - 60 || f[1] > camY + SH + 60) {
                f[0] = camX + rnd.nextDouble() * SW;
                f[1] = camY + rnd.nextDouble() * SH;
            }
        }
    }

    // ================================================================ Zonen & Kreaturen

    /** 1 = dunkler Ort, -1 = heller/fröhlicher Ort, 0 = neutral. */
    private int zone(double wx, double wy) {
        double tx = wx / TS, ty = wy / TS;
        int[][] dark = {HEART_C, SWAMP_C, RAVINE, SHADOW};
        double[] dr = {st.flags.contains("freed") ? 9 : 13, 12, 11, 12};
        int[][] bright = {LAKE, MUSHROOMS, MEADOW, COURTYARD};
        double[] br = {12, 8, 10, 7};
        for (int i = 0; i < bright.length; i++) if (Math.hypot(tx - bright[i][0], ty - bright[i][1]) < br[i]) return -1;
        for (int i = 0; i < dark.length; i++) if (Math.hypot(tx - dark[i][0], ty - dark[i][1]) < dr[i]) return 1;
        return 0;
    }

    private void updateCreatures(double dt) {
        invT = Math.max(0, invT - dt);
        hurtT = Math.max(0, hurtT - dt);
        foxMana = Math.max(0, foxMana - dt);
        spawnT -= dt;
        int darkCount = 0, foxCount = 0;
        for (Creature c : creatures) { if (c.dark()) darkCount++; else foxCount++; }
        int darkCap = Math.min(14, 5 + st.level / 2 + st.count());
        if (spawnT <= 0) {
            spawnT = 0.8;
            for (int tries = 0; tries < 6; tries++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 480 + rnd.nextDouble() * 320;
                double sx = px + Math.cos(a) * d, sy = py + Math.sin(a) * d;
                int tx = (int) (sx / TS), ty = (int) (sy / TS);
                int tile = map.get(tx, ty);
                if (WorldMap.solid(tile) || tile == FLOOR || tile == CARPET) continue;
                int z = zone(sx, sy);
                Creature c = new Creature();
                if (z == 1 && darkCount < darkCap) c.type = rnd.nextDouble() < 0.55 ? Creature.WOLF : Creature.MOTH;
                else if (z == -1 && foxCount < 5) c.type = Creature.FOX;
                else continue;
                c.x = sx;
                c.y = sy;
                c.t = rnd.nextDouble() * 10;
                creatures.add(c);
                break;
            }
        }
        // Irrlichtbegleiter
        if (st.has(Ability.BEGLEITER)) {
            double tx = px - flip * 30 + Math.cos(animT * 2) * 14, ty = py - 60 + Math.sin(animT * 3) * 8;
            compX += (tx - compX) * Math.min(1, dt * 4);
            compY += (ty - compY) * Math.min(1, dt * 4);
            companionT -= dt;
            if (companionT <= 0) {
                Creature target = null;
                double best = 230;
                for (Creature c : creatures) {
                    double d = Math.hypot(c.x - px, c.y - py);
                    if (c.dark() && d < best) { best = d; target = c; }
                }
                if (target != null) {
                    for (int i = 0; i < 20; i++) {
                        double f = i / 20.0;
                        spawn(compX + (target.x - compX) * f, compY + (target.y - 20 - compY) * f, 0, 0, 0.5, new Color(255, 230, 150), 2);
                    }
                    banish(target);
                    creatures.remove(target);
                    companionT = 4.5;
                } else {
                    companionT = 0.5;
                }
            }
        }
        for (Iterator<Creature> it = creatures.iterator(); it.hasNext(); ) {
            Creature c = it.next();
            c.t += dt;
            c.cool = Math.max(0, c.cool - dt);
            double dx = px - c.x, dy = py - c.y, d = Math.hypot(dx, dy);
            if (d > 1300) { it.remove(); continue; }
            double sp;
            switch (c.type) {
                case Creature.WOLF -> {
                    sp = d < 300 ? 115 + st.level * 2 : 45;
                    if (d < 300 && d > 1) { c.vx = dx / d * sp; c.vy = dy / d * sp; }
                    else if (rnd.nextDouble() < dt * 0.8) { double a = rnd.nextDouble() * 6.28; c.vx = Math.cos(a) * sp; c.vy = Math.sin(a) * sp; }
                    moveCreature(c, dt);
                }
                case Creature.MOTH -> {
                    double tx = d < 360 ? dx / Math.max(1, d) * 70 : 0, ty = d < 360 ? dy / Math.max(1, d) * 70 : 0;
                    c.x += (tx + Math.cos(c.t * 2.3) * 90) * dt;
                    c.y += (ty + Math.sin(c.t * 3.1) * 70) * dt;
                }
                default -> {
                    if (rnd.nextDouble() < dt * 0.7) { double a = rnd.nextDouble() * 6.28; c.vx = Math.cos(a) * 60; c.vy = Math.sin(a) * 60; }
                    if (zone(c.x + c.vx, c.y + c.vy) != -1) { c.vx = -c.vx; c.vy = -c.vy; }
                    moveCreature(c, dt);
                }
            }
            if (d < 28) {
                if (c.dark() && invT <= 0) {
                    double loss = st.has(Ability.SCHILD) ? 8 : 16;
                    mana = Math.max(0, mana - loss);
                    invT = 1.2;
                    hurtT = 0.5;
                    floatText("-" + (int) loss + " Mana", px, py - 90, Art.BLIGHT);
                    double kx = -dx / Math.max(1, d) * 46, ky = -dy / Math.max(1, d) * 46;
                    if (!blocked(px + kx, py)) px += kx;
                    if (!blocked(px, py + ky)) py += ky;
                } else if (!c.dark() && foxMana <= 0) {
                    foxMana = 6;
                    mana = Math.min(st.hero.manaMax(), mana + 20);
                    floatText("Der Lichtfuchs schmiegt sich an dich: +20 Mana", px, py - 90, new Color(255, 210, 120));
                    for (int i = 0; i < 14; i++) spawn(c.x, c.y - 20, rnd.nextGaussian() * 40, -40 - rnd.nextDouble() * 60, 1.2, new Color(255, 200, 120), 2.5);
                }
            }
        }
    }

    private void moveCreature(Creature c, double dt) {
        double nx = c.x + c.vx * dt, ny = c.y + c.vy * dt;
        if (!map.solidAtPixel(nx, c.y) && !map.solidAtPixel(nx, c.y - 8)) c.x = nx; else c.vx = -c.vx;
        if (!map.solidAtPixel(c.x, ny) && !map.solidAtPixel(c.x, ny - 8)) c.y = ny; else c.vy = -c.vy;
    }

    private void banish(Creature c) {
        st.banished++;
        for (int i = 0; i < 26; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 30 + rnd.nextDouble() * 110;
            spawn(c.x, c.y - 18, Math.cos(a) * s, Math.sin(a) * s - 30, 1.0, i % 2 == 0 ? Art.BLIGHT : Art.MAGIC, 3);
        }
        List<String> msgs = st.addXp(8);
        floatText("Vertrieben +8 EP", c.x, c.y - 50, Art.MAGIC);
        if (!msgs.isEmpty() && dialog == null) say(null, null, msgs.toArray(new String[0]), null);
    }

    private void drawCreature(Graphics2D g, Creature c) {
        double x = c.x, y = c.y, t = c.t;
        int dir = c.vx < 0 ? -1 : 1;
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(x - 16, y - 4, 32, 8));
        switch (c.type) {
            case Creature.WOLF -> {
                double run = Math.sin(t * 12) * 3;
                g.setColor(new Color(22, 18, 32, 230));
                g.fill(new Ellipse2D.Double(x - 20, y - 26, 40, 20));
                g.fill(new Rectangle2D.Double(x - 15 + run, y - 10, 5, 10));
                g.fill(new Rectangle2D.Double(x + 10 - run, y - 10, 5, 10));
                double hx = x + dir * 18;
                g.fill(new Ellipse2D.Double(hx - 10, y - 36, 20, 16));
                Path2D ears = new Path2D.Double();
                ears.moveTo(hx - 7, y - 32); ears.lineTo(hx - 4, y - 44); ears.lineTo(hx - 1, y - 33);
                ears.moveTo(hx + 1, y - 33); ears.lineTo(hx + 5, y - 44); ears.lineTo(hx + 7, y - 32);
                g.fill(ears);
                g.setColor(new Color(110, 50, 160, 120));
                for (int i = 0; i < 4; i++) {
                    double sx = x - dir * (20 + i * 7), sy = y - 22 + Math.sin(t * 6 + i) * 4;
                    g.fill(new Ellipse2D.Double(sx - 5, sy - 5, 10 - i, 10 - i));
                }
            }
            case Creature.MOTH -> {
                double fl = Math.abs(Math.sin(t * 14));
                double my = y - 40 + Math.sin(t * 3) * 6;
                g.setColor(new Color(60, 24, 70, 220));
                g.fill(new Ellipse2D.Double(x - 4 - 22 * fl, my - 12, 22 * fl + 4, 22));
                g.fill(new Ellipse2D.Double(x, my - 12, 22 * fl + 4, 22));
                g.setColor(new Color(200, 90, 255, 160));
                g.fill(new Ellipse2D.Double(x - 4 - 14 * fl, my - 4, 6, 6));
                g.fill(new Ellipse2D.Double(x + 4 + 10 * fl, my - 4, 6, 6));
                g.setColor(new Color(20, 10, 20));
                g.fill(new Ellipse2D.Double(x - 3, my - 10, 6, 20));
            }
            default -> {
                double hop = Math.abs(Math.sin(t * 6)) * 4;
                Art.glow(g, x, y - 16, 34, new Color(255, 190, 100), 70);
                g.setColor(new Color(240, 150, 70));
                g.fill(new Ellipse2D.Double(x - 14, y - 22 - hop, 28, 15));
                double hx = x + dir * 13;
                g.fill(new Ellipse2D.Double(hx - 8, y - 30 - hop, 16, 13));
                Path2D ears = new Path2D.Double();
                ears.moveTo(hx - 6, y - 27 - hop); ears.lineTo(hx - 4, y - 37 - hop); ears.lineTo(hx - 1, y - 28 - hop);
                ears.moveTo(hx + 1, y - 28 - hop); ears.lineTo(hx + 4, y - 37 - hop); ears.lineTo(hx + 6, y - 27 - hop);
                g.fill(ears);
                g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new QuadCurve2D.Double(x - dir * 12, y - 18 - hop, x - dir * 26, y - 26 - hop, x - dir * 24, y - 36 - hop));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(x - dir * 24 - 4, y - 40 - hop, 8, 8));
                g.setColor(new Color(40, 20, 10));
                g.fill(new Ellipse2D.Double(hx + dir * 3 - 1.5, y - 26 - hop, 3, 3));
            }
        }
    }

    // ================================================================ Render

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(8, 12, 14));
        g.fillRect(0, 0, SW, SH);
        int x0 = Math.max(0, (int) (camX / TS) - 2), x1 = Math.min(W - 1, (int) ((camX + SW) / TS) + 2);
        int y0 = Math.max(0, (int) (camY / TS) - 1), y1 = Math.min(H - 1, (int) ((camY + SH) / TS) + 1);

        AffineTransform base = g.getTransform();
        g.translate(-Math.round(camX), -Math.round(camY));

        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) drawGround(g, x, y);

        int yEnd = Math.min(H - 1, y1 + 10);
        for (int y = y0; y <= yEnd; y++) {
            for (Npc n : npcs) {
                if ((int) (n.y / TS) == y && n.look != null) Art.drawCharacter(g, n.look, n.x, n.y, 1.0, animT + n.x, false, n.x > px ? -1 : 1);
            }
            for (Creature c : creatures) if ((int) (c.y / TS) == y) drawCreature(g, c);
            if ((int) (py / TS) == y) drawHero(g);
            if (y <= y1 + 3) for (int x = x0; x <= x1; x++) drawObject(g, x, y);
            if (y == HEART_C[1] + 1) drawHeartTree(g);
        }

        for (Particle p : particles) {
            double a = p.life / p.max;
            g.setColor(Art.alpha(p.c, (int) (a * 230)));
            g.fill(new Ellipse2D.Double(p.x - p.size, p.y - p.size, p.size * 2, p.size * 2));
        }
        g.setTransform(base);

        drawDarkness(g);

        g.translate(-Math.round(camX), -Math.round(camY));
        drawLights(g);
        for (FloatText f : texts) {
            g.setFont(Art.UI);
            Art.shadowCentered(g, f.s, f.x, f.y, Art.alpha(f.c, (int) (Math.min(1, f.life) * 255)));
        }
        if (near != null && dialog == null) {
            g.setFont(Art.UI);
            String label = "[E] " + near.name;
            double ly = near.look == null ? near.y - 150 : near.y - 92;
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(label) + 20;
            g.setColor(new Color(10, 12, 20, 200));
            g.fill(new RoundRectangle2D.Double(near.x - w / 2.0, ly - 18, w, 26, 12, 12));
            Art.shadowCentered(g, label, near.x, ly, Art.GOLD);
        }
        g.setTransform(base);

        drawHud(g);
        if (showMap) drawMinimap(g);
        if (paused) drawJournal(g);
        if (dialog != null) dialog.render(g, animT);
    }

    private void drawHero(Graphics2D g) {
        if (castAnim > 0) Art.glow(g, px, py - 30, 60 + (0.5 - castAnim) * 120, Art.MAGIC, (int) (castAnim * 300));
        Art.drawCharacter(g, st.hero.look, px, py, 1.0, animT, moving, flip);
    }

    private static final Color GRASS_A = new Color(28, 46, 36), GRASS_B = new Color(38, 58, 42);

    private void drawGround(Graphics2D g, int x, int y) {
        int tile = map.t[x][y];
        float v = map.var[x][y];
        int sx = x * TS, sy = y * TS;
        Color c;
        switch (tile) {
            case PATH -> c = Art.mix(new Color(70, 60, 46), new Color(86, 72, 54), v);
            case STONE, ROCK -> c = Art.mix(new Color(66, 68, 74), new Color(84, 84, 92), v);
            case SWAMP -> c = Art.mix(new Color(34, 44, 32), new Color(46, 54, 36), v);
            case WATER -> c = Art.mix(new Color(18, 36, 62), new Color(22, 44, 72), v);
            case FLOOR, SHELF, WALL -> c = Art.mix(new Color(70, 52, 38), new Color(80, 60, 44), v);
            case CARPET -> c = new Color(104, 30, 40);
            case TREE -> c = Art.shade(Art.mix(GRASS_A, GRASS_B, v), 0.72);
            case BLIGHT -> c = Art.mix(new Color(44, 30, 50), new Color(54, 36, 60), v);
            default -> c = Art.mix(GRASS_A, GRASS_B, v);
        }
        g.setColor(c);
        g.fillRect(sx, sy, TS, TS);
        switch (tile) {
            case WATER -> {
                double s = Math.sin(animT * 2 + x * 0.7 + y * 0.4);
                g.setColor(new Color(120, 170, 220, (int) (25 + s * 20)));
                g.fillRect(sx + 4 + (int) (s * 3), sy + 10 + (int) (v * 10), 14, 2);
            }
            case FLOOR, SHELF, WALL -> {
                g.setColor(new Color(50, 36, 26));
                g.drawLine(sx, sy + 15, sx + TS, sy + 15);
                g.drawLine(sx + (y % 2 == 0 ? 10 : 22), sy, sx + (y % 2 == 0 ? 10 : 22), sy + 15);
            }
            case CARPET -> {
                g.setColor(new Color(200, 160, 80, 90));
                if (x == 44) g.fillRect(sx + 3, sy, 2, TS);
                if (x == 46) g.fillRect(sx + TS - 5, sy, 2, TS);
            }
            case SWAMP -> {
                if (v > 0.7) {
                    g.setColor(new Color(20, 30, 24, 160));
                    g.fill(new Ellipse2D.Double(sx + 4, sy + 8, 20, 12));
                }
            }
            case FLOWER -> {
                Color[] fc = {new Color(200, 150, 255), new Color(255, 220, 140), new Color(150, 220, 255)};
                g.setColor(fc[(int) (v * 3) % 3]);
                g.fill(new Ellipse2D.Double(sx + 8 + v * 10, sy + 10, 4, 4));
                g.fill(new Ellipse2D.Double(sx + 18 - v * 6, sy + 20, 4, 4));
            }
            case MUSH -> {
                g.setColor(new Color(210, 200, 180));
                g.fillRect(sx + 14, sy + 16, 3, 7);
                g.setColor(new Color(170, 60, 50));
                g.fill(new Arc2D.Double(sx + 9, sy + 11, 13, 10, 0, 180, Arc2D.CHORD));
            }
            case PATH -> {
                if (v > 0.8) {
                    g.setColor(new Color(100, 90, 74));
                    g.fill(new Ellipse2D.Double(sx + 10, sy + 12, 5, 4));
                }
            }
            default -> { }
        }
    }

    private void drawObject(Graphics2D g, int x, int y) {
        int tile = map.t[x][y];
        int sx = x * TS, sy = y * TS;
        switch (tile) {
            case TREE -> {
                BufferedImage img = trees[(int) (map.var[x][y] * 4) % 4];
                g.drawImage(img, sx + 16 - 42, sy + 32 - 106, null);
            }
            case BLIGHT -> g.drawImage(deadTree, sx + 16 - 42, sy + 32 - 106, null);
            case ROCK -> g.drawImage(rocks[(int) (map.var[x][y] * 3) % 3], sx - 8, sy - 10, null);
            case SHELF -> g.drawImage(shelf, sx, sy - 20, null);
            case ALTAR -> {
                g.setColor(new Color(0, 0, 0, 100));
                g.fill(new Ellipse2D.Double(sx - 10, sy + 22, 52, 14));
                Path2D o = new Path2D.Double();
                o.moveTo(sx + 4, sy + 30); o.lineTo(sx + 10, sy - 60); o.lineTo(sx + 16, sy - 72);
                o.lineTo(sx + 22, sy - 60); o.lineTo(sx + 28, sy + 30); o.closePath();
                g.setPaint(new GradientPaint(sx, 0, new Color(40, 30, 50), sx + 32, 0, new Color(14, 10, 20)));
                g.fill(o);
                g.setColor(new Color(220, 80, 255, (int) (140 + Math.sin(animT * 3) * 80)));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Line2D.Double(sx + 16, sy - 50, sx + 16, sy - 20));
                g.draw(new Line2D.Double(sx + 10, sy - 36, sx + 22, sy - 36));
                g.draw(new Ellipse2D.Double(sx + 11, sy - 14, 10, 10));
            }
            case CRYSTAL -> {
                double bob = Math.sin(animT * 2) * 4;
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(new Ellipse2D.Double(sx - 6, sy + 22, 44, 12));
                Path2D c = new Path2D.Double();
                c.moveTo(sx + 16, sy - 66 + bob); c.lineTo(sx + 32, sy - 26 + bob); c.lineTo(sx + 16, sy + 8 + bob);
                c.lineTo(sx, sy - 26 + bob); c.closePath();
                g.setPaint(new GradientPaint(sx, (float) (sy - 66), new Color(255, 255, 230), sx + 32, sy + 8, new Color(255, 190, 110)));
                g.fill(c);
                g.setColor(new Color(255, 255, 255, 200));
                g.draw(c);
            }
            case WALL -> {
                g.setColor(new Color(76, 72, 84));
                g.fillRect(sx, sy - 20, TS, TS + 20);
                g.setColor(new Color(100, 96, 110));
                g.fillRect(sx, sy - 20, TS, 10);
                g.setColor(new Color(56, 52, 62));
                g.drawLine(sx, sy, sx + TS, sy);
                g.drawLine(sx + (y % 2 == 0 ? 8 : 20), sy - 10, sx + (y % 2 == 0 ? 8 : 20), sy);
                g.drawLine(sx + 14, sy, sx + 14, sy + TS);
            }
            default -> { }
        }
    }

    private void drawHeartTree(Graphics2D g) {
        double bx = HEART_C[0] * TS + 16, by = (HEART_C[1] + 2) * TS;
        double health = st.count() / 6.0;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(bx - 90, by - 14, 180, 30));
        Path2D trunk = new Path2D.Double();
        trunk.moveTo(bx - 80, by);
        trunk.curveTo(bx - 40, by - 30, bx - 44, by - 110, bx - 30, by - 170);
        trunk.lineTo(bx + 30, by - 170);
        trunk.curveTo(bx + 44, by - 110, bx + 40, by - 30, bx + 80, by);
        trunk.closePath();
        g.setPaint(new GradientPaint((float) bx - 60, 0, new Color(70, 52, 50), (float) bx + 60, 0, new Color(36, 26, 30)));
        g.fill(trunk);
        g.setStroke(new BasicStroke(2.4f));
        double pulse = 0.5 + 0.5 * Math.sin(animT * 2);
        g.setColor(Art.alpha(Art.mix(Art.BLIGHT, new Color(160, 255, 190), health), (int) (120 + pulse * 100)));
        g.draw(new QuadCurve2D.Double(bx - 20, by - 4, bx - 30, by - 70, bx - 10, by - 150));
        g.draw(new QuadCurve2D.Double(bx + 18, by - 4, bx + 34, by - 90, bx + 8, by - 160));
        g.draw(new QuadCurve2D.Double(bx, by - 30, bx - 4, by - 100, bx + 2, by - 168));
        Random r = new Random(99);
        Color leaf = Art.mix(new Color(70, 40, 80), new Color(40, 110, 70), health);
        for (int i = 0; i < 16; i++) {
            double cx = bx + r.nextGaussian() * 90, cy = by - 230 + r.nextGaussian() * 45;
            double rad = 40 + r.nextInt(30);
            g.setColor(Art.shade(leaf, 0.6 + r.nextDouble() * 0.5));
            g.fill(new Ellipse2D.Double(cx - rad, cy - rad * 0.8, rad * 2, rad * 1.6));
        }
    }

    private void drawDarkness(Graphics2D g) {
        double sx = px - camX, sy = py - 30 - camY;
        boolean inside = map.get((int) (px / TS), (int) (py / TS)) == FLOOR || map.get((int) (px / TS), (int) (py / TS)) == CARPET;
        double radius = 330 + lightBoost / 3 * 160;
        int z = zone(px, py);
        int edge = inside ? 120 : z == -1 ? 150 : z == 1 ? 238 : 205;
        RadialGradientPaint p = new RadialGradientPaint(new Point2D.Double(sx, sy), (float) radius,
            new float[]{0f, 0.45f, 1f},
            new Color[]{new Color(6, 8, 22, 0), new Color(6, 8, 22, edge / 2), new Color(6, 8, 22, edge)});
        g.setPaint(p);
        g.fillRect(0, 0, SW, SH);
        if (z == 1 && !inside) {
            g.setColor(new Color(60, 0, 80, 40));
            g.fillRect(0, 0, SW, SH);
        }
        if (hurtT > 0) {
            g.setColor(new Color(130, 30, 170, (int) (hurtT * 220)));
            g.fillRect(0, 0, SW, SH);
        }
    }

    private void drawLights(Graphics2D g) {
        for (double[] f : flies) {
            double a = 0.5 + 0.5 * Math.sin(f[2] * 2.3);
            Art.dot(g, f[0], f[1], 1.5, new Color(255, 230, 150), (int) (a * 200));
        }
        // Kerzen & Fackeln an der Akademie
        int[][] lamps = {{43, 15}, {47, 15}, {38, 5}, {52, 5}, {38, 12}, {52, 12}, {45, 5}};
        for (int[] l : lamps) {
            double fl = 0.85 + 0.15 * Math.sin(animT * 9 + l[0]);
            Art.glow(g, l[0] * TS + 16, l[1] * TS + 4, 90 * fl, new Color(255, 180, 90), 70);
            g.setColor(new Color(255, 220, 150));
            g.fill(new Ellipse2D.Double(l[0] * TS + 13, l[1] * TS - 2, 6, 9));
        }
        // Welke-Glühen nahe der Kamera
        int x0 = Math.max(0, (int) (camX / TS)), x1 = Math.min(W - 1, (int) ((camX + SW) / TS));
        int y0 = Math.max(0, (int) (camY / TS)), y1 = Math.min(H - 1, (int) ((camY + SH) / TS));
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (map.t[x][y] == BLIGHT && (x + y) % 2 == 0) {
                    double a = 0.5 + 0.5 * Math.sin(animT * 1.5 + x * 0.9 + y);
                    Art.dot(g, x * TS + 16, y * TS - 10, 2, Art.BLIGHT, (int) (60 + a * 120));
                }
            }
        }
        // Lichtwiese & Schattenhain
        Art.glow(g, MEADOW[0] * TS + 16, MEADOW[1] * TS - 20, 260 + Math.sin(animT * 2) * 20, new Color(255, 220, 140), 70);
        Art.glow(g, SHADOW[0] * TS + 16, SHADOW[1] * TS - 30, 160, new Color(200, 60, 255), 55);
        // Herzbaum
        double hx = HEART_C[0] * TS + 16, hy = HEART_C[1] * TS - 60;
        Color hc = Art.mix(Art.BLIGHT, new Color(160, 255, 190), st.count() / 6.0);
        Art.glow(g, hx, hy, 240 + Math.sin(animT * 2) * 20, hc, st.allShards() ? 110 : 60);
        // Glühende Augen der Schattenwesen
        for (Creature c : creatures) {
            if (c.type == Creature.WOLF) {
                double ex = c.x + (c.vx < 0 ? -1 : 1) * 18;
                Art.dot(g, ex - 3, c.y - 30, 1.4, new Color(255, 60, 90), 255);
                Art.dot(g, ex + 3, c.y - 30, 1.4, new Color(255, 60, 90), 255);
            } else if (c.type == Creature.MOTH) {
                Art.dot(g, c.x, c.y - 40 + Math.sin(c.t * 3) * 6, 2, Art.BLIGHT, 160);
            } else {
                Art.dot(g, c.x, c.y - 18, 3, new Color(255, 200, 120), 90);
            }
        }
        if (st.has(Ability.BEGLEITER)) {
            Art.glow(g, compX, compY, 40, new Color(255, 230, 150), 150);
            Art.dot(g, compX, compY, 4, new Color(255, 240, 190), 255);
        }
        // Markierungen über Figuren mit offenen Prüfungen
        for (Npc n : npcs) {
            boolean open = (n.shard != null && !st.has(n.shard)) || (n.id.equals("heart") && st.allShards());
            if (!open) continue;
            Color c = n.shard != null ? n.shard.color : new Color(180, 255, 200);
            double my = (n.look == null ? n.y - 200 : n.y - 110) + Math.sin(animT * 3) * 5;
            Art.shard(g, n.x, my, 9, c, true, animT);
        }
    }

    private void drawHud(Graphics2D g) {
        // Figur + Mana
        Art.panel(g, 16, 16, 330, 118);
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Double(24, 24, 70, 76, 12, 12));
        g.setColor(new Color(30, 36, 52));
        g.fillRect(24, 24, 70, 76);
        Art.drawCharacter(g, st.hero.look, 59, 128, 1.25, animT, false, 1);
        g.setClip(clip);
        g.setFont(Art.UI);
        Art.shadowText(g, st.hero.name, 106, 44, Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(new Color(190, 190, 210));
        g.drawString(st.hero.epithet, 106, 63);
        Art.bar(g, 106, 74, 220, 14, mana / st.hero.manaMax(), Art.MAGIC);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("Mana " + (int) mana, 112, 85);
        Art.bar(g, 106, 96, 220, 10, st.xp / (double) st.xpNext(), Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(Art.GOLD);
        g.drawString("Stufe " + st.level + "   " + st.xp + " / " + st.xpNext() + " EP", 106, 124);

        // Splitter
        Art.panel(g, SW - 300, 16, 284, 70);
        Shard[] all = Shard.values();
        for (int i = 0; i < all.length; i++) {
            Art.shard(g, SW - 270 + i * 44, 44, 13, all[i].color, st.has(all[i]), animT + i);
        }
        g.setFont(Art.SMALL);
        g.setColor(new Color(200, 200, 220));
        Art.centered(g, "Splitter des Paktsteins: " + st.count() + "/6", SW - 158, 78);

        // Ziel
        String goal = objective();
        g.setFont(Art.UI);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(goal) + 40;
        if (dialog == null) {
            Art.panel(g, SW / 2.0 - w / 2.0, SH - 56, w, 40);
            Art.shadowCentered(g, goal, SW / 2.0, SH - 30, Art.PARCH);
            g.setFont(Art.SMALL);
            g.setColor(new Color(170, 170, 190, 200));
            g.drawString("[Leertaste] Zauber" + (st.has(Ability.SPRINT) ? "   [Umschalt] Windschritt" : "") + "   [M] Karte   [Esc] Tagebuch", 22, 154);
        }
    }

    private String objective() {
        if (st.flags.contains("ended")) {
            return "Endlos: Meistere höhere Prüfungsstufen, vertreibe Schattenwesen – Stufe " + st.level + ", " + st.gamesWon + " Prüfungen gewonnen.";
        }
        if (st.allShards()) return "Ziel: Bringe die sechs Splitter zum Herzbaum in der Mitte des Waldes.";
        for (Shard s : Shard.values()) {
            if (!st.has(s)) return "Ziel: Sammle die Splitter (" + st.count() + "/6) – nächster Hinweis: " + s.where;
        }
        return "";
    }

    private void rebuildMinimap() {
        if (minimap == null) minimap = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                int c = switch (map.t[x][y]) {
                    case TREE -> 0xFF14281E;
                    case WATER -> 0xFF234A78;
                    case PATH -> 0xFF8A7454;
                    case STONE, ROCK -> 0xFF6E7078;
                    case SWAMP -> 0xFF3C4A2E;
                    case FLOOR, CARPET, SHELF -> 0xFF8A6A4A;
                    case WALL -> 0xFFB0A8B8;
                    case BLIGHT -> 0xFF7A3496;
                    case HEART -> 0xFFC8FFD2;
                    case ALTAR -> 0xFFFF40FF;
                    case CRYSTAL -> 0xFFFFE89A;
                    default -> 0xFF2E4C38;
                };
                minimap.setRGB(x, y, c);
            }
        }
    }

    private void drawMinimap(Graphics2D g) {
        int s = 6, mw = W * s, mh = H * s;
        int mx = (SW - mw) / 2, my = (SH - mh) / 2 + 10;
        Art.panel(g, mx - 20, my - 50, mw + 40, mh + 70);
        g.setFont(Art.H2);
        Art.shadowCentered(g, "Karte des Dämmerwaldes", SW / 2.0, my - 18, Art.GOLD);
        g.drawImage(minimap, mx, my, mw, mh, null);
        g.setFont(Art.SMALL);
        String[][] labels = {{"Akademie", "45", "9"}, {"Herzbaum", "45", "35"}, {"Mondsee", "14", "34"},
            {"Steinbruch", "75", "20"}, {"Moor", "45", "62"}, {"Pilzkreis", "15", "63"}, {"Dornenschlucht", "77", "50"},
            {"Schattenhain", "14", "6"}, {"Lichtwiese", "74", "4"}};
        for (String[] l : labels) {
            double lx = mx + Integer.parseInt(l[1]) * s, ly = my + Integer.parseInt(l[2]) * s;
            Art.shadowCentered(g, l[0], lx, ly, Art.PARCH);
        }
        for (Npc n : npcs) {
            if (n.id.equals("vex") || n.id.equals("aurin")) {
                Art.dot(g, mx + n.x / TS * s, my + n.y / TS * s, 3, n.look.accent, 255);
            }
            if (n.shard != null && !st.has(n.shard)) {
                Art.shard(g, mx + n.x / TS * s, my + n.y / TS * s - 4, 6, n.shard.color, true, animT);
            }
        }
        if (Math.sin(animT * 8) > -0.4) {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(mx + px / TS * s - 5, my + py / TS * s - 5, 10, 10));
            g.setColor(Color.RED);
            g.fill(new Ellipse2D.Double(mx + px / TS * s - 3, my + py / TS * s - 3, 6, 6));
        }
    }

    private void drawJournal(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, SW, SH);
        Art.panel(g, 140, 60, 1000, 600);
        g.setFont(Art.H1);
        Art.shadowCentered(g, "Tagebuch", SW / 2.0, 115, Art.GOLD);

        g.setFont(Art.H2);
        Art.shadowText(g, "Splitter", 180, 165, Art.GOLD);
        double y = 200;
        for (Shard s : Shard.values()) {
            Art.shard(g, 192, y - 7, 9, s.color, st.has(s), animT);
            g.setFont(Art.SMALL);
            g.setColor(st.has(s) ? Art.PARCH : new Color(150, 150, 170));
            g.drawString(s.title + (st.has(s) ? "  ✓" : ""), 212, (float) y - 6);
            g.setColor(new Color(140, 140, 160));
            g.drawString(s.where, 212, (float) y + 11);
            y += 44;
        }

        g.setFont(Art.H2);
        Art.shadowText(g, "Erkenntnisse", 620, 165, Art.GOLD);
        g.setFont(Art.SMALL);
        g.setColor(Art.PARCH);
        double ny = 195;
        if (st.notes.isEmpty()) {
            g.setColor(new Color(150, 150, 170));
            ny = Art.drawWrapped(g, "Noch nichts. Sprich mit den Hüterinnen und Hütern der Splitter.", 620, ny, 480, 20);
        }
        for (String n : st.notes) {
            ny = Art.drawWrapped(g, "• " + n, 620, ny, 480, 20) + 8;
        }

        g.setFont(Art.H2);
        Art.shadowText(g, "Fähigkeiten (Stufe " + st.level + ")", 180, 485, Art.GOLD);
        g.setFont(Art.SMALL);
        Ability[] ab = Ability.values();
        for (int i = 0; i < ab.length; i++) {
            boolean ok = st.has(ab[i]);
            g.setColor(ok ? Art.PARCH : new Color(120, 120, 140));
            String line = (ok ? "✦ " : "Stufe " + ab[i].level + ": ") + ab[i].title + (ok ? " – " + ab[i].desc : "");
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(line.length() > 80 ? line.substring(0, 79) + "…" : line, 180, 512 + i * 18);
        }
        g.setColor(new Color(170, 170, 190));
        g.drawString("Vertriebene Schattenwesen: " + st.banished + "   ·   Prüfungen gewonnen: " + st.gamesWon, 620, 600);
        g.drawString("[Esc] zurück   [Q] zum Titelbild (Fortschritt geht verloren)", 620, 640);
        g.setColor(new Color(170, 170, 190));
        int min = (int) (st.playTime / 60);
        g.drawString("Spielzeit: " + min + " min", 620, 620);
    }
}
