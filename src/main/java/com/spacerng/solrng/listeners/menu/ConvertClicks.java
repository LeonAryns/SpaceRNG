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
        return ConvertGui.isDrop(plugin, stack);
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

        if (rawSlot == ConvertHolder.CONVERT_ALL_SLOT) {
            convertInventory(player);
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

    void convertInputSlots(Player player, Inventory top) {
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (int slot : ConvertHolder.INPUT_SLOTS) {
            ItemStack stack = top.getItem(slot);
            if (!isDrop(stack)) continue; // left alone, and handed back on close
            stacks.add(stack);
            slots.add(slot);
        }
        bank(player, stacks, index -> top.setItem(slots.get(index), null),
                "Place some rolled items in the top rows first.");
    }

    /**
     * Convert all (V159): every rolled drop in the player's own inventory
     * in one click. Shinies stay put, the same promise the shiny switch
     * makes, because a shiny is the one drop nobody wants banked by accident.
     */
    void convertInventory(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isDrop(stack) || plugin.getRollListener().isShiny(stack)) continue;
            stacks.add(stack);
            slots.add(slot);
        }
        bank(player, stacks, index -> player.getInventory().setItem(slots.get(index), null),
                "You are not carrying any rolled drops.");
    }

    /**
     * Converting banks each item as a stored drop of its own rarity
     * rather than paying Credits - a Common in the bank buys exactly what
     * a Common in the inventory buys, so /armor and /starforge stay the
     * sinks for rolled loot and Credits stay reserved for the paid store.
     *
     * A stack only leaves the inventory when all of it fits in the vault.
     * Clearing it regardless is how a full vault used to eat drops.
     */
    private void bank(Player player, java.util.List<ItemStack> stacks,
                      java.util.function.IntConsumer clear, String nothing) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        java.util.Map<Rarity, Long> banked = new java.util.EnumMap<>(Rarity.class);
        long itemsConverted = 0;
        boolean full = false;
        long cap = plugin.convertCap(data);
        double refinery = plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.CONVERT_BONUS)
                + plugin.getPerkManager().totalOf(data, com.spacerng.solrng.perk.PerkStat.CONVERT_PERCENT);

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            String rarityName = stack.getItemMeta().getPersistentDataContainer()
                    .get(rarityKey, PersistentDataType.STRING);
            Rarity rarity;
            try {
                rarity = Rarity.valueOf(rarityName);
            } catch (IllegalArgumentException | NullPointerException ex) {
                continue;
            }
            long amount = stack.getAmount();
            boolean shiny = plugin.getRollListener().isShiny(stack);
            if (!shiny && data.getBankedDrops(rarity) + amount > cap) {
                full = true;
                continue;
            }
            // Refinery: each drop independently gets a chance to bank
            // twice. Rolled per item rather than once for the batch, so
            // converting a stack of 64 pays the average instead of an
            // all-or-nothing double.
            long extra = 0L;
            for (long n = 0; refinery > 0 && n < amount; n++) {
                if (Math.random() < refinery) extra++;
            }
            if (shiny) {
                data.addBankedShiny(rarity, amount + extra);
            } else {
                long fits = data.addBankedDrops(rarity, amount + extra, cap);
                data.addConverted(rarity, fits);
            }
            banked.merge(rarity, amount, Long::sum);
            itemsConverted += amount;
            clear.accept(i);
        }

        if (itemsConverted == 0) {
            player.sendMessage(ChatColor.RED + (full
                    ? "Your vault is full. It holds " + cap + " of each rarity."
                    : nothing));
            return;
        }

        StringBuilder summary = new StringBuilder();
        for (java.util.Map.Entry<Rarity, Long> entry : banked.entrySet()) {
            if (summary.length() > 0) summary.append(ChatColor.WHITE).append(", ");
            summary.append(plugin.getRarityManager().style(entry.getKey(),
                    entry.getValue() + " " + entry.getKey().displayName()));
        }

        plugin.getScoreboardManager().update(player);
        player.openInventory(ConvertGui.build(plugin, player)); // refresh the vault panel
        player.sendMessage(ChatColor.GREEN + "Stored " + String.format("%,d", itemsConverted)
                + " drop" + (itemsConverted == 1 ? "" : "s") + ": " + summary);
        if (full) {
            player.sendMessage(ChatColor.RED + "Some stayed in your inventory: the vault holds "
                    + cap + " of each rarity.");
        }
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
        player.sendMessage(ChatColor.YELLOW + "Auto convert for " + rarity.displayName() + " is now "
                + (data.isAutoConverting(rarity) ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        player.openInventory(ConvertGui.build(plugin, player)); // refresh
    }
}
