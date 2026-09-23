package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.FirstsGui;
import com.spacerng.solrng.gui.Menus;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /firsts: who holds the Server First spots, per rarity. */
public class FirstsCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public FirstsCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can open the board.");
            return true;
        }
        Menus.open(plugin, player, () -> FirstsGui.build(plugin, player));
        return true;
    }
}
