package com.spacerng.solrng.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.ConvertGui;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Rolled drops are trophies, not gear (V298): an Elytra drop flew, a
 * Diamond Helmet drop could be worn. Every way onto the body (a click, a
 * shift-click, a number key, right-clicking it in hand, a dispenser) ends
 * in this one event, so the drop is taken back off the moment it lands
 * there and put back in the inventory, or /stash when that is full.
 */
public class DropEquipListener implements Listener {

    private final SolRNGPlugin plugin;

    public DropEquipListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        ItemStack worn = event.getNewItem();
        if (!ConvertGui.isDrop(plugin, worn)) return;
        Player player = event.getPlayer();
        EquipmentSlot slot = event.getSlot();
        // Next tick, because the slot is still being written while this runs.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack now = player.getInventory().getItem(slot);
            if (!ConvertGui.isDrop(plugin, now)) return;
            player.getInventory().setItem(slot, null);
            com.spacerng.solrng.player.Stash.give(plugin, player, now);
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(ChatColor.RED + "Rolled drops can't be worn."));
        });
    }
}
