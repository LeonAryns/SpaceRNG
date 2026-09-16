package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /fly for the ranks that carry it. Switch it off in config when another plugin has the command. */
public class FlyCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public FlyCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can fly.");
            return true;
        }
        if (!plugin.getRankManager().flyCommand()) return false;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!plugin.getRankManager().has(data, "fly") && !player.hasPermission("solrng.admin")) {
            player.sendMessage(ChatColor.RED + "Nova and Supernova can fly. See /ranks.");
            return true;
        }
        boolean on = !player.getAllowFlight();
        player.setAllowFlight(on);
        player.setFlying(on);
        player.sendMessage(on ? ChatColor.GREEN + "Flight on." : ChatColor.GRAY + "Flight off.");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, on ? 1.5f : 0.8f);
        return true;
    }
}
