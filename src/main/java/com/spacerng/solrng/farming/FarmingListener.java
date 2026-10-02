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
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Harvesting a fully-grown farm crop (config: farming.crops) pays Tokens
 * on top of the normal vanilla drop, scaled by the player's own
 * farmTokenMultiplier - everyone shares the same field, but payout is
 * personal. The crop is replanted straight to fully-grown a short delay
 * later instead of waiting on random tick growth. Requires the
 * "farming_unlock" skill tree node - breaking any listed crop material is
 * blocked entirely without it.
 */
public class FarmingListener implements Listener {

    private static final String FARMING_UNLOCK_NODE = "farming_unlock";

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
        // Shared-farm plots are wheat too, but they're handled entirely by
        // FarmPlotListener - paying out here as well would double-reward
        // and replant a block that's meant to never change.
        if (plugin.getFarmPlotManager().isPlot(block.getLocation())) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.hasUnlocked(FARMING_UNLOCK_NODE)) {
            event.setCancelled(true);
            sendActionBar(player, ChatColor.RED + "Unlock \"Farming Unlocked\" in /skilltree first!");
            return;
        }

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) return;

        data.addCropsHarvested(1L);
        long reward = Math.round(farming.tokensFor(material) * data.getFarmTokenMultiplier());
        if (reward > 0) {
            data.addTokens(reward);
            sendActionBar(player, ChatColor.AQUA + "+" + reward + " Coins");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }

        Block regrowTarget = block;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> replant(regrowTarget, material), farming.getRegrowTicks());
    }

    /**
     * The bound hoe stays with its owner (V254): it cannot be put in a
     * chest, a barrel or anything else that is not their own inventory or
     * ender chest, where nothing could refresh it and anyone could take it.
     */
    @EventHandler(ignoreCancelled = true)
    public void onStore(org.bukkit.event.inventory.InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        var type = top.getType();
        if (type == org.bukkit.event.inventory.InventoryType.CRAFTING
                || type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        FarmingManager farming = plugin.getFarmingManager();
        boolean hoeInvolved = farming.isBoundHoe(event.getCurrentItem())
                || farming.isBoundHoe(event.getCursor())
                || (event.getHotbarButton() >= 0
                        && farming.isBoundHoe(player.getInventory().getItem(event.getHotbarButton())))
                || (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                        && farming.isBoundHoe(player.getInventory().getItemInOffHand()));
        if (!hoeInvolved) return;
        boolean intoTop = event.getClickedInventory() == top || event.isShiftClick();
        if (intoTop) {
            event.setCancelled(true);
            sendActionBar(player, ChatColor.RED + "Your Farmer's Hoe stays with you.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStoreDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        var type = event.getView().getTopInventory().getType();
        if (type == org.bukkit.event.inventory.InventoryType.CRAFTING
                || type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) return;
        if (!plugin.getFarmingManager().isBoundHoe(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getFarmingManager().isBoundHoe(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Your Farmer's Hoe is bound to you and can't be dropped.");
        }
    }

    private void replant(Block block, Material material) {
        if (block.getType() != Material.AIR) return; // something else occupies it now - leave it alone
        block.setType(material);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable);
        }
    }

    private void sendActionBar(Player player, String text) {
        // Sent as a real component rather than as legacy text.
        // TextComponent carries the section codes through untouched, and
        // the client's own legacy reader does not understand the hex form
        // a gradient is written in: it read each of the six hex digits as
        // its own old colour code, which is why a gradient name came out
        // of the action bar in the wrong colours while the same name was
        // right everywhere else.
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacySection().deserialize(text));
    }
}
