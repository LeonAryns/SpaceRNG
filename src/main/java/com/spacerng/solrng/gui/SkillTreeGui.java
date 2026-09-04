package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;
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
 * 6x9 skill tree. Slots are given as (column, row), both 1-indexed, which
 * is how the layout gets specified — slot = (row-1)*9 + (column-1):
 * <pre>
 * (5,2) potion_unlock   13
 * (5,3) armor_unlock    22
 * (5,4) farming_unlock  31
 * (4,5) speed_skill     39   (5,5) luck_skill 40
 * (6,5) auto_convert    41   (8,5) shiny_unlock 43
 * (5,6) auto_roll_root  49   <- the root
 * </pre>
 * Slots between the real nodes render as decorative "???" placeholders so
 * the tree reads as a connected path with room to grow. Bottom-right
 * corner shows Money.
 */
public class SkillTreeGui {

    private static final Map<String, Integer> SLOT_BY_ID = Map.of(
            "auto_roll_root", 49,   // (5,6)
            "luck_skill", 40,       // (5,5)
            "speed_skill", 39,      // (4,5)
            "auto_convert", 41,     // (6,5)
            "shiny_unlock", 43,     // (8,5)
            "farming_unlock", 31,   // (5,4)
            "armor_unlock", 22,     // (5,3)
            "potion_unlock", 13     // (5,2)
    );

    // Slots that belong to the tree's visual structure. Anything here not
    // claimed by a real node above renders as a "???" placeholder, which
    // is what visually joins the nodes into a path.
    private static final int[] BRANCH_REGION_SLOTS = {
            13, 22, 31,
            38, 39, 40, 41, 42, 43
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

            boolean reqMet = tree.requirementMet(data, node);
            ItemStack icon = reqMet ? buildNodeIcon(plugin, player, data, node) : placeholderNode();
            inv.setItem(entry.getValue(), icon);
        }

        inv.setItem(STATS_SLOT, buildMoneyPanel(player));

        return inv;
    }

    private static ItemStack buildNodeIcon(SolRNGPlugin plugin, Player player, PlayerData data, SkillNode node) {
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
            double price = plugin.getSkillTreeManager().priceFor(data, node);
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + "$" + String.format("%,.0f", price)
                    + ChatColor.DARK_GRAY + " (" + formatMoney(player) + ")");
        } else {
            lore.add(ChatColor.GREEN + (leveled ? "Maxed!" : "Unlocked"));
        }
        lore.add("");
        lore.add(describeEffect(node, level));

        // A leveled node only goes green once it's actually finished —
        // part-done reads as "still work to do" rather than complete.
        Material material;
        ChatColor nameColor;
        if (complete) {
            material = Material.LIME_DYE;
            nameColor = ChatColor.GREEN;
        } else if (leveled) {
            material = Material.RECOVERY_COMPASS;
            nameColor = started ? ChatColor.YELLOW : ChatColor.RED;
        } else {
            material = Material.RED_DYE;
            nameColor = ChatColor.RED;
        }

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
        return "$" + String.format("%,.0f", registration.getProvider().getBalance(player));
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
            case UNLOCK_SHINY -> ChatColor.DARK_AQUA + "Unlocks Shiny drops (coming soon)";
            case AUTO_ROLL -> ChatColor.DARK_AQUA + "Rolls automatically at your own roll speed";
            case ROLL_SPEED -> leveled
                    ? ChatColor.DARK_AQUA + "+" + pct(node.getValue()) + " Speed per level "
                        + ChatColor.GRAY + "(+" + pct(node.getValue() * level) + " so far)"
                    : ChatColor.DARK_AQUA + "+" + pct(node.getValue()) + " Speed";
            case BONUS_ROLL_CHANCE -> ChatColor.DARK_AQUA + "+" + pct(node.getValue()) + "% chance for a free bonus roll";
        };
    }

    private static int pct(double fraction) {
        return (int) Math.round(fraction * 100);
    }
}
