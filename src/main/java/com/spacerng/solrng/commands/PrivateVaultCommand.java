package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Menus;
import com.spacerng.solrng.gui.PrivateVaultGui;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /pv lists your vault pages, /pv 2 opens one straight away. */
public class PrivateVaultCommand implements CommandExecutor, TabCompleter {

    private final SolRNGPlugin plugin;

    public PrivateVaultCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have vaults.");
            return true;
        }
        if (args.length == 0) {
            Menus.open(plugin, player, () -> PrivateVaultGui.buildSelector(plugin, player));
            return true;
        }

        int page;
        try {
            page = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Usage: /pv [page]");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int open = plugin.getRankManager().vaultPages(data);
        if (page < 1 || page > open) {
            player.sendMessage(ChatColor.RED + "You have " + open
                    + (open == 1 ? " vault page." : " vault pages.")
                    + ChatColor.GRAY + " A higher rank opens more, see /ranks.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return true;
        }
        int wanted = page;
        Menus.open(plugin, player, () -> PrivateVaultGui.build(plugin, player, wanted));
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.4f);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) return List.of();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= plugin.getRankManager().vaultPages(data); page++) {
            String text = String.valueOf(page);
            if (text.startsWith(args[0])) pages.add(text);
        }
        return pages;
    }
}
