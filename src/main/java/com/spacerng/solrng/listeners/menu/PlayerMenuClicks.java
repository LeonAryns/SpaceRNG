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

/** Clicks in the menus about the player: options, stats, the index, crops and the hoe. */
final class PlayerMenuClicks {

    private final SolRNGPlugin plugin;

    PlayerMenuClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    void handleOptionsClick(InventoryClickEvent event) {
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
        } else if (rawSlot == OptionsHolder.WORN_AURA_SLOT) {
            data.setWornAurasVisible(!data.isWornAurasVisible());
            plugin.getAuraManager().refreshVisibility(player);
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.OWN_AURA_SLOT) {
            data.cycleOwnAuraView();
            plugin.getAuraManager().refreshVisibility(player);
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.AURA_EPIC_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.EPIC);
        } else if (rawSlot == OptionsHolder.AURA_LEGENDARY_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.LEGENDARY);
        } else if (rawSlot == OptionsHolder.AURA_MYTHICAL_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.MYTHICAL);
        } else if (rawSlot == OptionsHolder.AURA_DIVINE_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.DIVINE);
        } else if (rawSlot == OptionsHolder.SHOUT_EPIC_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.EPIC);
        } else if (rawSlot == OptionsHolder.SHOUT_LEGENDARY_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.LEGENDARY);
        } else if (rawSlot == OptionsHolder.SHOUT_MYTHICAL_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.MYTHICAL);
        } else if (rawSlot == OptionsHolder.SHOUT_DIVINE_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.DIVINE);
        } else if (rawSlot >= OptionsHolder.DROP_COMMON_SLOT
                && rawSlot <= OptionsHolder.DROP_DIVINE_SLOT) {
            var rarity = com.spacerng.solrng.rarity.Rarity.values()
                    [rawSlot - OptionsHolder.DROP_COMMON_SLOT];
            data.setDropMessageEnabled(rarity, !data.isDropMessageEnabled(rarity));
            player.openInventory(OptionsGui.build(plugin, player));
        }
    }

    void toggleShout(Player player, PlayerData data,
                             com.spacerng.solrng.rarity.Rarity rarity) {
        data.setBroadcastEnabled(rarity, !data.isBroadcastEnabled(rarity));
        player.openInventory(OptionsGui.build(plugin, player));
    }

    /** Read-only: open one stat's breakdown, or come back from it. */
    void handleStatsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.StatsHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        if (holder.getOpen() != null) {
            if (clicked.getType() == org.bukkit.Material.ARROW) {
                player.openInventory(com.spacerng.solrng.gui.StatsGui.overview(
                        plugin, holder.getTarget(), holder.getTargetName()));
            }
            return;
        }

        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.StatsGui.statKey(plugin), PersistentDataType.STRING);
        if (id == null) return;
        try {
            player.openInventory(com.spacerng.solrng.gui.StatsGui.breakdown(plugin,
                    holder.getTarget(), holder.getTargetName(),
                    com.spacerng.solrng.stats.StatSources.Id.valueOf(id)));
        } catch (IllegalArgumentException ignored) {
            // A stat that no longer exists - the menu is stale, not broken.
        }
    }

    /** Picking a crop repaints the shared farm for this player only. */
    void handleCropsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof CropsHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        String cropId = clicked.getItemMeta().getPersistentDataContainer()
                .get(CropsGui.cropKey(plugin), PersistentDataType.STRING);
        if (cropId == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var farm = plugin.getFarmPlotManager();
        var crop = farm.getCrop(cropId);
        if (crop == null) return;

        if (!farm.isUnlocked(data, crop)) {
            player.sendMessage(ChatColor.RED + "You haven't unlocked " + crop.getDisplay() + " yet.");
            return;
        }

        data.setSelectedCrop(crop.getId());
        farm.render(player);
        player.sendMessage(ChatColor.GREEN + "Your farm is now growing " + ChatColor.YELLOW + crop.getDisplay()
                + ChatColor.GREEN + ".");
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_CROP_PLANT, 0.8f, 1.2f);
        player.openInventory(CropsGui.build(plugin, player));
    }

    void handleHoeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof HoeHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // The tool card at the top is the tier button.
        if (event.getRawSlot() == 4) {
            var farming = plugin.getFarmingManager();
            var next = farming.nextTier(data);
            if (next == null) {
                player.sendMessage(ChatColor.GRAY + "Your hoe is at the top of the ladder.");
            } else if (farming.purchaseTier(player, data)) {
                player.sendMessage(ChatColor.GREEN + "Hoe upgraded to "
                        + ChatColor.YELLOW + farming.tierOf(data).display() + ChatColor.GREEN + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
            } else {
                StringBuilder price = new StringBuilder();
                for (var cost : next.costs().entrySet()) {
                    if (price.length() > 0) price.append(", ");
                    price.append(cost.getValue()).append(" ").append(cost.getKey().displayName());
                }
                player.sendMessage(ChatColor.RED + "The next tier costs " + price + ".");
            }
            player.openInventory(com.spacerng.solrng.gui.HoeGui.build(plugin, player));
            return;
        }

        if (event.getRawSlot() == HoeGui.farmSoundSlot()) {
            data.setFarmSoundEnabled(!data.isFarmSoundEnabled());
            player.openInventory(HoeGui.build(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f,
                    data.isFarmSoundEnabled() ? 1.5f : 0.8f);
            return;
        }
        if (event.getRawSlot() == HoeGui.enchantSoundSlot()) {
            data.setEnchantSoundEnabled(!data.isEnchantSoundEnabled());
            player.openInventory(HoeGui.build(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f,
                    data.isEnchantSoundEnabled() ? 1.5f : 0.8f);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(HoeGui.enchantKey(plugin), PersistentDataType.STRING);
        if (id == null) return;

        var hoe = plugin.getHoeEnchantManager();

        int bought = 0;
        // Ten thousand levels is unclickable one at a time. Left is one,
        // shift is a hundred, right buys everything the wallet covers.
        int attempts = event.isRightClick() ? 10_000 : event.isShiftClick() ? 100 : 1;
        bought = hoe.buyMany(data, id, attempts);

        if (bought == 0) {
            var enchant = hoe.get(id);
            player.sendMessage(ChatColor.RED + (enchant != null && !hoe.isUnlocked(data, id)
                    ? "Unlock that enchant in /farmtree first."
                    : "You can't upgrade that right now."));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        var enchant = hoe.get(id);
        player.sendMessage(ChatColor.GREEN + "Upgraded " + enchant.colour() + enchant.display()
                + ChatColor.GREEN + " to level " + ChatColor.WHITE + hoe.levelOf(data, id)
                + (bought > 1 ? ChatColor.DARK_GRAY + " (+" + bought + ")" : ""));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);

        // The hoe's lore is a snapshot, so it has to be rewritten whenever
        // what it describes changes.
        plugin.getFarmingManager().refreshHoe(player, data);
        plugin.getScoreboardManager().update(player);
        player.openInventory(HoeGui.build(plugin, player));
    }

    void toggleAura(Player player, PlayerData data, com.spacerng.solrng.rarity.Rarity rarity) {
        data.setAuraEnabled(rarity, !data.isAuraEnabled(rarity));
        player.openInventory(OptionsGui.build(plugin, player));
    }

    void handleIndexClick(InventoryClickEvent event) {
        event.setCancelled(true); // collection log - clicking equips a tag or navigates, never moves items
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof IndexHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        // Page buttons first: they sit on the divider row, clear of the
        // tab bar, and they have to win regardless of what else is there.
        if (rawSlot == IndexGui.shinySlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(), 0,
                    !holder.isShinyView()));
            return;
        }
        if (rawSlot == IndexGui.prevSlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(),
                    Math.max(0, holder.getPage() - 1), holder.isShinyView()));
            return;
        }
        if (rawSlot == IndexGui.nextSlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(),
                    holder.getPage() + 1, holder.isShinyView()));
            return;
        }
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

    /** Slots 0 to 6 are the rarity tabs. 7 is a spacer, 8 is your head. */
    void handleIndexTopBar(IndexHolder holder, Player player, int rawSlot) {
        Rarity[] rarities = Rarity.values();
        if (rawSlot >= rarities.length) return;

        Rarity clicked = rarities[rawSlot];
        Rarity newFilter = holder.getFilter() == clicked ? null : clicked;
        player.openInventory(IndexGui.build(plugin, player, newFilter, 0, holder.isShinyView()));
    }
}
