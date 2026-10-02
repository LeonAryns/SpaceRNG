package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.consumable.Consumable;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /keys (V282): crate keys kept out of the inventory. New keys land here on
 * their own while auto store is on, the way drops auto convert, and a crate
 * spends these before any key item. One icon per key with how many are
 * kept; a click takes one out as an item.
 */
public final class KeysGui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    public static final int TOGGLE_SLOT = 30;
    public static final int STORE_SLOT = 32;

    private KeysGui() {
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        KeysHolder holder = new KeysHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, MenuStyle.title("Keys", "#FFE082", "#FF8F00"));
        holder.setInventory(inv);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // Every key a crate takes, held or not, in config order, so the row
        // shows what exists to collect rather than only what you have.
        Set<String> ids = new LinkedHashSet<>();
        for (String id : plugin.getConsumableManager().getAll().keySet()) {
            if (plugin.getCrateManager().isStorableKey(id)) ids.add(id);
        }
        ids.addAll(data.getStoredKeys().keySet());

        int index = 0;
        for (String id : ids) {
            if (index >= SLOTS.length) break;
            Consumable key = plugin.getConsumableManager().get(id);
            if (key == null) continue;
            long stored = data.storedKeys(id);
            ItemStack item = plugin.getConsumableManager().build(key, (int) Math.max(1, Math.min(64, stored)));
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add(Lore.stat(stored > 0 ? ChatColor.AQUA : ChatColor.DARK_GRAY, "Stored", String.format("%,d", stored)));
            lore.add("");
            lore.add(stored > 0
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to take one out"
                    : ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "None stored");
            if (stored > 0) lore.add(ChatColor.DARK_GRAY + "Shift-click for a stack.");
            meta.setLore(lore);
            meta.setEnchantmentGlintOverride(stored > 0 ? Boolean.TRUE : null);
            item.setItemMeta(meta);
            inv.setItem(SLOTS[index], item);
            holder.slots().put(SLOTS[index], id);
            index++;
        }

        boolean auto = data.isAutoStoreKeys();
        ItemStack toggle = new ItemStack(auto ? Material.HOPPER : Material.BARRIER);
        ItemMeta toggleMeta = toggle.getItemMeta();
        toggleMeta.setDisplayName(Lore.title(auto ? ChatColor.GREEN : ChatColor.RED, "Auto Store"));
        toggleMeta.setLore(List.of(
                Lore.stat(auto ? ChatColor.GREEN : ChatColor.RED, "Auto Store", auto ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                "",
                Lore.line(ChatColor.GRAY, "New keys go here instead of"),
                Lore.line(ChatColor.GRAY, "into your inventory."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + (auto ? "Click to switch off" : "Click to switch on")));
        toggle.setItemMeta(toggleMeta);
        inv.setItem(TOGGLE_SLOT, toggle);

        ItemStack store = new ItemStack(Material.CHEST);
        ItemMeta storeMeta = store.getItemMeta();
        storeMeta.setDisplayName(Lore.title(ChatColor.YELLOW, "Store Inventory Keys"));
        storeMeta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Puts every key you carry in here."),
                Lore.line(ChatColor.GRAY, "Crates use these keys first."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to store"));
        store.setItemMeta(storeMeta);
        inv.setItem(STORE_SLOT, store);

        MenuStyle.apply(inv, MenuStyle.Palette.GOLD);
        return inv;
    }
}
