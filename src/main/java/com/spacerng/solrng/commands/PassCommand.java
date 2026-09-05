package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.PassGui;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PassCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public PassCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!plugin.getPassManager().isUnlocked(data)) {
            player.sendMessage(ChatColor.RED + "Unlock " + ChatColor.YELLOW + "Battle Pass"
                    + ChatColor.RED + " in " + ChatColor.YELLOW + "/skilltree" + ChatColor.RED + " first.");
            return true;
        }

        player.openInventory(PassGui.build(plugin, player, 0));
        return true;
    }
}
