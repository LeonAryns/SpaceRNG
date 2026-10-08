package com.spacerng.solrng.pet;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.PetOrbit;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.stats.StatSources;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pets: relics that ride in the aura's slots and pay a percentage.
 *
 * A pet is made out of Cosmic Dust, which falls rarely while rolling once
 * the skill tree has unlocked it. From there it grows along two axes that
 * never touch each other: rarity is bought with Cosmic Dust and always
 * takes, tier is bought with Farm Dust from harvesting and can fail. The
 * two currencies are the point. Rolling feeds one axis and farming feeds
 * the other, so a pet is the one thing on the server that asks you to do
 * both.
 *
 * What a pet pays is a MULTIPLIER on a single stat (V324), and
 * {@link PetUpgrades} is the only place that says what rarity and tier do
 * to it. Three pets are still a choice between three numbers a player can
 * compare at a glance.
 */
public class PetManager {

    /**
     * The hard ceiling on worn pets. Three is the aura's look budget, not
     * a guess, and the skill tree climbs towards it rather than past it.
     */
    public static final int MAX_SLOTS = 3;

    private final SolRNGPlugin plugin;
    private final Map<String, PetType> types = new LinkedHashMap<>();
    private final PetUpgrades upgrades = new PetUpgrades();
    private boolean enabled = true;
    private int baseSlots = 1;

    public PetManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        types.clear();
        enabled = config.getBoolean("pets.enabled", true);
        baseSlots = Math.max(1, Math.min(MAX_SLOTS, config.getInt("pets.base-slots", 1)));
        upgrades.load(config);

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

            // V324: what a pet is worth is its RARITY, from
            // pets.multipliers, not a number on its own entry. An old
            // config that still carries percent is honoured, so a server
            // that has not taken the new table yet keeps its nine pets
            // working exactly as they did.
            double bonus = p.contains("percent")
                    ? Math.max(0.0, p.getDouble("percent", 0.0))
                    : upgrades.bonusFor(rarity);
            types.put(id, new PetType(id, p.getString("display", rawId), colors, icon, rarity, stat,
                    bonus,
                    Math.max(0.0, p.getDouble("weight", 1.0)),
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

    /**
     * Switches autotrash for a whole rarity (V327) and says what it is
     * now: on unless every pet in it is already marked, in which case the
     * click takes them all off again.
     */
    public boolean toggleAutoTrash(PlayerData data, Rarity rarity) {
        java.util.List<String> ids = new ArrayList<>();
        for (PetType type : types.values()) if (type.rarity() == rarity) ids.add(type.id());
        boolean allOn = !ids.isEmpty();
        for (String id : ids) if (!data.isPetAutoTrash(id)) allOn = false;
        for (String id : ids) {
            if (allOn) data.getPetAutoTrash().remove(id);
            else data.getPetAutoTrash().add(id);
        }
        return !allOn;
    }

    /** Whether any pet is defined at this rarity, for the index ladder. */
    public boolean hasRarity(Rarity rarity) {
        for (PetType type : types.values()) if (type.rarity() == rarity) return true;
        return false;
    }

    public PetType get(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    public PetUpgrades upgrades() {
        return upgrades;
    }

    // ---------------------------------------------------------------
    // Slots
    // ---------------------------------------------------------------

    /**
     * How many pets this player can wear. Starts at baseSlots and climbs
     * with the Pet Slots skill, never past MAX_SLOTS.
     */
    public int slots(PlayerData data) {
        double extra = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.PET_SLOTS);
        return Math.max(1, Math.min(MAX_SLOTS, baseSlots + (int) Math.round(extra)));
    }

    // ---------------------------------------------------------------
    // The V359 wipe
    // ---------------------------------------------------------------

    /**
     * Clears the pets and the Cosmic Dust a save carries from before the
     * rework, once, on join (V359).
     *
     * Leon's call, and it is not tidiness. A pet used to carry a rarity
     * level and a tier, two ladders bought with Gems, and both are gone;
     * the multipliers under them moved by a factor of ten as well. There
     * is no honest way to map a Rarity 7 Tier 4 pet onto a level, so
     * rather than invent one the collection starts again, and the dust
     * that paid for it goes with it so nobody is sitting on a balance
     * bought against the old prices.
     *
     * pets.wipe-version says which round this is. Raising it in config
     * does it again, which is the door to use if the eggs are re-cut.
     */
    public void wipeIfOld(Player player, PlayerData data) {
        int version = plugin.getConfig().getInt("pets.wipe-version", 0);
        if (data.getPetWipeVersion() >= version) return;

        int had = data.getOwnedPets().size();
        long dust = data.getCosmicDust();
        data.getOwnedPets().clear();
        data.getEquippedPets().clear();
        data.getPetAutoTrash().clear();
        if (dust > 0) data.spendCosmicDust(dust);
        data.setPetWipeVersion(version);

        if (had > 0 || dust > 0) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Pets have been rebuilt. "
                    + ChatColor.GRAY + "A pet is one level ladder now, 1 to 10, paid in Cosmic Dust, "
                    + "and you pick which stat it boosts. The old rarity and tier ladders are gone, "
                    + "so the " + ChatColor.WHITE + had + ChatColor.GRAY + " pets and "
                    + ChatColor.WHITE + dust + ChatColor.GRAY + " Cosmic Dust bought against them "
                    + "went with them.");
        }
    }

