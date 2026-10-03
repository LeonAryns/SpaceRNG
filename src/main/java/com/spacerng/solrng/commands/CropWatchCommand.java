package com.spacerng.solrng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /cropwatch, short for /rngadmin cropwatch (V298), staff only. */
public class CropWatchCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Bukkit.dispatchCommand(sender, "rngadmin cropwatch " + String.join(" ", args));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(List.of("list", "off"));
        for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        options.removeIf(o -> !o.toLowerCase().startsWith(args[0].toLowerCase()));
        return options;
    }
}
