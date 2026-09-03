package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    private final SolRNGPlugin plugin;

    public JoinQuitListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        if (data.getEquippedTagItemKey() != null && data.getEquippedTagRarity() != null) {
            String color = plugin.getRarityManager().colorFor(
                    com.spacerng.solrng.rarity.Rarity.valueOf(data.getEquippedTagRarity()));
            plugin.getTagManager().applyTag(event.getPlayer(), data.getEquippedTagItemKey(), color);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
    }
}
