package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Taking the afk marker back off (V344).
 *
 * Anything that proves somebody is at the keyboard clears it: walking a
 * block, talking, breaking a crop. The move check does nothing at all
 * while nobody is afk, and then only fires on a real block change, so the
 * hottest event on the server stays cheap.
 */
public class AfkListener implements Listener {

    private final SolRNGPlugin plugin;

    public AfkListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getTabListManager().anyAfk()) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        plugin.getTabListManager().clearAfk(event.getPlayer(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getTabListManager().isAfk(event.getPlayer().getUniqueId())) return;
        // Off the main thread, so the name is rewritten on it.
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getTabListManager().clearAfk(event.getPlayer(), true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.getTabListManager().clearAfk(event.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTabListManager().clearAfk(event.getPlayer(), false);
    }
}
