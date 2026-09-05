package com.spacerng.solrng.pass;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Battle Pass (/pass).
 *
 * One XP bar, two reward tracks. The free track pays everybody; the
 * premium track is bought once per season with Credits and back-pays
 * every level already earned, so buying it late is never a punishment for
 * having played first.
 *
 * A player's level is DERIVED from their XP against the configured
 * thresholds rather than stored, for the same reason the milestones are:
 * retuning the curve mid-season then moves everyone to where the new
 * curve says they should be, instead of leaving them stranded on a level
 * the numbers no longer justify.
 */
public class PassManager {

    /**
     * One rung's payout. Every field is optional — a level that pays only
     * Tokens simply leaves the rest at zero, and `drops` banks virtual
     * drops of a rarity, the same currency /armor and /starforge spend.
     */
    public record Reward(long tokens, long gems, double coins, long credits,
                         Rarity dropRarity, long dropAmount, String note) {

        public boolean isEmpty() {
            return tokens <= 0 && gems <= 0 && coins <= 0 && credits <= 0 && dropAmount <= 0;
        }
    }

    public record Level(int level, long xpRequired, Reward free, Reward premium) {
    }

    public static final String FREE = "F";
    public static final String PREMIUM = "P";

    private final SolRNGPlugin plugin;

    private int season = 1;
    private String seasonName = "Season I";
    private long premiumCost = 1000L;
    private final List<Level> levels = new ArrayList<>();
    private final Map<Rarity, Long> xpPerRoll = new EnumMap<>(Rarity.class);
    private long xpPerHarvest = 2L;
    private String unlockNode = "pass_unlock";

    public PassManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        season = config.getInt("pass.season", 1);
        seasonName = config.getString("pass.season-name", "Season I");
        premiumCost = config.getLong("pass.premium-cost-credits", 1000L);
        unlockNode = config.getString("pass.node", "pass_unlock");
        xpPerHarvest = config.getLong("pass.xp.per-harvest", 2L);

        xpPerRoll.clear();
        ConfigurationSection roll = config.getConfigurationSection("pass.xp.per-roll");
        for (Rarity rarity : Rarity.values()) {
            long amount = roll == null ? 1L : roll.getLong(rarity.name(), 1L);
            xpPerRoll.put(rarity, amount);
        }

