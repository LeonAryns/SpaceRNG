package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ConvertGui {

    private static final int STORED_SLOT = 49; // bottom row, centred

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ConvertHolder holder = new ConvertHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_GREEN + "Convert Items → Drops");
        holder.setInventory(inv);

        // Glass border under the input rows so players know the rest isn't functional input.
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = 27; i < 54; i++) {
            inv.setItem(i, pane);
        }

        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(Lore.title(ChatColor.GREEN, "Convert"));
        confirmMeta.setLore(List.of(
                Lore.section(ChatColor.GREEN, "What it does"),
                Lore.line(ChatColor.GREEN, "Turns everything in the top three"),
                Lore.line(ChatColor.GREEN, "rows into stored drops of the"),
                Lore.line(ChatColor.GREEN, "same rarity."),
                "",
                ChatColor.DARK_GRAY + Lore.BULLET + " Spend them in /armor and /starforge.",
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO CONVERT"));
        confirm.setItemMeta(confirmMeta);
        inv.setItem(ConvertHolder.CONFIRM_SLOT, confirm);

        // Auto-convert toggles, only meaningful once the auto_convert node is unlocked.
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean autoConvertUnlocked = data.hasUnlocked("auto_convert");
        int slot = ConvertHolder.AUTO_TOGGLE_ROW_START;
        for (Rarity rarity : Rarity.values()) {
            boolean on = data.isAutoConverting(rarity);
            ItemStack toggle = new ItemStack(!autoConvertUnlocked ? Material.GRAY_STAINED_GLASS_PANE
                    : on ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
            ItemMeta meta = toggle.getItemMeta();
            String status = on ? ChatColor.GREEN + "" + ChatColor.BOLD + "On" : ChatColor.RED + "" + ChatColor.BOLD + "Off";
            meta.setDisplayName(plugin.getRarityManager().style(rarity, rarity.displayName())
                    + ChatColor.DARK_GRAY + " — " + status);
            meta.setLore(autoConvertUnlocked
                    ? List.of(
                            Lore.line(ChatColor.AQUA, "Every " + rarity.displayName() + " you roll goes"),
                            Lore.line(ChatColor.AQUA, "straight to your bank."),
                            "",
                            ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO TOGGLE")
                    : List.of(
                            ChatColor.RED + "" + ChatColor.BOLD + "LOCKED",
                            ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Unlock "
                                    + ChatColor.YELLOW + "Auto Convert" + ChatColor.GRAY + " in "
                                    + ChatColor.YELLOW + "/skilltree"));
            toggle.setItemMeta(meta);
            inv.setItem(slot, toggle);
            slot++;
        }

        inv.setItem(ConvertHolder.SHINY_TOGGLE_SLOT, buildShinyToggle(data));
        inv.setItem(STORED_SLOT, buildStoredPanel(plugin, data));

        return inv;
    }

    /**
     * What the player has banked. Converting doesn't destroy a drop, it
     * just moves it out of the inventory — so this is a running total of
     * spendable Common/Uncommon/... rather than a separate currency.
     */
    /**
     * The shiny switch, kept away from the rarity row on purpose. A shiny
     * is a 1-in-100 find; having it swallowed by a toggle somebody set for
     * the common version of the same drop would be the worst thing this
     * menu could do, so it only ever answers to this button.
     */
    private static ItemStack buildShinyToggle(PlayerData data) {
        boolean on = data.isAutoConvertShiny();
        ItemStack item = new ItemStack(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, Lore.SPARK + " Shiny")
                + ChatColor.DARK_GRAY + " — "
                + (on ? ChatColor.GREEN.toString() + ChatColor.BOLD + "On"
                      : ChatColor.RED.toString() + ChatColor.BOLD + "Off"));
        meta.setLore(List.of(
                Lore.section(ChatColor.AQUA, "Shinies only"),
                Lore.line(ChatColor.AQUA, "The rarity switches never touch"),
                Lore.line(ChatColor.AQUA, "a shiny. Only this one does."),
                Lore.line(ChatColor.AQUA, "They bank separately too."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO TOGGLE"));
        meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildStoredPanel(SolRNGPlugin plugin, PlayerData data) {
        ItemStack panel = new ItemStack(Material.CHEST);
        ItemMeta meta = panel.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Stored Drops"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GOLD, "Your bank"));
        for (Rarity rarity : Rarity.values()) {
            long shiny = data.getBankedShiny(rarity);
            lore.add(plugin.getRarityManager().style(rarity, Lore.BULLET + " " + rarity.displayName() + ": ")
                    + ChatColor.WHITE + data.getBankedDrops(rarity)
                    + (shiny > 0 ? ChatColor.DARK_GRAY + "  " + ChatColor.AQUA + Lore.SPARK + " " + shiny : ""));
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Spendable in /armor and /starforge.");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + ChatColor.AQUA + Lore.SPARK
                + ChatColor.DARK_GRAY + " Shinies bank separately.");
        meta.setLore(lore);
        panel.setItemMeta(meta);
        return panel;
    }
}
