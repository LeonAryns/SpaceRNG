package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.PotionGui;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PotionCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public PotionCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.hasUnlocked("potion_unlock")) {
            player.sendMessage(ChatColor.RED + "Unlock " + ChatColor.YELLOW + "Potions"
                    + ChatColor.RED + " in " + ChatColor.YELLOW + "/skilltree" + ChatColor.RED + " first.");
            return true;
        }

        com.spacerng.solrng.gui.Menus.open(plugin, player, () -> PotionGui.build(plugin, player));
        return true;
    }
}
