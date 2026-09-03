package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Harvesting a fully-grown farm crop (config: farming.crops) pays Tokens
 * on top of the normal vanilla drop, scaled by the player's own
 * farmTokenMultiplier — everyone shares the same field, but payout is
 * personal. The crop is replanted straight to fully-grown a short delay
 * later instead of waiting on random tick growth.
 */
public class FarmingListener implements Listener {

    private final SolRNGPlugin plugin;

    public FarmingListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        FarmingManager farming = plugin.getFarmingManager();
        if (!farming.isCrop(material)) return;

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        long reward = Math.round(farming.tokensFor(material) * data.getFarmTokenMultiplier());
        if (reward > 0) {
            data.addTokens(reward);
            sendActionBar(player, ChatColor.AQUA + "+" + reward + " Tokens");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }

        Block regrowTarget = block;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> replant(regrowTarget, material), farming.getRegrowTicks());
    }

    private void replant(Block block, Material material) {
        if (block.getType() != Material.AIR) return; // something else occupies it now — leave it alone
        block.setType(material);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable);
        }
    }

    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }
}
