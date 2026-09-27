package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.discord.LinkedAccountManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /link: links this Minecraft account to Discord, through our own bot
 * since V230.
 *
 * Not linked: hands out a six digit code, valid five minutes, which the
 * player types into the bot in Discord (the button on the link card, the
 * /link slash command, or the code on its own in the link channel).
 * Linked: says so, lists the bonuses, and /link unlink undoes it.
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
            sender.sendMessage(ChatColor.RED + "Only players can link an account.");
            return true;
        }
        LinkedAccountManager linked = plugin.getLinkedAccountManager();
        var store = plugin.getLinkStore();
        var bot = plugin.getDiscordBot();

        if (args.length >= 1 && args[0].equalsIgnoreCase("unlink")) {
            String discordId = store.discordOf(player.getUniqueId());
            if (!store.unlink(player.getUniqueId())) {
                player.sendMessage(ChatColor.GRAY + "Your account is not linked.");
                return true;
            }
            if (bot != null) bot.clearRoles(discordId);
            linked.checkNow();
            plugin.getRankManager().refreshName(player);
            player.sendMessage(ChatColor.YELLOW + "Unlinked from Discord.");
            return true;
        }

        boolean isLinked = linked.isLinked(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Discord Link");
        player.sendMessage(ChatColor.GRAY + "Status: "
                + (isLinked ? ChatColor.GREEN + "linked" : ChatColor.RED + "not linked"));
        if (!isLinked) {
            if (bot == null || !bot.isOnline()) {
                player.sendMessage(ChatColor.GRAY + "The Discord bot is offline right now, try again later.");
            } else {
                String code = store.newCode(player.getUniqueId());
                player.sendMessage(Component.text("Your code: ", NamedTextColor.GRAY)
                        .append(Component.text(code, NamedTextColor.GOLD, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.copyToClipboard(code))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))))
                        .append(Component.text("  (click to copy, valid 5 minutes)", NamedTextColor.DARK_GRAY)));
                player.sendMessage(ChatColor.WHITE + "In Discord, press " + ChatColor.GREEN + "Link account"
                        + ChatColor.WHITE + " in the link channel and enter it.");
            }
        } else {
            player.sendMessage(ChatColor.DARK_GRAY + "/link unlink takes the link off again.");
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
