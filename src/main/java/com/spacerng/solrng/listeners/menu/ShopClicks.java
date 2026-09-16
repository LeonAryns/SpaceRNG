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

/** Clicks in the menus that spend or collect: buy, shop, daily, potions and perks. */
final class ShopClicks {

    private final SolRNGPlugin plugin;
    // When each player was last warned that a roll would replace an unsaved perk.
    private final java.util.Map<java.util.UUID, Long> replaceConfirm = new java.util.HashMap<>();

    ShopClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /** /perks: roll a perk, buy Perk Tickets, or open the perk index. */
    void handlePerkRollerClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PerkRollerHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var perks = plugin.getPerkManager();
        int slot = event.getRawSlot();

        if (slot == com.spacerng.solrng.gui.PerkRollerGui.indexSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkIndexGui.build(plugin, player));
            return;
        }

        Integer bundle = com.spacerng.solrng.gui.PerkRollerGui.buyAmountAt(plugin, slot);
        if (bundle != null) {
            long price = perks.ticketPrices().getOrDefault(bundle, 0L);
            if (!perks.buyTickets(data, bundle)) {
                player.sendMessage(ChatColor.RED + "You need " + Currency.CREDITS.amount(price)
                        + ChatColor.RED + " for " + bundle + " Perk Tickets.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Bought " + bundle + " Perk Tickets. You have "
                    + String.format("%,d", data.getPerkTickets()) + ".");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
            plugin.getScoreboardManager().update(player);
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }

        if (slot != com.spacerng.solrng.gui.PerkRollerGui.rollSlot()) return;
        if (data.getPerkTickets() < 1) {
            player.sendMessage(ChatColor.RED + "You need a Perk Ticket. Buy them along the bottom.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // A perk and level with confirmation on takes a second click to replace.
        var current = perks.activeType(data);
        if (current != null && data.getPerkConfirm().contains(current.key(data.getActivePerkLevel()))) {
            Long asked = replaceConfirm.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (asked == null || now - asked > 5000L) {
                replaceConfirm.put(player.getUniqueId(), now);
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Careful: " + ChatColor.RESET
                        + ChatColor.GRAY + "your " + com.spacerng.solrng.gui.PerkLore.name(plugin, current) + " "
                        + plugin.getRarityManager().style(current.rarity(),
                        com.spacerng.solrng.perk.PerkType.roman(data.getActivePerkLevel()))
                        + ChatColor.GRAY + " has confirmation on. Click again within 5 seconds to roll it away.");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                return;
            }
        }
        replaceConfirm.remove(player.getUniqueId());

        boolean fresh;
        data.setPerkTickets(data.getPerkTickets() - 1);
        int foundBefore = data.getPerkFound().size();
        var result = perks.roll(data);
        if (result == null) {
            data.setPerkTickets(data.getPerkTickets() + 1);
            player.sendMessage(ChatColor.RED + "There are no perks set up yet.");
            return;
        }
        fresh = data.getPerkFound().size() > foundBefore;

        var type = result.type();
        String roman = com.spacerng.solrng.perk.PerkType.roman(result.level());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "New perk: " + ChatColor.RESET
                + com.spacerng.solrng.gui.PerkLore.name(plugin, type) + " "
                + plugin.getRarityManager().styleBold(type.rarity(), roman));
        player.sendMessage(ChatColor.GRAY + "  " + com.spacerng.solrng.gui.PerkLore.shortStats(type, result.level()));
        if (result.pity()) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Pity Luck! " + ChatColor.RESET
                    + ChatColor.GRAY + "This one was guaranteed Mythical or better.");
        }
        if (fresh) {
            player.sendMessage(ChatColor.GREEN + "New in your perk index! " + ChatColor.GRAY + "See /perks index");
        }
        org.bukkit.Sound sound = switch (type.rarity()) {
            case DIVINE -> org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case MYTHICAL -> org.bukkit.Sound.ITEM_TRIDENT_THUNDER;
            case LEGENDARY -> org.bukkit.Sound.ENTITY_PLAYER_LEVELUP;
            case EPIC -> org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT;
            case RARE -> org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            default -> org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
        player.playSound(player.getLocation(), sound, 0.9f, 1.2f);
        plugin.getScoreboardManager().update(player);
        plugin.getLuckBarManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
    }

    /** Perk index: press 1 to 5 on a perk to switch that level's confirmation, click to switch all five. */
    void handlePerkIndexClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PerkIndexHolder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (event.getRawSlot() == com.spacerng.solrng.gui.PerkIndexGui.backSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }
        String typeId = com.spacerng.solrng.gui.PerkIndexGui.clickedType(event.getCurrentItem());
        if (typeId == null) return;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            int level = event.getHotbarButton() + 1;
            if (level < 1 || level > 5) return;
            String key = typeId + ":" + level;
            if (!data.getPerkConfirm().remove(key)) data.getPerkConfirm().add(key);
        } else {
            boolean allOn = true;
            for (int level = 1; level <= 5; level++) {
                if (!data.getPerkConfirm().contains(typeId + ":" + level)) allOn = false;
            }
            for (int level = 1; level <= 5; level++) {
                if (allOn) data.getPerkConfirm().remove(typeId + ":" + level);
                else data.getPerkConfirm().add(typeId + ":" + level);
            }
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        player.openInventory(com.spacerng.solrng.gui.PerkIndexGui.build(plugin, player));
    }

    /** /ranks: buy the clicked rank with Credits. */
    void handleRanksClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.RanksHolder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        String id = com.spacerng.solrng.gui.RanksGui.clickedRank(event.getCurrentItem());
        if (id == null) return;
        var ranks = plugin.getRankManager();
        var tier = ranks.tier(id);
        if (tier == null) return;
        var current = ranks.rankOf(data);
        if (current != null && ranks.indexOf(current) >= ranks.indexOf(tier)) {
            player.sendMessage(ChatColor.GRAY + "You already have that rank or better.");
            return;
        }
        if (tier.price() <= 0) {
            player.sendMessage(ChatColor.GRAY + "Linked is free: use " + ChatColor.YELLOW + "/discord link"
                    + ChatColor.GRAY + ".");
            return;
        }
        if (!ranks.buy(player, data, tier)) {
            player.sendMessage(ChatColor.RED + "You need " + Currency.CREDITS.amount(tier.price())
                    + ChatColor.RED + " for that rank.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        plugin.getScoreboardManager().update(player);
        plugin.getLuckBarManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.RanksGui.build(plugin, player));
    }

    void handleBuyClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof BuyHolder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (event.getRawSlot() == BuyGui.BATTLEPASS_SLOT) {
            if (data.isPassPremium()) return;
            if (!plugin.getPassManager().buyPremium(player, data)) {
                player.sendMessage(ChatColor.RED + "You need "
                        + String.format("%,d", plugin.getPassManager().getPremiumCost())
                        + " Credits for the premium track.");
                return;
            }
            player.openInventory(BuyGui.build(plugin, player));
            return;
        }
        if (event.getRawSlot() != BuyGui.BOOST_SLOT) return;

        if (plugin.getBoostManager().isMaxed()) {
            player.sendMessage(ChatColor.RED + "The boost is already at its cap for this run.");
            return;
        }
        if (!plugin.getBoostManager().purchase(player, data)) {
            player.sendMessage(ChatColor.RED + "You need "
                    + String.format("%,d", plugin.getBoostManager().nextCost()) + " Credits for that.");
            return;
        }
        plugin.getLuckBarManager().updateAll();
        player.openInventory(BuyGui.build(plugin, player));
    }

    void handleDailyClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof DailyHolder)) return;
        if (event.getRawSlot() != DailyGui.CLAIM_SLOT) return;

        Player player = (Player) event.getWhoClicked();
        if (!plugin.getDailyManager().claim(player)) {
            player.sendMessage(ChatColor.RED + "You've already claimed today. Come back tomorrow.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
        player.openInventory(DailyGui.build(plugin, player));
    }

    /** Buys enchant levels with Tokens; shift-click buys ten. */
    /**
     * The hub just runs the shop's own command. Going through the command
     * rather than opening the menu directly means every gate, message and
     * future change lives in exactly one place - the hub can't drift out
     * of step with what /armor itself does.
     */
    void handleShopClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.ShopHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        String command = clicked.getItemMeta().getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.ShopGui.commandKey(plugin), PersistentDataType.STRING);
        if (command == null) return;

        Player player = (Player) event.getWhoClicked();
        player.closeInventory();
        player.performCommand(command);
    }

    void handlePotionClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PotionHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.PotionGui.potionKey(plugin), PersistentDataType.STRING);
        if (id == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var consumable = plugin.getConsumableManager().get(id);
        int amount = event.isShiftClick() ? 5 : 1;

        if (!plugin.getConsumableManager().purchase(player, data, consumable, amount)) {
            player.sendMessage(ChatColor.RED + "You don't have the drops for that.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Brewed " + ChatColor.WHITE + amount + "x "
                + ChatColor.LIGHT_PURPLE + consumable.display() + ChatColor.GREEN + ".");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BREWING_STAND_BREW, 0.9f, 1.4f);
        player.openInventory(com.spacerng.solrng.gui.PotionGui.build(plugin, player));
    }
}
