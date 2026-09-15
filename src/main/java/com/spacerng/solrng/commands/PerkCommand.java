package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Menus;
import com.spacerng.solrng.gui.PerkIndexGui;
import com.spacerng.solrng.gui.PerkRollerGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /perks} opens the perk menu, {@code /perks index} the perk index. */
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
        if (args.length > 0 && args[0].equalsIgnoreCase("index")) {
            Menus.open(plugin, player, () -> PerkIndexGui.build(plugin, player));
        } else {
            Menus.open(plugin, player, () -> PerkRollerGui.build(plugin, player));
        }
        return true;
    }
}
