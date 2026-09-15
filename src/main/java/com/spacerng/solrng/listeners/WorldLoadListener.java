package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * A world that loads after the plugin (a Multiverse world) gets its farm
 * plots, holograms, crates and floating heads back.
 *
 * Every one of those files is read on enable. Before V133 the farm plots
 * of a world that wasn't loaded yet were dropped, and the next save wrote
 * them out of farmplots.yml for good, which is why the farm vanished on
 * each jar update; holograms in such a world were kept but never drawn.
 */
public class WorldLoadListener implements Listener {

    private final SolRNGPlugin plugin;

    public WorldLoadListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        plugin.getFarmPlotManager().resolveWorld(world);
        plugin.getHoloManager().resolveWorld(world);
        plugin.getCrateManager().resolveWorld(world);
        plugin.getTopHeadManager().resolveWorld(world);
    }
}