    // ---------------------------------------------------------------
    // Storage
    // ---------------------------------------------------------------

    /**
     * How many pets this player holds, counting one per kind.
     *
     * V359: Leon asked for a hundred and for a full storage to STOP the
     * opening rather than throw anything away. Duplicates do not count,
     * because a duplicate is a number on a pet already in the list and
     * nobody would understand a collection that filled up without any new
     * pet appearing in it.
     */
    public int held(PlayerData data) {
        return data.getOwnedPets().size();
    }

    public int storage() {
        return upgrades.storage();
    }

    /** True when no more pets fit, so the eggs refuse before taking payment. */
    public boolean storageFull(PlayerData data) {
        return held(data) >= upgrades.storage();
    }

    // ---------------------------------------------------------------
    // Making and growing
    // ---------------------------------------------------------------

    /**
     * Spends Cosmic Dust on a new pet.
     *
     * V324: a type you already own comes back as a DUPLICATE rather than
     * a free rarity level. A hatch you already had used to turn into a
     * number going up somewhere you were not looking, which is why it
     * never felt like a result. A pet marked for autotrash in the pet
     * index is thrown away the moment it hatches, so the one cost of
     * duplicates, a collection filling with things you do not want, is
     * yours to switch off per pet.
     */
    public Made make(PlayerData data) {
        return upgrades.eggs().isEmpty() ? Made.none() : make(data, upgrades.eggs().get(0));
    }

    /** Hatches one egg: its price in Cosmic Dust, its odds on the roll. */
    public Made make(PlayerData data, PetEgg egg) {
        if (!enabled || types.isEmpty() || egg == null) return Made.none();
        if (data.getPrestige() < egg.minPrestige()) return Made.none();
        if (storageFull(data)) return Made.none();
        long cost = egg.cost();
        if (data.getCosmicDust() < cost) return Made.none();

        PetType picked = roll(data, egg);
        if (picked == null) return Made.none();
        if (!data.spendCosmicDust(cost)) return Made.none();

        PetInstance had = data.getPet(picked.id());
        if (data.isPetAutoTrash(picked.id())) {
            // Thrown away on landing. The dust is spent either way: a free
            // reroll on everything you did not want would make autotrash
            // the best way to hatch rather than a tidying tool.
            // The instance here is only what the hatch animation and the
            // chat line read; nothing is stored, because the copy was
            // thrown away. It is never null, or the hatch would take the
            // server down on the first autotrashed pet nobody owns.
            return new Made(picked, had == null ? PetInstance.fresh(picked.id()) : had, false, true);
        }
        if (had == null) {
            PetInstance made = PetInstance.fresh(picked.id());
            data.putPet(made);
            // The first pets go straight into the empty slots. Somebody who
            // has just paid for their first one should not have to find the
            // menu before it does anything.
            if (data.getEquippedPets().size() < slots(data)) data.getEquippedPets().add(picked.id());
            return new Made(picked, made, true, false);
        }
        PetInstance grown = had.plusCopy();
        data.putPet(grown);
        return new Made(picked, grown, false, false);
    }

