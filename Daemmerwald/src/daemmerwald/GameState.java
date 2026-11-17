package daemmerwald;

import java.util.*;

/** Spielfortschritt eines Durchlaufs. */
public class GameState {
    public CharacterDef hero = CharacterDef.ALL.get(0);
    public final EnumSet<Shard> shards = EnumSet.noneOf(Shard.class);
    public final Set<String> flags = new HashSet<>();
    public final List<String> notes = new ArrayList<>();
    public final Map<String, Integer> stages = new HashMap<>();
    public double playTime;
    public int level = 1, xp, banished, gamesWon;

    public boolean has(Shard s) { return shards.contains(s); }

    public void give(Shard s) { shards.add(s); }

    public int count() { return shards.size(); }

    public boolean allShards() { return shards.size() == Shard.values().length; }

    public boolean has(Ability a) { return level >= a.level; }

    public int stage(String key) { return stages.getOrDefault(key, 1); }

    public int xpNext() { return 60 + level * 50; }

    /** Fügt Erfahrung hinzu und liefert neu erreichte Fähigkeiten (leer, falls keine). */
    public List<String> addXp(int amount) {
        List<String> msgs = new ArrayList<>();
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
}
