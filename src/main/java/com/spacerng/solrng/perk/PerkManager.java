package com.spacerng.solrng.perk;

import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Perks, the way Leon laid them out: a player has one perk at a time. A
 * roll costs one Perk Ticket, lands on a perk by its chance and a level from
 * I to V, and replaces the perk they had. Tickets are bought with Credits.
 * After pity-rolls rolls in a row without a Mythical or better, the next one
 * is Mythical or Divine.
 *
 * Everything but the rules above comes from the perks section of config.
 */
public class PerkManager {

    /** What one roll gave, and whether pity picked it. */
    public record RollResult(PerkType type, int level, boolean pity) { }

    private static final double[] DEFAULT_LEVEL_CHANCES = {0.40, 0.30, 0.18, 0.09, 0.03};

    private final Logger logger;
    private final Map<String, PerkType> types = new LinkedHashMap<>();
    private final double[] levelChances = DEFAULT_LEVEL_CHANCES.clone();
    private final Map<Integer, Long> ticketPrices = new LinkedHashMap<>();
    private final Map<Rarity, Double> pityChances = new EnumMap<>(Rarity.class);
    private int pityRolls = 100;

    public PerkManager(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        types.clear();
        ticketPrices.clear();
        pityChances.clear();

        List<Double> lc = config.getDoubleList("perks.level-chances");
        for (int i = 0; i < 5; i++) levelChances[i] = i < lc.size() ? lc.get(i) : DEFAULT_LEVEL_CHANCES[i];

        pityRolls = Math.max(1, config.getInt("perks.pity-rolls", 100));
        ConfigurationSection pc = config.getConfigurationSection("perks.pity-chances");
        if (pc != null) {
            for (String key : pc.getKeys(false)) {
                try {
                    pityChances.put(Rarity.valueOf(key), pc.getDouble(key));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (pityChances.isEmpty()) {
            pityChances.put(Rarity.MYTHICAL, 0.8);
            pityChances.put(Rarity.DIVINE, 0.2);
        }

        ConfigurationSection tp = config.getConfigurationSection("perks.ticket-prices");
        if (tp != null) {
            for (String key : tp.getKeys(false)) {
                try {
                    ticketPrices.put(Integer.parseInt(key), Math.max(0L, tp.getLong(key)));
                } catch (NumberFormatException ignored) { }
            }
        }
        if (ticketPrices.isEmpty()) {
            ticketPrices.put(1, 35L);
            ticketPrices.put(10, 320L);
            ticketPrices.put(25, 750L);
        }

        ConfigurationSection ts = config.getConfigurationSection("perks.types");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection t = ts.getConfigurationSection(id);
                if (t == null) continue;
                try {
                    Rarity rarity = Rarity.valueOf(t.getString("rarity", "COMMON").toUpperCase(Locale.ROOT));
                    Material icon = Material.matchMaterial(t.getString("icon", "NETHER_STAR"));
                    Map<PerkStat, double[]> stats = new LinkedHashMap<>();
                    ConfigurationSection ss = t.getConfigurationSection("stats");
                    if (ss != null) {
                        for (String stat : ss.getKeys(false)) {
                            List<Double> pair = ss.getDoubleList(stat);
                            if (pair.size() >= 2) stats.put(PerkStat.valueOf(stat), new double[]{pair.get(0), pair.get(1)});
                        }
                    }
                    types.put(id, new PerkType(id, t.getString("display", id), rarity,
                            icon == null ? Material.NETHER_STAR : icon, Math.max(0.0, t.getDouble("chance", 0.0)), stats));
                } catch (IllegalArgumentException ex) {
                    logger.warning("Skipped perk '" + id + "': " + ex.getMessage());
                }
            }
        }
        logger.info("Loaded " + types.size() + " perks.");
    }

    public Collection<PerkType> types() { return types.values(); }

    // Remembered per player in completed quests, like the guide's gifts, so
    // the defaults go on once and a perk switched off afterwards stays off.
    private static final String CONFIRM_DEFAULTS = "default:perk-confirm-legendary";

    /**
     * Confirmation on for every level of every Legendary or rarer perk,
     * once per player (V219). Leon: "alles vanaf fortune perk en hoger
     * automatisch confirmation on". Those are the ones nobody wants to roll
     * away by accident; anybody who switches one off keeps it off.
     */
    public void applyConfirmDefaults(com.spacerng.solrng.player.PlayerData data) {
        if (data.getCompletedQuests().contains(CONFIRM_DEFAULTS)) return;
        for (PerkType type : types.values()) {
            if (type.rarity().ordinal() < com.spacerng.solrng.rarity.Rarity.LEGENDARY.ordinal()) continue;
            for (int level = 1; level <= 5; level++) data.getPerkConfirm().add(type.key(level));
        }
        data.getCompletedQuests().add(CONFIRM_DEFAULTS);
    }

    public PerkType type(String id) { return id == null ? null : types.get(id); }

    /** The first perk of a rarity, or null. */
    public PerkType typeFor(Rarity rarity) {
        for (PerkType type : types.values()) {
            if (type.rarity() == rarity) return type;
        }
        return null;
    }

    /** Perk Tickets per purchase and what that many cost in Credits, in the order the buttons show. */
    public Map<Integer, Long> ticketPrices() { return ticketPrices; }

    public int pityRolls() { return pityRolls; }

    /** The perk a player has now, or null. */
    public PerkType activeType(PlayerData data) {
        return type(data.getActivePerkType());
    }

    /** What a player's perk adds to one stat: 0.5 is +50%. */
    public double totalOf(PlayerData data, PerkStat stat) {
        PerkType type = activeType(data);
        return type == null ? 0.0 : type.valueAt(stat, data.getActivePerkLevel());
    }

    /** The chance one roll lands on this perk, at any level. */
    public double chanceOf(PerkType type) {
        double total = 0.0;
        for (PerkType each : types.values()) total += each.chance();
        return total <= 0.0 ? 0.0 : type.chance() / total;
    }

    public double levelChance(int level) {
        double total = 0.0;
        for (double v : levelChances) total += v;
        int i = Math.max(1, Math.min(5, level)) - 1;
        return total <= 0.0 ? 0.0 : levelChances[i] / total;
    }

    /** Buys one of the configured ticket bundles. False when it isn't one or the player can't pay. */
    public boolean buyTickets(PlayerData data, int amount) {
        Long price = ticketPrices.get(amount);
        if (price == null || !data.spendPoints(price)) return false;
        data.setPerkTickets(data.getPerkTickets() + amount);
        return true;
    }

    /**
     * Rolls a perk and puts it on the player in place of the one they had.
     * The caller takes the ticket. Null when no perks are set up.
     */
    public RollResult roll(PlayerData data) {
        if (types.isEmpty()) return null;
        boolean pity = data.getPerkPity() >= pityRolls - 1;
        PerkType type = pity ? pickPity() : pick(types.values());
        if (type == null) return null;
        int level = pickLevel();
        data.setPerkPity(type.rarity().ordinal() >= Rarity.MYTHICAL.ordinal() ? 0 : data.getPerkPity() + 1);
        data.setActivePerk(type.id(), level);
        data.getPerkFound().add(type.key(level));
        return new RollResult(type, level, pity);
    }

    private PerkType pick(Collection<PerkType> pool) {
        double total = 0.0;
        for (PerkType type : pool) total += type.chance();
        PerkType last = null;
        if (total <= 0.0) {
            for (PerkType type : pool) last = type;
            return last;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (PerkType type : pool) {
            roll -= type.chance();
            last = type;
            if (roll <= 0.0) return type;
        }
        return last;
    }

    /** A rarity from pity-chances, then one of that rarity's perks. */
    private PerkType pickPity() {
        double total = 0.0;
        for (double v : pityChances.values()) total += v;
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        Rarity rarity = Rarity.MYTHICAL;
        for (var entry : pityChances.entrySet()) {
            roll -= entry.getValue();
            rarity = entry.getKey();
            if (roll <= 0.0) break;
        }
        List<PerkType> pool = new ArrayList<>();
        for (PerkType type : types.values()) {
            if (type.rarity() == rarity) pool.add(type);
        }
        if (pool.isEmpty()) {
            for (PerkType type : types.values()) {
                if (type.rarity().ordinal() >= Rarity.MYTHICAL.ordinal()) pool.add(type);
            }
        }
        return pool.isEmpty() ? pick(types.values()) : pick(pool);
    }

    private int pickLevel() {
        double total = 0.0;
        for (double v : levelChances) total += v;
        if (total <= 0.0) return 1;
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < levelChances.length; i++) {
            roll -= levelChances[i];
            if (roll <= 0.0) return i + 1;
        }
        return levelChances.length;
    }

    /**
     * Before V136 a player kept a vault of perks. Their best one becomes
     * their perk (its tier picks the perk of that rarity, an old level keeps
     * its level), and save rolls they never used come back as the one
     * Credit each cost.
     */
    public void migrateOldVault(PlayerData data, List<String> vault, long saveRolls) {
        if (saveRolls > 0) data.addPoints(saveRolls);
        Rarity best = null;
        int bestLevel = 1;
        for (String raw : vault) {
            String[] parts = raw.split("\\|", -1);
            try {
                Rarity tier;
                int level;
                if (parts.length == 4) {
                    tier = Rarity.valueOf(parts[2]);
                    level = Integer.parseInt(parts[3]);
                } else if (parts.length == 3) {
                    tier = Rarity.valueOf(parts[1]);
                    level = 1;
                } else {
                    continue;
                }
                if (best == null || tier.ordinal() > best.ordinal() || (tier == best && level > bestLevel)) {
                    best = tier;
                    bestLevel = level;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        if (best == null || data.getActivePerkType() != null) return;
        PerkType type = typeFor(best);
        if (type == null) return;
        int level = Math.max(1, Math.min(5, bestLevel));
        data.setActivePerk(type.id(), level);
        data.getPerkFound().add(type.key(level));
    }
}
