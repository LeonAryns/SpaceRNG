package com.spacerng.solrng.listeners.menu;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.PrivateVaultGui;
import com.spacerng.solrng.gui.PrivateVaultHolder;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Clicks in /pv. The selector is read only, but a page is real storage, so
 * only the button bar along the bottom is cancelled there.
 */
public class VaultClicks {

    private final SolRNGPlugin plugin;

    public VaultClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PrivateVaultHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (holder.isSelector()) {
            event.setCancelled(true);
            Integer page = PrivateVaultGui.clickedPage(event.getCurrentItem());
            if (page == null) return;

            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (page > plugin.getRankManager().vaultPages(data)) {
                player.sendMessage(ChatColor.RED + "Vault " + page + " needs a higher rank. See /ranks.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
                return;
            }
            open(player, page);
            return;
        }

        // A page. Everything above the bar is storage and stays clickable.
        boolean inBar = event.getRawSlot() >= PrivateVaultGui.PAGE_SLOTS
                && event.getRawSlot() < top.getSize();
        if (!inBar) {
            // Shift clicking from below can only land in the storage rows,
            // because the bar is full. Nothing to guard.
            return;
        }
        event.setCancelled(true);

        int page = holder.getPage();
        if (event.getRawSlot() == PrivateVaultGui.prevSlot() && page > 1) {
            save(player, top, page);
            open(player, page - 1);
        } else if (event.getRawSlot() == PrivateVaultGui.nextSlot()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (page < plugin.getRankManager().vaultPages(data)) {
                save(player, top, page);
                open(player, page + 1);
            }
        } else if (event.getRawSlot() == 49) {
            save(player, top, page);
            openSelector(player);
        }
    }

    /** Writes the storage rows back onto the player before the menu goes. */
    public void save(Player player, Inventory top, int page) {
        if (page < 1) return;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        List<ItemStack> items = new ArrayList<>();
        boolean any = false;
        for (int slot = 0; slot < PrivateVaultGui.PAGE_SLOTS; slot++) {
            ItemStack stack = top.getItem(slot);
            if (stack != null && stack.getType() == Material.AIR) stack = null;
            if (stack != null) any = true;
            items.add(stack);
        }
        if (any) {
            data.getVaults().put(page, items);
        } else {
            data.getVaults().remove(page);
        }
    }

    public void open(Player player, int page) {
        com.spacerng.solrng.gui.Menus.open(plugin, player,
                () -> PrivateVaultGui.build(plugin, player, page));
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.4f);
    }

    public void openSelector(Player player) {
        com.spacerng.solrng.gui.Menus.open(plugin, player,
                () -> PrivateVaultGui.buildSelector(plugin, player));
    }
}
