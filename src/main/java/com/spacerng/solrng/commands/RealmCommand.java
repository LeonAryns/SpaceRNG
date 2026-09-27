package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.SecretIndexGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /realm [leave] and /secretindex (V229). */
public class RealmCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public RealmCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can do that.");
            return true;
        }
        var realm = plugin.getRealmManager();
        if (command.getName().equalsIgnoreCase("secretindex")) {
            var data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (data.getPrestige() < realm.minPrestige()) {
                player.sendMessage(ChatColor.RED + "The Secret Index opens at Prestige " + realm.minPrestige() + ".");
                return true;
            }
            player.openInventory(SecretIndexGui.build(plugin, player));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave")) {
            realm.leave(player);
            return true;
        }
        realm.enter(player);
        return true;
    }
}
