package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.PrestigeGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PrestigeCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public PrestigeCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        player.openInventory(PrestigeGui.build(plugin, player));
        return true;
    }
}
