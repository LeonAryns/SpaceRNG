package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;
import net.milkbowl.vault.economy.Economy;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A 6x9 skill tree, drawn entirely from config. Every node declares its own
 * (column, row) and icon, so adding a skill — or a whole second tree, like
 * the farming one — is a config change rather than a code change.
 *
 * Slots in the tree's shape that no node has claimed render as "???"
 * placeholders. That's deliberate: the outline of everything still to come
 * is visible from the first time a player opens the menu, which makes the
 * tree feel like a map rather than a list that grows.
 */
public class SkillTreeGui {

    /**
     * Slots that are part of a tree's shape regardless of what's in them.
     * The whole of row 1 between the corners is held for future skills, as
     * is the left and right column of the branch region.
     */
    private static final int[] RESERVED = {
            1, 2, 3, 4, 5, 6, 7,   // (2,1) through (8,1)
            10,                    // (2,2)
            16, 25, 34,            // (8,2) (8,3) (8,4)
            19, 28, 37             // (2,3) (2,4) (2,5)
    };

    private static final int STATS_SLOT = 53;
    private static final int PREV_SLOT = 0;
    private static final int NEXT_SLOT = 8;

    public static NamespacedKey nodeIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_node_id");
    }

    public static int prevSlot() {
        return PREV_SLOT;
    }

    public static int nextSlot() {
        return NEXT_SLOT;
    }

    /** Total pages a tree has. Page 0 is the live layout. */
    public static int pageCount() {
        return 2;
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        return build(plugin, player, "skilltree", 0);
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, String tree, int page) {
        boolean farming = "farmtree".equals(tree);
        page = Math.max(0, Math.min(page, pageCount() - 1));

        SkillTreeHolder holder = new SkillTreeHolder();
        holder.setTree(tree);
        holder.setPage(page);

        String title = (farming
                ? ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Farming Skills"
                : ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Skill Tree")
                + (page > 0 ? ChatColor.GRAY + " — Page " + (page + 1) : "");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        SkillTreeManager manager = plugin.getSkillTreeManager();

        ItemStack filler = glassFiller();
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        Map<String, SkillNode> nodes = manager.getNodes(tree);

        // The tree's silhouette is the reserved slots plus wherever this
        // tree's own nodes sit. Page 2 draws that exact silhouette with
        // nothing in it, so every page is the same shape and later pages
        // are visibly room to grow rather than a different menu.
        Set<Integer> shape = new HashSet<>();
        for (int slot : RESERVED) shape.add(slot);
        for (SkillNode node : nodes.values()) {
            if (node.getSlot() >= 0 && node.getSlot() < 54) shape.add(node.getSlot());
        }

        Set<Integer> placed = new HashSet<>();
        if (page == 0) {
            for (SkillNode node : nodes.values()) {
                if (node.getSlot() < 0 || node.getSlot() >= 54) continue;
                boolean reqMet = manager.requirementMet(data, node);
                inv.setItem(node.getSlot(), reqMet
                        ? buildNodeIcon(plugin, player, data, node)
                        : placeholderNode());
                placed.add(node.getSlot());
            }
        }

        for (int slot : shape) {
            if (!placed.contains(slot)) {
                inv.setItem(slot, placeholderNode());
            }
        }

        inv.setItem(STATS_SLOT, buildMoneyPanel(player));
        if (page > 0) {
            inv.setItem(PREV_SLOT, pageButton(Material.SPECTRAL_ARROW, "◀ Previous", page, pageCount()));
        }
        if (page < pageCount() - 1) {
            inv.setItem(NEXT_SLOT, pageButton(Material.ARROW, "Next ▶", page + 2, pageCount()));
        }
        return inv;
    }

    private static ItemStack pageButton(Material material, String label, int shownPage, int total) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + label);
        meta.setLore(List.of(ChatColor.GRAY + "Page " + shownPage + "/" + total));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildNodeIcon(SolRNGPlugin plugin, Player player, PlayerData data, SkillNode node) {
        boolean leveled = node.getMaxLevel() > 1;
        int level = leveled ? data.getNodeLevel(node.getId()) : 0;
        boolean maxed = leveled && level >= node.getMaxLevel();
        boolean started = leveled ? level > 0 : data.hasUnlocked(node.getId());
        boolean complete = leveled ? maxed : started;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + (node.getTree().equals("farmtree") ? "FARMING SKILL" : "SKILL"));
        lore.add("");
        lore.add(describeEffect(node, level));
        lore.add("");
        if (leveled) {
            lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Level: " + ChatColor.AQUA + level
                    + ChatColor.GRAY + "/" + ChatColor.AQUA + node.getMaxLevel());
        }
        if (!complete) {
            double price = plugin.getSkillTreeManager().priceFor(data, node);
            boolean affordable = plugin.getSkillTreeManager().canAfford(player, data, node);
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + "▎ " + ChatColor.GRAY + "Price: "
                    + (affordable ? ChatColor.DARK_GREEN : ChatColor.RED) + "$" + String.format("%,.0f", price));
            lore.add(ChatColor.DARK_GRAY + "▎ You have " + ChatColor.DARK_GREEN + formatMoney(player));
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO BUY"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH MONEY");
        } else {
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + (leveled ? "MAXED" : "UNLOCKED"));
        }

        Material material = Material.matchMaterial(node.getIcon());
        if (material == null) material = Material.RECOVERY_COMPASS;

        ChatColor nameColor = complete ? ChatColor.GREEN : started ? ChatColor.YELLOW : ChatColor.RED;

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(nameColor + "" + ChatColor.BOLD + node.getDisplay().toUpperCase());
        // Glint instead of a colour-coded dye, so the skill keeps its own
        // icon while still reading as "done" at a glance.
        meta.setEnchantmentGlintOverride(complete ? Boolean.TRUE : null);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(nodeIdKey(plugin), PersistentDataType.STRING, node.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * Undefined, unclickable reserved slot — no PersistentData tag, so
     * clicking it is a no-op in GuiListener.
     */
    private static ItemStack placeholderNode() {
        ItemStack icon = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "???");
        meta.setLore(List.of(ChatColor.DARK_GRAY + "Reserved for a future skill."));
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
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "MONEY");
        meta.setLore(List.of(
                ChatColor.DARK_GRAY + "WALLET",
                "",
                ChatColor.DARK_GREEN + "▎ " + ChatColor.DARK_GREEN + formatMoney(player)));
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
        String target = node.getTarget() == null ? "" : prettify(node.getTarget());
        return switch (node.getEffect()) {
            case LUCK -> leveled
                    ? ChatColor.GREEN + "▎ +" + pct(node.getValue()) + "% Luck per level "
                        + ChatColor.GRAY + "(+" + pct(node.getValue() * level) + "% now)"
                    : ChatColor.GREEN + "▎ +" + pct(node.getValue()) + "% Luck";
            case ROLL_SPEED -> leveled
                    ? ChatColor.YELLOW + "▎ +" + pct(node.getValue()) + " Speed per level "
                        + ChatColor.GRAY + "(+" + pct(node.getValue() * level) + " now)"
                    : ChatColor.YELLOW + "▎ +" + pct(node.getValue()) + " Speed";
            case UNLOCK_AUTO_CONVERT -> ChatColor.AQUA + "▎ Unlocks auto-convert in /convert";
            case UNLOCK_FARMING -> ChatColor.AQUA + "▎ Unlocks the farm and the Farmer's Hoe";
            case UNLOCK_ARMOR -> ChatColor.AQUA + "▎ Unlocks the /armor shop";
            case UNLOCK_POTION -> ChatColor.AQUA + "▎ Unlocks the Potion system (coming soon)";
            case UNLOCK_SHINY -> ChatColor.AQUA + "▎ Unlocks Shiny drops (coming soon)";
            case UNLOCK_INDEX_LUCK -> ChatColor.AQUA + "▎ Lets you equip a tag for its index Luck";
            case AUTO_ROLL -> ChatColor.AQUA + "▎ Rolls automatically at your own speed";
            case BONUS_ROLL_CHANCE -> ChatColor.AQUA + "▎ +" + pct(node.getValue()) + "% chance of a free roll";
            case UNLOCK_CROP -> ChatColor.GREEN + "▎ Unlocks " + target + " on the farm";
            case UNLOCK_SHARDS -> ChatColor.AQUA + "▎ Farm crops start paying Shards";
            case UNLOCK_ENCHANT -> ChatColor.LIGHT_PURPLE + "▎ Unlocks the " + target + " hoe enchant";
            case ENCHANT_POWER -> leveled
                    ? ChatColor.LIGHT_PURPLE + "▎ +1 " + target + " level per rank "
                        + ChatColor.GRAY + "(+" + level + " now)"
                    : ChatColor.LIGHT_PURPLE + "▎ +1 " + target + " level";
            case TOKEN_MULTIPLIER -> leveled
                    ? ChatColor.YELLOW + "▎ +" + String.format("%.2f", node.getValue()) + "x Tokens per level "
                        + ChatColor.GRAY + "(+" + String.format("%.2f", node.getValue() * level) + "x now)"
                    : ChatColor.YELLOW + "▎ +" + String.format("%.2f", node.getValue()) + "x farm Tokens";
            case FARM_SPEED -> leveled
                    ? ChatColor.GREEN + "▎ " + pct(node.getValue()) + "% faster regrow per level "
                        + ChatColor.GRAY + "(" + pct(node.getValue() * level) + "% now)"
                    : ChatColor.GREEN + "▎ " + pct(node.getValue()) + "% faster regrow";
        };
    }

    /** "token_greed" -> "Token Greed", for lore that names a target. */
    private static String prettify(String raw) {
        StringBuilder out = new StringBuilder();
        for (String word : raw.toLowerCase().split("[_\\s]+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static int pct(double fraction) {
        return (int) Math.round(fraction * 100);
    }
}
