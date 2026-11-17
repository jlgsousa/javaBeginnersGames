package daemmerwald;

import java.util.ArrayList;
import java.util.List;

/**
 * Freundschaften: Jede Figur hat bis zu fünf Herzen. Herzen gibt es fürs tägliche Plaudern, für Geschenke
 * (Lieblingsgeschenke zählen viel mehr), erledigte Aufträge und gemeinsame Spiele.
 * Belohnungen: ab 2 Herzen eine Rarität, ab 3 Herzen ein Geheimnis (z. B. ein Geheimgang im Palast),
 * ab 4 Herzen Taler und Tränke, mit 5 Herzen ein legendäres Geschenk.
 */
public final class Friends {
    private Friends() { }

    public static final int PER_HEART = 20, MAX_HEARTS = 5;

    public record Friend(String id, String name, Item[] likes, Treasure[] loves, Treasure gift2, String secret, String secretText, Treasure gift5) { }

    public static final List<Friend> ALL = List.of(
        new Friend("borga", "Borga", new Item[]{Item.KROETENMOOS, Item.STERNKRISTALL}, new Treasure[]{Treasure.GOLDENE_EICHEL, Treasure.DRACHENSCHUPPE},
            Treasure.GOLDENE_EICHEL, null, "Im Steinbruch liegen manchmal Drachenschuppen – nein, keine echten. Oder doch?", Treasure.DRACHENEI),
        new Friend("ilvy", "Ilvy", new Item[]{Item.MONDBLUME, Item.SEEROSE}, new Treasure[]{Treasure.GLUEHGLAS, Treasure.FEENKRONE},
            Treasure.GLUEHGLAS, "regal", "Psst! In der Palastbibliothek gibt es ein blaues Buch, das nicht ins Regal passt. Zauber es an!", Treasure.HERZSAMEN),
        new Friend("rektor", "Rektor Aldric Venn", new Item[]{Item.FEDER, Item.STERNKRISTALL}, new Treasure[]{Treasure.STERNKARTE, Treasure.ZEITSAND},
            Treasure.KRISTALLROSE, "sternwarte", "Über dem großen Saal liegt eine Sternwarte, die nicht auf den Plänen steht. Die Wendeltreppe beginnt hinter dem Wandteppich.", Treasure.HERZSAMEN),
        new Friend("velmara", "Magistra Velmara", new Item[]{Item.STERNKRISTALL, Item.ASCHWURZ}, new Treasure[]{Treasure.STERNENSTAUB, Treasure.GEISTERLATERNE},
            Treasure.STERNENSTAUB, "regal", "Die Bibliothek hat ein Regal, das nur so tut, als wäre es ein Regal. Ein Lichtzauber verrät es.", Treasure.SPIEGELSCHERBE),
        new Friend("oberion", "Kanzler Oberion", new Item[]{Item.SEEROSE, Item.MONDBLUME}, new Treasure[]{Treasure.MONDPERLE, Treasure.KRISTALLROSE},
            Treasure.MONDMOOS, "kamin", "Der Kamin im Thronsaal ist kalt – aber dahinter zieht es. Ein Feuerzauber würde ihn öffnen.", Treasure.MONDPERLE),
        new Friend("grumhild", "Clanälteste Grumhild", new Item[]{Item.STERNKRISTALL, Item.KROETENMOOS}, new Treasure[]{Treasure.DRACHENSCHUPPE, Treasure.DRACHENEI},
            Treasure.DRACHENSCHUPPE, "kamin", "Wir Trolle haben den Palastkamin gemauert. Und eine Tür dahinter, von der niemand weiß.", Treasure.DRACHENEI),
        new Friend("ysolde", "Moorhexe Ysolde", new Item[]{Item.ASCHWURZ, Item.KROETENMOOS}, new Treasure[]{Treasure.GEISTERLATERNE, Treasure.ZEITSAND},
            Treasure.GEISTERLATERNE, "gruft", "Unter dem Palast ist eine Gruft. Der Eingang ist eine Steinplatte mit einem Mondzeichen im Keller.", Treasure.SPIEGELSCHERBE),
        new Friend("orakel", "Das Pilzorakel", new Item[]{Item.KROETENMOOS, Item.MONDBLUME}, new Treasure[]{Treasure.MONDMOOS, Treasure.SPIEGELSCHERBE},
            Treasure.MONDMOOS, "sternwarte", "Ich sehe … Sterne hinter Stoff. Einen Wandteppich, der atmet.", Treasure.STERNKARTE),
        new Friend("kestra", "Fährtenleserin Kestra", new Item[]{Item.FEDER, Item.SEEROSE}, new Treasure[]{Treasure.EINHORNHAAR, Treasure.PHOENIXFEDER},
            Treasure.REGENSCHUPPE, null, "Einhornhaar findest du nur bei Sonnenaufgang auf den hellsten Wiesen. Steh früh auf!", Treasure.PHOENIXFEDER),
        new Friend("vex", "Nachtmahr Vex", new Item[]{Item.ASCHWURZ}, new Treasure[]{Treasure.GEISTERLATERNE, Treasure.SPIEGELSCHERBE},
            Treasure.GEISTERLATERNE, "gruft", "Die Gruft unter dem Palast … dort schlafe ich manchmal. Die Platte mit dem Mond, du weißt schon.", Treasure.SPIEGELSCHERBE),
        new Friend("aurin", "Lichtfee Aurin", new Item[]{Item.MONDBLUME, Item.FEDER}, new Treasure[]{Treasure.FEENKRONE, Treasure.GLUEHGLAS},
            Treasure.FEENKRONE, "sternwarte", "Ich fliege oft zur geheimen Sternwarte hinauf. Der Weg beginnt hinter dem Wandteppich im Saal!", Treasure.HERZSAMEN),
        new Friend("brumm", "Brumm der Läufer", new Item[]{Item.FEDER, Item.KROETENMOOS}, new Treasure[]{Treasure.UHRWERKKAEFER, Treasure.GOLDENE_EICHEL},
            Treasure.UHRWERKKAEFER, null, "Im Feendorf Laternhain krabbeln Uhrwerk-Käfer. Ganz selten, aber es gibt sie!", Treasure.PHOENIXFEDER),
        new Friend("grol", "Steinwächter Grol", new Item[]{Item.STERNKRISTALL}, new Treasure[]{Treasure.DRACHENSCHUPPE, Treasure.ZEITSAND},
            Treasure.ZEITSAND, "gruft", "Ich bewache Steine. Auch die mit dem Mondzeichen im Palastkeller. Darunter: die Gruft.", Treasure.STERNKARTE),
        new Friend("pimpernell", "Mäusewirtin Pimpernell", new Item[]{Item.MONDBLUME, Item.SEEROSE}, new Treasure[]{Treasure.FLUESTERMUSCHEL, Treasure.KRISTALLROSE},
            Treasure.FLUESTERMUSCHEL, null, "Flüsternde Muscheln liegen an der Südküste unter der Klippe. Ich trage eine als Hut!", Treasure.KRISTALLROSE)
    );

