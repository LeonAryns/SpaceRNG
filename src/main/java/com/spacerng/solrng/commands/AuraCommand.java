package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.AuraGui;
import com.spacerng.solrng.gui.Menus;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /aura picks which aura you wear. */
public class AuraCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public AuraCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players wear auras.");
            return true;
        }
        Menus.open(plugin, player, () -> AuraGui.build(plugin, player));
        return true;
    }
}
