package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * /rngadmin freeze (V395, Leon): the minute before a jar update. While
 * frozen nobody but an admin can run a command, open a SpaceRNG menu,
 * break or click anything, or roll, so nothing is half done when
 * /plugman reload pulls the plugin out. The reload itself ends the
 * freeze, because the new instance starts unfrozen.
 */
public class FreezeListener implements Listener {

    private final SolRNGPlugin plugin;

    public FreezeListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean blocked(Player player) {
        return plugin.isFrozen() && !player.hasPermission("solrng.admin");
    }

    private void tell(Player player) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                ChatColor.AQUA + "Updating, one moment. Your Luck, boosts and Momentum are kept."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!blocked(event.getPlayer())) return;
        event.setCancelled(true);
        tell(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        if (!plugin.isFrozen() || !(event.getPlayer() instanceof Player player) || !blocked(player)) return;
        var holder = event.getInventory().getHolder();
        if (holder instanceof com.spacerng.solrng.gui.MenuHolder
                || holder instanceof com.spacerng.solrng.clan.ClanChestHolder) {
            event.setCancelled(true);
            tell(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!blocked(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (!blocked(event.getPlayer())) return;
        event.setCancelled(true);
        tell(event.getPlayer());
    }
}
