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

/** Clicks in /convert, the one menu that takes items: what may go in, auto convert toggles, and turning the input into stored drops. */
final class ConvertClicks {

    private final SolRNGPlugin plugin;

    ConvertClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Only rolled drops may enter the input rows. Anything else - the
     * Starforge, the Farmer's Hoe, armor, a stack of dirt - is bounced at
     * the door rather than being silently accepted and then having to be
     * handled by the converter.
     *
     * Every route an item can take into a container has to be covered
     * separately: placing from the cursor, shift-clicking from the player's
     * inventory, the 1-9 hotbar swap, the offhand (F) swap, and dragging.
     * Blocking only the obvious click leaves the other four wide open.
     */
    boolean isDrop(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getItemMeta() == null) return false;
        if (plugin.getStarforgeManager().isStarforge(stack)) return false;

        var pdc = stack.getItemMeta().getPersistentDataContainer();
        // Both keys: only an item this plugin rolled carries a roll name
        // AND a rarity, which is what makes it convertible.
        return pdc.has(plugin.getRollListener().getRollNameKey(), PersistentDataType.STRING)
                && pdc.has(plugin.getRollListener().getRarityKey(), PersistentDataType.STRING);
    }

    void rejectNonDrop(InventoryClickEvent event) {
        event.setCancelled(true);
        event.getWhoClicked().sendMessage(ChatColor.RED + "Only rolled drops can go in there.");
    }

    void handleConvertClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();

        boolean clickedTopInventory = rawSlot >= 0 && rawSlot < top.getSize();
        boolean isInputSlot = clickedTopInventory && rawSlot <= 26;

        if (!clickedTopInventory) {
            // The player's own inventory. Shift-clicking is the one action
            // down here that pushes an item into the GUI.
            if (event.isShiftClick() && !isDrop(event.getCurrentItem())) {
                rejectNonDrop(event);
            }
            return;
        }

        if (isInputSlot) {
            switch (event.getClick()) {
                case NUMBER_KEY -> {
                    ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
                    // Empty hotbar slot = taking an item OUT, which is fine.
                    if (hotbar != null && hotbar.getType() != Material.AIR && !isDrop(hotbar)) {
                        rejectNonDrop(event);
                    }
                }
                case SWAP_OFFHAND -> {
                    ItemStack offhand = player.getInventory().getItemInOffHand();
                    if (offhand.getType() != Material.AIR && !isDrop(offhand)) {
                        rejectNonDrop(event);
                    }
                }
                default -> {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && cursor.getType() != Material.AIR && !isDrop(cursor)) {
                        rejectNonDrop(event);
                    }
                }
            }
            return; // otherwise let them place/remove drops freely
        }

        // Any other top-inventory slot (glass, confirm, toggles) is a button, not storage.
        event.setCancelled(true);

        if (rawSlot == ConvertHolder.CONFIRM_SLOT) {
            convertInputSlots(player, top);
            return;
        }

        if (rawSlot == ConvertHolder.SHINY_TOGGLE_SLOT) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (!data.hasUnlocked("auto_convert")) {
                player.sendMessage(ChatColor.RED + "Unlock 'Auto-Convert' in /skilltree first.");
                return;
            }
            data.setAutoConvertShiny(!data.isAutoConvertShiny());
            player.sendMessage(ChatColor.AQUA + "Shiny auto-convert is now "
                    + (data.isAutoConvertShiny() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            player.openInventory(ConvertGui.build(plugin, player));
            return;
        }

        if (rawSlot >= ConvertHolder.AUTO_TOGGLE_ROW_START && rawSlot <= ConvertHolder.AUTO_TOGGLE_ROW_END) {
            handleAutoToggleClick(player, rawSlot);
        }
    }

    /**
     * Converting banks each item as a stored drop of its own rarity
     * rather than paying Credits - a Common in the bank buys exactly what
     * a Common in the inventory buys, so /armor and /starforge stay the
     * sinks for rolled loot and Credits stay reserved for the paid store.
     */
    void convertInputSlots(Player player, Inventory top) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        java.util.Map<Rarity, Long> banked = new java.util.EnumMap<>(Rarity.class);
        int itemsConverted = 0;

        for (int slot : ConvertHolder.INPUT_SLOTS) {
            ItemStack stack = top.getItem(slot);
            if (!isDrop(stack)) continue; // left alone, and handed back on close
            ItemMeta meta = stack.getItemMeta();

            String rarityName = meta.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (rarityName == null) continue;

            try {
                Rarity rarity = Rarity.valueOf(rarityName);
                long amount = stack.getAmount();
                banked.merge(rarity, amount, Long::sum);
                itemsConverted += amount;
                // Refinery: each drop independently gets a chance to bank
                // twice. Rolled per item rather than once for the batch, so
                // converting a stack of 64 pays the average instead of an
                // all-or-nothing double.
                double refinery = plugin.getSkillTreeManager()
                        .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.CONVERT_BONUS);
                long extra = 0L;
                for (long i = 0; refinery > 0 && i < amount; i++) {
                    if (Math.random() < refinery) extra++;
                }
                if (plugin.getRollListener().isShiny(stack)) {
                    data.addBankedShiny(rarity, amount + extra);
                } else {
                    long cap = plugin.convertCap(data);
                    long fits = data.addBankedDrops(rarity, amount + extra, cap);
                    if (fits < amount + extra) {
                        player.sendMessage(ChatColor.RED + "Only " + fits
                                + " fit. Your vault holds " + cap + " of each.");
                    }
                    data.addConverted(rarity, fits);
                }
                top.setItem(slot, null);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (itemsConverted == 0) {
            player.sendMessage(ChatColor.RED + "Place some rolled items in the top rows first.");
            return;
        }

        StringBuilder summary = new StringBuilder();
        for (java.util.Map.Entry<Rarity, Long> entry : banked.entrySet()) {
            if (summary.length() > 0) summary.append(ChatColor.GRAY).append(", ");
            summary.append(plugin.getRarityManager().style(entry.getKey(),
                    entry.getValue() + " " + entry.getKey().displayName()));
        }

        plugin.getScoreboardManager().update(player);
        player.openInventory(ConvertGui.build(plugin, player)); // refresh the Stored Drops panel
        player.sendMessage(ChatColor.GREEN + "Stored " + itemsConverted + " drop(s): " + summary);
    }

    void handleAutoToggleClick(Player player, int rawSlot) {
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
