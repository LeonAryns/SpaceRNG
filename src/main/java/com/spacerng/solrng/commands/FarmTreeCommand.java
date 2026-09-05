package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.SkillTreeGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FarmTreeCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public FarmTreeCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have skills.");
            return true;
        }
        player.openInventory(SkillTreeGui.build(plugin, player, "farmtree", 0));
        return true;
    }
}
