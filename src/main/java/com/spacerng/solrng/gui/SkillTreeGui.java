package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;
import com.spacerng.solrng.rarity.Rarity;
import net.milkbowl.vault.economy.Economy;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 6x9 skill tree. Layout (row*9+col, root at the bottom-middle):
 * <pre>
 * row1:  ???(10)         farming_unlock(16)
 * row2:  luck_skill(19)  potion/armor col   armor_unlock(25)
 * row3:  speed_skill(28)                    potion_unlock(34)
 * row4:  37  38  39  luck_gate(40)  41  42  43   <- connector row, full width
 * row5:                 auto_roll_root(49)
 * </pre>
 * The row-4 connector fills columns 1-7 solid so the three branches visibly
 * merge into one path above the root. Everything else in the branch region
 * not claimed by a real node is a decorative "???" placeholder — reserved
 * for whatever gets designed later. Bottom-right corner shows Money.
 */
public class SkillTreeGui {

    private static final Map<String, Integer> SLOT_BY_ID = Map.of(
            "auto_roll_root", 49,
            "luck_gate", 40,
            "farming_unlock", 16,
            "armor_unlock", 25,
            "potion_unlock", 34,
            "luck_skill", 19,
            "speed_skill", 28
    );

    // Every slot that's part of the tree's visual branch structure: three
    // vertical columns (rows 1-3) plus the row-4 connector spanning the
    // full width between them. Anything here not claimed by a real node
    // above renders as a decorative "???" placeholder.
    private static final int[] BRANCH_REGION_SLOTS = {
            10, 13, 16,
            19, 22, 25,
            28, 31, 34,
            37, 38, 39, 40, 41, 42, 43
    };

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
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        ItemStack filler = glassFiller();
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        Set<Integer> realNodeSlots = new HashSet<>(SLOT_BY_ID.values());
        for (int slot : BRANCH_REGION_SLOTS) {
            if (!realNodeSlots.contains(slot)) {
                inv.setItem(slot, placeholderNode());
            }
        }

        for (Map.Entry<String, Integer> entry : SLOT_BY_ID.entrySet()) {
            SkillNode node = tree.get(entry.getKey());
            if (node == null) continue; // config doesn't define this id — leave the filler glass

            boolean reqMet = node.getRequires() == null || data.hasUnlocked(node.getRequires());
            ItemStack icon = reqMet ? buildNodeIcon(plugin, player, data, node, rarityKey) : placeholderNode();
            inv.setItem(entry.getValue(), icon);
        }

        inv.setItem(STATS_SLOT, buildMoneyPanel(player));

        return inv;
    }

    private static ItemStack buildNodeIcon(SolRNGPlugin plugin, Player player, PlayerData data, SkillNode node, NamespacedKey rarityKey) {
        boolean leveled = node.getMaxLevel() > 1;
        int level = leveled ? data.getNodeLevel(node.getId()) : 0;
        boolean maxed = leveled && level >= node.getMaxLevel();
        boolean started = leveled ? level > 0 : data.hasUnlocked(node.getId());
        boolean complete = leveled ? maxed : started;
        boolean canBuyMore = !complete;

        List<String> lore = new ArrayList<>();
        if (leveled) {
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.AQUA + level + ChatColor.GRAY + "/" + node.getMaxLevel());
        }
        if (canBuyMore) {
            lore.add(ChatColor.GRAY + "Price:");
            for (Map.Entry<Rarity, Long> cost : node.getCosts().entrySet()) {
                long held = countHeld(player, rarityKey, cost.getKey());
                String costText = cost.getValue() + " " + cost.getKey().displayName();
                lore.add(ChatColor.GRAY + " - " + plugin.getRarityManager().style(cost.getKey(), costText)
                        + ChatColor.DARK_GRAY + " (" + held + ")");
            }
        } else {
            lore.add(ChatColor.GREEN + (leveled ? "Maxed!" : "Unlocked"));
        }
        lore.add("");
        lore.add(describeEffect(node, level));

        Material material = complete ? Material.LIME_DYE : (started ? Material.YELLOW_DYE : Material.RED_DYE);
        ChatColor nameColor = complete ? ChatColor.GREEN : (started ? ChatColor.YELLOW : ChatColor.RED);

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(nameColor + node.getDisplay());
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(nodeIdKey(plugin), PersistentDataType.STRING, node.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * Undefined, unclickable reserved slot — no PersistentData tag, so
     * clicking it is a no-op in GuiListener. Same "???" look as a locked
     * node so the whole row reads as "more coming later".
     */
    private static ItemStack placeholderNode() {
        ItemStack icon = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "???");
        meta.setLore(List.of(ChatColor.GRAY + "Reserved for a future skill."));
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack glassFiller() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildMoneyPanel(Player player) {
        ItemStack stats = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stats.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Money");
        meta.setLore(List.of(ChatColor.GREEN + formatMoney(player)));
        stats.setItemMeta(meta);
        return stats;
    }

    private static String formatMoney(Player player) {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) return "N/A";
        return "$" + String.format("%.0f", registration.getProvider().getBalance(player));
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

    private static String describeEffect(SkillNode node, int level) {
        boolean leveled = node.getMaxLevel() > 1;
        return switch (node.getEffect()) {
            case LUCK -> leveled
                    ? ChatColor.DARK_AQUA + "+" + pct(node.getValue()) + "% Luck per level "
                        + ChatColor.GRAY + "(+" + pct(node.getValue() * level) + "% so far)"
                    : ChatColor.DARK_AQUA + "+" + pct(node.getValue()) + "% Luck";
            case UNLOCK_AUTO_CONVERT -> ChatColor.DARK_AQUA + "Unlocks auto-convert toggles in /convert";
            case UNLOCK_FARMING -> ChatColor.DARK_AQUA + "Unlocks farming crops for Tokens";
            case UNLOCK_ARMOR -> ChatColor.DARK_AQUA + "Unlocks the /armor shop";
            case UNLOCK_POTION -> ChatColor.DARK_AQUA + "Unlocks the Potion system (coming soon)";
            case AUTO_ROLL -> ChatColor.DARK_AQUA + "Auto-rolls every " + (int) node.getValue() + "s";
            case ROLL_SPEED -> leveled
                    ? ChatColor.DARK_AQUA + "+" + pct(node.getValue()) + "% Roll Speed per level "
                        + ChatColor.GRAY + "(+" + pct(node.getValue() * level) + "% so far)"
                    : ChatColor.DARK_AQUA + "+" + node.getValue() + " roll speed";
            case BONUS_ROLL_CHANCE -> ChatColor.DARK_AQUA + "+" + pct(node.getValue()) + "% chance for a free bonus roll";
        };
    }

    private static int pct(double fraction) {
        return (int) Math.round(fraction * 100);
    }
}
