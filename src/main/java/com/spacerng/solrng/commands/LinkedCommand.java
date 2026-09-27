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
        String reward = rewardText(linked);
        player.sendMessage("");
        player.sendMessage(com.spacerng.solrng.gui.Lore.gradient("DISCORD LINK", true, "#5865F2", "#7289DA"));
        if (isLinked) {
            player.sendMessage(ChatColor.GREEN + " \u258E " + ChatColor.GRAY + "Linked " + ChatColor.GREEN + "\u2714");
            if (!reward.isEmpty()) {
                player.sendMessage(ChatColor.GREEN + " \u258E " + ChatColor.GRAY + "You roll with "
                        + ChatColor.GREEN + reward + ChatColor.GRAY + " while you stay linked");
            }
            player.sendMessage(ChatColor.DARK_GRAY + " /link unlink takes it off again");
        } else if (bot == null || !bot.isOnline()) {
            player.sendMessage(ChatColor.RED + " \u258E " + ChatColor.GRAY + "The Discord bot is offline, try again later.");
        } else {
            String code = store.newCode(player.getUniqueId());
            player.sendMessage(Component.text(" \u258E ", NamedTextColor.YELLOW)
                    .append(Component.text("Your code  ", NamedTextColor.GRAY))
                    .append(Component.text(code, NamedTextColor.GOLD, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.copyToClipboard(code))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))))
                    .append(Component.text("  click to copy", NamedTextColor.DARK_GRAY)));
            player.sendMessage(ChatColor.YELLOW + " \u258E " + ChatColor.GRAY + "In Discord, press "
                    + ChatColor.GREEN + "Link account" + ChatColor.GRAY + " in the link channel and type it in");
            player.sendMessage(ChatColor.YELLOW + " \u258E " + ChatColor.GRAY + "The code works for 5 minutes");
            if (!reward.isEmpty()) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + " Reward: " + ChatColor.GREEN + reward
                        + ChatColor.GRAY + " for as long as you stay linked");
            }
        }
        player.sendMessage("");
        return true;
    }

    /** "+100% Luck", or several joined, from whatever the config pays. */
    private static String rewardText(LinkedAccountManager linked) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (linked.luckBonus() > 0) parts.add("+" + LinkedAccountManager.percent(linked.luckBonus()) + " Luck");
        if (linked.moneyBonus() > 0) parts.add("+" + LinkedAccountManager.percent(linked.moneyBonus()) + " Money");
        if (linked.coinsBonus() > 0) parts.add("+" + LinkedAccountManager.percent(linked.coinsBonus()) + " Coins");
        if (linked.shinyBonus() > 0) parts.add("+" + LinkedAccountManager.percent(linked.shinyBonus()) + " Shiny");
        return String.join(", ", parts);
    }

}
