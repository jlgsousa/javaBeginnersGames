package daemmerwald;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Spielfortschritt eines Durchlaufs – kann gespeichert und geladen werden. */
public class GameState {
    public CharacterDef hero = CharacterDef.ALL.get(0);
    /** Der Name, den sich die Spielerin oder der Spieler gegeben hat. */
    public String playerName = "";
    /** Zwei Spieler am geteilten Bildschirm. Spieler 2 teilt Taler, Gepäck und Missionen. */
    public boolean twoPlayers;
    public CharacterDef hero2 = CharacterDef.ALL.get(5);
    public String player2Name = "";
    public final EnumSet<Shard> shards = EnumSet.noneOf(Shard.class);
    public final Set<String> flags = new HashSet<>();
    public final List<String> notes = new ArrayList<>();
    public final Map<String, Integer> stages = new HashMap<>();
    public final EnumMap<Item, Integer> items = new EnumMap<>(Item.class);
    public final EnumMap<Potion, Integer> potions = new EnumMap<>(Potion.class);
    public final EnumMap<Potion, Float> effects = new EnumMap<>(Potion.class);
    public final Set<String> picked = new HashSet<>();
    /** Sammelaufträge: Auftraggeber-ID -> {Zutat-Index, Anzahl, Belohnung}. */
    public final Map<String, int[]> quests = new LinkedHashMap<>();
    public final EnumSet<Outfit> owned = EnumSet.of(Outfit.WANDERER);
    public Outfit outfit = Outfit.WANDERER;
    public int coins = 40;
    public double playTime;
    public int level = 1, xp, banished, gamesWon, racesWon, riddlesSolved, brewed, collected;
    public float px = 0, pz = -50, dayTime = 0.30f;
    public boolean loaded;

    /** Anzeigename: eigener Name oder der Vorname der Figur. */
    public String displayName() { return playerName == null || playerName.isBlank() ? hero.name.split(" ")[0] : playerName; }

    public String displayName2() { return player2Name == null || player2Name.isBlank() ? hero2.name.split(" ")[0] : player2Name; }

    public boolean has(Shard s) { return shards.contains(s); }

    public void give(Shard s) { shards.add(s); }

    public int count() { return shards.size(); }

    public boolean allShards() { return shards.size() == Shard.values().length; }

    public boolean has(Ability a) { return level >= a.level; }

    public boolean gadget(Outfit.Gadget g) { return outfit.has(g); }

    /** Belohnung in Glimmertalern (Händlerrock: +30 %). */
    public int earn(int c) {
        int v = gadget(Outfit.Gadget.GREED) ? Math.round(c * 1.3f) : c;
        coins += v;
        return v;
    }

    public int stage(String key) { return stages.getOrDefault(key, 1); }

    public int xpNext() { return 60 + level * 50; }

    public int items(Item i) { return items.getOrDefault(i, 0); }

    public void addItem(Item i, int n) { items.merge(i, n, Integer::sum); }

    public boolean effect(Potion p) { return effects.getOrDefault(p, 0f) > 0; }

    /** Fügt Erfahrung hinzu und liefert Meldungen über Stufenaufstiege. */
    public List<String> addXp(int amount) {
        List<String> msgs = new ArrayList<>();
        if (gadget(Outfit.Gadget.WISDOM)) amount = Math.round(amount * 1.2f);
        xp += amount;
        while (xp >= xpNext()) {
            xp -= xpNext();
            level++;
            msgs.add("*Du erreichst Stufe " + level + ".");
            for (Ability a : Ability.values()) {
                if (a.level == level) msgs.add("*Neue Fähigkeit: " + a.title + " – " + a.desc);
            }
        }
        return msgs;
    }

    // ---------------------------------------------------------------- Speichern

    public static Path saveFile() {
        return Paths.get(System.getProperty("user.home"), ".daemmerwald", "spielstand.properties");
    }

    public static boolean saveExists() { return Files.exists(saveFile()); }

