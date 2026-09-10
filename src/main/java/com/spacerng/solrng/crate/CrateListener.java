package com.spacerng.solrng.crate;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Crates in the world and in menus.
 *
 * Left click previews, right click with a key opens, sneak and right click
 * opens a stack. The interaction is always cancelled on a crate block, so
 * an ender chest used as a crate never opens its own inventory.
 */
public class CrateListener implements Listener {

    private final SolRNGPlugin plugin;

    public CrateListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        CrateManager crates = plugin.getCrateManager();
        Crate crate = crates.crateAt(block);
        if (crate == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            player.openInventory(CratePreviewGui.build(plugin, player, crate));
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        int keys = crates.keysHeld(player, crate);
        if (keys <= 0) {
            crates.noKey(player, crate);
            player.openInventory(CratePreviewGui.build(plugin, player, crate));
            return;
        }
        if (player.isSneaking() && keys > 1) {
            crates.quickOpen(player, block, crate);
        } else {
            crates.open(player, block, crate);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getCrateManager().crateAt(event.getBlock()) == null) return;
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("solrng.admin")) {
            event.getPlayer().sendMessage(ChatColor.GRAY + "That block is a crate. Look at it and run "
                    + ChatColor.YELLOW + "/rngadmin crate remove" + ChatColor.GRAY + " first.");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof CrateSpinHolder || holder instanceof CratePreviewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof CrateSpinHolder || holder instanceof CratePreviewHolder) {
            event.setCancelled(true);
        }
    }

    /** Closing the reel early skips to the end. It never skips the reward. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CrateSpinHolder holder) {
            holder.spin().finish(false, true);
        }
    }

    /** LOWEST, so the reward lands before anything else saves the player on the way out. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getCrateManager().endSpin(event.getPlayer().getUniqueId());
    }
}
