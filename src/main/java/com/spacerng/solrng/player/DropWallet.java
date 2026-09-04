package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * One place that answers "how many drops of rarity X can this player
 * spend?" — physical rolled items sitting in their inventory plus the
 * virtual drops they've banked through /convert. Shops spend the physical
 * items first and only fall back to the bank, so converting is never a
 * downgrade: a banked Common is worth exactly the same as one in hand.
 */
public final class DropWallet {

    private DropWallet() {
    }

    private static NamespacedKey rarityKey(SolRNGPlugin plugin) {
        return plugin.getRollListener().getRarityKey();
    }

    /** Rolled items of this rarity physically in the player's inventory. */
    public static long inInventory(SolRNGPlugin plugin, Player player, Rarity rarity) {
        NamespacedKey key = rarityKey(plugin);
        long total = 0L;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String name = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (rarity.name().equals(name)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Everything spendable: inventory + bank. */
    public static long total(SolRNGPlugin plugin, Player player, PlayerData data, Rarity rarity) {
        return inInventory(plugin, player, rarity) + data.getBankedDrops(rarity);
    }

    /**
     * Takes {@code amount} drops, inventory first then the bank. Callers
     * are expected to have checked {@link #total} first; if there really
     * isn't enough, it takes what's there and returns false.
     */
    public static boolean spend(SolRNGPlugin plugin, Player player, PlayerData data, Rarity rarity, long amount) {
        long remaining = amount - consumeFromInventory(plugin, player, rarity, amount);
        if (remaining <= 0) return true;

        remaining -= data.takeBankedDrops(rarity, remaining);
        return remaining <= 0;
    }

    private static long consumeFromInventory(SolRNGPlugin plugin, Player player, Rarity rarity, long amount) {
        NamespacedKey key = rarityKey(plugin);
        ItemStack[] contents = player.getInventory().getStorageContents();
        long remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getItemMeta() == null) continue;
            String name = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (!rarity.name().equals(name)) continue;

            long take = Math.min(remaining, stack.getAmount());
            stack.setAmount((int) (stack.getAmount() - take));
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        return amount - remaining;
    }
}
