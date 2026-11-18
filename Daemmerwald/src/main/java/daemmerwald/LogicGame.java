package daemmerwald;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Denkrätsel: Logik, Zahlenfolgen und Um-die-Ecke-Denken. Nach jeder Antwort erklärt Ambrosius den Lösungsweg.
 * Je höher die Stufe, desto kniffliger die Rätsel.
 */
public class LogicGame extends MiniGame {
    /** {Schwierigkeit 1–3, Frage, richtige Antwort, falsch, falsch, falsch, Erklärung} */
    static final String[][] POOL = {
        // --- Stufe 1: Aufwärmen ---------------------------------------------------------------
        {"1", "Eine Kerze brennt in 3 Stunden ab. Wie lange brennen 3 gleiche Kerzen, wenn man sie gleichzeitig anzündet?", "3 Stunden", "9 Stunden", "1 Stunde", "6 Stunden",
            "Gleichzeitig angezündet brennen alle drei nebeneinander – jede braucht ihre 3 Stunden, zusammen also auch nur 3."},
        {"1", "Welche Zahl fehlt?  2 · 4 · 8 · 16 · ?", "32", "24", "20", "18", "Jede Zahl ist das Doppelte der vorigen: 16 · 2 = 32."},
        {"1", "Eine Eule ist älter als der Rabe. Der Rabe ist älter als die Taube. Wer ist am jüngsten?", "Die Taube", "Die Eule", "Der Rabe", "Das kann man nicht wissen",
            "Eule > Rabe > Taube – die Taube steht ganz hinten."},
        {"1", "Im Kessel schwimmen 10 Frösche. 3 springen heraus, 2 springen wieder hinein. Wie viele sind im Kessel?", "9", "7", "5", "15", "10 − 3 + 2 = 9."},
        {"1", "Ein Zauberlehrling hat 5 Kerzen. Er zündet alle an, dann bläst er 2 aus. Wie viele Kerzen hat er am Ende noch?", "5", "3", "2", "0",
            "Ausgeblasene Kerzen sind immer noch Kerzen! Er hat weiterhin 5 – drei davon sind kürzer abgebrannt."},
        {"1", "Welche Zahl fehlt?  1 · 3 · 5 · 7 · ?", "9", "8", "10", "11", "Immer zwei weiter: die ungeraden Zahlen."},
        {"1", "Montag ist zwei Tage nach welchem Tag?", "Samstag", "Sonntag", "Mittwoch", "Freitag", "Zwei Tage vor Montag: Sonntag, dann Samstag."},
        {"1", "Was ist schwerer: ein Kilo Federn oder ein Kilo Steine?", "Beide gleich schwer", "Die Steine", "Die Federn", "Kommt auf das Wetter an",
            "Ein Kilo ist ein Kilo. Nur das Gefühl täuscht uns, weil Federn so viel Platz brauchen."},
        // --- Stufe 2: Logik --------------------------------------------------------------------
        {"2", "Drei Truhen: Gold, Silber, Kupfer. Auf JEDER steht ein falsches Schild. Auf der ersten steht „Gold“, auf der zweiten „Silber“, auf der dritten „Gold oder Silber“. Wo ist das Gold?",
            "In der zweiten Truhe", "In der ersten Truhe", "In der dritten Truhe", "In keiner",
            "Die dritte lügt, also liegt dort Kupfer. Die erste lügt („Gold“), also liegt das Gold in der zweiten – und Silber in der ersten."},
        {"2", "Zwei Statuen bewachen zwei Türen. Eine sagt immer die Wahrheit, eine lügt immer. Du darfst EINE Frage stellen. Welche führt sicher zur richtigen Tür?",
            "„Welche Tür würde mir die andere Statue zeigen?“ – und dann die andere Tür nehmen", "„Bist du die Lügnerin?“", "„Welche Tür ist richtig?“", "„Wie spät ist es?“",
            "Beide Statuen zeigen auf dieselbe FALSCHE Tür: Die Wahre gibt die Lüge der anderen ehrlich weiter, die Lügnerin verdreht die Wahrheit. Also nimmst du die andere."},
        {"2", "Welche Zahl fehlt?  1 · 1 · 2 · 3 · 5 · 8 · ?", "13", "11", "12", "16", "Jede Zahl ist die Summe der beiden davor: 5 + 8 = 13."},
        {"2", "Ein Zauberer braucht genau 4 Liter Trank, hat aber nur Krüge mit 3 und 5 Litern. Wie geht es?",
            "5er füllen, in den 3er gießen (2 bleiben), 3er leeren, die 2 hineingießen, 5er füllen und den 3er auffüllen – 4 bleiben im 5er",
            "Zweimal den 3er füllen und 2 wegschütten", "Den 5er halb voll machen und 1 Liter dazu", "Das geht nicht",
            "Mit Umgießen entstehen neue Mengen: 5 − 3 = 2, dann 5 − (3 − 2) = 4."},
        {"2", "Lena ist größer als Paul. Paul ist größer als Mira. Mira ist größer als Tom. Wer ist der Zweitkleinste?", "Mira", "Paul", "Tom", "Lena",
            "Reihe von groß nach klein: Lena, Paul, Mira, Tom. Der Zweitkleinste ist Mira."},
        {"2", "Welche Zahl fehlt?  3 · 6 · 11 · 18 · 27 · ?", "38", "36", "35", "40", "Die Abstände wachsen: +3, +5, +7, +9, also +11 → 38."},
        {"2", "Ein Händler kauft einen Zauberstab für 6 Taler, verkauft ihn für 7, kauft ihn für 8 zurück und verkauft ihn für 9. Wie viel hat er gewonnen?",
            "2 Taler", "1 Taler", "3 Taler", "Nichts", "Zwei getrennte Geschäfte: 7 − 6 = 1 und 9 − 8 = 1. Zusammen 2 Taler."},
        {"2", "Fünf Freunde geben sich gegenseitig je einmal die Hand. Wie viele Händedrücke sind das?", "10", "20", "25", "5",
            "Der Erste schüttelt 4 Hände, der Zweite noch 3 neue, dann 2, dann 1: 4 + 3 + 2 + 1 = 10."},
        // --- Stufe 3: Um die Ecke denken --------------------------------------------------------
        {"3", "Ein Mann wohnt im 10. Stock. Morgens fährt er mit dem Aufzug ganz hinunter. Abends fährt er nur bis zum 7. Stock und läuft den Rest – außer wenn es regnet. Warum?",
            "Er ist klein und kommt nur mit dem Regenschirm an den Knopf für den 10. Stock", "Er will Sport machen", "Der Aufzug ist kaputt", "Er besucht jemanden im 7. Stock",
            "Um die Ecke gedacht: Er reicht nur bis zum Knopf 7 – mit dem Schirm kommt er höher."},
        {"3", "Du bist bei einem Wettrennen und überholst den Zweiten. Auf welchem Platz bist du jetzt?", "Zweiter", "Erster", "Dritter", "Das kommt drauf an",
            "Wer den Zweiten überholt, nimmt seinen Platz ein – Erster bist du damit noch nicht."},
        {"3", "Ein Hirte hat 17 Schafe. Alle bis auf 9 laufen davon. Wie viele bleiben?", "9", "8", "17", "0", "„Alle bis auf 9“ heißt: 9 bleiben."},
        {"3", "Ein Zimmer hat 3 Lichtschalter draußen und eine Lampe drinnen. Du darfst nur EINMAL hineingehen. Wie findest du den richtigen Schalter?",
            "Schalter 1 lange an, dann aus, Schalter 2 an, hineingehen: leuchtet = 2, warm = 1, kalt = 3", "Alle drei nacheinander probieren", "Durchs Schlüsselloch schauen", "Das ist unmöglich",
            "Eine Lampe verrät nicht nur Licht, sondern auch Wärme – so bekommst du drei Antworten mit einem Besuch."},
        {"3", "Manche Monate haben 31 Tage, manche 30. Wie viele Monate haben 28 Tage?", "Alle zwölf", "Einer", "Zwei", "Keiner", "Jeder Monat hat mindestens 28 Tage."},
        {"3", "Ein Bauer muss Wolf, Ziege und Kohl über den Fluss bringen. Im Boot ist nur Platz für ihn und eine Sache. Was bringt er ZUERST hinüber?",
            "Die Ziege", "Den Wolf", "Den Kohl", "Alles gleichzeitig",
            "Allein frisst der Wolf die Ziege und die Ziege den Kohl – nur Wolf und Kohl vertragen sich. Also zuerst die Ziege."},
        {"3", "Welche Zahl fehlt?  2 · 3 · 5 · 7 · 11 · ?", "13", "12", "14", "15", "Es sind Primzahlen – teilbar nur durch 1 und sich selbst. Die nächste ist 13."},
        {"3", "Wie oft kann man 10 von 100 abziehen?", "Einmal – danach ziehst du von 90 ab", "Zehnmal", "Neunmal", "Unendlich oft",
            "Um die Ecke gedacht: Nach dem ersten Mal ist es nicht mehr 100."},
        {"3", "Eine Seerose verdoppelt jeden Tag ihre Fläche. Nach 30 Tagen bedeckt sie den ganzen Teich. Wann war der Teich halb bedeckt?", "Am 29. Tag", "Am 15. Tag", "Am 20. Tag", "Am 1. Tag",
            "Rückwärts denken: Einen Tag vor „ganz“ war es „halb“, denn danach hat sie sich noch einmal verdoppelt."},
        {"3", "Eine Statue sagt: „Ich lüge gerade.“ Was stimmt?", "Der Satz kann weder wahr noch falsch sein – ein Paradoxon", "Sie lügt", "Sie sagt die Wahrheit", "Sie ist eine Wahrsagerin",
            "Wäre der Satz wahr, würde sie lügen – dann wäre er falsch. Wäre er falsch, würde sie nicht lügen – dann wäre er wahr. Ein klassisches Lügner-Paradoxon."},
    };

