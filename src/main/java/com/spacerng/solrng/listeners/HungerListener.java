package com.spacerng.solrng.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Nobody gets hungry. There is no food loop in SpaceRNG, so an empty
 * hunger bar only ever meant a player standing at the farm unable to
 * sprint.
 */
public class HungerListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // Eating still fills the bar; only the drain is stopped.
        if (event.getFoodLevel() < player.getFoodLevel()) event.setCancelled(true);
    }

    /** The XP bar shows rolls (V162), so vanilla experience never touches it. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExp(PlayerExpChangeEvent event) {
        event.setAmount(0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }
}
