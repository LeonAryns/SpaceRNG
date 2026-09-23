package com.spacerng.solrng.listeners.menu;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.cosmetic.CosmeticManager;
import com.spacerng.solrng.gui.AuraGui;
import com.spacerng.solrng.gui.CosmeticsGui;
import com.spacerng.solrng.gui.CosmeticsHolder;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Clicks in /cosmetics: the three doors on the hub, and picking a title
 * or a name colour on the two screens behind them.
 *
 * Nothing here can fail in a way worth a red message except picking
 * something you do not own, which the menu already draws as locked, so a
 * click on one says how it is earned rather than telling you no.
 */
final class CosmeticClicks {

    private final SolRNGPlugin plugin;

    CosmeticClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    void handle(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof CosmeticsHolder holder)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int slot = event.getRawSlot();

        switch (holder.getSection()) {
            case CosmeticsHolder.TITLES -> titles(player, data, event, slot);
            case CosmeticsHolder.COLOURS -> colours(player, data, event, slot);
            default -> hub(player, data, slot);
        }
    }

    private void hub(Player player, PlayerData data, int slot) {
        if (slot == CosmeticsGui.auraSlot()) {
            if (plugin.getRankManager().rankOf(data) == null) {
                player.sendMessage(ChatColor.GRAY + "Auras open up once your Discord is linked. "
                        + ChatColor.YELLOW + "/discord link");
                deny(player);
                return;
            }
            click(player);
            player.openInventory(AuraGui.build(plugin, player));
        } else if (slot == CosmeticsGui.titleSlot()) {
            click(player);
            player.openInventory(CosmeticsGui.buildTitles(plugin, player));
        } else if (slot == CosmeticsGui.colourSlot()) {
            click(player);
            player.openInventory(CosmeticsGui.buildColours(plugin, player));
        }
    }

    private void titles(Player player, PlayerData data, InventoryClickEvent event, int slot) {
        if (slot == CosmeticsGui.backSlot()) {
            click(player);
            player.openInventory(CosmeticsGui.build(plugin, player));
            return;
        }
        if (slot == CosmeticsGui.noneSlot()) {
            data.setWornCosmeticTag(null);
            apply(player);
            player.sendMessage(ChatColor.GRAY + "Title cleared.");
            player.openInventory(CosmeticsGui.buildTitles(plugin, player));
            return;
        }
        String id = CosmeticsGui.clicked(event.getCurrentItem());
        if (id == null) return;
        CosmeticManager.Title title = plugin.getCosmeticManager().title(id);
        if (title == null) return;
        if (!data.hasCosmeticTag(id)) {
            player.sendMessage(ChatColor.GRAY + "That title is handed out, not bought.");
            deny(player);
            return;
        }
        data.setWornCosmeticTag(id);
        apply(player);
        player.sendMessage(ChatColor.GRAY + "Wearing "
                + plugin.getCosmeticManager().styled(title) + ChatColor.GRAY + ".");
        player.openInventory(CosmeticsGui.buildTitles(plugin, player));
    }

    private void colours(Player player, PlayerData data, InventoryClickEvent event, int slot) {
        if (slot == CosmeticsGui.backSlot()) {
            click(player);
            player.openInventory(CosmeticsGui.build(plugin, player));
            return;
        }
        boolean allowed = plugin.getCosmeticManager().canPickNameColour(data);
        if (slot == CosmeticsGui.noneSlot()) {
            data.setNameColour(null);
            apply(player);
            player.sendMessage(ChatColor.GRAY + "Name colour cleared.");
            player.openInventory(CosmeticsGui.buildColours(plugin, player));
            return;
        }
        String id = CosmeticsGui.clicked(event.getCurrentItem());
        if (id == null) return;
        CosmeticManager.NameColour colour = plugin.getCosmeticManager().nameColour(id);
        if (colour == null) return;
        if (!allowed) {
            player.sendMessage(ChatColor.GRAY + "Painting your own name comes with the top rank. "
                    + ChatColor.YELLOW + "/ranks");
            deny(player);
            return;
        }
        data.setNameColour(id);
        apply(player);
        player.sendMessage(ChatColor.GRAY + "Your name is now "
                + plugin.getCosmeticManager().styled(colour, player.getName()) + ChatColor.GRAY + ".");
        player.openInventory(CosmeticsGui.buildColours(plugin, player));
    }

    /** Redraws the name everywhere it is shown, which is the whole point of both pickers. */
    private void apply(Player player) {
        plugin.getRankManager().refreshName(player);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private static void deny(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }
}
