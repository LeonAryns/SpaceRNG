package com.spacerng.solrng.firsts;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Server First fireworks are for looking at. A firework with an effect
 * hurts whatever is within five blocks when it goes off; they explode well
 * above that, and this cancels any damage one of them does all the same.
 */
public final class FirstTenFireworks implements Listener {

    static final NamespacedKey KEY = SolRNGPlugin.key("first_firework");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