    public void save() {
        Properties p = new Properties();
        p.setProperty("hero", String.valueOf(CharacterDef.ALL.indexOf(hero)));
        p.setProperty("name", playerName);
        p.setProperty("two", String.valueOf(twoPlayers));
        p.setProperty("hero2", String.valueOf(CharacterDef.ALL.indexOf(hero2)));
        p.setProperty("name2", player2Name);
        p.setProperty("shards", join(shards.stream().map(Enum::name).toList()));
        p.setProperty("flags", join(flags));
        p.setProperty("notes", String.join("\u0001", notes));
        p.setProperty("picked", join(picked));
        StringBuilder sb = new StringBuilder();
        stages.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        p.setProperty("stages", sb.toString());
        StringBuilder it = new StringBuilder();
        items.forEach((k, v) -> it.append(k.name()).append('=').append(v).append(','));
        p.setProperty("items", it.toString());
        StringBuilder po = new StringBuilder();
        potions.forEach((k, v) -> po.append(k.name()).append('=').append(v).append(','));
        p.setProperty("potions", po.toString());
        StringBuilder q = new StringBuilder();
        quests.forEach((k, v) -> q.append(k).append('=').append(v[0]).append(':').append(v[1]).append(':').append(v[2]).append(','));
        p.setProperty("quests", q.toString());
        p.setProperty("outfits", join(owned.stream().map(Enum::name).toList()));
        p.setProperty("outfit", outfit.name());
        p.setProperty("coins", String.valueOf(coins));
        p.setProperty("nums", playTime + "," + level + "," + xp + "," + banished + "," + gamesWon + "," + racesWon + ","
            + riddlesSolved + "," + brewed + "," + collected + "," + px + "," + pz + "," + dayTime);
        try {
            Files.createDirectories(saveFile().getParent());
            try (Writer w = Files.newBufferedWriter(saveFile())) {
                p.store(w, "Das Fluestern im Daemmerwald - Spielstand");
            }
        } catch (IOException e) {
            System.err.println("Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    public static GameState load() {
        GameState s = new GameState();
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(saveFile())) {
            p.load(r);
            s.hero = CharacterDef.ALL.get(Integer.parseInt(p.getProperty("hero", "0")));
            s.playerName = p.getProperty("name", "");
            s.twoPlayers = Boolean.parseBoolean(p.getProperty("two", "false"));
            s.hero2 = CharacterDef.ALL.get(Integer.parseInt(p.getProperty("hero2", "5")));
            s.player2Name = p.getProperty("name2", "");
            for (String x : split(p.getProperty("shards"))) s.shards.add(Shard.valueOf(x));
            s.flags.addAll(split(p.getProperty("flags")));
            String notes = p.getProperty("notes", "");
            if (!notes.isEmpty()) s.notes.addAll(Arrays.asList(notes.split("\u0001")));
            s.picked.addAll(split(p.getProperty("picked")));
            for (String kv : split(p.getProperty("stages"))) {
                String[] a = kv.split("=");
                s.stages.put(a[0], Integer.parseInt(a[1]));
            }
            for (String kv : split(p.getProperty("items"))) {
                String[] a = kv.split("=");
                s.items.put(Item.valueOf(a[0]), Integer.parseInt(a[1]));
            }
            for (String kv : split(p.getProperty("potions"))) {
                String[] a = kv.split("=");
                s.potions.put(Potion.valueOf(a[0]), Integer.parseInt(a[1]));
            }
            for (String kv : split(p.getProperty("quests"))) {
                String[] a = kv.split("=");
                String[] v = a[1].split(":");
                s.quests.put(a[0], new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])});
            }
            for (String x : split(p.getProperty("outfits"))) s.owned.add(Outfit.valueOf(x));
            s.outfit = Outfit.valueOf(p.getProperty("outfit", "WANDERER"));
            s.coins = Integer.parseInt(p.getProperty("coins", "0"));
            String[] n = p.getProperty("nums").split(",");
            s.playTime = Double.parseDouble(n[0]);
            s.level = Integer.parseInt(n[1]);
            s.xp = Integer.parseInt(n[2]);
            s.banished = Integer.parseInt(n[3]);
            s.gamesWon = Integer.parseInt(n[4]);
            s.racesWon = Integer.parseInt(n[5]);
            s.riddlesSolved = Integer.parseInt(n[6]);
            s.brewed = Integer.parseInt(n[7]);
            s.collected = Integer.parseInt(n[8]);
            s.px = Float.parseFloat(n[9]);
            s.pz = Float.parseFloat(n[10]);
            s.dayTime = Float.parseFloat(n[11]);
            s.loaded = true;
        } catch (Exception e) {
            System.err.println("Spielstand konnte nicht geladen werden: " + e);
            return null;
        }
        return s;
    }

    private static String join(Collection<String> c) { return String.join(";", c); }

    private static List<String> split(String s) {
        if (s == null || s.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String x : s.split("[;,]")) if (!x.isEmpty()) out.add(x);
        return out;
    }
}
