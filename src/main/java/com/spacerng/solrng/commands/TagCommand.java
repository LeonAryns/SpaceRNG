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

        // V186: bare /tag opens the index, because that is where a tag is
        // actually picked. Printing a usage line at somebody who typed the
        // name of the thing they wanted is the least useful answer there is.
        if (args.length == 0) {
            com.spacerng.solrng.gui.Menus.open(plugin, player,
                    () -> com.spacerng.solrng.gui.IndexGui.build(plugin, player, null, 0));
            player.sendMessage(ChatColor.GRAY + "Click any drop you have found to wear it. "
                    + ChatColor.YELLOW + "/tag clear" + ChatColor.GRAY + " takes it off.");
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
                player.sendMessage(ChatColor.RED + "That's not a rolled item - hold one of your RNG rolls and try again.");
                return true;
            }

            equip(plugin, player, data, rollName, rarityName);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /tag <equip|clear>");
        return true;
    }

    /**
     * The rank perk auto-tag (V227, Supernova): whenever a drop is found
     * whose tag is worth more Luck than the one being worn, it is put on
     * for the player. Only ever an upgrade, so somebody who picked a tag by
     * hand keeps it until something better turns up.
     */
    public static void autoEquipBest(SolRNGPlugin plugin, Player player, PlayerData data) {
        if (player == null || !plugin.getRankManager().has(data, "auto-tag")) return;
        if (!data.hasUnlocked(INDEX_LUCK_NODE)) return;
        RollableItem best = null;
        for (String name : data.getDiscoveredItems()) {
            RollableItem item = plugin.getRarityManager().findByDisplayName(name);
            if (item == null) continue;
            if (best == null || item.getLuckMultiplier() > best.getLuckMultiplier()) best = item;
        }
        if (best == null) return;
        RollableItem worn = data.getEquippedTagItemKey() == null ? null
                : plugin.getRarityManager().findByDisplayName(data.getEquippedTagItemKey());
        if (worn != null && worn.getLuckMultiplier() >= best.getLuckMultiplier()) return;
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Auto tag: " + ChatColor.GRAY + "your best tag goes on.");
        equip(plugin, player, data, best.getDisplayName(), best.getRarity().name());
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
                    + ChatColor.RESET + ChatColor.GRAY + "Unlock " + ChatColor.YELLOW + "Tag Luck"
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
