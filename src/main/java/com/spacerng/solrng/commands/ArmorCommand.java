package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.ArmorGui;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ArmorCommand implements CommandExecutor {

    private static final String ARMOR_UNLOCK_NODE = "armor_unlock";

    private final SolRNGPlugin plugin;

    public ArmorCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.hasUnlocked(ARMOR_UNLOCK_NODE)) {
            player.sendMessage(ChatColor.RED + "Unlock \"Armor Unlocked\" in /skilltree first.");
            return true;
        }

        player.openInventory(ArmorGui.build(plugin, player));
        return true;
    }
}
