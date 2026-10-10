package com.spacerng.solrng.pet;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.PetEggGui;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The pet egg standing in the world (V359).
 *
 * Leon asked for an egg he can place like a crate: the item he was
 * holding floats and turns over an invisible barrier, and a click on it
 * opens that egg. This is the click half of it, built the same way
 * {@code CrateListener} is, so the two behave identically in the world.
 *
 * Either button opens it, because there is only one thing to do with it
 * and Geyser sends every Bedrock click as a left click anyway.
 */
public class PetEggListener implements Listener {

    private final SolRNGPlugin plugin;

    public PetEggListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String eggId = plugin.getHoloManager().eggAt(block);
        if (eggId == null) return;

        // Always cancelled, so a block used as an egg never does its own
        // job as well, and so a barrier is never punched out from under
        // one of these.
        event.setCancelled(true);
        event.getPlayer().openInventory(com.spacerng.solrng.holo.HoloManager.ALL_EGGS.equals(eggId)
                ? com.spacerng.solrng.gui.PetsGui.build(plugin, event.getPlayer())
                : PetEggGui.build(plugin, event.getPlayer(), eggId));
        event.getPlayer().playSound(event.getPlayer().getLocation(),
                org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    /** The block under a placed egg is not breakable; only the command removes it. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getHoloManager().eggAt(event.getBlock()) == null) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(org.bukkit.ChatColor.GRAY
                + "That is a pet egg. Remove it with /rngadmin petegg remove.");
    }
}
