package com.spacerng.solrng.consumable;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click to use a consumable.
 *
 * The event is cancelled either way: a potion item that also drinks as a
 * vanilla potion, or a nether star that places, would be a very fast way
 * to lose a reward by accident.
 */
public class ConsumableListener implements Listener {

    private final SolRNGPlugin plugin;

    public ConsumableListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack held = event.getItem();
        ConsumableManager consumables = plugin.getConsumableManager();
        if (!consumables.isConsumable(held)) return;

        event.setCancelled(true);

        Consumable consumable = consumables.from(held);
        if (consumable == null) {
            // The id was removed from config after the item was handed out.
            event.getPlayer().sendMessage(ChatColor.RED
                    + "That reward isn't part of the game any more — hold onto it, or ask staff.");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        if (!consumables.redeem(event.getPlayer(), data, consumable)) return;

        held.setAmount(held.getAmount() - 1);
    }
}
