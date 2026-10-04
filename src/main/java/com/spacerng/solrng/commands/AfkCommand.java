package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /afk (V344, Leon asked for it): marks you away, and tab says so.
 *
 * The marker lives on the tab list rather than only in chat, because the
 * question it answers ("is anybody actually there") is asked by looking
 * at the player list. It drops off on its own as soon as somebody moves,
 * so nobody is left wearing it after they come back.
 */
public class AfkCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public AfkCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can go afk.");
            return true;
        }
        boolean afk = plugin.getTabListManager().toggleAfk(player);
        plugin.getServer().broadcastMessage(afk
                ? ChatColor.DARK_GRAY + "* " + ChatColor.GRAY + player.getName() + " is afk."
                : ChatColor.DARK_GRAY + "* " + ChatColor.GRAY + player.getName() + " is back.");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, afk ? 0.8f : 1.4f);
        return true;
    }
}