        levels.clear();
        List<Map<?, ?>> raw = config.getMapList("pass.levels");
        int index = 1;
        for (Map<?, ?> entry : raw) {
            try {
                long xp = asLong(entry.get("xp"), 100L);
                levels.add(new Level(index, xp,
                        parseReward(entry.get("free")),
                        parseReward(entry.get("premium"))));
                index++;
            } catch (Exception ex) {
                plugin.getLogger().warning("[SolRNG] Skipped a malformed pass level: " + entry);
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded Battle Pass " + seasonName + " with " + levels.size() + " levels.");
    }

    private Reward parseReward(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new Reward(0, 0, 0, 0, null, 0, "");
        }
        Rarity rarity = null;
        Object dropRarity = map.get("drop-rarity");
        if (dropRarity != null) {
            try {
                rarity = Rarity.valueOf(String.valueOf(dropRarity).toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new Reward(
                asLong(map.get("tokens"), 0L),
                asLong(map.get("gems"), 0L),
                asDouble(map.get("coins"), 0.0),
                asLong(map.get("credits"), 0L),
                rarity,
                asLong(map.get("drops"), 0L),
                map.get("note") == null ? "" : String.valueOf(map.get("note")));
    }

    private static long asLong(Object raw, long fallback) {
        if (raw == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double asDouble(Object raw, double fallback) {
        if (raw == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // --------------------------------------------------------------- state

    public String getSeasonName() {
        return seasonName;
    }

    public int getSeason() {
        return season;
    }

    public long getPremiumCost() {
        return premiumCost;
    }

    public List<Level> getLevels() {
        return levels;
    }

    public int getMaxLevel() {
        return levels.size();
    }

    public boolean isUnlocked(PlayerData data) {
        return unlockNode.isEmpty() || data.hasUnlocked(unlockNode);
    }

    /**
     * Wipes a player's pass if the config's season number has moved past
     * theirs. Done lazily on read rather than as a sweep over every save
     * file, so a season change is a one-line config edit and a reload.
     */
    public void syncSeason(PlayerData data) {
        if (data.getPassSeason() >= season) return;
        data.resetPass();
        data.setPassSeason(season);
    }

    /** Total XP needed to have finished a given level (1-indexed). */
    public long cumulativeXp(int level) {
        long total = 0L;
        for (int i = 0; i < Math.min(level, levels.size()); i++) {
            total += levels.get(i).xpRequired();
        }
        return total;
    }

    /** The level this player's XP currently buys — derived, never stored. */
    public int levelOf(PlayerData data) {
        syncSeason(data);
        long xp = data.getPassXp();
        int level = 0;
        for (Level rung : levels) {
            if (xp < rung.xpRequired()) break;
            xp -= rung.xpRequired();
            level++;
        }
        return level;
    }

    /** XP banked toward the NEXT level, and how much that level needs. */
    public long xpIntoLevel(PlayerData data) {
        long xp = data.getPassXp();
        for (Level rung : levels) {
            if (xp < rung.xpRequired()) return xp;
            xp -= rung.xpRequired();
        }
        return 0L;
    }

    public long xpForNextLevel(PlayerData data) {
        int level = levelOf(data);
        if (level >= levels.size()) return 0L;
        return levels.get(level).xpRequired();
    }

    // -------------------------------------------------------------- earning

    /**
     * XP from a roll. Rarer rolls are worth more, so the pass moves with
     * your Luck rather than purely with your click count — which is the
     * whole point of tying it to this gamemode instead of to playtime.
     */
    public void awardRoll(Player player, PlayerData data, Rarity rarity) {
        if (!isUnlocked(data)) return;
        award(player, data, xpPerRoll.getOrDefault(rarity, 1L));
    }

    public void awardHarvest(Player player, PlayerData data, long crops) {
        if (!isUnlocked(data)) return;
        award(player, data, xpPerHarvest * Math.max(0L, crops));
    }

    private void award(Player player, PlayerData data, long baseXp) {
        if (baseXp <= 0) return;
        syncSeason(data);

        double multiplier = plugin.getSkillTreeManager().multiplierOf(data, SkillNode.Effect.PASS_XP);
        long gained = Math.max(1L, Math.round(baseXp * multiplier));

        int before = levelOf(data);
        data.addPassXp(gained);
        int after = levelOf(data);

        if (after > before && player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "BATTLE PASS " + ChatColor.RESET
                    + ChatColor.GRAY + "reached level " + ChatColor.YELLOW + after
                    + ChatColor.GRAY + " — claim it in " + ChatColor.YELLOW + "/pass");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    // -------------------------------------------------------------- premium

    public boolean buyPremium(Player player, PlayerData data) {
        syncSeason(data);
        if (data.isPassPremium()) return false;
        if (!data.spendPoints(premiumCost)) return false;
        data.setPassPremium(true);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "PREMIUM PASS UNLOCKED"
                + ChatColor.RESET + ChatColor.GRAY + " — every premium reward you've already earned is"
                + " waiting in " + ChatColor.YELLOW + "/pass" + ChatColor.GRAY + ".");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        return true;
    }

    // -------------------------------------------------------------- claiming

    public boolean canClaim(PlayerData data, int level, String track) {
        if (level < 1 || level > levels.size()) return false;
        if (levelOf(data) < level) return false;
        if (data.hasClaimedPass(track, level)) return false;
        if (PREMIUM.equals(track) && !data.isPassPremium()) return false;
        Reward reward = track.equals(PREMIUM) ? levels.get(level - 1).premium() : levels.get(level - 1).free();
        return !reward.isEmpty();
    }

    public boolean claim(Player player, PlayerData data, int level, String track) {
        if (!canClaim(data, level, track)) return false;
        Reward reward = PREMIUM.equals(track) ? levels.get(level - 1).premium() : levels.get(level - 1).free();

        data.markPassClaimed(track, level);
        pay(player, data, reward);

        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "CLAIMED " + ChatColor.RESET
                + ChatColor.GRAY + "level " + ChatColor.YELLOW + level + ChatColor.GRAY + " "
                + (PREMIUM.equals(track) ? ChatColor.LIGHT_PURPLE + "premium" : ChatColor.GREEN + "free")
                + ChatColor.GRAY + ": " + describe(reward));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        return true;
    }

    /** Everything earned but not yet taken, in one click. */
    public int claimAll(Player player, PlayerData data) {
        int claimed = 0;
        for (int level = 1; level <= levels.size(); level++) {
            if (canClaim(data, level, FREE) && claim(player, data, level, FREE)) claimed++;
            if (canClaim(data, level, PREMIUM) && claim(player, data, level, PREMIUM)) claimed++;
        }
        return claimed;
    }

    private void pay(Player player, PlayerData data, Reward reward) {
        if (reward.tokens() > 0) data.addTokens(reward.tokens());
        if (reward.gems() > 0) data.addShards(reward.gems());
        if (reward.credits() > 0) data.addPoints(reward.credits());
        if (reward.coins() > 0) {
            var registration = org.bukkit.Bukkit.getServicesManager()
                    .getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (registration != null) registration.getProvider().depositPlayer(player, reward.coins());
        }
        if (reward.dropRarity() != null && reward.dropAmount() > 0) {
            data.addBankedDrops(reward.dropRarity(), reward.dropAmount());
        }
    }

    /** "5,000 Tokens, 2 Gems" — blank when a rung pays nothing. */
    public String describe(Reward reward) {
        List<String> parts = new ArrayList<>();
        if (reward.tokens() > 0) {
            parts.add(ChatColor.YELLOW + String.format("%,d", reward.tokens()) + " Tokens");
        }
        if (reward.gems() > 0) {
            parts.add(ChatColor.AQUA + String.format("%,d", reward.gems()) + " Gems");
        }
        if (reward.coins() > 0) {
            parts.add(ChatColor.GOLD + String.format("%,.0f", reward.coins()) + " Coins");
        }
        if (reward.credits() > 0) {
            parts.add(ChatColor.LIGHT_PURPLE + String.format("%,d", reward.credits()) + " Credits");
        }
        if (reward.dropRarity() != null && reward.dropAmount() > 0) {
            parts.add(plugin.getRarityManager().style(reward.dropRarity(),
                    String.format("%,d", reward.dropAmount()) + " " + reward.dropRarity().displayName()));
        }
        return String.join(ChatColor.GRAY + ", ", parts);
    }
}
