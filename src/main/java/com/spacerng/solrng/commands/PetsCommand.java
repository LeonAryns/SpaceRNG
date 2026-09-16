package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.PetsGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pets: the three aura slots and every pet. */
public class PetsCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public PetsCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have pets.");
            return true;
        }
        if (!plugin.getPetManager().isEnabled()) {
            player.sendMessage(ChatColor.RED + "Pets are switched off.");
            return true;
        }
        player.openInventory(PetsGui.build(plugin, player));
        return true;
    }
}
