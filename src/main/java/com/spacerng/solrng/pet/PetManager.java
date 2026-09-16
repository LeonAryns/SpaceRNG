package com.spacerng.solrng.pet;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.PetOrbit;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.stats.StatSources;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pets: relics that ride in the aura's slots and pay a percentage.
 *
 * Everything about a pet is config, and a player's side of it is two
 * lists in their save file: what they own and the at most three they
 * wear. The boost is a percentage of a single stat rather than a
 * multiplier of everything, so three pets are a choice between three
 * numbers a player can actually compare.
 *
 * How a pet is earned is deliberately not decided here yet. Until it is,
 * they are handed out with /rngadmin pet give, and /pets shows the rest
 * as locked without pretending to know the way in.
 */
public class PetManager {

    /** The three aura slots. Three is the look budget, not a guess. */
    public static final int SLOTS = 3;

    private final SolRNGPlugin plugin;
    private final Map<String, PetType> types = new LinkedHashMap<>();
    private boolean enabled = true;

    public PetManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        types.clear();
        enabled = config.getBoolean("pets.enabled", true);
        ConfigurationSection section = config.getConfigurationSection("pets.types");
        if (section == null) {
            plugin.getLogger().info("No pets configured.");
            return;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(rawId);
            if (p == null) continue;
            String id = rawId.toLowerCase(Locale.ROOT);

            Material icon = Material.matchMaterial(p.getString("icon", "NETHER_STAR"));
            if (icon == null) icon = Material.NETHER_STAR;
            List<String> colors = p.getStringList("colors");
            if (colors.isEmpty()) colors = List.of("#FFD54F");

            Rarity rarity;
            try {
                rarity = Rarity.valueOf(p.getString("rarity", "COMMON").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Pet '" + id + "' has an unknown rarity, using Common.");
                rarity = Rarity.COMMON;
            }
            StatSources.Id stat;
            try {
                stat = StatSources.Id.valueOf(p.getString("stat", "LUCK").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Pet '" + id + "' boosts an unknown stat, using Luck.");
                stat = StatSources.Id.LUCK;
            }

            types.put(id, new PetType(id, p.getString("display", rawId), colors, icon, rarity, stat,
                    Math.max(0.0, p.getDouble("percent", 0.0)),
                    p.getString("blurb", "")));
        }
        plugin.getLogger().info("Loaded " + types.size() + " pets.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, PetType> getTypes() {
        return types;
    }

    public PetType get(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------
    // Owning and wearing
    // ---------------------------------------------------------------

    /** Hands a pet over. False when they already had it. */
    public boolean give(PlayerData data, PetType pet) {
        if (!data.getOwnedPets().add(pet.id())) return false;
        // The first pets go straight into the empty slots. Somebody who has
        // just been handed their first one should not have to go and find
        // the menu before it does anything.
        if (data.getEquippedPets().size() < SLOTS) data.getEquippedPets().add(pet.id());
        return true;
    }

    public boolean take(PlayerData data, PetType pet) {
        data.getEquippedPets().remove(pet.id());
        return data.getOwnedPets().remove(pet.id());
    }

    public boolean isEquipped(PlayerData data, PetType pet) {
        return data.getEquippedPets().contains(pet.id());
    }

    /**
     * Puts a pet in or takes it out. Returns what happened so the menu can
     * say it: EQUIPPED, UNEQUIPPED, FULL or LOCKED.
     */
    public Result toggle(PlayerData data, PetType pet) {
        if (!data.ownsPet(pet.id())) return Result.LOCKED;
        if (data.getEquippedPets().remove(pet.id())) return Result.UNEQUIPPED;
        if (data.getEquippedPets().size() >= SLOTS) return Result.FULL;
        data.getEquippedPets().add(pet.id());
        return Result.EQUIPPED;
    }

    public enum Result {
        EQUIPPED, UNEQUIPPED, FULL, LOCKED
    }

    /** The equipped pets that still exist in config, in slot order. */
    public List<PetType> equipped(PlayerData data) {
        List<PetType> out = new ArrayList<>();
        for (String id : data.getEquippedPets()) {
            PetType pet = types.get(id);
            if (pet != null) out.add(pet);
        }
        return out;
    }

    // ---------------------------------------------------------------
    // What they pay
    // ---------------------------------------------------------------

    /**
     * The total percentage the worn pets add to one stat, as a fraction:
     * two pets at 5% each come back as 0.10. StatSources turns that into
     * the 1.10x it applies.
     */
    public double totalOf(PlayerData data, StatSources.Id stat) {
        if (!enabled) return 0.0;
        double total = 0.0;
        for (PetType pet : equipped(data)) {
            if (pet.stat() == stat) total += pet.percent();
        }
        return total;
    }

    // ---------------------------------------------------------------
    // The look
    // ---------------------------------------------------------------

    /** The orbit for what this player is wearing, or null when nothing is. */
    public PetOrbit orbit(PlayerData data) {
        if (!enabled) return null;
        List<Material> icons = new ArrayList<>();
        for (PetType pet : equipped(data)) icons.add(pet.icon());
        return icons.isEmpty() ? null : new PetOrbit(icons);
    }

    /** What the orbit is built from, so the aura can tell one set from another. */
    public String signature(PlayerData data) {
        return String.join(",", data.getEquippedPets());
    }

    /** Puts the pets back on after a change, without disturbing anything else. */
    public void refresh(Player player) {
        plugin.getAuraManager().applyTag(player);
    }
}
