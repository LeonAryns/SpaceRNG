package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hide Other Farmers, the hoe menu switch. Once a second, a player who has
 * it on and stands near a farm plot has every other player hidden from
 * them, with the tag and aura riding on those players; walking away or
 * switching it off shows them again. Only the viewer's own screen changes.
 *
 * Hides are plugin-scoped, so another plugin's vanish is never undone, and
 * a player's own aura rules are put back through AuraManager when they
 * come back into view.
 */
public final class FarmVisibility {

    private final SolRNGPlugin plugin;
    private final Set<UUID> hiding = new HashSet<>();

    public FarmVisibility(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        double radius = plugin.getConfig().getDouble("farming.hide-players-radius", 24.0);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean hide = plugin.getPlayerDataManager().get(viewer.getUniqueId()).isFarmHidePlayers()
                    && plugin.getFarmPlotManager().isNearPlot(viewer.getLocation(), radius);
            if (hide) {
                hiding.add(viewer.getUniqueId());
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.equals(viewer)) continue;
                    viewer.hidePlayer(plugin, other);
                    // Tags and auras ride on the player, so they go too.
                    for (Entity rider : other.getPassengers()) viewer.hideEntity(plugin, rider);
                }
            } else if (hiding.remove(viewer.getUniqueId())) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.equals(viewer)) continue;
                    viewer.showPlayer(plugin, other);
                    for (Entity rider : other.getPassengers()) {
                        // Some riders, like a roll showcase, are only ever for their owner.
                        if (rider.isVisibleByDefault()) viewer.showEntity(plugin, rider);
                    }
                }
                plugin.getAuraManager().refreshVisibility(viewer);
            }
        }
    }
}
