package com.spacerng.solrng.pet;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.PetOrbit;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
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
 * What a pet pays stays a percentage of a single stat, and
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

            types.put(id, new PetType(id, p.getString("display", rawId), colors, icon, rarity, stat,
                    Math.max(0.0, p.getDouble("percent", 0.0)),
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
    // Making and growing
    // ---------------------------------------------------------------

    /**
     * Spends Cosmic Dust on a new pet.
     *
     * A type you do not own yet comes home as a fresh Rarity 1 copy. A
     * type you already own becomes a rarity level on the copy you have,
     * so the dust is never wasted and the menu never fills with
     * duplicates. When every pet is already at max rarity there is
     * nothing to buy and the dust stays where it is.
     */
    public Made make(PlayerData data) {
        return upgrades.eggs().isEmpty() ? Made.none() : make(data, upgrades.eggs().get(0));
    }

    /** Hatches one egg: its price in Cosmic Dust, its odds on the roll. */
    public Made make(PlayerData data, PetEgg egg) {
        if (!enabled || types.isEmpty() || egg == null) return Made.none();
        if (data.getPrestige() < egg.minPrestige()) return Made.none();
        long cost = egg.cost();
        if (data.getCosmicDust() < cost) return Made.none();

        PetType picked = roll(data, egg);
        if (picked == null) return Made.none();
        if (!data.spendCosmicDust(cost)) return Made.none();

        PetInstance had = data.getPet(picked.id());
        if (had == null) {
            PetInstance made = PetInstance.fresh(picked.id());
            data.putPet(made);
            // The first pets go straight into the empty slots. Somebody who
            // has just paid for their first one should not have to find the
            // menu before it does anything.
            if (data.getEquippedPets().size() < slots(data)) data.getEquippedPets().add(picked.id());
            return new Made(picked, made, true);
        }
        PetInstance grown = had.withRarity(had.rarity() + 1);
        data.putPet(grown);
        return new Made(picked, grown, false);
    }

    /** What {@link #make} did, so the menu and the chat line can say it. */
    public record Made(PetType type, PetInstance pet, boolean isNew) {
        public static Made none() {
            return new Made(null, null, false);
        }

        public boolean happened() {
            return type != null;
        }
    }

    /**
     * Picks which pet the dust becomes, weighted by the weight in config.
     * Types already sitting at max rarity are left out, because paying for
     * one would do nothing.
     */
    private PetType roll(PlayerData data, PetEgg egg) {
        // V313: Divine is read first, as a flat chance per egg. See the
        // note on PetEgg.hatchesDivine for why it is not a weight: the
        // boost would drag it along and "ten times the chance" would stop
        // being ten times the moment the boost was retuned.
        if (egg.hatchesDivine() && ThreadLocalRandom.current().nextDouble() < egg.divineChance()) {
            PetType divine = pickDivine(data);
            // Nobody left to hatch there (every Divine pet already maxed),
            // so the roll falls through to the ordinary pool rather than
            // eating the dust for nothing.
            if (divine != null) return divine;
        }
        Map<PetType, Double> pool = pool(data, egg);
        double total = 0.0;
        for (double weight : pool.values()) total += weight;
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
     * One Divine pet, weighted among themselves (V313).
     *
     * Weighted rather than picked flat, because there is one Divine pet
     * today and there will be more, and a flat pick would silently make
     * each new one as likely as the Starheart.
     */
    private PetType pickDivine(PlayerData data) {
        Map<PetType, Double> pool = new java.util.LinkedHashMap<>();
        double total = 0.0;
        for (PetType type : types.values()) {
            if (type.rarity() != com.spacerng.solrng.rarity.Rarity.DIVINE) continue;
            if (type.weight() <= 0.0) continue;
            PetInstance had = data.getPet(type.id());
            if (had != null && had.rarity() >= upgrades.maxRarity()) continue;
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
     * What an egg can hatch for this player and how heavy each pet is.
     * Pets already at max rarity are left out, because paying for one
     * would do nothing.
     */
    private Map<PetType, Double> pool(PlayerData data, PetEgg egg) {
        Map<PetType, Double> pool = new java.util.LinkedHashMap<>();
        for (PetType type : types.values()) {
            PetInstance had = data.getPet(type.id());
            if (had != null && had.rarity() >= upgrades.maxRarity()) continue;
            if (type.weight() <= 0.0) continue;
            if (!egg.hatches(type.rarity())) continue;
            double weight = type.weight();
            if (type.rarity().ordinal() >= upgrades.boostedFrom().ordinal()) weight *= egg.boost();
            pool.put(type, weight);
        }
        return pool;
    }

    /** The chance of each pet rarity out of this egg, for the tooltip. */
    public Map<com.spacerng.solrng.rarity.Rarity, Double> odds(PlayerData data, PetEgg egg) {
        Map<PetType, Double> pool = pool(data, egg);
        double total = 0.0;
        for (double weight : pool.values()) total += weight;
        Map<com.spacerng.solrng.rarity.Rarity, Double> odds =
                new java.util.EnumMap<>(com.spacerng.solrng.rarity.Rarity.class);
        // V313: the Divine chance is read before the pool, so the pool's
        // own odds are what is left after it. Printing the raw pool here
        // would have the tooltip claim more than the egg gives, and the
        // whole point of working the tooltip out from the real numbers is
        // that it cannot drift from them.
        double divine = egg.hatchesDivine() && pickDivine(data) != null
                ? Math.min(1.0, egg.divineChance())
                : 0.0;
        if (divine > 0.0) {
            odds.merge(com.spacerng.solrng.rarity.Rarity.DIVINE, divine, Double::sum);
        }
        if (total <= 0.0) return odds;
        for (Map.Entry<PetType, Double> entry : pool.entrySet()) {
            odds.merge(entry.getKey().rarity(), (1.0 - divine) * entry.getValue() / total, Double::sum);
        }
        return odds;
    }

    /** Buys one rarity level with Gems (V313). Always takes. */
    public Result upgradeRarity(PlayerData data, String typeId) {
        PetInstance pet = data.getPet(typeId);
        if (pet == null) return Result.LOCKED;
        if (pet.rarity() >= upgrades.maxRarity()) return Result.MAXED;
        if (!data.spendShards(upgrades.rarityCost(pet.rarity()))) return Result.TOO_POOR;
        data.putPet(pet.withRarity(pet.rarity() + 1));
        return Result.DONE;
    }

    /**
     * Attempts one tier with Gems (V313). The Gems are spent either way: a
     * tier attempt that cost nothing on a failure would just be a slower
     * guaranteed upgrade, and then the percentage means nothing.
     */
    public Result upgradeTier(PlayerData data, String typeId) {
        PetInstance pet = data.getPet(typeId);
        if (pet == null) return Result.LOCKED;
        if (pet.tier() >= upgrades.maxTier()) return Result.MAXED;
        if (!data.spendShards(upgrades.tierCost(pet.tier()))) return Result.TOO_POOR;

        double bonus = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.PET_TIER_CHANCE);
        if (ThreadLocalRandom.current().nextDouble() >= upgrades.tierChance(pet.tier(), bonus)) {
            return Result.FAILED;
        }
        data.putPet(pet.withTier(pet.tier() + 1));
        return Result.DONE;
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
     * Rarity, tier and shiny are folded in here through PetUpgrades, so
     * every stat picks them up without StatSources knowing pets have
     * levels at all.
     */
    public double totalOf(PlayerData data, StatSources.Id stat) {
        if (!enabled) return 0.0;
        double total = 0.0;
        for (String id : worn(data)) {
            PetType type = types.get(id);
            if (type == null || type.stat() != stat) continue;
            PetInstance pet = data.getPet(id);
            total += type.percent() * upgrades.multiplier(pet);
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
