package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Nobody breaks the world except the farm (V251). With
 * {@code protection.block-break} on, a break is refused unless the block is
 * a farm plot or a farm crop, which their own listeners handle. Someone
 * with solrng.admin in creative still builds as normal. Switched with
 * /rngadmin protect on|off.
 */
public class BlockProtectListener implements Listener {

    private final SolRNGPlugin plugin;

    public BlockProtectListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-break", true)) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("solrng.admin")) return;
        Block block = event.getBlock();
        if (plugin.getFarmPlotManager().isPlot(block.getLocation())) return;
        if (plugin.getFarmingManager().isCrop(block.getType())) return;
        event.setCancelled(true);
    }
}
