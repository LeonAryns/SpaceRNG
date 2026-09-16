package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraConcepts;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /aura: pick the aura you wear. The plain look of a rarity comes with
 * finding any drop of it, the shiny look with finding a shiny of it, and
 * wearing any of them needs a linked Discord.
 *
 * Picking nothing means the aura follows whichever tag is equipped, which
 * is how it worked before this menu existed.
 */
public class AuraGui {

    private static final int SIZE = 45;
    private static final int INFO_SLOT = 4;
    private static final int[] PLAIN_SLOTS = {19, 21, 23, 25};
    private static final int[] SHINY_SLOTS = {28, 30, 32, 34};
    private static final int FOLLOW_SLOT = 40;
    private static final Rarity[] SHOWN = {Rarity.EPIC, Rarity.LEGENDARY, Rarity.MYTHICAL, Rarity.DIVINE};

    public static NamespacedKey choiceKey() {
        return SolRNGPlugin.key("solrng_aura_choice");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        AuraHolder holder = new AuraHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Auras");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ItemStack rail = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || i >= 36 ? rail : filler);

        inv.setItem(INFO_SLOT, infoIcon(plugin, data));
        for (int i = 0; i < SHOWN.length; i++) {
            inv.setItem(PLAIN_SLOTS[i], auraIcon(plugin, data, SHOWN[i], false));
            inv.setItem(SHINY_SLOTS[i], auraIcon(plugin, data, SHOWN[i], true));
        }
        inv.setItem(FOLLOW_SLOT, followIcon(data));
        return inv;
    }

    private static ItemStack infoIcon(SolRNGPlugin plugin, PlayerData data) {
        boolean linked = plugin.getRankManager().rankOf(data) != null;
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your aura"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "An aura is worn around you, for"));
        lore.add(Lore.line(ChatColor.GRAY, "everyone to see."));
        lore.add("");
        String choice = data.getAuraChoice();
        lore.add(Lore.stat(ChatColor.AQUA, "Wearing",
                choice == null || choice.isEmpty() ? "whatever your tag gives" : label(choice)));
        lore.add(Lore.stat(linked ? ChatColor.GREEN : ChatColor.RED, "Discord",
                linked ? "linked" : "not linked"));
        lore.add("");
        if (linked) {
            lore.add(Lore.footnote("Switch it off entirely in /options."));
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Link your Discord with /discord link."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack auraIcon(SolRNGPlugin plugin, PlayerData data, Rarity rarity, boolean shiny) {
        var auras = plugin.getAuraManager();
        String choice = rarity.name() + (shiny ? ":shiny" : "");
        String[] look = shiny ? auras.shinyLookFor(rarity) : auras.lookFor(rarity);
        boolean owned = auras.owns(data, choice);
        boolean linked = plugin.getRankManager().rankOf(data) != null;
        boolean worn = choice.equalsIgnoreCase(data.getAuraChoice());

        ItemStack item = new ItemStack(owned ? material(rarity) : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        String name = plugin.getRarityManager().styleBold(rarity, rarity.displayName() + (shiny ? " Shiny" : ""));
        meta.setDisplayName(owned ? name : ChatColor.DARK_GRAY + "???");

        List<String> lore = new ArrayList<>();
        if (look == null) {
            lore.add(Lore.line(ChatColor.DARK_GRAY, "No look is set for this one yet."));
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }
        lore.add(Lore.stat(ChatColor.AQUA, "Look", look[0]));
        String description = AuraConcepts.DESCRIPTIONS.get(look[0]);
        if (description != null) lore.add(Lore.line(ChatColor.GRAY, description));
        if (!look[1].equalsIgnoreCase("none")) {
            lore.add(Lore.stat(ChatColor.AQUA, "Accent", look[1]));
        }
        lore.add("");
        if (!owned) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, shiny
                    ? "Roll a shiny " + ChatColor.stripColor(rarity.displayName()) + " to unlock it."
                    : "Roll any " + ChatColor.stripColor(rarity.displayName()) + " to unlock it."));
        } else if (!linked) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Link your Discord with /discord link."));
        } else if (worn) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Worn");
            lore.add(Lore.footnote("Click to go back to your tag."));
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to wear");
        }
        meta.setLore(lore);
        if (worn || shiny) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(choiceKey(), PersistentDataType.STRING, choice);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack followIcon(PlayerData data) {
        boolean on = data.getAuraChoice() == null || data.getAuraChoice().isEmpty();
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(on ? ChatColor.GREEN : ChatColor.GRAY, "Follow my tag"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Wear the aura of whichever tag"),
                Lore.line(ChatColor.GRAY, "you have equipped in /index."),
                "",
                on ? ChatColor.GREEN + "" + ChatColor.BOLD + "On"
                        : ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to switch on"));
        if (on) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(choiceKey(), PersistentDataType.STRING, "");
        item.setItemMeta(meta);
        return item;
    }

    private static String label(String choice) {
        String rarity = choice.split(":")[0];
        String name = rarity.charAt(0) + rarity.substring(1).toLowerCase();
        return choice.endsWith(":shiny") ? name + " shiny" : name;
    }

    private static Material material(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> Material.NETHER_STAR;
            case MYTHICAL -> Material.NETHERITE_INGOT;
            case LEGENDARY -> Material.GLOWSTONE;
            default -> Material.AMETHYST_CLUSTER;
        };
    }

    /** The choice on a clicked item, or null when it carries none. */
    public static String clickedChoice(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(choiceKey(), PersistentDataType.STRING);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
