package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Menus;
import com.spacerng.solrng.gui.PerkRollerGui;
import com.spacerng.solrng.gui.PerkVaultGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /perks} opens the Roller. {@code /perks vault} opens the
 * vault, since players who just want to reshuffle a loadout should not
 * have to walk through the roller to reach it.
 */
public class PerkCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public PerkCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the perks menu.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("vault")) {
            Menus.open(plugin, player, () -> PerkVaultGui.build(plugin, player, 0));
        } else {
            Menus.open(plugin, player, () -> PerkRollerGui.build(plugin, player));
        }
        return true;
    }
}
