package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.commands.TagCommand;
import com.spacerng.solrng.gui.ConvertGui;
import com.spacerng.solrng.gui.ConvertHolder;
import com.spacerng.solrng.gui.IndexHolder;
import com.spacerng.solrng.gui.SkillTreeGui;
import com.spacerng.solrng.gui.SkillTreeHolder;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GuiListener implements Listener {

    private final SolRNGPlugin plugin;

    public GuiListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof SkillTreeHolder) {
            handleSkillTreeClick(event);
        } else if (topInventory.getHolder() instanceof ConvertHolder) {
            handleConvertClick(event);
        } else if (topInventory.getHolder() instanceof IndexHolder) {
            handleIndexClick(event);
        }
    }

    private void handleIndexClick(InventoryClickEvent event) {
        event.setCancelled(true); // collection log — clicking equips a tag, never moves items
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof IndexHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        ItemMeta meta = clicked.getItemMeta();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        NamespacedKey nameKey = plugin.getRollListener().getRollNameKey();
        String rarityName = meta.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        String rollName = meta.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        if (rarityName == null || rollName == null) return; // undiscovered entry or the info book

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        TagCommand.equip(plugin, player, data, rollName, rarityName);
    }

    private void handleSkillTreeClick(InventoryClickEvent event) {
        event.setCancelled(true); // whole GUI is view/click only, no item movement
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof SkillTreeHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        NamespacedKey nodeIdKey = SkillTreeGui.nodeIdKey(plugin);
        ItemMeta meta = clicked.getItemMeta();
        String nodeId = meta.getPersistentDataContainer().get(nodeIdKey, PersistentDataType.STRING);
        if (nodeId == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        boolean success = plugin.getSkillTreeManager().purchase(data, nodeId);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Unlocked: " + plugin.getSkillTreeManager().get(nodeId).getDisplay());
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            player.openInventory(SkillTreeGui.build(plugin, player)); // refresh
        } else {
            player.sendMessage(ChatColor.RED + "You can't unlock that yet.");
        }
    }

    private void handleConvertClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();

        // Allow free item movement into/out of the input row (slots 0-8).
        boolean isInputSlot = rawSlot >= 0 && rawSlot <= 8;
        boolean clickedTopInventory = rawSlot < top.getSize();

        if (!clickedTopInventory) {
            return; // clicking in the player's own inventory below is always allowed
        }

        if (isInputSlot) {
            return; // let them place/remove items freely
        }

        // Any other top-inventory click (glass, confirm, toggles) is a button, not item storage.
        event.setCancelled(true);

        if (rawSlot == ConvertHolder.CONFIRM_SLOT) {
            convertInputSlots(player, top);
            return;
        }

        if (rawSlot >= ConvertHolder.AUTO_TOGGLE_ROW_START) {
            handleAutoToggleClick(player, rawSlot);
        }
    }

    private void convertInputSlots(Player player, Inventory top) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        long totalPoints = 0;
        int itemsConverted = 0;

        for (int slot : ConvertHolder.INPUT_SLOTS) {
            ItemStack stack = top.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) continue;

            String rarityName = meta.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (rarityName == null) continue; // not a SolRNG-rolled item, skip it (leave it in the slot)

            try {
                Rarity rarity = Rarity.valueOf(rarityName);
                long pointsEach = plugin.getConfig().getLong("conversion.points-per-rarity." + rarity.name(), 1L);
                totalPoints += pointsEach * stack.getAmount();
                itemsConverted += stack.getAmount();
                top.setItem(slot, null);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (itemsConverted == 0) {
            player.sendMessage(ChatColor.RED + "Place some rolled items in the top row first.");
            return;
        }

        data.addPoints(totalPoints);
        plugin.getScoreboardManager().update(player);
        player.sendMessage(ChatColor.GREEN + "Converted " + itemsConverted + " item(s) → " + ChatColor.YELLOW + totalPoints + " Credits");
    }

    private void handleAutoToggleClick(Player player, int rawSlot) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.hasUnlocked("auto_convert")) {
            player.sendMessage(ChatColor.RED + "Unlock 'Auto-Convert' in /skilltree first.");
            return;
        }

        int index = rawSlot - ConvertHolder.AUTO_TOGGLE_ROW_START;
        Rarity[] values = Rarity.values();
        if (index < 0 || index >= values.length) return;

        Rarity rarity = values[index];
        data.toggleAutoConvert(rarity);
        player.sendMessage(ChatColor.YELLOW + "Auto-convert for " + rarity.name() + " is now "
                + (data.isAutoConverting(rarity) ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        player.openInventory(ConvertGui.build(plugin, player)); // refresh
    }
}
