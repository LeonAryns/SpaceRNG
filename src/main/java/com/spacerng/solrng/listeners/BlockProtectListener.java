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
        if (!guarded(event.getPlayer())) return;
        Block block = event.getBlock();
        if (plugin.getFarmPlotManager().isPlot(block.getLocation())) return;
        if (plugin.getFarmingManager().isCrop(block.getType())) return;
        event.setCancelled(true);
    }

    /** Whether this player is held to the protection right now. */
    private boolean guarded(Player player) {
        if (!plugin.getConfig().getBoolean("protection.block-break", true)) return false;
        return !(player.getGameMode() == GameMode.CREATIVE && player.hasPermission("solrng.admin"));
    }

    /** No placing either (V256), with the same admin-in-creative exception. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (guarded(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (guarded(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        if (guarded(event.getPlayer())) event.setCancelled(true);
    }

    /** Paintings and item frames are blocks to a player, so they are kept too. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && guarded(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(org.bukkit.event.hanging.HangingPlaceEvent event) {
        if (event.getPlayer() != null && guarded(event.getPlayer())) event.setCancelled(true);
    }

    /**
     * No vanilla advancements (V256). The data pack route from
     * /rngadmin advancements needs a reload and a format the server
     * accepts; this stops every minecraft: criterion from being granted at
     * all, so there is no toast and no chat line, from the moment the
     * plugin loads. advancements.block in config, on by default.
     */
    @EventHandler(ignoreCancelled = true)
    public void onAdvancement(com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent event) {
        if (!plugin.getConfig().getBoolean("advancements.block", true)) return;
        if (event.getAdvancement().getKey().getNamespace().equals("minecraft")) event.setCancelled(true);
    }

    /**
     * Bought armor never wears out (V253). New pieces are marked
     * unbreakable; this covers the pieces handed out before that.
     */
    @EventHandler(ignoreCancelled = true)
    public void onArmorDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (plugin.getArmorManager().isPluginArmor(event.getItem())) event.setCancelled(true);
    }
}
