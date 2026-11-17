package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rätsel des Steinwächters: Antwort mit Maus oder Tasten 1–4 wählen. */
public class RiddleGame extends MiniGame {
    private static final String[][] POOL = {
        {"Ich habe Städte, aber keine Häuser, Wälder, aber keine Bäume, Wasser, aber keine Fische. Was bin ich?", "Eine Landkarte", "Ein Traum", "Ein Spiegel", "Ein Gemälde"},
        {"Je mehr du davon wegnimmst, desto größer werde ich.", "Ein Loch", "Ein Schatten", "Ein Berg", "Eine Schuld"},
        {"Ich spreche ohne Mund und höre ohne Ohren. Ich habe keinen Körper, doch der Wind erweckt mich.", "Das Echo", "Ein Geist", "Die Glocke", "Ein Brief"},
        {"Was gehört dir, wird aber von anderen viel häufiger benutzt als von dir?", "Dein Name", "Dein Schatten", "Dein Atem", "Dein Weg"},
        {"Ich laufe, habe aber keine Beine. Ich habe ein Bett, schlafe aber nie. Ich habe einen Mund, esse aber nicht.", "Ein Fluss", "Eine Uhr", "Ein Wind", "Ein Weg"},
        {"Morgens auf vier Beinen, mittags auf zwei, abends auf drei. Wer ist es?", "Der Mensch", "Der Troll", "Die Katze", "Der Baum"},
        {"Was wird nass, während es trocknet?", "Ein Handtuch", "Ein Fluss", "Der Regen", "Eine Wolke"},
        {"Ich bin leichter als eine Feder, doch selbst der stärkste Troll kann mich nicht lange halten.", "Der Atem", "Ein Gedanke", "Ein Funke", "Ein Blatt"},
        {"Je mehr es gibt, desto weniger siehst du.", "Dunkelheit", "Nebel", "Bäume", "Sterne"},
        {"Was hat Zähne, beißt aber nie?", "Ein Kamm", "Eine Säge", "Ein Schlüssel", "Ein Berg"},
        {"Ich habe einen Hals, aber keinen Kopf, zwei Arme, aber keine Hände.", "Ein Hemd", "Eine Flasche", "Ein Baum", "Eine Harfe"},
        {"Was kann man brechen, ohne es je zu berühren?", "Ein Versprechen", "Das Eis", "Die Stille", "Ein Herz"},
        {"Ich komme nachts ohne Ruf und verschwinde am Tag, ohne gestohlen zu werden.", "Die Sterne", "Der Tau", "Der Mond", "Die Träume"},
        {"Was läuft immer, kommt aber nie an?", "Die Zeit", "Ein Fluss", "Ein Rad", "Der Wind"},
        {"Wer mich macht, will mich nicht. Wer mich kauft, braucht mich nicht. Wer mich braucht, weiß es nicht.", "Ein Sarg", "Ein Schloss", "Ein Ring", "Ein Bett"},
        {"Ich habe Blätter, bin aber kein Baum. Ich habe einen Rücken, aber keine Knochen.", "Ein Buch", "Ein Fächer", "Ein Salat", "Ein Schild"},
        {"Was ist immer vor dir, aber du kannst es nie sehen?", "Die Zukunft", "Die Nase", "Der Horizont", "Die Luft"},
        {"Vorwärts bin ich schwer, rückwärts bin ich nicht. Was bin ich?", "Das Wort »Ton«", "Ein Stein", "Ein Anker", "Ein Ochse"},
        {"Was wird größer, je mehr man es teilt?", "Die Freude", "Das Brot", "Ein Geheimnis", "Der Kreis"},
        {"Ich fliege ohne Flügel und weine ohne Augen.", "Die Wolke", "Der Vogel", "Der Wind", "Die Fee"},
        {"Ein Haus voller Essen, aber ohne Tür.", "Ein Ei", "Eine Nuss", "Ein Bienenstock", "Ein Kürbis"},
        {"Was hat ein Auge, kann aber nicht sehen?", "Eine Nadel", "Ein Sturm", "Ein Würfel", "Eine Kartoffel"},
        {"Ich bin nicht lebendig, aber ich wachse. Ich habe keine Lunge, aber ich brauche Luft. Wasser tötet mich.", "Das Feuer", "Der Schimmel", "Ein Kristall", "Die Welke"},
        {"Was gehört zu einem Moment, zweimal zu einem Augenblick, aber nie zu hundert Jahren?", "Der Buchstabe M", "Die Stille", "Ein Herzschlag", "Das Licht"},
        {"Je länger ich stehe, desto kürzer werde ich.", "Eine Kerze", "Ein Schatten", "Ein Troll", "Ein Turm"},
        {"Viele hören mich, keiner sieht mich, und ich spreche nur, wenn man mich zuerst ruft.", "Das Echo", "Der Donner", "Ein Gerücht", "Die Glocke"}
    };

    private final List<Integer> order = new ArrayList<>();
    private int idx, correct, mistakes, chosen = -1;
    private int[] perm = new int[4];
    private double timeLeft, timeMax, reveal;

