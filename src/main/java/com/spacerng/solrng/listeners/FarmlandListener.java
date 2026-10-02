package com.spacerng.solrng.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Farmland stays farmland (V167). The crops on a farm plot are only sent
 * to each player, so the real block above the soil is never a crop, and
 * vanilla dries such farmland back to dirt after a while. Jumping on it
 * turns it to dirt too. Neither is ever wanted on this server.
 */
public class FarmlandListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND) event.setCancelled(true);
    }

    /**
     * Wet or dry stays as it was set (V252, /rngadmin farmland dry|wet).
     * Without this, water nearby darkens dry farmland again and soil away
     * from water slowly dries.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoisture(org.bukkit.event.block.MoistureChangeEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL && block != null && block.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTrample(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT) {
            event.setCancelled(true);
        }
    }
}
