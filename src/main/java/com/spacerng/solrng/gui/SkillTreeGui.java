package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 6x9 skill tree: "Auto Roll" is the root at the bottom-middle, branching
 * up into three tracks — Luck Multiplier, Rolling Speed, and Bonus Roll
 * (with Auto-Convert as a small side-branch off Luck Multiplier I). The
 * bottom-right corner shows a running Common/Uncommon conversion summary.
 */
public class SkillTreeGui {

    // row*9 + col
    private static final Map<String, Integer> SLOT_BY_ID = Map.ofEntries(
            Map.entry("auto_roll_root", 49),
            Map.entry("luck_mult_1", 37),
            Map.entry("luck_mult_2", 28),
            Map.entry("luck_mult_3", 19),
            Map.entry("auto_convert", 29),
            Map.entry("rolling_speed_1", 40),
            Map.entry("rolling_speed_2", 31),
            Map.entry("rolling_speed_3", 22),
            Map.entry("bonus_roll_1", 43),
            Map.entry("bonus_roll_2", 34),
            Map.entry("bonus_roll_3", 25)
    );

    public static NamespacedKey nodeIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_node_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        SkillTreeHolder holder = new SkillTreeHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "Skill Tree");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        SkillTreeManager tree = plugin.getSkillTreeManager();
        NamespacedKey nodeIdKey = nodeIdKey(plugin);

        for (Map.Entry<String, SkillNode> entry : orderedNodes(tree).entrySet()) {
            SkillNode node = entry.getValue();
            Integer slot = SLOT_BY_ID.get(node.getId());
            if (slot == null) continue; // unknown node id — no fixed spot for it

            List<String> lore = new ArrayList<>();
            boolean unlocked = data.hasUnlocked(node.getId());
            boolean canAfford = data.getPoints() >= node.getCost();
            boolean reqMet = node.getRequires() == null || data.hasUnlocked(node.getRequires());

            Material material;
            if (unlocked) {
                material = Material.LIME_DYE;
                lore.add(ChatColor.GREEN + "Unlocked");
            } else if (!reqMet) {
                material = Material.GRAY_DYE;
                lore.add(ChatColor.RED + "Requires: " + tree.get(node.getRequires()).getDisplay());
            } else {
                material = canAfford ? Material.YELLOW_DYE : Material.RED_DYE;
                lore.add(ChatColor.GRAY + "Cost: " + node.getCost() + " Credits");
                lore.add(canAfford ? ChatColor.GREEN + "Click to unlock!" : ChatColor.RED + "Not enough Credits");
            }

            lore.add("");
            lore.add(describeEffect(node));

            ItemStack icon = new ItemStack(material);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName((unlocked ? ChatColor.GREEN : ChatColor.AQUA) + node.getDisplay());
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(nodeIdKey, PersistentDataType.STRING, node.getId());
            icon.setItemMeta(meta);

            inv.setItem(slot, icon);
        }

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "Your Credits: " + data.getPoints());
        infoMeta.setLore(List.of(ChatColor.GRAY + "Luck: +" + String.format("%.2f", data.getBonusLuck())));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        inv.setItem(53, buildConversionStats(plugin, player, data));

        return inv;
    }

    /**
     * Bottom-right summary: lifetime Common/Uncommon items converted, plus
     * whatever's currently sitting unconverted in the player's inventory.
     */
    private static ItemStack buildConversionStats(SolRNGPlugin plugin, Player player, PlayerData data) {
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        long heldCommon = countHeld(player, rarityKey, Rarity.COMMON);
        long heldUncommon = countHeld(player, rarityKey, Rarity.UNCOMMON);

        ItemStack stats = new ItemStack(Material.HOPPER);
        ItemMeta meta = stats.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Conversion Stats");
        String commonColor = plugin.getRarityManager().colorFor(Rarity.COMMON);
        String uncommonColor = plugin.getRarityManager().colorFor(Rarity.UNCOMMON);
        meta.setLore(List.of(
                commonColor + "Common: " + ChatColor.WHITE + data.getConvertedCommon() + ChatColor.GRAY + " converted"
                        + ChatColor.WHITE + " + " + heldCommon + ChatColor.GRAY + " held",
                uncommonColor + "Uncommon: " + ChatColor.WHITE + data.getConvertedUncommon() + ChatColor.GRAY + " converted"
                        + ChatColor.WHITE + " + " + heldUncommon + ChatColor.GRAY + " held",
                "",
                ChatColor.GRAY + "Total Common: " + ChatColor.WHITE + (data.getConvertedCommon() + heldCommon),
                ChatColor.GRAY + "Total Uncommon: " + ChatColor.WHITE + (data.getConvertedUncommon() + heldUncommon)
        ));
        stats.setItemMeta(meta);
        return stats;
    }

    private static long countHeld(Player player, NamespacedKey rarityKey, Rarity rarity) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (rarity.name().equals(rarityName)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static Map<String, SkillNode> orderedNodes(SkillTreeManager tree) {
        return new LinkedHashMap<>(tree.getNodes());
    }

    private static String describeEffect(SkillNode node) {
        return switch (node.getEffect()) {
            case LUCK -> ChatColor.DARK_AQUA + "+" + node.getValue() + " Luck";
            case UNLOCK_AUTO_CONVERT -> ChatColor.DARK_AQUA + "Unlocks auto-convert toggles in /convert";
            case AUTO_ROLL -> ChatColor.DARK_AQUA + "Auto-rolls every " + (int) node.getValue() + "s";
            case ROLL_SPEED -> ChatColor.DARK_AQUA + "+" + node.getValue() + " roll speed";
            case BONUS_ROLL_CHANCE -> ChatColor.DARK_AQUA + "+" + (int) (node.getValue() * 100) + "% chance for a free bonus roll";
        };
    }
}
