package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class TagCommand implements CommandExecutor {

    private static final String INDEX_LUCK_NODE = "index_luck";

    private final SolRNGPlugin plugin;

    public TagCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /tag <equip|clear>");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (args[0].equalsIgnoreCase("clear")) {
            plugin.getTagManager().clearTag(player, data);
            player.sendMessage(ChatColor.YELLOW + "Tag cleared.");
            return true;
        }

        if (args[0].equalsIgnoreCase("equip")) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            ItemMeta meta = hand.getItemMeta();
            if (meta == null) {
                player.sendMessage(ChatColor.RED + "Hold a rolled item in your hand first.");
                return true;
            }

            NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
            NamespacedKey nameKey = plugin.getRollListener().getRollNameKey();
            String rarityName = meta.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            String rollName = meta.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);

            if (rarityName == null || rollName == null) {
                player.sendMessage(ChatColor.RED + "That's not a rolled item — hold one of your RNG rolls and try again.");
                return true;
            }

            equip(plugin, player, data, rollName, rarityName);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /tag <equip|clear>");
        return true;
    }

    /**
     * Equips a tag by item name + rarity, shared by /tag equip (reads a
     * held item) and the /index GUI (reads a clicked collection-log entry).
     */
    public static void equip(SolRNGPlugin plugin, Player player, PlayerData data, String rollName, String rarityName) {
        // The tag IS the index multiplier, so equipping one is gated on the
        // skill that turns that multiplier on. Letting people equip first
        // and quietly get 1.00x reads as a bug rather than a lock.
        if (!data.hasUnlocked(INDEX_LUCK_NODE)) {
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "LOCKED "
                    + ChatColor.RESET + ChatColor.GRAY + "Unlock " + ChatColor.YELLOW + "Index Luck"
                    + ChatColor.GRAY + " in " + ChatColor.YELLOW + "/skilltree" + ChatColor.GRAY
                    + " to equip a tag.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
            return;
        }

        data.setEquippedTag(rollName, rarityName);
        plugin.getTagManager().refreshPrefix(player, data);

        RollableItem rollable = plugin.getRarityManager().findByDisplayName(rollName);
        if (rollable != null) {
            // The item's own colors, so the hologram matches how the item
            // itself is named everywhere else.
            plugin.getTagManager().showHologram(player, RollFormat.displayName(plugin, rollable),
                    RollFormat.tagOdds(plugin, rollable));
        }

        player.sendMessage(ChatColor.GREEN + "Equipped tag: "
                + (rollable != null ? RollFormat.displayName(plugin, rollable) : rollName));
    }
}
