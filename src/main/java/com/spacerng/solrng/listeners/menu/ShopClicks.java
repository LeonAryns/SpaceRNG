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

    /**
     * Vault: click a vault perk to equip/unequip; click an equipped
     * slot to unequip; shift-click a vault perk to discard it.
     */
    void handlePerkVaultClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PerkVaultHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int slot = event.getRawSlot();

        if (slot == com.spacerng.solrng.gui.PerkVaultGui.rollerSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PerkVaultGui.indexSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkIndexGui.build(plugin, player));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PerkVaultGui.prevSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkVaultGui.build(plugin, player,
                    Math.max(0, holder.getPage() - 1)));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PerkVaultGui.nextSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkVaultGui.build(plugin, player,
                    holder.getPage() + 1));
            return;
        }

        java.util.UUID perkId = com.spacerng.solrng.gui.PerkVaultGui.clickedId(
                plugin, event.getCurrentItem());
        if (perkId == null) return;

        // Loadout row (0..8): a click here just unequips.
        if (slot < 9) {
            data.getEquippedPerks().removeIf(p -> p.id().equals(perkId));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
            player.openInventory(com.spacerng.solrng.gui.PerkVaultGui.build(plugin, player, holder.getPage()));
            return;
        }

        // Vault row: shift-click discards, plain click toggles equip.
        if (event.isShiftClick()) {
            data.takePerkById(perkId);
            player.sendMessage(ChatColor.GRAY + "Perk discarded.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.6f, 1.2f);
            player.openInventory(com.spacerng.solrng.gui.PerkVaultGui.build(plugin, player, holder.getPage()));
            return;
        }
        boolean equipped = data.isPerkEquipped(perkId);
        if (equipped) {
            data.getEquippedPerks().removeIf(p -> p.id().equals(perkId));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
        } else {
            int max = plugin.getPerkManager().loadoutSlots();
            if (data.getEquippedPerks().size() >= max) {
                player.sendMessage(ChatColor.RED + "Loadout is full. Unequip one first.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            for (var perk : data.getPerkVault()) {
                if (perk.id().equals(perkId)) {
                    data.getEquippedPerks().add(perk);
                    break;
                }
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f);
        }
        plugin.getScoreboardManager().update(player);
        plugin.getLuckBarManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.PerkVaultGui.build(plugin, player, holder.getPage()));
    }

    /** Roller: one of four buttons - or the vault link. */
    void handlePerkRollerClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PerkRollerHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (event.getRawSlot() == com.spacerng.solrng.gui.PerkRollerGui.vaultSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkVaultGui.build(plugin, player, 0));
            return;
        }
        if (event.getRawSlot() == com.spacerng.solrng.gui.PerkRollerGui.indexSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkIndexGui.build(plugin, player));
            return;
        }
        int slot = event.getRawSlot();
        if (slot == com.spacerng.solrng.gui.PerkRollerGui.autoSaveSlot()) {
            data.setPerkAutoSave(!data.isPerkAutoSave());
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f,
                    data.isPerkAutoSave() ? 1.5f : 0.8f);
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PerkRollerGui.amountSlot()) {
            int[] amounts = com.spacerng.solrng.gui.PerkRollerGui.SAVE_AMOUNTS;
            int at = 0;
            for (int i = 0; i < amounts.length; i++) {
                if (amounts[i] == data.getPerkSaveAmount()) at = i;
            }
            int next = Math.floorMod(at + (event.isRightClick() ? -1 : 1), amounts.length);
            data.setPerkSaveAmount(amounts[next]);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PerkRollerGui.buySlot()) {
            int amount = data.getPerkSaveAmount();
            if (!plugin.getPerkManager().buySaveRolls(data, amount)) {
                player.sendMessage(ChatColor.RED + "You need "
                        + Currency.CREDITS.amount(amount * plugin.getPerkManager().saveCost())
                        + ChatColor.RED + " for " + amount + " save rolls.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Bought " + amount + " save rolls. You have "
                    + String.format("%,d", data.getPerkSaveRolls()) + ".");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
            plugin.getScoreboardManager().update(player);
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PerkRollerGui.confirmSlot()) {
            com.spacerng.solrng.rarity.Rarity[] tiers = com.spacerng.solrng.rarity.Rarity.values();
            // Positions 0..6 are the tiers, 7 is "never ask".
            int at = data.getPerkConfirmFrom() == null ? tiers.length : data.getPerkConfirmFrom().ordinal();
            int next = Math.floorMod(at + (event.isRightClick() ? -1 : 1), tiers.length + 1);
            data.setPerkConfirmFrom(next == tiers.length ? null : tiers[next]);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.2f);
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PerkRollerGui.pendingSlot()) {
            if (data.getPendingPerk() == null) return;
            var saved = plugin.getPerkManager().save(data);
            if (saved == null) {
                player.sendMessage(ChatColor.RED + "You need "
                        + Currency.CREDITS.amount(plugin.getPerkManager().saveCost())
                        + ChatColor.RED + " or a save roll to save this perk.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Saved: " + ChatColor.RESET
                    + plugin.getRarityManager().style(saved.tier(), saved.display()) + ChatColor.GRAY + " "
                    + saved.roman() + ChatColor.GRAY + " is in your vault.");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_CLOSE, 0.7f, 1.2f);
            plugin.getScoreboardManager().update(player);
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }

        var clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        String tierName = clicked.getItemMeta().getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.PerkRollerGui.rollTierKey(plugin),
                        PersistentDataType.STRING);
        if (tierName == null) return;

        com.spacerng.solrng.rarity.Rarity tier;
        try {
            tier = com.spacerng.solrng.rarity.Rarity.valueOf(tierName);
        } catch (IllegalArgumentException ex) { return; }

        // Auto save with nothing left to save with: the menu shows why, and nothing rolls.
        if (com.spacerng.solrng.gui.PerkRollerGui.outOfSaves(data)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
            return;
        }

        // An unsaved perk at or above the player's chosen tier takes a second click to throw away.
        var waiting = data.getPendingPerk();
        var askFrom = data.getPerkConfirmFrom();
        if (!data.isPerkAutoSave() && waiting != null && askFrom != null
                && waiting.tier().ordinal() >= askFrom.ordinal()) {
            Long asked = replaceConfirm.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (asked == null || now - asked > 5000L) {
                replaceConfirm.put(player.getUniqueId(), now);
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Careful: " + ChatColor.RESET
                        + ChatColor.GRAY + "your unsaved "
                        + plugin.getRarityManager().style(waiting.tier(), waiting.display())
                        + ChatColor.GRAY + " will be lost. Click again within 5 seconds to roll anyway.");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                return;
            }
        }
        replaceConfirm.remove(player.getUniqueId());

        var perk = plugin.getPerkManager().purchase(plugin, player, data, tier);
        if (perk == null) {
            var roll = plugin.getPerkManager().getRoll(tier);
            String need = roll == null ? "drops" : (roll.costAmount() + "x "
                    + plugin.getRarityManager().style(roll.costRarity(), roll.costRarity().displayName())
                    + ChatColor.RED + " drops");
            player.sendMessage(ChatColor.RED + "Not enough. You need " + need + ChatColor.RED + ".");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        boolean newInIndex = data.recordPerk(perk);
        String tierColored = plugin.getRarityManager().style(perk.tier(), perk.display());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "New perk: "
                + ChatColor.RESET + tierColored + ChatColor.GRAY + " " + perk.roman());
        if (data.getPendingPerk() == perk) {
            player.sendMessage(ChatColor.GRAY + "Save it in the roller, or your next roll replaces it.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Saved to your vault. " + ChatColor.GRAY
                    + String.format("%,d", data.getPerkSaveRolls()) + " save rolls left.");
        }
        if (newInIndex) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "New in your perk index! "
                    + ChatColor.RESET + ChatColor.GRAY + data.getPerkIndex().size() + " found. See /perks index");
        }
        // Reveal cue: a short one-frame flourish matching the tier.
        org.bukkit.Sound sound = switch (perk.tier()) {
            case DIVINE -> org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case MYTHICAL -> org.bukkit.Sound.ITEM_TRIDENT_THUNDER;
            case LEGENDARY -> org.bukkit.Sound.ENTITY_PLAYER_LEVELUP;
            case EPIC -> org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT;
            case RARE -> org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            default -> org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
        player.playSound(player.getLocation(), sound, 0.9f, 1.2f);
        player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
    }

    /** Perk index: read-only, apart from the links to the roller and the vault. */
    void handlePerkIndexClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PerkIndexHolder)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getRawSlot() == com.spacerng.solrng.gui.PerkIndexGui.backSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player));
        } else if (event.getRawSlot() == com.spacerng.solrng.gui.PerkIndexGui.vaultSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PerkVaultGui.build(plugin, player, 0));
        }
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
