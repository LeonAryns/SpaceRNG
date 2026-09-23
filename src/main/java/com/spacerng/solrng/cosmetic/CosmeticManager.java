package com.spacerng.solrng.cosmetic;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cosmetics: the things a player wears that change nothing (V188).
 *
 * Two kinds live here. A TITLE is a word in front of a name, given out
 * rather than bought, and Beta is the first of them. A NAME COLOUR is the
 * gradient a name is painted in, which the top rank picks for itself.
 *
 * Deliberately not the same thing as the rarity tag from /index. That is
 * a drop you equip and it carries a Luck multiplier; these carry nothing
 * at all, which is the point: a rank should be able to sell them without
 * selling an advantage.
 */
public final class CosmeticManager {

    /** One title. {@code autoGrant} hands it to anybody who logs in while it is on. */
    public record Title(String id, String display, List<String> colors, String description,
                        boolean autoGrant) {
    }

    /** One name gradient, for whoever the top rank is. */
    public record NameColour(String id, String display, List<String> colors) {
    }

    private final SolRNGPlugin plugin;
    private final Map<String, Title> titles = new LinkedHashMap<>();
    private final Map<String, NameColour> nameColours = new LinkedHashMap<>();

    public CosmeticManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        titles.clear();
        nameColours.clear();

        ConfigurationSection tagSection = config.getConfigurationSection("cosmetics.titles");
        if (tagSection != null) {
            for (String id : tagSection.getKeys(false)) {
                ConfigurationSection t = tagSection.getConfigurationSection(id);
                if (t == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                titles.put(key, new Title(key,
                        t.getString("display", id),
                        colours(t.getStringList("colors")),
                        t.getString("description", ""),
                        t.getBoolean("auto-grant", false)));
            }
        }

        ConfigurationSection colourSection = config.getConfigurationSection("cosmetics.name-colours");
        if (colourSection != null) {
            for (String id : colourSection.getKeys(false)) {
                ConfigurationSection c = colourSection.getConfigurationSection(id);
                if (c == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                nameColours.put(key, new NameColour(key, c.getString("display", id),
                        colours(c.getStringList("colors"))));
            }
        }
        plugin.getLogger().info("Loaded " + titles.size() + " cosmetic titles and "
                + nameColours.size() + " name colours.");
    }

    private static List<String> colours(List<String> raw) {
        return raw.isEmpty() ? List.of("#FFFFFF") : List.copyOf(raw);
    }

    public List<Title> titles() {
        return new ArrayList<>(titles.values());
    }

    public Title title(String id) {
        return id == null ? null : titles.get(id.toLowerCase(Locale.ROOT));
    }

    public List<NameColour> nameColours() {
        return new ArrayList<>(nameColours.values());
    }

    public NameColour nameColour(String id) {
        return id == null ? null : nameColours.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Hands out every title marked auto-grant. Called on join, so switching
     * one off ends the handout without taking it from anybody who has it,
     * which is exactly what "the first tag for beta players" needs.
     */
    public void grantAutomatic(PlayerData data) {
        for (Title title : titles.values()) {
            if (title.autoGrant()) data.giveCosmeticTag(title.id());
        }
    }

    /**
     * The worn title as " [Beta]", or empty.
     *
     * It goes AFTER the name (V190). A badge in front of a name competes
     * with the rank badge for the first thing the eye lands on, and the
     * name is what people are actually looking for in a list.
     */
    public String suffixOf(PlayerData data) {
        Title title = title(data.getWornCosmeticTag());
        if (title == null || !data.hasCosmeticTag(title.id())) return "";
        return " " + ChatColor.DARK_GRAY + "[" + styled(title) + ChatColor.DARK_GRAY + "]";
    }

    /** A title's name in its own colours. */
    public String styled(Title title) {
        if (title == null) return "";
        return title.colors().size() > 1
                ? Lore.gradient(title.display(), true, title.colors().toArray(new String[0]))
                : Lore.gradient(title.display(), true, title.colors().get(0));
    }

    public String styled(NameColour colour, String text) {
        if (colour == null) return text;
        return Lore.gradient(text, false, colour.colors().toArray(new String[0]));
    }

    /**
     * Whether a player may paint their own name. It rides on the rank
     * perk that already exists, {@code rgb-name}, so the top rank is the
     * one that can and nothing new had to be invented to say so.
     */
    public boolean canPickNameColour(PlayerData data) {
        return plugin.getRankManager().has(data, "rgb");
    }

    /** The gradient stops for a player's chosen name colour, or null for the rank's own. */
    public String[] stopsFor(PlayerData data) {
        if (!canPickNameColour(data)) return null;
        NameColour colour = nameColour(data.getNameColour());
        return colour == null ? null : colour.colors().toArray(new String[0]);
    }
}
