package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /convert. The top three rows take drops; under them one row of actions
 * (convert what you put in, convert everything you carry, and what is
 * already in your vault), one row of auto convert switches, one per
 * rarity, and the shiny switch on its own at the bottom.
 *
 * Rebuilt in V159 to the house style: black filler, the footers from
 * menu-design, one icon per kind of switch with a glint when it is on
 * instead of red and lime concrete.
 */
public class ConvertGui {

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ConvertHolder holder = new ConvertHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Convert" + ChatColor.GRAY + " - drops to your vault");
        holder.setInventory(inv);

        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = 27; i < 54; i++) inv.setItem(i, pane);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        inv.setItem(ConvertHolder.CONFIRM_SLOT, buildConvert());
        inv.setItem(ConvertHolder.CONVERT_ALL_SLOT, buildConvertAll(plugin, player));
        inv.setItem(ConvertHolder.STORED_SLOT, buildStoredPanel(plugin, data));

        boolean unlocked = data.hasUnlocked("auto_convert");
        int slot = ConvertHolder.AUTO_TOGGLE_ROW_START;
        for (Rarity rarity : Rarity.values()) {
            if (slot > ConvertHolder.AUTO_TOGGLE_ROW_END) break;
            inv.setItem(slot++, buildAutoToggle(plugin, data, rarity, unlocked));
        }
        inv.setItem(ConvertHolder.SHINY_TOGGLE_SLOT, buildShinyToggle(data, unlocked));
        return inv;
    }

    /**
     * Only an item this plugin rolled carries a roll name AND a rarity,
     * which is what makes it convertible. Anything else - the Starforge,
     * the Farmer's Hoe, armor, a stack of dirt - is not a drop.
     */
    public static boolean isDrop(SolRNGPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getItemMeta() == null) return false;
        if (plugin.getStarforgeManager().isStarforge(stack)) return false;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.getRollListener().getRollNameKey(), org.bukkit.persistence.PersistentDataType.STRING)
                && pdc.has(plugin.getRollListener().getRarityKey(), org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static ItemStack buildConvert() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, "Convert"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GREEN, "Banks every drop in the top three"),
                Lore.line(ChatColor.GREEN, "rows as a stored drop of its rarity."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to convert"));
        item.setItemMeta(meta);
        return item;
    }

    /** Everything rolled in the player's inventory, counted so the button says what it will take. */
    private static ItemStack buildConvertAll(SolRNGPlugin plugin, Player player) {
        long drops = 0L;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isDrop(plugin, stack) && !plugin.getRollListener().isShiny(stack)) {
                drops += stack.getAmount();
            }
        }
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, "Convert all"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GREEN, "Banks every rolled drop you carry,"));
        lore.add(Lore.line(ChatColor.GREEN, "without dragging them up first."));
        lore.add(Lore.stat(ChatColor.AQUA, "In your inventory", String.format("%,d", drops)));
        lore.add(Lore.line(ChatColor.AQUA, "Shinies are left where they are."));
        lore.add("");
        lore.add(drops > 0
                ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to convert all"
                : ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Nothing to convert");
        meta.setLore(lore);
        if (drops > 0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildAutoToggle(SolRNGPlugin plugin, PlayerData data, Rarity rarity, boolean unlocked) {
        String name = plugin.getRarityManager().style(rarity, rarity.displayName());
        if (!unlocked) {
            ItemStack item = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name + ChatColor.GRAY + " auto convert");
            meta.setLore(List.of(
                    ChatColor.RED + "" + ChatColor.BOLD + "Locked",
                    Lore.line(ChatColor.RED, "Unlock Auto Convert in /skilltree")));
            item.setItemMeta(meta);
            return item;
        }
        boolean on = data.isAutoConverting(rarity);
        // A chest minecart rather than the hopper it was until V186: a
        // hopper is the block every other plugin uses for a sorting
        // machine, and this switch is about a drop leaving on its own.
        ItemStack item = new ItemStack(Material.CHEST_MINECART);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name + ChatColor.GRAY + " auto convert: "
                + (on ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));
        meta.setLore(List.of(
                Lore.line(ChatColor.AQUA, "Every " + rarity.displayName() + " you roll goes"),
                Lore.line(ChatColor.AQUA, "straight to your vault."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + (on ? "Click to turn off" : "Click to turn on")));
        if (on) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The shiny switch, kept away from the rarity row on purpose. A shiny
     * is a rare find; having it swallowed by a switch somebody set for the
     * common version of the same drop would be the worst thing this menu
     * could do, so it only ever answers to this button.
     */
    private static ItemStack buildShinyToggle(PlayerData data, boolean unlocked) {
        boolean on = data.isAutoConvertShiny();
        ItemStack item = new ItemStack(unlocked ? Material.PRISMARINE_CRYSTALS : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + Lore.SPARK + " Shiny auto convert"
                + (unlocked ? ChatColor.GRAY + ": " + (on ? ChatColor.GREEN + "On" : ChatColor.RED + "Off") : ""));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.AQUA, "The rarity switches never touch"));
        lore.add(Lore.line(ChatColor.AQUA, "a shiny. Only this one does."));
        lore.add("");
        if (unlocked) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + (on ? "Click to turn off" : "Click to turn on"));
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.RED, "Unlock Auto Convert in /skilltree"));
        }
        meta.setLore(lore);
        if (unlocked && on) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /** What the player has banked, per rarity, with the vault's size. */
    private static ItemStack buildStoredPanel(SolRNGPlugin plugin, PlayerData data) {
        ItemStack panel = new ItemStack(Material.CHEST);
        ItemMeta meta = panel.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your vault"));

        long cap = plugin.convertCap(data);
        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GOLD, "Stored drops"));
        for (Rarity rarity : Rarity.values()) {
            long shiny = data.getBankedShiny(rarity);
            lore.add(plugin.getRarityManager().style(rarity, Lore.BULLET + " " + rarity.displayName())
                    + ChatColor.GRAY + ": " + ChatColor.WHITE + String.format("%,d", data.getBankedDrops(rarity))
                    + ChatColor.DARK_GRAY + "/" + String.format("%,d", cap)
                    + (shiny > 0 ? "  " + ChatColor.AQUA + Lore.SPARK + " " + shiny : ""));
        }
        lore.add("");
        lore.add(Lore.line(ChatColor.AQUA, "Spend them in /armor and /starforge."));
        lore.add(Lore.line(ChatColor.AQUA, "Vault Space in /skilltree holds more."));
        meta.setLore(lore);
        panel.setItemMeta(meta);
        return panel;
    }
}