    /** Besondere Rätselreihe (z. B. das Siegel des Buches der Fünf): feste Anzahl, nur schwere Rätsel. */
    private final String customTitle;
    private final int minTier;
    private final List<Integer> order = new ArrayList<>();
    private int idx, correct, mistakes, chosen = -1;
    private final int[] perm = new int[4];

    public LogicGame(GamePanel g) { this(g, null, 1); }

    public LogicGame(GamePanel g, String title, int minTier) {
        super(g);
        this.customTitle = title;
        this.minTier = minTier;
    }

    private int count() { return customTitle != null ? 3 : 5; }

    private int allowed() { return customTitle != null ? 1 : 1 + (level <= 2 ? 1 : 0); }

    @Override protected String title() { return customTitle != null ? customTitle : "Denkrätsel bei Ambrosius Kniffel"; }

    @Override protected String subtitle() { return "»Die Lösung liegt selten dort, wo man zuerst hinschaut.«"; }

    @Override protected String[] rules() {
        return new String[]{
            "Löse " + count() + " Rätsel: Logik, Zahlenfolgen und Um-die-Ecke-Denken. Wähle mit der Maus oder den Tasten 1–4.",
            "Du darfst dir " + allowed() + " Fehler erlauben. Nimm dir Zeit – hier gibt es keine Uhr.",
            "Nach jeder Antwort erklärt Ambrosius, wie man darauf kommt. Weiter mit Enter oder Klick."
        };
    }

