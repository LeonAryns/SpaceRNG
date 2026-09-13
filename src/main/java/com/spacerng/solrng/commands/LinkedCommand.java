package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.discord.LinkedAccountManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Prints this player's linked-account status: whether DiscordSRV is
 * even installed, whether they are linked, and what bonuses the link
 * is worth.
 */
public class LinkedCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public LinkedCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can check their linked account.");
            return true;
        }

        LinkedAccountManager linked = plugin.getLinkedAccountManager();

        if (!linked.isDiscordSrvPresent()) {
            player.sendMessage(ChatColor.GRAY + "The Discord bridge is not installed on this server.");
            return true;
        }

        boolean isLinked = linked.isLinked(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Discord Link");
        player.sendMessage(ChatColor.GRAY + "Status: "
                + (isLinked ? ChatColor.GREEN + "linked" : ChatColor.RED + "not linked"));
        if (!isLinked) {
            player.sendMessage(ChatColor.GRAY + "Link with " + ChatColor.YELLOW
                    + "/discord link" + ChatColor.GRAY + " in-game and use the code in "
                    + ChatColor.AQUA + "#link" + ChatColor.GRAY + " on Discord.");
        }
        player.sendMessage(ChatColor.GRAY + "Bonuses while linked:");
        player.sendMessage(bonusLine(ChatColor.GOLD, "Money", linked.moneyBonus()));
        player.sendMessage(bonusLine(ChatColor.YELLOW, "Coins", linked.coinsBonus()));
        player.sendMessage(bonusLine(ChatColor.GREEN, "Luck", linked.luckBonus()));
        player.sendMessage(bonusLine(ChatColor.AQUA, "Shiny", linked.shinyBonus()));
        player.sendMessage("");
        return true;
    }

    private static String bonusLine(ChatColor colour, String label, double amount) {
        return colour + " ▎ " + ChatColor.GRAY + label + ": " + ChatColor.WHITE
                + "+" + Math.round(amount * 100) + "%";
    }
}
