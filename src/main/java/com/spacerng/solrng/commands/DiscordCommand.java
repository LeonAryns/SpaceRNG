package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * /discord (V248): the invite, in a short card in chat. The link is
 * clickable and also written out in full, because Bedrock players cannot
 * click chat and have to be able to read it. The invite is
 * {@code discord.invite} in config.
 */
public class DiscordCommand implements CommandExecutor {

    static final String DEFAULT_INVITE = "https://discord.gg/E8V67kjAj";

    private final SolRNGPlugin plugin;

    public DiscordCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String url = plugin.getConfig().getString("discord.invite", DEFAULT_INVITE);
        if (url == null || url.isBlank()) url = DEFAULT_INVITE;
        String shown = url.replaceFirst("^https?://", "");
        String luck = Math.round(plugin.getConfig().getDouble("linked-account.bonus.luck-percent", 1.0) * 100) + "%";

        sender.sendMessage("");
        sender.sendMessage("  " + Lore.gradient("✦ DISCORD ✦", true, "#5865F2", "#7289DA", "#5865F2"));
        sender.sendMessage(ChatColor.BLUE + Lore.BULLET + " " + ChatColor.GRAY + "Giveaways, news and updates.");
        sender.sendMessage(ChatColor.BLUE + Lore.BULLET + " " + ChatColor.GRAY + "Link with " + ChatColor.AQUA
                + "/link" + ChatColor.GRAY + " for " + ChatColor.GREEN + "+" + luck + " Luck" + ChatColor.GRAY + ".");
        sender.sendMessage(Component.text(Lore.BULLET + " ", NamedTextColor.BLUE)
                .append(Component.text("Click to join ", NamedTextColor.GRAY))
                .append(Component.text(shown, NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED))
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Open " + shown, NamedTextColor.GRAY))));
        sender.sendMessage("");
        return true;
    }
}
