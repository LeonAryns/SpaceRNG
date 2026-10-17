package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.starforge.StarforgeTier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Wiping accounts back to new ones.
 *
 * {@link #wipeOnline} is what /rngadmin reset does to one player, moved
 * here in V406 so the Sunday reset of every player (Leon, 17 October)
 * runs the very same steps instead of a copy that drifts.
 * {@link #everyone} is that Sunday reset.
 */
public final class AccountReset {

    private AccountReset() {
    }

    /** What {@link #everyone} did, for the log and the admin. */
    public record Summary(int online, int offline, int clans, String backup) {
    }

    /**
     * Wipes one online player back to a brand-new account: save file,
     * Money, playtime, leaderboard row, inventory and ender chest, with a
     * Roll item and the starter Starforge handed back.
     *
     * @param keepRank put back on the fresh account, for the season reset
     *     keeping Owner; null keeps none.
     * @param keepEarned V321: carries the rank, Credits, Battle Pass and
     *     claimed milestones onto the fresh account, Leon's call for
     *     /rngadmin reset. The season reset passes false, because a new
     *     season is exactly when the pass and the milestones SHOULD go.
     */
    public static PlayerData wipeOnline(SolRNGPlugin plugin, Player target, String keepRank, boolean keepEarned) {
        UUID uuid = target.getUniqueId();
        plugin.getRollListener().cancelRoll(uuid);

        PlayerData fresh = keepEarned
                ? plugin.getPlayerDataManager().resetKeepingEarned(uuid)
                : plugin.getPlayerDataManager().reset(uuid);
        if (keepRank != null) fresh.setRank(keepRank);

        // Coins, Gems and Credits live on PlayerData and go with it. Money
        // does not: it lives in the economy plugin, so a reset that only
        // touched the save file left the richest thing about the account
        // untouched.
        var economyReg = Bukkit.getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyReg != null) {
            var economy = economyReg.getProvider();
            double balance = economy.getBalance(target);
            if (balance > 0) economy.withdrawPlayer(target, balance);
        }

        // Playtime is a vanilla statistic, so it lives on the player and
        // not in the save file the reset just deleted.
        try {
            target.setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, 0);
        } catch (Exception ignored) {
            // Some server setups refuse statistic writes. Not worth failing
            // the whole reset over.
        }
        // The leaderboard keeps its own copy of the numbers, and a wiped
        // account still sitting at the top of a board is worse than useless.
        plugin.getLeaderboardManager().forget(uuid);

        target.getInventory().clear();
        target.getEnderChest().clear();

        // Put the live state back in sync with the wiped data.
        plugin.getTagManager().clearTag(target, fresh);
        StarforgeTier basic = plugin.getStarforgeManager().tierOf(fresh);
        if (basic != null) {
            plugin.getStarforgeManager().replaceHeldStarforge(target, basic);
        }
        target.getInventory().addItem(
                com.spacerng.solrng.roll.RollItemFactory.create(plugin, 1));
        plugin.getScoreboardManager().update(target);
        plugin.getRankManager().refreshName(target);
        return fresh;
    }

    /**
     * The Sunday reset (V406, Leon): every player, online and offline,
     * goes through the same wipe as /rngadmin reset, so the rank, Credits,
     * Battle Pass and milestones stay. On top of that, Leon's answers of
     * 17 October: every clan goes (members, levels, upgrades and chests,
     * since a chest would carry items through the reset), and the Server
     * First 10, the drop records and the found counts start over.
     *
     * Every file it touches is copied to backups/ first. One player that
     * throws is logged and skipped; the rest still go.
     */
    public static Summary everyone(SolRNGPlugin plugin) {
        var log = plugin.getLogger();
        PlayerDataManager players = plugin.getPlayerDataManager();

        // Everything on disk is current before it is copied.
        players.saveAll();
        if (plugin.getClanManager() != null) plugin.getClanManager().save();
        String backup = backup(plugin);

        // A farming payout due at the same minute pays out on the old
        // numbers first, rather than finding every counter at zero.
        try {
            plugin.getLeaderboardManager().tick();
        } catch (RuntimeException ex) {
            log.warning("Reset: the farming payout check failed: " + ex);
        }

        // Menus first, while their close listeners still write back (a
        // clan chest, a vault page), then nobody holds a stale screen.
        plugin.closeMenus();

        Set<UUID> online = new HashSet<>();
        int onlineDone = 0;
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            UUID uuid = player.getUniqueId();
            online.add(uuid);
            try {
                var realm = plugin.getRealmManager();
                if (realm != null && realm.inside(uuid)) realm.leave(player);
                wipeOnline(plugin, player, null, true);
                plugin.getFarmPlotManager().resetMomentum(uuid);
                onlineDone++;
            } catch (RuntimeException ex) {
                log.severe("Reset: could not wipe " + player.getName() + ": " + ex);
            }
        }

        List<UUID> offline = new ArrayList<>();
        for (UUID uuid : players.savedPlayers()) {
            if (online.contains(uuid)) continue;
            try {
                players.resetKeepingEarned(uuid);
                // Writes the fresh account and lets go of it again.
                players.unload(uuid);
                plugin.getFarmPlotManager().resetMomentum(uuid);
                offline.add(uuid);
            } catch (RuntimeException ex) {
                log.severe("Reset: could not wipe the save of " + uuid + ": " + ex);
            }
        }
        // Their inventory lives in the world's player file, which only
        // opens while they are online: it goes on their next join. Every
        // player the server knows is queued, not only those with a save
        // file, so no inventory slips through.
        Set<UUID> inventories = new HashSet<>(offline);
        for (org.bukkit.OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (!online.contains(player.getUniqueId())) inventories.add(player.getUniqueId());
        }
        SeasonWipe.queue(plugin, inventories);

        var economyReg = Bukkit.getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyReg != null) {
            var economy = economyReg.getProvider();
            for (org.bukkit.OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (online.contains(player.getUniqueId())) continue;
                try {
                    double balance = economy.getBalance(player);
                    if (balance > 0) economy.withdrawPlayer(player, balance);
                } catch (RuntimeException ex) {
                    log.warning("Reset: Money of " + player.getUniqueId() + ": " + ex);
                }
            }
        }

        int clans = 0;
        try {
            plugin.getLeaderboardManager().clearAll();
            if (plugin.getClanManager() != null) clans = plugin.getClanManager().wipeAll();
            plugin.getFirstTenManager().resetAll();
            plugin.getFoundCounts().clear();
            plugin.getRollListener().resetDropRecord();
        } catch (RuntimeException ex) {
            log.severe("Reset: clearing the server lists failed: " + ex);
        }

        log.warning("Reset done: " + onlineDone + " online and " + offline.size() + " offline players, "
                + clans + " clans. Backup: " + backup);
        return new Summary(onlineDone, offline.size(), clans, backup);
    }

    /**
     * Copies the save files and every server list the reset clears into
     * backups/reset-&lt;date&gt;_&lt;time&gt;/, so a reset that went wrong
     * can be put back by hand: stop the server, copy them over, start.
     */
    private static String backup(SolRNGPlugin plugin) {
        File root = plugin.getDataFolder();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path target = root.toPath().resolve("backups").resolve("reset-" + stamp);
        try {
            Files.createDirectories(target);
            Path saves = plugin.getPlayerDataManager().folder().toPath();
            Path savesCopy = target.resolve("playerdata");
            Files.createDirectories(savesCopy);
            try (Stream<Path> files = Files.list(saves)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (Files.isRegularFile(file)) {
                        Files.copy(file, savesCopy.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            for (String name : List.of("clans.yml", "leaderboard.yml", "firsts.yml", "found.yml",
                    "drop-record.yml", "season-wipe.yml", "rarest-drop.yml")) {
                Path file = root.toPath().resolve(name);
                if (Files.exists(file)) Files.copy(file, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return "plugins/" + root.getName() + "/backups/reset-" + stamp;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().severe("Reset: the backup failed, going on without one: " + ex);
            return "none (failed)";
        }
    }
}