    /** What {@link #make} did, so the menu and the chat line can say it. */
    public record Made(PetType type, PetInstance pet, boolean isNew, boolean trashed) {
        public static Made none() {
            return new Made(null, null, false, false);
        }

        public boolean happened() {
            return type != null;
        }
    }

    /**
     * Picks which pet an egg becomes, in two steps.
     *
     * First the rarity, from the egg's own written chances: one draw
     * walked from the rarest down, and anything left over is the egg's
     * floor. Then which pet inside that rarity, by weight. The two steps
     * are the whole design Leon asked for, because every stat exists at
     * every rarity: the rarity you hatch and the stat you wanted are
     * separate rolls, so a Divine can still be a stat you do not care
     * about.
     *
     * Nothing is excluded for being maxed any more. A pet you already own
     * comes back as a duplicate, so there is always something to hatch.
     */
    private PetType roll(PlayerData data, PetEgg egg) {
        Rarity rarity = rollRarity(egg);
        PetType picked = pickIn(rarity);
        if (picked != null) return picked;
        // No pet defined at that rarity: walk down rather than eat the
        // dust for nothing.
        for (int ordinal = rarity.ordinal() - 1; ordinal >= 0; ordinal--) {
            PetType lower = pickIn(Rarity.values()[ordinal]);
            if (lower != null) return lower;
        }
        for (int ordinal = rarity.ordinal() + 1; ordinal < Rarity.values().length; ordinal++) {
            PetType higher = pickIn(Rarity.values()[ordinal]);
            if (higher != null) return higher;
        }
        return null;
    }

    /** One draw against the egg's ladder, rarest first. */
    private Rarity rollRarity(PetEgg egg) {
        double pick = ThreadLocalRandom.current().nextDouble();
        double acc = 0.0;
        Rarity[] all = Rarity.values();
        for (int ordinal = all.length - 1; ordinal >= 0; ordinal--) {
            Rarity rarity = all[ordinal];
            if (rarity == egg.floor()) continue;
            acc += Math.max(0.0, egg.chances().getOrDefault(rarity, 0.0));
            if (pick < acc) return rarity;
        }
        return egg.floor();
    }

    /** One pet of this rarity, weighted among themselves, or null if none exist. */
    private PetType pickIn(Rarity rarity) {
        Map<PetType, Double> pool = new java.util.LinkedHashMap<>();
        double total = 0.0;
        for (PetType type : types.values()) {
            if (type.rarity() != rarity || type.weight() <= 0.0) continue;
            pool.put(type, type.weight());
            total += type.weight();
        }
        if (pool.isEmpty() || total <= 0.0) return null;
        double pick = ThreadLocalRandom.current().nextDouble() * total;
        PetType last = null;
        for (Map.Entry<PetType, Double> entry : pool.entrySet()) {
            last = entry.getKey();
            pick -= entry.getValue();
            if (pick <= 0.0) return last;
        }
        return last;
    }

    /**
     * The chance of each pet rarity out of this egg, for the tooltip.
     * These ARE the config numbers now, not a calculation of them, so
     * what the egg says and what the egg does cannot drift apart.
     */
    public Map<Rarity, Double> odds(PlayerData data, PetEgg egg) {
        Map<Rarity, Double> odds = new java.util.EnumMap<>(Rarity.class);
        for (Map.Entry<Rarity, Double> entry : egg.ladder().entrySet()) {
            if (pickIn(entry.getKey()) == null) continue;
            odds.put(entry.getKey(), entry.getValue());
        }
        return odds;
    }

