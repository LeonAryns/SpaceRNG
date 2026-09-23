package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.CosmeticsGui;
import com.spacerng.solrng.gui.Menus;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /cosmetics: auras, titles and the colour of your name. */
public class CosmeticsCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public CosmeticsCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have cosmetics.");
            return true;
        }
        Menus.open(plugin, player, () -> CosmeticsGui.build(plugin, player));
        return true;
    }
}
