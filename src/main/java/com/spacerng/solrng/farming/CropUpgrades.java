package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The /crops upgrades (V391, Leon), one set for every crop rather than a
 * board per crop.
 *
 * They are paid in crop coins: one for every 1,000 crops farmed. Crop
 * coins are not stored. The balance is what farming has earned minus the
 * levels already bought, because every level costs exactly one, so it can
 * never drift from the crops counter it comes from.
 *
 * Every stat is +1% a level. The ceiling grows with the farm: 100 levels
 * for every crop unlocked, so a player on Wheat alone stops at 100 and one
 * with all six crops at 600.
 *
 * Levels live in PlayerData's crop boost map under "ALL:&lt;STAT&gt;", the
 * map the per crop boards used until V391. Their old keys are refunded in
 * Coins once by {@link #refundOld}.
 */
public final class CropUpgrades {

    /** One upgrade. {@code requires} is a crop that has to be unlocked first, or null. */
    public record Stat(String id, String display, ChatColor colour, Material icon,
                       String pitch, String requires) {
    }

    public static final Stat ENCHANT_PROC = new Stat("ENCHANT_PROC", "Enchant Proc", ChatColor.LIGHT_PURPLE,
            Material.ENCHANTED_BOOK, "Every hoe enchant fires more often.", null);
    public static final Stat COINS = new Stat("COINS", "Coin Boost", ChatColor.GOLD,
            Material.GOLD_INGOT, "Every crop pays more Coins.", null);
    public static final Stat GEMS = new Stat("GEMS", "Gem Boost", ChatColor.AQUA,
            Material.AMETHYST_SHARD, "Every crop pays more Gems.", null);

    /** In the order /crops draws them. */
    public static final List<Stat> ALL = List.of(ENCHANT_PROC, COINS, GEMS);

    private static final String PREFIX = "ALL:";

    private CropUpgrades() {
    }

    public static double perLevel(SolRNGPlugin plugin) {
        return plugin.getConfig().getDouble("crop-upgrades.per-level", 0.01);
    }

    public static long cropsPerCoin(SolRNGPlugin plugin) {
        return Math.max(1L, plugin.getConfig().getLong("crop-upgrades.crops-per-coin", 1000L));
    }

    public static int levelOf(PlayerData data, Stat stat) {
        return data.getCropBoost(PREFIX + stat.id());
    }

    /** What a stat is worth now, as a multiplier on 1.0. */
    public static double multiplier(SolRNGPlugin plugin, PlayerData data, Stat stat) {
        return 1.0 + perLevel(plugin) * levelOf(data, stat);
    }

    /** 100 levels per crop unlocked. */
    public static int maxLevel(SolRNGPlugin plugin, PlayerData data) {
        FarmPlotManager farm = plugin.getFarmPlotManager();
        int unlocked = 0;
        for (CropType crop : farm.getCrops().values()) {
            if (farm.isUnlocked(data, crop)) unlocked++;
        }
        return Math.max(1, unlocked) * plugin.getConfig().getInt("crop-upgrades.levels-per-crop", 100);
    }

    /** Crop coins farming has handed out, spent or not. */
    public static long earned(SolRNGPlugin plugin, PlayerData data) {
        return data.getCropsHarvested() / cropsPerCoin(plugin);
    }

    public static long spent(PlayerData data) {
        long total = 0;
        for (Stat stat : ALL) total += levelOf(data, stat);
        return total;
    }

    public static long balance(SolRNGPlugin plugin, PlayerData data) {
        return Math.max(0L, earned(plugin, data) - spent(data));
    }

    /** Whether the crop a stat waits on is open. */
    public static boolean available(SolRNGPlugin plugin, PlayerData data, Stat stat) {
        if (stat.requires() == null) return true;
        CropType crop = plugin.getFarmPlotManager().getCrop(stat.requires());
        return crop == null || plugin.getFarmPlotManager().isUnlocked(data, crop);
    }

    public enum Buy { DONE, MAXED, TOO_POOR, LOCKED }

    /** Buys up to {@code levels} levels, as many as the coins and the ceiling allow. */
    public static Buy buy(SolRNGPlugin plugin, PlayerData data, Stat stat, int levels) {
        if (!available(plugin, data, stat)) return Buy.LOCKED;
        int level = levelOf(data, stat);
        int room = maxLevel(plugin, data) - level;
        if (room <= 0) return Buy.MAXED;
        long can = Math.min(Math.min(levels, room), balance(plugin, data));
        if (can <= 0) return Buy.TOO_POOR;
        data.setCropBoost(PREFIX + stat.id(), level + (int) can);
        return Buy.DONE;
    }

    /**
     * Pays back the Coins spent on the per crop boards V353 to V390 had,
     * once, and clears those levels. Their prices are still read from the
     * old crop-boosts config section.
     */
    public static void refundOld(SolRNGPlugin plugin, Player player, PlayerData data) {
        long coins = 0;
        for (var entry : new java.util.ArrayList<>(data.getCropBoosts().entrySet())) {
            String key = entry.getKey();
            if (key.startsWith(PREFIX)) continue;
            int at = key.indexOf(':');
            CropBoosts.Stat old = at < 0 ? null : CropBoosts.get(plugin, key.substring(at + 1));
            if (old != null) {
                for (int level = 0; level < entry.getValue(); level++) coins += old.costAt(level);
            }
            data.getCropBoosts().remove(key);
        }
        if (coins <= 0) return;
        data.addTokens(coins);
        player.sendMessage(ChatColor.GREEN + "The crop boards are gone: one set of upgrades in "
                + ChatColor.YELLOW + "/crops" + ChatColor.GREEN + " now, paid in crop coins. "
                + ChatColor.GRAY + "Refunded " + com.spacerng.solrng.gui.Currency.COINS.amount(coins) + ChatColor.GRAY + ".");
    }
}
