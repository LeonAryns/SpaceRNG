package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.item.RollItemFactory;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
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
                    Rarity.valueOf(data.getEquippedTagRarity()));
            plugin.getTagManager().applyTag(event.getPlayer(), data.getEquippedTagItemKey(), color);
        }

        if (!event.getPlayer().hasPlayedBefore()) {
            event.getPlayer().getInventory().addItem(RollItemFactory.create(plugin, 1));
            event.getPlayer().sendMessage(ChatColor.GREEN + "Welcome to SpaceRNG! "
                    + ChatColor.GRAY + "Right-click your " + ChatColor.LIGHT_PURPLE + "Roll"
                    + ChatColor.GRAY + " item to get started.");
        }

        plugin.getScoreboardManager().setup(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getRollListener().cancelRoll(event.getPlayer().getUniqueId());
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
    }
}
