package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Where an item goes when the plugin hands one over and the inventory is
 * full: the player's /stash, instead of the floor. A stack on the ground at
 * spawn was anyone's to pick up, and several rewards were never dropped at
 * all, just lost. Rolled drops don't come through here; losing one to a
 * full inventory is still the rule for those.
 */
public final class Stash {

    private Stash() {
    }

    /** Gives the items, and stashes whatever doesn't fit. */
    public static void give(SolRNGPlugin plugin, Player player, ItemStack... items) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items);
        if (overflow.isEmpty()) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int count = 0;
        for (ItemStack left : overflow.values()) {
            data.getStash().add(left);
            count += left.getAmount();
        }
        player.sendMessage(ChatColor.YELLOW + "Your inventory is full. " + ChatColor.WHITE + count
                + ChatColor.YELLOW + (count == 1 ? " item" : " items") + " went to "
                + ChatColor.GOLD + "/stash" + ChatColor.YELLOW + ".");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.2f);
    }
}
