package com.spacerng.solrng.crate;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a crate can give, and the real chance of each.
 *
 * Most likely first, reading left to right and top to bottom, so the eye
 * lands on what you will usually get and has to travel to the jackpots.
 * Every chance is computed from the weights, never typed into config, so
 * the preview cannot drift from what the crate actually does.
 */
public final class CratePreviewGui {

    private static final int KEY_SLOT = 4;
    private static final int[] REWARD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
    };

    private CratePreviewGui() {
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, Crate crate) {
        CrateManager manager = plugin.getCrateManager();
        CratePreviewHolder holder = new CratePreviewHolder();
        String[] stops = crate.colors() == null || crate.colors().isEmpty()
                ? new String[]{"#FFE082", "#FF8F00"} : crate.colors().toArray(new String[0]);
        Inventory inv = Bukkit.createInventory(holder, 54,
                com.spacerng.solrng.gui.MenuStyle.title(crate.display(), stops));
        holder.setInventory(inv);

        // V239: the shared look, the inside left empty so the rewards stand
        // on their own the way Leon's reference crate does.
        com.spacerng.solrng.gui.MenuStyle.frame(inv, com.spacerng.solrng.gui.MenuStyle.Palette.PURPLE);
        for (int slot : REWARD_SLOTS) inv.setItem(slot, null);
        com.spacerng.solrng.gui.MenuStyle.close(inv);

        List<CrateReward> rewards = new ArrayList<>(crate.rewards());
        rewards.sort(Comparator.comparingDouble(CrateReward::weight).reversed());
        for (int i = 0; i < rewards.size() && i < REWARD_SLOTS.length; i++) {
            inv.setItem(REWARD_SLOTS[i], manager.icon(crate, rewards.get(i)));
        }

        inv.setItem(KEY_SLOT, keyPanel(manager, player, crate));
        return inv;
    }

    private static ItemStack keyPanel(CrateManager manager, Player player, Crate crate) {
        int keys = manager.keysHeld(player, crate);

        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(manager.keyName(crate));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.stat(keys > 0 ? ChatColor.GREEN : ChatColor.RED, "Keys held", String.valueOf(keys)));
        if (!crate.keySource().isEmpty()) {
            lore.add(Lore.footnote(crate.keySource()));
        }
        lore.add("");
        if (keys > 0) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click the crate to open one");
            if (keys > 1) {
                lore.add(ChatColor.YELLOW + Lore.BULLET + " " + ChatColor.GRAY + "Shift click opens all "
                        + Math.min(keys, manager.quickOpenMax()) + " of them");
            }
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "No keys yet");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
