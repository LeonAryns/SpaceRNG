package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Menus;
import com.spacerng.solrng.gui.StatsGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final SolRNGPlugin plugin;

    public StatsCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player target = player;
        if (args.length >= 1) {
            // Online only. An offline player's stats would mean loading
            // their save into the live cache to read it, which is a good
            // way to have somebody's progress overwritten by a stale copy.
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "No player online called " + args[0] + ".");
                return true;
            }
        }

        Player shown = target;
        Menus.open(plugin, player,
                () -> StatsGui.overview(plugin, shown.getUniqueId(), shown.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(online.getName());
        }
        return out;
    }
}