    @Override protected String winText() { return "Ambrosius klatscht leise in die Hände. »Ein Kopf, der um Ecken denkt – genau so einen braucht Sternenhall.«"; }

    @Override protected String loseText() { return "»Knapp daneben ist auch vorbei«, sagt Ambrosius und lächelt. »Aber jetzt kennst du die Tricks. Komm wieder!«"; }

    @Override protected Color tint() { return new Color(26, 22, 40); }

    @Override
    protected void start() {
        int tier = customTitle != null ? minTier : Math.min(3, 1 + (level - 1) / 2);
        List<Integer> hard = new ArrayList<>(), easy = new ArrayList<>();
        for (int i = 0; i < POOL.length; i++) {
            int tt = Integer.parseInt(POOL[i][0]);
            if (tt == tier) hard.add(i); else if (tt < tier || tt == tier + 1) easy.add(i);
        }
        Collections.shuffle(hard, rnd);
        Collections.shuffle(easy, rnd);
        order.addAll(hard);
        order.addAll(easy);
        next();
    }

    private void next() {
        List<Integer> p = new ArrayList<>(List.of(2, 3, 4, 5));
        Collections.shuffle(p, rnd);
        for (int i = 0; i < 4; i++) perm[i] = p.get(i);
        chosen = -1;
    }