    public static Friend get(String id) {
        for (Friend f : ALL) if (f.id().equals(id)) return f;
        return null;
    }

    public static int points(GameState st, String id) { return st.friendship.getOrDefault(id, 0); }

    public static int hearts(GameState st, String id) { return Math.min(MAX_HEARTS, points(st, id) / PER_HEART); }

    /** Fügt Freundschaftspunkte hinzu und vergibt Belohnungen. Liefert Texte für die Anzeige. */
    public static List<String> add(GameState st, String id, int pts) {
        List<String> out = new ArrayList<>();
        Friend f = get(id);
        if (f == null || pts <= 0) return out;
        int before = hearts(st, id);
        st.friendship.put(id, Math.min(MAX_HEARTS * PER_HEART, points(st, id) + pts));
        int after = hearts(st, id);
        for (int h = before + 1; h <= after; h++) {
            out.add("*♥ " + f.name() + " und du: jetzt " + h + (h == 1 ? " Herz" : " Herzen") + " Freundschaft!");
            switch (h) {
                case 1 -> out.add("*Du weißt jetzt, was " + f.name() + " gern mag (siehe Menü → Freunde).");
                case 2 -> {
                    st.addTreasure(f.gift2(), 1);
                    out.add(f.name() + ": Hier, für dich. Ich will, dass du das hast: " + f.gift2().title + "!");
                }
                case 3 -> {
                    out.add(f.name() + ": Ich verrate dir ein Geheimnis … " + f.secretText());
                    if (f.secret() != null) st.flags.add("hint:" + f.secret());
                }
                case 4 -> {
                    int c = st.earn(60);
                    st.potions.merge(Potion.values()[st.friendship.size() % Potion.values().length], 1, Integer::sum);
                    out.add("*" + f.name() + " schenkt dir " + c + " Glimmertaler und einen Trank.");
                }
                case 5 -> {
                    st.addTreasure(f.gift5(), 1);
                    st.flags.add("bff:" + id);
                    out.add(f.name() + ": Du bist mein allerbester Freund. Das hier ist das Kostbarste, was ich habe: " + f.gift5().title + ".");
                }
                default -> { }
            }
        }
        return out;
    }

    /** Wie sehr freut sich die Figur über ein Geschenk? (Schlüssel "I:…" Zutat, "T:…" Rarität) */
    public static int giftValue(Friend f, String key) {
        String n = key.substring(2);
        if (key.startsWith("T:")) {
            Treasure t = Treasure.valueOf(n);
            for (Treasure l : f.loves()) if (l == t) return 45;
            return 18 + t.rarity * 8;
        }
        Item it = Item.valueOf(n);
        for (Item l : f.likes()) if (l == it) return 12;
        return 4;
    }

    public static String reaction(Friend f, int value) {
        if (value >= 40) return "Oh! Das ist ja … genau das habe ich mir immer gewünscht! Danke, danke, danke!";
        if (value >= 18) return "Eine echte Rarität? Für mich? Das bewahre ich gut auf.";
        if (value >= 12) return "Mein Lieblingsding! Du hast dir gemerkt, was ich mag.";
        return "Oh, danke. Das ist … nett von dir.";
    }
}
