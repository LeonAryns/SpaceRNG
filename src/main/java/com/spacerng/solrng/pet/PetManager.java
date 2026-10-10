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
    // V359, Leon's numbers: 5% Pet Luck for every pet found and 100% for
    // every egg finished.
    private double perPetLuck = 0.05;
    private double perEggLuck = 1.0;

    public PetManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        types.clear();
        enabled = config.getBoolean("pets.enabled", true);
        baseSlots = Math.max(1, Math.min(MAX_SLOTS, config.getInt("pets.base-slots", 1)));
        perPetLuck = Math.max(0.0, config.getDouble("pets.index.luck-per-pet", 0.05));
        perEggLuck = Math.max(0.0, config.getDouble("pets.index.luck-per-egg", 1.0));
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
            // pets.multipliers, not a number on its own entry. V359 adds
            // the egg it came out of, because a later egg's pets are
            // worth proportionally more. An old config that still carries
            // percent is honoured, so a server that has not taken the new
            // table yet keeps its pets working exactly as they did.
            String eggId = p.getString("egg", "");
            PetEgg from = eggId.isBlank() ? null : upgrades.egg(eggId);
            double bonus = p.contains("percent")
                    ? Math.max(0.0, p.getDouble("percent", 0.0))
                    : upgrades.bonusFor(rarity, from);
            types.put(id, new PetType(id, p.getString("display", rawId), colors, icon, rarity, stat,
                    bonus,
                    Math.max(0.0, p.getDouble("weight", 1.0)),
                    p.getString("blurb", ""), eggId));
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
        data.getPetsFound().clear();
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

    /**
     * Opens one egg: its price in Gems, its odds on the roll.
     *
     * V359: Gems, Leon's call. Cosmic Dust is what LEVELS a pet now, so
     * the two currencies are the two halves of the system rather than
     * both coming out of rolling.
     */
    public Made make(PlayerData data, PetEgg egg) {
        if (!enabled || types.isEmpty() || egg == null) return Made.none();
        if (data.getPrestige() < egg.minPrestige()) return Made.none();
        if (storageFull(data)) return Made.none();
        long cost = egg.cost();
        if (data.getShards() < cost) return Made.none();

        PetType picked = roll(data, egg);
        if (picked == null) return Made.none();
        if (!data.spendShards(cost)) return Made.none();

        // V359: the index records it the moment it lands, before autotrash
        // gets a look at it. A pet thrown away was still discovered, and
        // the Pet Luck it paid for is never taken back.
        data.discoverPet(picked.id());

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

    /**
     * Opens several eggs on one click (V359).
     *
     * Leon asked for 1, 3 and 9, and for the buying to keep going while
     * auto open is on. It stops the moment it cannot honestly continue:
     * out of Gems, out of storage, or the Prestige wall. Whatever it did
     * manage is paid for and kept, because a batch that rolls back half
     * way is a worse answer than a batch that says where it stopped.
     */
    public Batch makeMany(PlayerData data, PetEgg egg, int count) {
        java.util.List<Made> made = new ArrayList<>();
        Stop stop = Stop.DONE;
        for (int i = 0; i < Math.max(1, count); i++) {
            if (storageFull(data)) {
                stop = Stop.STORAGE_FULL;
                break;
            }
            if (egg != null && data.getShards() < egg.cost()) {
                stop = Stop.OUT_OF_GEMS;
                break;
            }
            Made one = make(data, egg);
            if (!one.happened()) {
                stop = Stop.REFUSED;
                break;
            }
            made.add(one);
        }
        return new Batch(made, stop);
    }

    /** What a batch of openings did, and why it stopped. */
    public record Batch(java.util.List<Made> made, Stop stop) {
        public int opened() {
            return made.size();
        }

        public int fresh() {
            int count = 0;
            for (Made one : made) if (one.isNew()) count++;
            return count;
        }

        /** The best thing that came out of it, for the chat line. */
        public Made best() {
            Made best = null;
            for (Made one : made) {
                if (best == null || one.type().rarity().ordinal() > best.type().rarity().ordinal()) {
                    best = one;
                }
            }
            return best;
        }
    }

    /** Why a batch stopped early, so the menu can say it in one line. */
    public enum Stop {
        DONE, OUT_OF_GEMS, STORAGE_FULL, REFUSED
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
     * Picks which pet an egg becomes.
     *
     * V359: an egg holds its own seven pets, one per rarity, so the whole
     * roll is the RARITY roll. Draw a rarity against the egg's written
     * ladder, and the egg's pet at that rarity is the pet. There is no
     * second draw to make any more, which is what lets the egg screen
     * print a chance beside every pet in it and be telling the truth.
     *
     * Nothing is excluded for being maxed. A pet you already own comes
     * back as a duplicate, so there is always something to hatch.
     */
    private PetType roll(PlayerData data, PetEgg egg) {
        Rarity rarity = rollRarity(data, egg);
        PetType picked = types.get(egg.petAt(rarity));
        if (picked != null) return picked;
        // An egg with a gap in its list: walk down rather than eat the
        // payment for nothing.
        for (int ordinal = rarity.ordinal() - 1; ordinal >= 0; ordinal--) {
            PetType lower = types.get(egg.petAt(Rarity.values()[ordinal]));
            if (lower != null) return lower;
        }
        for (int ordinal = rarity.ordinal() + 1; ordinal < Rarity.values().length; ordinal++) {
            PetType higher = types.get(egg.petAt(Rarity.values()[ordinal]));
            if (higher != null) return higher;
        }
        // An egg with no list at all falls back to the whole catalogue, so
        // a half written config still hatches something.
        return pickIn(rarity);
    }

    /**
     * One draw against the egg's ladder, rarest first, with Pet Luck on
     * top (V359).
     *
     * Pet Luck multiplies every rarity ABOVE the floor and the floor
     * absorbs the difference, which is exactly "the odds of the rarer
     * pets go up". It cannot run away: if the raised chances would add up
     * past a whole egg they are scaled back to leave a sliver of floor,
     * because a ladder that sums past 1 would silently make the rarest
     * entry impossible.
     */
    private Rarity rollRarity(PlayerData data, PetEgg egg) {
        double luck = 1.0 + petLuck(data);
        Rarity[] all = Rarity.values();

        double raised = 0.0;
        for (Rarity rarity : all) {
            if (rarity == egg.floor()) continue;
            raised += Math.max(0.0, egg.chances().getOrDefault(rarity, 0.0)) * luck;
        }
        double scale = raised > 0.999 ? 0.999 / raised : 1.0;

        double pick = ThreadLocalRandom.current().nextDouble();
        double acc = 0.0;
        for (int ordinal = all.length - 1; ordinal >= 0; ordinal--) {
            Rarity rarity = all[ordinal];
            if (rarity == egg.floor()) continue;
            acc += Math.max(0.0, egg.chances().getOrDefault(rarity, 0.0)) * luck * scale;
            if (pick < acc) return rarity;
        }
        return egg.floor();
    }

    // ---------------------------------------------------------------
    // Pet Luck
    // ---------------------------------------------------------------

    /**
     * How much the rarer pets are tilted toward, as a fraction (0.35 is
     * +35%). Leon's numbers (V359):
     *
     * <ul>
     *   <li>5% for every pet discovered, whatever its rarity. "5% if a
     *       common gets discovered etc" were his words, so a Common
     *       counts the same as a Divine.
     *   <li>100% for every egg whose seven rarities have all been found.
     *       An egg is seven pets, one per rarity, so completing it and
     *       finding every rarity in it are the same thing.
     *   <li>whatever the Pet Luck nodes in /skilltree add.
     * </ul>
     *
     * Discovery is "have you ever owned it", which is what the pet index
     * records, so throwing a pet away never costs the luck it bought.
     */
    public double petLuck(PlayerData data) {
        return perPetLuck * found(data) + perEggLuck * eggsCompleted(data)
                + Math.max(0.0, plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.PET_LUCK));
    }

    /** How many different pets this player has discovered. */
    public int found(PlayerData data) {
        int count = 0;
        for (String id : data.getPetsFound()) if (types.containsKey(id)) count++;
        return count;
    }

    /** Whether every pet in an egg has been discovered. */
    public boolean completed(PlayerData data, PetEgg egg) {
        if (egg == null || egg.pets().isEmpty()) return false;
        for (String id : egg.pets()) {
            if (!types.containsKey(id)) continue;
            if (!data.getPetsFound().contains(id)) return false;
        }
        return true;
    }

    /** How many of this player's eggs are finished. */
    public int eggsCompleted(PlayerData data) {
        int count = 0;
        for (PetEgg egg : upgrades.eggs()) if (completed(data, egg)) count++;
        return count;
    }

    /**
     * The chance this egg hatches a rarity right now, Pet Luck included.
     *
     * The egg screen prints this beside every pet, and the roll uses the
     * same arithmetic, so a card can never promise odds the egg does not
     * use. The floor rarity is whatever the others leave behind.
     */
    public double liveChance(PlayerData data, PetEgg egg, Rarity rarity) {
        if (egg == null) return 0.0;
        double luck = 1.0 + petLuck(data);
        double raised = 0.0;
        for (Rarity other : Rarity.values()) {
            if (other == egg.floor()) continue;
            raised += Math.max(0.0, egg.chances().getOrDefault(other, 0.0)) * luck;
        }
        double scale = raised > 0.999 ? 0.999 / raised : 1.0;
        if (rarity == egg.floor()) return Math.max(0.0, 1.0 - raised * scale);
        return Math.max(0.0, egg.chances().getOrDefault(rarity, 0.0)) * luck * scale;
    }

    /** How many pets an egg has, and how many of them are found. */
    public int foundIn(PlayerData data, PetEgg egg) {
        int count = 0;
        for (String id : egg.pets()) if (data.getPetsFound().contains(id)) count++;
        return count;
    }

    /** Every pet in the catalogue, so the index can say "of seventy". */
    public int catalogue() {
        return types.size();
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