    /**
     * Throws one spare copy away. The copy that IS the pet never goes, so
     * a pet is never lost to a misclick; /pets has no delete and this is
     * not one.
     */
    public boolean trashCopy(PlayerData data, String typeId) {
        PetInstance pet = data.getPet(typeId);
        if (pet == null || pet.spare() <= 0) return false;
        data.putPet(pet.minusCopy());
        return true;
    }

    /**
     * Buys one level with Cosmic Dust (V359). Always takes.
     *
     * Dust is the point of it. Leon asked for pets to grow on the back of
     * the Cosmic Dust skills, so the thing that makes a pet stronger is
     * the same thing those skills pay out, and a player who has levelled
     * them levels pets faster without a second ladder to read.
     */
    public Result upgradeLevel(PlayerData data, String typeId) {
        PetInstance pet = data.getPet(typeId);
        if (pet == null) return Result.LOCKED;
        if (pet.level() >= upgrades.maxLevel()) return Result.MAXED;
        if (!data.spendCosmicDust(levelCost(data, pet))) return Result.TOO_POOR;
        data.putPet(pet.withLevel(pet.level() + 1));
        return Result.DONE;
    }

    /**
     * What the next level costs this player, Steady Hands taken off.
     *
     * The menu and the purchase both read this, so the price on the
     * button is the price that is charged. The discount never passes 90%,
     * or a long enough skill tree would make levels free.
     */
    public long levelCost(PlayerData data, PetInstance pet) {
        long base = upgrades.levelCost(pet == null ? 1 : pet.level());
        double off = Math.min(0.90, Math.max(0.0,
                plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.PET_LEVEL_DISCOUNT)));
        return Math.max(1L, (long) Math.ceil(base * (1.0 - off)));
    }

    /**
     * Points a pet's multiplier at another stat (V359).
     *
     * Free and reversible at any time, Leon's call. What a pet is worth
     * is its rarity and its level; which stat it pays is the player's
     * choice, so a collection is never wasted on stats nobody wanted.
     */
    public Result setStat(PlayerData data, String typeId, StatSources.Id stat) {
        PetInstance pet = data.getPet(typeId);
        if (pet == null || stat == null) return Result.LOCKED;
        data.putPet(pet.withStat(stat));
        return Result.DONE;
    }

    /** Which stat an owned pet pays, falling back to the type's own. */
    public StatSources.Id statOf(PlayerData data, PetType type) {
        if (type == null) return StatSources.Id.LUCK;
        PetInstance pet = data.getPet(type.id());
        return pet == null ? type.stat() : pet.statOr(type);
    }

    /**
     * Makes a pet shiny for Gems (V313).
     *
     * The gate is having found a shiny of the pet's own rarity, which is
     * the same thing that unlocks that rarity's shiny aura. That is the
     * closest the plugin has to "owning a shiny aura": there is no shiny
     * aura item to combine, only the rarity you have proven you can find.
     */
    public Result makeShiny(PlayerData data, String typeId) {
        PetInstance pet = data.getPet(typeId);
        if (pet == null) return Result.LOCKED;
        if (pet.shiny()) return Result.MAXED;
        PetType type = get(typeId);
        if (type == null) return Result.LOCKED;
        if (plugin.getRarityManager().foundIn(data, type.rarity(), true) <= 0) return Result.NO_SHINY;
        if (!data.spendShards(upgrades.shinyCost())) return Result.TOO_POOR;
        data.putPet(pet.asShiny());
        return Result.DONE;
    }

    /** What an upgrade attempt did. */
    public enum Result {
        DONE, FAILED, TOO_POOR, MAXED, LOCKED, NO_SHINY
    }

    // ---------------------------------------------------------------
    // Wearing
    // ---------------------------------------------------------------

    /**
     * Hands a pet over outright, for /rngadmin. False when they had it
     * already. This is the testing door, not the way in: players make
     * pets out of Cosmic Dust.
     */
    public boolean give(PlayerData data, PetType pet) {
        if (data.ownsPet(pet.id())) return false;
        data.putPet(PetInstance.fresh(pet.id()));
        if (data.getEquippedPets().size() < slots(data)) data.getEquippedPets().add(pet.id());
        return true;
    }

    public boolean take(PlayerData data, PetType pet) {
        data.getEquippedPets().remove(pet.id());
        return data.getOwnedPets().remove(pet.id()) != null;
    }

    public boolean isEquipped(PlayerData data, PetType pet) {
        return data.getEquippedPets().contains(pet.id());
    }

    /**
     * Puts a pet in or takes it out. Returns what happened so the menu can
     * say it: EQUIPPED, UNEQUIPPED, FULL or LOCKED.
     */
    public Worn toggle(PlayerData data, PetType pet) {
        if (!data.ownsPet(pet.id())) return Worn.LOCKED;
        if (data.getEquippedPets().remove(pet.id())) return Worn.UNEQUIPPED;
        if (data.getEquippedPets().size() >= slots(data)) return Worn.FULL;
        data.getEquippedPets().add(pet.id());
        return Worn.EQUIPPED;
    }

    public enum Worn {
        EQUIPPED, UNEQUIPPED, FULL, LOCKED
    }

    /** The equipped pets that still exist in config, in slot order. */
    public List<PetType> equipped(PlayerData data) {
        List<PetType> out = new ArrayList<>();
        for (String id : worn(data)) {
            PetType pet = types.get(id);
            if (pet != null) out.add(pet);
        }
        return out;
    }

    /**
     * The ids actually being worn, trimmed to the slots this player has.
     * Slots come from the skill tree, so somebody who respecs out of the
     * node keeps the pets and simply stops wearing the extra ones.
     */
    private List<String> worn(PlayerData data) {
        List<String> ids = data.getEquippedPets();
        int limit = Math.min(ids.size(), slots(data));
        return ids.subList(0, Math.max(0, limit));
    }

    // ---------------------------------------------------------------
    // What they pay
    // ---------------------------------------------------------------

    /**
     * The total percentage the worn pets add to one stat, as a fraction:
     * two pets at 5% each come back as 0.10. StatSources turns that into
     * the 1.10x it applies.
     *
     * Level and shiny are folded in here through PetUpgrades, so every
     * stat picks them up without StatSources knowing pets have levels at
     * all.
     */
    public double totalOf(PlayerData data, StatSources.Id stat) {
        if (!enabled) return 0.0;
        double total = 0.0;
        for (String id : worn(data)) {
            PetType type = types.get(id);
            if (type == null) continue;
            PetInstance pet = data.getPet(id);
            // V359: the stat is the player's choice, held on the owned
            // copy, so a worn pet pays wherever it has been pointed.
            if ((pet == null ? type.stat() : pet.statOr(type)) != stat) continue;
            total += type.bonus() * upgrades.multiplier(pet);
        }
        return total;
    }

    // ---------------------------------------------------------------
    // The look
    // ---------------------------------------------------------------

    /** The orbit for what this player is wearing, or null when nothing is. */
    public PetOrbit orbit(PlayerData data) {
        if (!enabled) return null;
        List<PetOrbit.Worn> relics = new ArrayList<>();
        for (PetType pet : equipped(data)) {
            PetInstance owned = data.getPet(pet.id());
            relics.add(new PetOrbit.Worn(pet.icon(),
                    com.spacerng.solrng.roll.RollAura.colorFor(pet.rarity()),
                    owned != null && owned.shiny()));
        }
        return relics.isEmpty() ? null : new PetOrbit(relics);
    }

    /**
     * What the orbit is built from, so the aura can tell one set from
     * another. Shiny is in the signature because a pet turning shiny has
     * to redraw the orbit.
     */
    public String signature(PlayerData data) {
        StringBuilder out = new StringBuilder();
        for (String id : worn(data)) {
            PetInstance pet = data.getPet(id);
            out.append(id).append(pet != null && pet.shiny() ? "*" : "").append(',');
        }
        return out.toString();
    }

    /** Puts the pets back on after a change, without disturbing anything else. */
    public void refresh(Player player) {
        plugin.getAuraManager().applyTag(player);
    }
}
