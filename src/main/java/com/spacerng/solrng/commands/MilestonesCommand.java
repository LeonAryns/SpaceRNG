package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.MilestoneGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MilestonesCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public MilestonesCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have milestones.");
            return true;
        }
        // Catch anything reached while they were offline before showing it.
        plugin.getMilestoneManager().check(player);
        player.openInventory(MilestoneGui.buildRoot(plugin, player));
        return true;
    }
}
