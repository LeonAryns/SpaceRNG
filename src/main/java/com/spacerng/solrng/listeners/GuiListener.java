package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.commands.TagCommand;
import com.spacerng.solrng.gui.ArmorGui;
import com.spacerng.solrng.gui.ArmorHolder;
import com.spacerng.solrng.gui.ConvertGui;
import com.spacerng.solrng.gui.ConvertHolder;
import com.spacerng.solrng.gui.IndexGui;
import com.spacerng.solrng.gui.IndexHolder;
import com.spacerng.solrng.gui.OptionsGui;
import com.spacerng.solrng.gui.OptionsHolder;
import com.spacerng.solrng.gui.PrestigeGui;
import com.spacerng.solrng.gui.PrestigeHolder;
import com.spacerng.solrng.gui.SkillTreeGui;
import com.spacerng.solrng.gui.SkillTreeHolder;
import com.spacerng.solrng.player.ArmorManager;
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
        } else if (topInventory.getHolder() instanceof PrestigeHolder) {
            handlePrestigeClick(event);
        } else if (topInventory.getHolder() instanceof ArmorHolder) {
            handleArmorClick(event);
        } else if (topInventory.getHolder() instanceof OptionsHolder) {
            handleOptionsClick(event);
        }
    }

    private void handleOptionsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof OptionsHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int rawSlot = event.getRawSlot();

        if (rawSlot == OptionsHolder.SOUND_SLOT) {
            data.setRollSoundEnabled(!data.isRollSoundEnabled());
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.ANIMATION_SLOT) {
            data.setRollAnimationEnabled(!data.isRollAnimationEnabled());
            player.openInventory(OptionsGui.build(plugin, player));
        }
    }

    private void handleArmorClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof ArmorHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        NamespacedKey tierIdKey = ArmorGui.tierIdKey(plugin);
        String tierId = clicked.getItemMeta().getPersistentDataContainer().get(tierIdKey, PersistentDataType.STRING);
        if (tierId == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ArmorManager armor = plugin.getArmorManager();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        if (armor.purchase(player, data, tierId, rarityKey)) {
            player.sendMessage(ChatColor.GREEN + "Bought: " + armor.get(tierId).getDisplay());
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            player.openInventory(ArmorGui.build(plugin, player)); // refresh
        } else {
            player.sendMessage(ChatColor.RED + "You can't buy that yet.");
        }
    }

    private void handlePrestigeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof PrestigeHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();
        int rawSlot = event.getRawSlot();

        if (rawSlot == PrestigeHolder.LEVEL_SLOT) {
            if (prestige.levelUp(data)) {
                plugin.getTagManager().refreshPrefix(player, data);
                player.sendMessage(ChatColor.GREEN + "Level up! You're now level " + data.getLevel() + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
                player.openInventory(PrestigeGui.build(plugin, player));
            } else {
                player.sendMessage(ChatColor.RED + "Keep rolling to level up.");
            }
        } else if (rawSlot == PrestigeHolder.PRESTIGE_SLOT) {
            if (prestige.prestige(data)) {
                plugin.getTagManager().refreshPrefix(player, data);
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Prestiged! " + ChatColor.RESET
                        + ChatColor.GRAY + "You're now [P" + data.getPrestige() + "] and back to level 1.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                player.openInventory(PrestigeGui.build(plugin, player));
            } else {
                player.sendMessage(ChatColor.RED + "Not enough levels to prestige yet.");
            }
        }
    }

    private void handleIndexClick(InventoryClickEvent event) {
        event.setCancelled(true); // collection log — clicking equips a tag or navigates, never moves items
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof IndexHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        if (rawSlot < 9) {
            handleIndexTopBar(holder, player, rawSlot);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        ItemMeta meta = clicked.getItemMeta();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        NamespacedKey nameKey = plugin.getRollListener().getRollNameKey();
        String rarityName = meta.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        String rollName = meta.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        if (rarityName == null || rollName == null) return; // undiscovered entry

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        TagCommand.equip(plugin, player, data, rollName, rarityName);
    }

    private void handleIndexTopBar(IndexHolder holder, Player player, int rawSlot) {
        Rarity[] rarities = Rarity.values();
        if (rawSlot < rarities.length) {
            Rarity clicked = rarities[rawSlot];
            Rarity newFilter = holder.getFilter() == clicked ? null : clicked;
            player.openInventory(IndexGui.build(plugin, player, newFilter, 0));
        } else if (rawSlot == 6) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(), holder.getPage() - 1));
        } else if (rawSlot == 8) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(), holder.getPage() + 1));
        }
        // slot 7 is the Index Progress readout — no-op
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

        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        boolean success = plugin.getSkillTreeManager().purchase(player, data, nodeId, rarityKey);
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

        // Allow free item movement into/out of the input rows (slots 0-26).
        boolean isInputSlot = rawSlot >= 0 && rawSlot <= 26;
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
                data.addConverted(rarity, stack.getAmount());
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
