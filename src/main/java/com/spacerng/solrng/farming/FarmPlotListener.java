package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Wires the shared farm into the world.
 *
 * The plot block is never allowed to actually change: breaks are cancelled
 * and paid out instead, and physics updates are suppressed so a wheat plot
 * can't pop off when the block under it is disturbed. Everything a player
 * "does" to the field is a per-player illusion on top of a field that
 * never moves.
 */
public class FarmPlotListener implements Listener {

    private static final String FARMING_UNLOCK_NODE = "farming_unlock";

    private final SolRNGPlugin plugin;

    public FarmPlotListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /** Placing the admin Farm Plot item registers the tile. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        FarmPlotManager farm = plugin.getFarmPlotManager();
        if (!farm.isPlotItem(event.getItemInHand())) return;

        if (!event.getPlayer().hasPermission("solrng.admin")) {
            event.setCancelled(true);
            return;
        }

        // Replace the placed block with the marker crop on the next tick —
        // doing it inside the event fights the placement itself.
        var block = event.getBlockPlaced();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            farm.addPlot(block);
            farm.renderAll();
        });
        sendActionBar(event.getPlayer(), ChatColor.GREEN + "Farm plot added "
                + ChatColor.GRAY + "(" + (farm.plotCount() + 1) + " total)");
    }

    /**
     * Harvesting. The block break is always cancelled — what the player
     * sees disappear is a per-player block change, not the real block.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        FarmPlotManager farm = plugin.getFarmPlotManager();
        if (!farm.isPlot(event.getBlock().getLocation())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Sneaking in creative removes the real tile; anything else — any
        // gamemode, any click — is just harvesting the crop on top of it.
        // Gating on creative means an admin can farm normally in survival
        // without accidentally deleting the field.
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                && player.isSneaking()
                && player.hasPermission("solrng.admin")) {
            farm.removePlot(event.getBlock().getLocation());
            farm.renderAll();
            sendActionBar(player, ChatColor.RED + "Farm plot removed "
                    + ChatColor.GRAY + "(" + farm.plotCount() + " left)");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.hasUnlocked(FARMING_UNLOCK_NODE)) {
            sendActionBar(player, ChatColor.RED + "Unlock \"Farming Unlocked\" in /skilltree first!");
            return;
        }

        farm.harvest(player, event.getBlock().getLocation());
    }

    /**
     * Wheat normally pops off when whatever it's standing on changes. A
     * plot has to survive that — it's scenery, not a real crop.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (plugin.getFarmPlotManager().isPlot(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }
}
