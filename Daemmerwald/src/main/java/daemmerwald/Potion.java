package daemmerwald;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/** Brau-Rezepte. Zutaten werden an einem Kessel verbraut, die Tränke mit den Tasten 1–4 getrunken. */
public enum Potion {
    MANA("Manatrank", "Füllt dein Mana auf und verdoppelt 2 Minuten lang die Regeneration.", new Color(120, 180, 255), 120,
        Item.MONDBLUME, 2, Item.SEEROSE, 1),
    SCHATTEN("Schattenschutz", "3 Minuten lang können dir Schattenwesen nichts anhaben.", new Color(190, 110, 255), 180,
        Item.ASCHWURZ, 2, Item.STERNKRISTALL, 1),
    WIND("Windtrank", "3 Minuten lang läufst du 40 % schneller.", new Color(170, 255, 170), 180,
        Item.FEDER, 1, Item.MONDBLUME, 1, Item.KROETENMOOS, 1),
    SICHT("Sternensicht", "5 Minuten lang zeigt die Karte alle Zutaten in deiner Nähe.", new Color(255, 230, 140), 300,
        Item.STERNKRISTALL, 1, Item.KROETENMOOS, 2);

    public final String title, desc;
    public final Color color;
    public final float seconds;
    public final Map<Item, Integer> needs = new EnumMap<>(Item.class);

    Potion(String title, String desc, Color color, float seconds, Object... parts) {
        this.title = title;
        this.desc = desc;
        this.color = color;
        this.seconds = seconds;
        for (int i = 0; i < parts.length; i += 2) needs.put((Item) parts[i], (Integer) parts[i + 1]);
    }

    public boolean canBrew(GameState st) {
        for (Map.Entry<Item, Integer> e : needs.entrySet()) if (st.items(e.getKey()) < e.getValue()) return false;
        return true;
    }

    public String recipe() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Item, Integer> e : needs.entrySet()) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(e.getValue()).append("× ").append(e.getKey().title);
        }
        return sb.toString();
    }
}
