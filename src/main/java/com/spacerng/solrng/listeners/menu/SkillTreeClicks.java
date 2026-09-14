package com.spacerng.solrng.listeners.menu;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.commands.TagCommand;
import com.spacerng.solrng.gui.ArmorGui;
import com.spacerng.solrng.gui.ArmorHolder;
import com.spacerng.solrng.gui.BuyGui;
import com.spacerng.solrng.gui.BuyHolder;
import com.spacerng.solrng.gui.ConvertGui;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.gui.CropsGui;
import com.spacerng.solrng.gui.DailyGui;
import com.spacerng.solrng.gui.DailyHolder;
import com.spacerng.solrng.gui.HoeGui;
import com.spacerng.solrng.gui.HoeHolder;
import com.spacerng.solrng.gui.CropsHolder;
import com.spacerng.solrng.gui.ConvertHolder;
import com.spacerng.solrng.gui.IndexGui;
import com.spacerng.solrng.gui.IndexHolder;
import com.spacerng.solrng.gui.MilestoneGui;
import com.spacerng.solrng.gui.MilestoneHolder;
import com.spacerng.solrng.gui.NovaCoreGui;
import com.spacerng.solrng.gui.NovaCoreHolder;
import com.spacerng.solrng.gui.OptionsGui;
import com.spacerng.solrng.gui.OptionsHolder;
import com.spacerng.solrng.gui.PrestigeGui;
import com.spacerng.solrng.gui.PrestigeHolder;
import com.spacerng.solrng.gui.SkillTreeGui;
import com.spacerng.solrng.gui.SkillTreeHolder;
import com.spacerng.solrng.player.ArmorManager;
import com.spacerng.solrng.player.ArmorPiece;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.PrestigeManager;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Clicks in both skill trees: buying and respeccing nodes, and what an unlock announces and runs. */
final class SkillTreeClicks {

    private final SolRNGPlugin plugin;

    SkillTreeClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Respec spends the climbing shiny cost and refunds every bought
     * node. Shift-click required so a stray click can't wipe a whole
     * tree, and the message names exactly what was taken and what came
     * back so nothing is lost silently.
     */
    void handleRespecClick(Player player, SkillTreeHolder holder, InventoryClickEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int cost = data.nextRespecCost();
        int owned = 0;
        long money = plugin.getSkillTreeManager().totalMoneySpent(data);
        long coins = plugin.getSkillTreeManager().totalCoinsSpent(data);
        for (com.spacerng.solrng.player.SkillNode node
                : plugin.getSkillTreeManager().getNodes().values()) {
            if (!node.usesTokens()) owned += plugin.getSkillTreeManager().levelOf(data, node);
        }

        if (owned == 0) {
            player.sendMessage(ChatColor.GRAY + "Nothing to respec.");
            return;
        }
        if (!event.isShiftClick()) {
            player.sendMessage(ChatColor.YELLOW + "Shift-click to confirm the respec.");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }
        if (data.totalShinies() < cost) {
            player.sendMessage(ChatColor.RED + "You need " + cost + " shinies for that.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        if (!data.spendAnyShinies(cost)) {
            player.sendMessage(ChatColor.RED + "The shinies didn't clear. Try again.");
            return;
        }

        plugin.getSkillTreeManager().respec(player, data);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Respec complete. "
                + ChatColor.RESET + ChatColor.GRAY + "Refunded "
                + Currency.MONEY.amount(money) + ChatColor.GRAY + " and "
                + Currency.COINS.amount(coins) + ChatColor.GRAY + ". Next respec costs "
                + ChatColor.LIGHT_PURPLE + data.nextRespecCost() + ChatColor.GRAY + " shinies.");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
        plugin.getScoreboardManager().update(player);
        plugin.getLuckBarManager().update(player);
        player.openInventory(SkillTreeGui.build(plugin, player, holder.getTree(), holder.getPage()));
    }

