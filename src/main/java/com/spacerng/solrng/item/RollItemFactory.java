package com.spacerng.solrng.item;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Builds the special "Roll" item players right-click to roll. Shared by the
 * /rngcore give command and the automatic first-join starter kit so both
 * hand out an identical item.
 */
public final class RollItemFactory {

    private RollItemFactory() {
    }

    public static ItemStack create(SolRNGPlugin plugin, int amount) {
        String materialName = plugin.getConfig().getString("roll-item.material", "NETHER_STAR");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.NETHER_STAR;

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("roll-item.name", "&d&lRoll")));
        meta.setLore(List.of(
                ChatColor.GRAY + "Right-click to roll!",
                ChatColor.GRAY + "Takes a few seconds to resolve."
        ));
        item.setItemMeta(meta);
        return item;
    }
}
