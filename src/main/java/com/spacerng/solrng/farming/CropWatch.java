package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff eyes on the farm (V289). Counts every crop a player breaks by hand
 * (crops an enchant takes with it do not count, or TNT Blast would look
 * like a cheat) and once a second turns that into a rate.
 *
 * Holding farming.watch.alert-per-second or more for alert-seconds in a
 * row tells everyone with solrng.admin, at most once a minute per player.
 * /rngadmin cropwatch <player> puts that player's rate on the watcher's
 * action bar, and /rngadmin cropwatch list shows everybody farming now.
 */
public final class CropWatch {

    private final SolRNGPlugin plugin;
    private final Map<UUID, Integer> thisSecond = new HashMap<>();
    private final Map<UUID, Integer> lastSecond = new HashMap<>();
    private final Map<UUID, int[]> recent = new HashMap<>();       // last ten seconds, ring
    private final Map<UUID, Integer> fastStreak = new HashMap<>();
    private final Map<UUID, Long> alertedAt = new HashMap<>();
    private final Map<UUID, UUID> watching = new HashMap<>();      // watcher -> target
    private int tick;
    private BukkitTask task;

    public CropWatch(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** One crop broken by hand. */
    public void record(Player player) {
        thisSecond.merge(player.getUniqueId(), 1, Integer::sum);
    }

    public int rate(UUID uuid) {
        return lastSecond.getOrDefault(uuid, 0);
    }

    /** Average over the last ten seconds. */
    public double average(UUID uuid) {
        int[] ring = recent.get(uuid);
        if (ring == null) return 0.0;
        int sum = 0;
        for (int n : ring) sum += n;
        return sum / 10.0;
    }

    public void watch(Player watcher, Player target) {
        watching.put(watcher.getUniqueId(), target.getUniqueId());
    }

    public boolean unwatch(Player watcher) {
        return watching.remove(watcher.getUniqueId()) != null;
    }

    /** Everybody with a crop in the last ten seconds, fastest first. */
    public List<UUID> active() {
        List<UUID> out = new ArrayList<>();
        for (UUID uuid : recent.keySet()) if (average(uuid) > 0) out.add(uuid);
        out.sort((a, b) -> Double.compare(average(b), average(a)));
        return out;
    }

    private void second() {
        tick++;
        int slot = tick % 10;
        lastSecond.clear();
        lastSecond.putAll(thisSecond);
        for (UUID uuid : new ArrayList<>(recent.keySet())) {
            if (!thisSecond.containsKey(uuid)) recent.get(uuid)[slot] = 0;
        }
        for (Map.Entry<UUID, Integer> entry : thisSecond.entrySet()) {
            recent.computeIfAbsent(entry.getKey(), k -> new int[10])[slot] = entry.getValue();
        }
        thisSecond.clear();
        recent.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null);

        int limit = plugin.getConfig().getInt("farming.watch.alert-per-second", 30);
        int seconds = Math.max(1, plugin.getConfig().getInt("farming.watch.alert-seconds", 5));
        long now = System.currentTimeMillis();
        for (UUID uuid : recent.keySet()) {
            int rate = rate(uuid);
            int streak = rate >= limit ? fastStreak.merge(uuid, 1, Integer::sum) : 0;
            if (rate < limit) fastStreak.remove(uuid);
            if (streak >= seconds && now - alertedAt.getOrDefault(uuid, 0L) > 60_000L) {
                alertedAt.put(uuid, now);
                alert(uuid, rate, streak);
            }
        }

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(watching.entrySet())) {
            Player watcher = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(entry.getValue());
            if (watcher == null) {
                watching.remove(entry.getKey());
                continue;
            }
            if (target == null) {
                watcher.sendActionBar(net.kyori.adventure.text.Component.text("Crop watch: that player left"));
                watching.remove(entry.getKey());
                continue;
            }
            int rate = rate(target.getUniqueId());
            ChatColor tone = rate >= limit ? ChatColor.RED : rate >= limit / 2 ? ChatColor.YELLOW : ChatColor.GREEN;
            long total = plugin.getPlayerDataManager().get(target.getUniqueId()).getCropsHarvested();
            watcher.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(ChatColor.GRAY + target.getName() + "  " + tone + rate + "/s"
                            + ChatColor.DARK_GRAY + "  avg " + String.format("%.1f", average(target.getUniqueId())) + "/s"
                            + ChatColor.DARK_GRAY + "  total " + String.format("%,d", total)));
        }
    }

    private void alert(UUID uuid, int rate, int streak) {
        Player suspect = Bukkit.getPlayer(uuid);
        if (suspect == null) return;
        String line = ChatColor.RED + "" + ChatColor.BOLD + "Crop watch " + ChatColor.RESET + ChatColor.YELLOW
                + suspect.getName() + ChatColor.GRAY + " broke " + ChatColor.WHITE + rate + "/s"
                + ChatColor.GRAY + " for " + streak + "s (avg " + String.format("%.1f", average(uuid)) + "/s). "
                + ChatColor.YELLOW + "/rngadmin cropwatch " + suspect.getName();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("solrng.admin")) staff.sendMessage(line);
        }
        plugin.getLogger().warning(ChatColor.stripColor(line));
    }
}