    public RiddleGame(GamePanel g) { super(g); }

    private int count() { return Math.min(8, 3 + level); }

    private int allowed() { return Math.max(0, 2 - (level - 1) / 3); }

    @Override protected String title() { return "Die Rätsel des Steinwächters"; }

    @Override protected String subtitle() { return "»Wer fragt, muss auch antworten können.«"; }

    @Override protected String[] rules() {
        return new String[]{
            "Beantworte " + count() + " Rätsel. Wähle mit der Maus oder den Tasten 1–4.",
            "Du darfst dir " + allowed() + " Fehler erlauben.",
            "Für jedes Rätsel hast du " + (int) perRiddle() + " Sekunden Bedenkzeit."
        };
    }

    private double perRiddle() { return Math.max(12, 32 - level * 3) * timeFactor(); }

    @Override protected String winText() { return "Der Steinwächter neigt den Kopf. Staub rieselt von seinen Schultern. Das ist bei ihm ein Lächeln."; }

    @Override protected String loseText() { return "»Falsch.« Der Wächter schließt die Augen. Du hast den Eindruck, er sei nicht enttäuscht – nur geduldig."; }

    @Override protected Color tint() { return new Color(30, 30, 40); }

    @Override
    protected void start() {
        for (int i = 0; i < POOL.length; i++) order.add(i);
        Collections.shuffle(order, rnd);
        idx = 0;
        next();
    }

    private void next() {
        List<Integer> p = new ArrayList<>(List.of(1, 2, 3, 4));
        Collections.shuffle(p, rnd);
        for (int i = 0; i < 4; i++) perm[i] = p.get(i);
        timeMax = timeLeft = perRiddle();
        chosen = -1;
        reveal = 0;
    }

    @Override
    protected void play(double dt) {
        if (chosen >= 0) {
            reveal += dt;
            if (reveal > 1.4) {
                idx++;
                if (mistakes > allowed()) { end(false); return; }
                if (idx >= count()) { end(true); return; }
                next();
            }
            return;
        }
        timeLeft -= dt;
        int pick = -1;
        for (int k = 0; k < 4; k++) if (in.wasPressed(KeyEvent.VK_1 + k, KeyEvent.VK_NUMPAD1 + k)) pick = k;
        if (in.mouseClicked) for (int k = 0; k < 4; k++) if (inBox(k)) pick = k;
        if (timeLeft <= 0) pick = 99;
        if (pick >= 0) {
            chosen = pick;
            if (pick < 4 && perm[pick] == 1) { correct++; game.state.riddlesSolved++; }
            else mistakes++;
        }
    }

    private double bx(int k) { return 190 + (k % 2) * 460; }

    private double by(int k) { return 400 + (k / 2) * 110; }

    private boolean inBox(int k) {
        return in.mouseX > bx(k) && in.mouseX < bx(k) + 440 && in.mouseY > by(k) && in.mouseY < by(k) + 90;
    }

    @Override
    protected void draw(Graphics2D g) {
        if (idx >= order.size()) return;
        String[] r = POOL[order.get(Math.min(idx, count() - 1))];
        Art.panel(g, 150, 110, 980, 260);
        g.setFont(Art.SMALL);
        g.setColor(new Color(180, 180, 200));
        Art.centered(g, "RÄTSEL " + Math.min(count(), idx + 1) + " VON " + count(), 640, 150);
        g.setFont(new Font("Serif", Font.ITALIC, 28));
        g.setColor(Art.PARCH);
        double y = 205;
        for (String l : Art.wrap(g, "»" + r[0] + "«", 860)) {
            Art.centered(g, l, 640, y);
            y += 38;
        }
        for (int k = 0; k < 4; k++) {
            RoundRectangle2D box = new RoundRectangle2D.Double(bx(k), by(k), 440, 90, 18, 18);
            boolean right = perm[k] == 1;
            Color fill = new Color(16, 20, 32, 220);
            if (chosen >= 0 && right) fill = new Color(40, 110, 60, 230);
            else if (chosen == k) fill = new Color(130, 40, 40, 230);
            else if (chosen < 0 && inBox(k)) fill = new Color(40, 40, 70, 230);
            g.setColor(fill);
            g.fill(box);
            g.setColor(Art.alpha(Art.GOLD, 150));
            g.setStroke(new BasicStroke(2f));
            g.draw(box);
            g.setFont(Art.BIG_UI);
            Art.shadowText(g, String.valueOf(k + 1), bx(k) + 24, by(k) + 56, Art.GOLD);
            g.setFont(Art.H2);
            Art.shadowText(g, r[perm[k]], bx(k) + 70, by(k) + 56, Color.WHITE);
        }
        topBar(g, "Richtig: " + correct + "   Fehler: " + mistakes + " / " + allowed(), "Zeit: " + (int) Math.ceil(Math.max(0, timeLeft)) + " s",
            timeLeft / timeMax, Art.GOLD);
    }
}
