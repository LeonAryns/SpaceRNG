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
 * bottom-right corner shows a running Common/Uncommon total. Nodes are
 * paid for with rolled drops, not Credits — Credits are the real-money
 * store currency.
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

    private static final int STATS_SLOT = 53;

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
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        ItemStack filler = glassFiller();
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        for (Map.Entry<String, SkillNode> entry : orderedNodes(tree).entrySet()) {
            SkillNode node = entry.getValue();
            Integer slot = SLOT_BY_ID.get(node.getId());
            if (slot == null) continue; // unknown node id — no fixed spot for it

            boolean unlocked = data.hasUnlocked(node.getId());
            boolean reqMet = node.getRequires() == null || data.hasUnlocked(node.getRequires());

            ItemStack icon;
            if (!reqMet) {
                // Mystery node — don't reveal what it does until the
                // previous node in its branch is unlocked.
                icon = new ItemStack(Material.GRAY_DYE);
                ItemMeta meta = icon.getItemMeta();
                meta.setDisplayName(ChatColor.DARK_GRAY + "???");
                meta.setLore(List.of(ChatColor.GRAY + "Unlock the previous skill", ChatColor.GRAY + "to reveal this."));
                icon.setItemMeta(meta);
            } else {
                List<String> lore = new ArrayList<>();
                boolean canAfford = tree.canAfford(player, node, rarityKey);

                if (unlocked) {
                    lore.add(ChatColor.GREEN + "Unlocked");
                } else {
                    lore.add(ChatColor.GRAY + "Cost:");
                    for (Map.Entry<Rarity, Long> cost : node.getCosts().entrySet()) {
                        String color = plugin.getRarityManager().colorFor(cost.getKey());
                        lore.add(ChatColor.GRAY + " - " + color + cost.getValue() + " " + cost.getKey().displayName());
                    }
                    lore.add(canAfford ? ChatColor.GREEN + "Click to unlock!" : ChatColor.RED + "Not enough drops");
                }
                lore.add("");
                lore.add(describeEffect(node));

                icon = new ItemStack(unlocked ? Material.LIME_DYE : Material.RED_DYE);
                ItemMeta meta = icon.getItemMeta();
                meta.setDisplayName((unlocked ? ChatColor.GREEN : ChatColor.RED) + node.getDisplay());
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(nodeIdKey, PersistentDataType.STRING, node.getId());
                icon.setItemMeta(meta);
            }

            inv.setItem(slot, icon);
        }

        inv.setItem(STATS_SLOT, buildConversionStats(plugin, player, data));

        return inv;
    }

    private static ItemStack glassFiller() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    /**
     * Bottom-right summary: Common/Uncommon drops converted or spent on the
     * skill tree, plus whatever's currently sitting in the player's
     * inventory — one combined total per rarity.
     */
    private static ItemStack buildConversionStats(SolRNGPlugin plugin, Player player, PlayerData data) {
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        long totalCommon = data.getConvertedCommon() + countHeld(player, rarityKey, Rarity.COMMON);
        long totalUncommon = data.getConvertedUncommon() + countHeld(player, rarityKey, Rarity.UNCOMMON);

        ItemStack stats = new ItemStack(Material.HOPPER);
        ItemMeta meta = stats.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Drop Totals");
        String commonColor = plugin.getRarityManager().colorFor(Rarity.COMMON);
        String uncommonColor = plugin.getRarityManager().colorFor(Rarity.UNCOMMON);
        meta.setLore(List.of(
                commonColor + "Common: " + ChatColor.WHITE + totalCommon,
                uncommonColor + "Uncommon: " + ChatColor.WHITE + totalUncommon
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