    void handleSkillTreeClick(InventoryClickEvent event) {
        event.setCancelled(true); // whole GUI is view/click only, no item movement
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof SkillTreeHolder holder)) return;

        Player clicker = (Player) event.getWhoClicked();
        if (event.getRawSlot() == SkillTreeGui.prevSlot()) {
            clicker.openInventory(SkillTreeGui.build(plugin, clicker, holder.getTree(), holder.getPage() - 1));
            return;
        }
        if (event.getRawSlot() == SkillTreeGui.nextSlot()) {
            clicker.openInventory(SkillTreeGui.build(plugin, clicker, holder.getTree(), holder.getPage() + 1));
            return;
        }
        if (event.getRawSlot() == SkillTreeGui.respecSlot() && !"farmtree".equals(holder.getTree())) {
            handleRespecClick(clicker, holder, event);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        NamespacedKey nodeIdKey = SkillTreeGui.nodeIdKey(plugin);
        ItemMeta meta = clicked.getItemMeta();
        String nodeId = meta.getPersistentDataContainer().get(nodeIdKey, PersistentDataType.STRING);
        if (nodeId == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        com.spacerng.solrng.player.SkillNode node = plugin.getSkillTreeManager().get(nodeId);
        boolean success = plugin.getSkillTreeManager().purchase(player, data, nodeId);
        if (success) {
            if (node != null && node.getMaxLevel() > 1) {
                player.sendMessage(ChatColor.GREEN + node.getDisplay() + " is now level "
                        + data.getNodeLevel(nodeId) + "/" + node.getMaxLevel() + "!");
            } else {
                player.sendMessage(ChatColor.GREEN + "Unlocked: " + (node != null ? node.getDisplay() : nodeId));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);

            // A tool upgrade changes the hoe's material, so the item in the
            // player's hand has to be rebuilt, not just relabelled.
            if (node != null && node.getEffect() == com.spacerng.solrng.player.SkillNode.Effect.HOE_TIER) {
                player.sendMessage(ChatColor.GOLD + "Your hoe is now "
                        + ChatColor.YELLOW + plugin.getFarmingManager().tierOf(data).display()
                        + ChatColor.GOLD + ".");
            }

            if (nodeId.equals("farming_unlock")) {
                player.getInventory().addItem(plugin.getFarmingManager().createBoundHoe(data));
                player.sendMessage(ChatColor.GREEN + "You received a Farmer's Hoe - bound to you!");
            }

            if (node != null) {
                announceUnlock(player, node);
                runUnlockCommands(player, node);
            }

            // A farm node can change what the hoe shows, so rewrite it.
            plugin.getFarmingManager().refreshHoe(player, data);
            player.openInventory(SkillTreeGui.build(plugin, player, holder.getTree(), holder.getPage()));
        } else {
            player.sendMessage(ChatColor.RED + "You can't unlock that yet.");
        }
    }

    /**
     * A gate skill that opens a whole menu deserves more than a one-line
     * "unlocked" - the point of buying it is the thing it leads to, so the
     * message names the command to go and use.
     */
    void announceUnlock(Player player, com.spacerng.solrng.player.SkillNode node) {
        String hint = switch (node.getEffect()) {
            case UNLOCK_CONVERT -> "/convert";
            case UNLOCK_AUTO_CONVERT -> "/convert";
            case UNLOCK_ARMOR -> "/armor";
            case UNLOCK_INDEX_LUCK -> "/index";
            case UNLOCK_PASS -> "/pass";
            case UNLOCK_PRIVATE_VAULT -> "/pv";
            case UNLOCK_FARMING -> "/crops";
            default -> null;
        };
        if (hint == null) return;
        player.sendMessage(ChatColor.GRAY + "Open it with " + ChatColor.YELLOW + hint + ChatColor.GRAY + ".");
    }

    /**
     * Some unlocks live outside this plugin - a Private Vault is another
     * plugin's permission. The commands are configured rather than
     * hard-coded so the skill still works whatever /pv plugin is installed,
     * and silently does nothing at all if none is.
     */
    void runUnlockCommands(Player player, com.spacerng.solrng.player.SkillNode node) {
        String key = switch (node.getEffect()) {
            case UNLOCK_PRIVATE_VAULT -> "private-vault";
            case UNLOCK_ARTIFACT -> "artifact";
            case UNLOCK_POTION -> "potion";
            default -> null;
        };
        if (key == null) return;

        for (String raw : plugin.getConfig().getStringList("unlock-commands." + key)) {
            if (raw == null || raw.isBlank()) continue;
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    raw.replace("{player}", player.getName()));
        }
    }
}