    private String[] cur() { return POOL[order.get(Math.min(idx, order.size() - 1))]; }

    @Override
    protected void play(double dt) {
        if (chosen >= 0) {
            if (in.wasPressed(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE) || in.mouseClicked) {
                idx++;
                if (mistakes > allowed()) { end(false); return; }
                if (idx >= count()) { end(true); return; }
                next();
            }
            return;
        }
        int pick = -1;
        for (int k = 0; k < 4; k++) if (in.wasPressed(KeyEvent.VK_1 + k, KeyEvent.VK_NUMPAD1 + k)) pick = k;
        if (in.mouseClicked) for (int k = 0; k < 4; k++) if (inBox(k)) pick = k;
        if (pick >= 0) {
            chosen = pick;
            if (perm[pick] == 2) { correct++; game.state.riddlesSolved++; }
            else mistakes++;
        }
    }

    private double bx(int k) { return 120 + (k % 2) * 530; }

    private double by(int k) { return 330 + (k / 2) * 100; }

    private boolean inBox(int k) { return in.mouseX > bx(k) && in.mouseX < bx(k) + 510 && in.mouseY > by(k) && in.mouseY < by(k) + 88; }

    @Override
    protected void draw(Graphics2D g) {
        if (order.isEmpty()) return;
        String[] r = cur();
        Art.panel(g, 100, 96, 1080, 220);
        g.setFont(Art.SMALL);
        g.setColor(new Color(180, 180, 200));
        String kind = r[0].equals("1") ? "AUFWÄRMEN" : r[0].equals("2") ? "LOGIK" : "UM DIE ECKE DENKEN";
        Art.centered(g, "RÄTSEL " + Math.min(count(), idx + 1) + " VON " + count() + "  ·  " + kind, 640, 132);
        g.setFont(new Font("Serif", Font.ITALIC, 25));
        g.setColor(Art.PARCH);
        double y = 176;
        for (String l : Art.wrap(g, r[1], 1000)) { Art.centered(g, l, 640, y); y += 34; }
        for (int k = 0; k < 4; k++) {
            RoundRectangle2D box = new RoundRectangle2D.Double(bx(k), by(k), 510, 88, 18, 18);
            boolean right = perm[k] == 2;
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
            Art.shadowText(g, String.valueOf(k + 1), bx(k) + 20, by(k) + 54, Art.GOLD);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(Color.WHITE);
            double ty = by(k) + 34;
            for (String l : Art.wrap(g, r[perm[k]], 430)) { Art.str(g, l, bx(k) + 62, ty); ty += 20; }
        }
        if (chosen >= 0) {
            Art.panel(g, 100, 540, 1080, 120);
            g.setFont(Art.UI);
            Art.shadowText(g, perm[chosen] == 2 ? "Richtig!" : "Nicht ganz – so geht es:", 124, 574, perm[chosen] == 2 ? new Color(140, 230, 150) : new Color(255, 170, 160));
            g.setFont(new Font("Serif", Font.PLAIN, 19));
            g.setColor(Art.PARCH);
            Art.drawWrapped(g, r[6], 124, 604, 1030, 24);
            g.setFont(Art.SMALL);
            g.setColor(new Color(190, 185, 210));
            Art.str(g, "[Enter] weiter", 1060, 650);
        }
        topBar(g, "Richtig: " + correct + "   Fehler: " + mistakes + " / " + allowed(), "Stufe " + level, (idx + (chosen >= 0 ? 1 : 0)) / (double) count(), Art.GOLD);
    }
}
