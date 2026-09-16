package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /size for the top rank. The worn aura is rebuilt at the new size, so the
 * pieces grow and shrink with the player wearing them.
 */
public class SizeCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public SizeCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have a size.");
            return true;
        }
        var ranks = plugin.getRankManager();
        if (!ranks.sizeCommand()) return false;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!ranks.has(data, "size") && !player.hasPermission("solrng.admin")) {
            player.sendMessage(ChatColor.RED + "Supernova can change size. See /ranks.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /size <" + ranks.sizeMin() + " to " + ranks.sizeMax()
                    + "|reset>");
            return true;
        }
        double wanted;
        if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("off")) {
            wanted = 1.0;
        } else {
            try {
                wanted = Double.parseDouble(args[0]);
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "That is not a number.");
                return true;
            }
            if (wanted < ranks.sizeMin() || wanted > ranks.sizeMax()) {
                player.sendMessage(ChatColor.RED + "Pick between " + ranks.sizeMin() + " and " + ranks.sizeMax() + ".");
                return true;
            }
        }
        data.setPlayerSize(wanted);
        apply(plugin, player, wanted);
        player.sendMessage(ChatColor.GREEN + "Your size is now " + ChatColor.WHITE + wanted + ChatColor.GREEN + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, wanted < 1.0 ? 1.8f : 0.6f);
        return true;
    }

    /** Sets the size and rebuilds the aura at it. Used on join as well. */
    public static void apply(SolRNGPlugin plugin, Player player, double size) {
        var attribute = player.getAttribute(Attribute.SCALE);
        if (attribute != null) attribute.setBaseValue(size);
        plugin.getAuraManager().hide(player.getUniqueId());
        plugin.getAuraManager().applyTag(player);
    }
}
