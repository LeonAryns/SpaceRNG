package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillTreeGui {

    public static NamespacedKey nodeIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_node_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        SkillTreeHolder holder = new SkillTreeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + "Skill Tree");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        SkillTreeManager tree = plugin.getSkillTreeManager();
        NamespacedKey nodeIdKey = nodeIdKey(plugin);

        int slot = 10;
        for (Map.Entry<String, SkillNode> entry : orderedNodes(tree).entrySet()) {
            if (slot > 16 && slot < 19) slot = 19; // wrap to second row after a handful
            SkillNode node = entry.getValue();

            Material material;
            List<String> lore = new ArrayList<>();
            boolean unlocked = data.hasUnlocked(node.getId());
            boolean canAfford = data.getPoints() >= node.getCost();
            boolean reqMet = node.getRequires() == null || data.hasUnlocked(node.getRequires());

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
            slot++;
        }

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "Your Credits: " + data.getPoints());
        infoMeta.setLore(List.of(ChatColor.GRAY + "Luck: +" + String.format("%.2f", data.getBonusLuck())));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        return inv;
    }

    private static Map<String, SkillNode> orderedNodes(SkillTreeManager tree) {
        return new LinkedHashMap<>(tree.getNodes());
    }

    private static String describeEffect(SkillNode node) {
        return switch (node.getEffect()) {
            case LUCK -> ChatColor.DARK_AQUA + "+" + node.getValue() + " Luck";
            case UNLOCK_AUTO_CONVERT -> ChatColor.DARK_AQUA + "Unlocks auto-convert toggles in /convert";
            case AUTO_ROLL -> ChatColor.DARK_AQUA + "Auto-rolls every " + (int) node.getValue() + "s";
        };
    }
}
