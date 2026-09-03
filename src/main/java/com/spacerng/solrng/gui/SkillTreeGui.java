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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 6x9 skill tree: "Auto Roll" is the only real node right now (root, at
 * the bottom-middle) — everything else was stripped out on request so it
 * can be redesigned from scratch. The row directly above the root is
 * filled with decorative "???" placeholders (not real nodes, just visual
 * reserved slots) connecting left and right of center. Bottom-right
 * corner shows the player's Money balance.
 */
public class SkillTreeGui {

    // row*9 + col
    private static final Map<String, Integer> SLOT_BY_ID = Map.of("auto_roll_root", 49);
    // Row directly above the root (row 4), spanning the full width.
    private static final int PLACEHOLDER_ROW_START = 36;
    private static final int PLACEHOLDER_ROW_END = 44;

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

        for (int slot = PLACEHOLDER_ROW_START; slot <= PLACEHOLDER_ROW_END; slot++) {
            inv.setItem(slot, placeholderNode());
        }

        for (Map.Entry<String, SkillNode> entry : orderedNodes(tree).entrySet()) {
            SkillNode node = entry.getValue();
            Integer slot = SLOT_BY_ID.get(node.getId());
            if (slot == null) continue; // unknown node id — no fixed spot for it

            boolean unlocked = data.hasUnlocked(node.getId());
            boolean reqMet = node.getRequires() == null || data.hasUnlocked(node.getRequires());

            ItemStack icon;
            if (!reqMet) {
                icon = placeholderNode();
            } else {
                List<String> lore = new ArrayList<>();
                boolean canAfford = tree.canAfford(player, node, rarityKey);

                if (unlocked) {
                    lore.add(ChatColor.GREEN + "Unlocked");
                } else {
                    lore.add(ChatColor.GRAY + "Price:");
                    for (Map.Entry<Rarity, Long> cost : node.getCosts().entrySet()) {
                        String color = plugin.getRarityManager().colorFor(cost.getKey());
                        long held = countHeld(player, rarityKey, cost.getKey());
                        lore.add(ChatColor.GRAY + " - " + color + cost.getValue() + " " + cost.getKey().displayName()
                                + ChatColor.DARK_GRAY + " (" + held + ")");
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

        inv.setItem(STATS_SLOT, buildMoneyPanel(player));

        return inv;
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
        return String.format("%.0f", registration.getProvider().getBalance(player));
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
            case LUCK -> ChatColor.DARK_AQUA + "+" + (int) Math.round(node.getValue() * 100) + "% Luck";
            case UNLOCK_AUTO_CONVERT -> ChatColor.DARK_AQUA + "Unlocks auto-convert toggles in /convert";
            case AUTO_ROLL -> ChatColor.DARK_AQUA + "Auto-rolls every " + (int) node.getValue() + "s";
            case ROLL_SPEED -> ChatColor.DARK_AQUA + "+" + node.getValue() + " roll speed";
            case BONUS_ROLL_CHANCE -> ChatColor.DARK_AQUA + "+" + (int) (node.getValue() * 100) + "% chance for a free bonus roll";
        };
    }
}
