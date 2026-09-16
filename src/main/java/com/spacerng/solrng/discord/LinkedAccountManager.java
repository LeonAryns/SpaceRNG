package com.spacerng.solrng.discord;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.perk.PerkStat;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Tracks whether a player has linked their Minecraft account to
 * Discord through DiscordSRV, and hands out bonuses while the link
 * is live.
 *
 * DiscordSRV itself is a soft dependency accessed by reflection - no
 * compile-time link, no repo lookup, no maven version fight. When the
 * plugin isn't installed, this manager reports "not linked" for
 * everyone and every bonus returns zero.
 *
 * The link status is polled on a loop rather than listened to. That
 * avoids the DiscordSRV-specific listener registration (which changed
 * signature between versions) and keeps the bonuses' source of truth
 * as "whatever DiscordSRV said last time we asked", which is what
 * every other plugin using it does under the covers.
 */
public class LinkedAccountManager {

    private static final long POLL_TICKS = 20L * 30L; // half a minute

    private final SolRNGPlugin plugin;

    // Reflection handles, resolved lazily. When DiscordSRV isn't in
    // the classpath these stay null and every method short-circuits.
    private boolean available;
    private Method getPluginMethod;
    private Method getAccountLinkManagerMethod;
    private Method getDiscordIdMethod;

    // Config: per-stat bonus a linked account gets, and the one-time gift.
    private boolean enabled;
    private double moneyBonus;
    private double coinsBonus;
    private double luckBonus;
    private double shinyBonus;
    private long firstLinkCredits;
    private long firstLinkCoins;
    private long firstLinkMoney;
    private String linkAnnounce = "";
    private String unlinkAnnounce = "";

    // Cached per-player state so the poll can spot a transition.
    private final java.util.Map<UUID, Boolean> lastKnown = new java.util.HashMap<>();
    private int taskId = -1;

    public LinkedAccountManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        // Resolve DiscordSRV reflectively. Any failure here means the
        // manager silently becomes a no-op.
        available = false;
        try {
            Class<?> srv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            getPluginMethod = srv.getMethod("getPlugin");
            Object instance = getPluginMethod.invoke(null);
            getAccountLinkManagerMethod = instance.getClass().getMethod("getAccountLinkManager");
            Object linkManager = getAccountLinkManagerMethod.invoke(instance);
            getDiscordIdMethod = linkManager.getClass().getMethod("getDiscordId", UUID.class);
            available = true;
            plugin.getLogger().info("DiscordSRV detected - linked-account bonuses active.");
        } catch (Throwable ignored) {
            // DiscordSRV not installed, or its API changed. Bonuses off.
        }

        enabled = config.getBoolean("linked-account.enabled", true);
        moneyBonus = config.getDouble("linked-account.bonus.money-percent", 0.10);
        coinsBonus = config.getDouble("linked-account.bonus.coins-percent", 0.10);
        luckBonus = config.getDouble("linked-account.bonus.luck-percent", 0.10);
        shinyBonus = config.getDouble("linked-account.bonus.shiny-percent", 0.10);
        firstLinkCredits = config.getLong("linked-account.first-link-gift.credits", 100L);
        firstLinkCoins = config.getLong("linked-account.first-link-gift.coins", 25_000L);
        firstLinkMoney = config.getLong("linked-account.first-link-gift.money", 0L);
        linkAnnounce = colour(config.getString("linked-account.link-message",
                "&5&lLinked to Discord! &7Bonuses are active while your account stays linked."));
        unlinkAnnounce = colour(config.getString("linked-account.unlink-message",
                "&cUnlinked from Discord. &7Bonuses removed."));
    }

    private String colour(String raw) {
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }

    public boolean isDiscordSrvPresent() {
        return available;
    }

    /** Whether this player currently has a linked Discord account. */
    public boolean isLinked(UUID uuid) {
        if (!available || !enabled) return false;
        try {
            Object instance = getPluginMethod.invoke(null);
            Object linkManager = getAccountLinkManagerMethod.invoke(instance);
            Object discordId = getDiscordIdMethod.invoke(linkManager, uuid);
            return discordId != null && !String.valueOf(discordId).isEmpty();
        } catch (Throwable ex) {
            return false;
        }
    }

    public double moneyBonus() { return moneyBonus; }
    public double coinsBonus() { return coinsBonus; }
    public double luckBonus() { return luckBonus; }
    public double shinyBonus() { return shinyBonus; }

    /** Bonus applied to one stat if this player is linked, else zero. */
    public double bonusFor(UUID uuid, PerkStat stat) {
        if (!isLinked(uuid)) return 0.0;
        return switch (stat) {
            case MONEY_PERCENT -> moneyBonus;
            case COINS_PERCENT -> coinsBonus;
            case LUCK_PERCENT -> luckBonus;
            case SHINY_PERCENT -> shinyBonus;
            default -> 0.0;
        };
    }

    /**
     * Starts the poll. Every 30 seconds each online player's link
     * status is compared to what it was last time; transitions announce
     * the change and, on first link, hand out the gift.
     */
    public void start() {
        if (taskId != -1) return;
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::pollAll, POLL_TICKS, POLL_TICKS).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void pollAll() {
        if (!available || !enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean linked = isLinked(uuid);
            Boolean previous = lastKnown.put(uuid, linked);
            if (previous == null || previous == linked) continue;
            if (linked) onJustLinked(player);
            else onJustUnlinked(player);
            // Linking is also what earns the Linked rank and its Discord
            // role, so the roles are put right on the same transition
            // rather than waiting for the next login.
            if (plugin.getDiscordBot() != null) plugin.getDiscordBot().syncRoles(player);
        }
    }

    private void onJustLinked(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!linkAnnounce.isEmpty()) player.sendMessage(linkAnnounce);
        // First-link gift only fires when the flag isn't set. A relink
        // does nothing so nobody farms the gift with unlink/relink
        // loops, which is exactly the failure mode this gate covers.
        if (!data.hasClaimedLinkGift()) {
            data.setClaimedLinkGift(true);
            if (firstLinkCredits > 0) data.addPoints(firstLinkCredits);
            if (firstLinkCoins > 0) data.addTokens(firstLinkCoins);
            if (firstLinkMoney > 0) {
                var registration = Bukkit.getServicesManager()
                        .getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (registration != null) {
                    registration.getProvider().depositPlayer(player, firstLinkMoney);
                }
            }
            StringBuilder gift = new StringBuilder(ChatColor.LIGHT_PURPLE + "One-time gift: ");
            boolean any = false;
            if (firstLinkCredits > 0) {
                gift.append(Currency.CREDITS.amount(firstLinkCredits));
                any = true;
            }
            if (firstLinkCoins > 0) {
                if (any) gift.append(ChatColor.GRAY).append(", ");
                gift.append(Currency.COINS.amount(firstLinkCoins));
                any = true;
            }
            if (firstLinkMoney > 0) {
                if (any) gift.append(ChatColor.GRAY).append(", ");
                gift.append(Currency.MONEY.amount(firstLinkMoney));
            }
            if (any) player.sendMessage(gift.toString());
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        }
    }

    private void onJustUnlinked(Player player) {
        if (!unlinkAnnounce.isEmpty()) player.sendMessage(unlinkAnnounce);
    }
}
