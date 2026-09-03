package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
            data.clearEquippedTag();
            plugin.getTagManager().clearTag(player);
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

            data.setEquippedTag(rollName, rarityName);
            String color = plugin.getRarityManager().colorFor(Rarity.valueOf(rarityName));
            plugin.getTagManager().applyTag(player, rollName, color);
            player.sendMessage(ChatColor.GREEN + "Equipped tag: " + color + "[" + rollName + "]");
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /tag <equip|clear>");
        return true;
    }
}
