package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.item.RollItemFactory;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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
            reattachHologram(event.getPlayer(), data, color);
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
        plugin.getTagManager().hideHologram(event.getPlayer().getUniqueId());
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
    }

    // Mounted passengers (the floating tag stands) are cleared on death,
    // so tear them down then and rebuild once the player respawns.
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getTagManager().hideHologram(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        if (data.getEquippedTagItemKey() == null || data.getEquippedTagRarity() == null) return;
        String color = plugin.getRarityManager().colorFor(Rarity.valueOf(data.getEquippedTagRarity()));

        // Respawn teleport happens after this event fires, so wait a tick
        // before re-mounting or the stands spawn at the death location.
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> reattachHologram(event.getPlayer(), data, color), 1L);
    }

    private void reattachHologram(org.bukkit.entity.Player player, PlayerData data, String color) {
        RollableItem rollable = plugin.getRarityManager().findByDisplayName(data.getEquippedTagItemKey());
        if (rollable == null) return;
        plugin.getTagManager().showHologram(player, color + data.getEquippedTagItemKey(),
                ChatColor.GRAY + "Odds: " + ChatColor.WHITE + RollFormat.compactOdds(rollable.getOdds()));
    }
}
