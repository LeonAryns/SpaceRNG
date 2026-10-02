package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * /limitluck - roll with less Luck than you have, on purpose.
 *
 * High Luck pushes every roll toward the rare end of the table, which is
 * exactly what you want until the only thing missing from your index is a
 * Common. Turning Luck down is the only way to go back for those.
 *
 * V279: you choose the Luck itself, "/limitluck 500" for +500%, where it
 * used to be a percentage of your Luck. It can only ever lower it: the
 * choice is applied as a ceiling on the finished number, so a choice above
 * your real Luck does nothing, whatever you wear or take off later.
 */
public class LimitLuckCommand implements CommandExecutor, TabCompleter {

    private final SolRNGPlugin plugin;

    public LimitLuckCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (args.length == 0) {
            show(player, data);
            return true;
        }

        String raw = args[0].replace("%", "").replace("+", "").replace(",", "").trim();
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("reset") || raw.equalsIgnoreCase("clear")) {
            data.setLuckCap(-1.0);
            plugin.getScoreboardManager().update(player);
            show(player, data);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
            return true;
        }

        double percent;
        try {
            percent = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "That's not a number. Try "
                    + ChatColor.YELLOW + "/limitluck 500" + ChatColor.RED + " for +500% Luck, or "
                    + ChatColor.YELLOW + "/limitluck off" + ChatColor.RED + ".");
            return true;
        }
        if (percent < 0) {
            player.sendMessage(ChatColor.RED + "Pick 0 or more. " + ChatColor.GRAY + "0 rolls with no Luck at all.");
            return true;
        }

        data.setLuckCap(percent / 100.0);
        plugin.getScoreboardManager().update(player);
        show(player, data);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.9f);
        return true;
    }

    private void show(Player player, PlayerData data) {
        double cap = data.getLuckCap();
        double now = plugin.getPrestigeManager().effectiveLuck(data);
        // The uncapped figure, so the player can see what they gave up.
        data.setLuckCap(-1.0);
        double full = plugin.getPrestigeManager().effectiveLuck(data);
        data.setLuckCap(cap);

        player.sendMessage("");
        player.sendMessage(Lore.header("Luck Limit"));
        if (cap < 0) {
            player.sendMessage(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY
                    + "No limit. You roll with all "
                    + ChatColor.WHITE + "+" + Math.round(full * 100.0) + "%" + ChatColor.GRAY + " Luck.");
        } else {
            player.sendMessage(ChatColor.YELLOW + Lore.BULLET + " " + ChatColor.GRAY
                    + "Limited to " + ChatColor.WHITE + "+" + Math.round(cap * 100.0) + "%" + ChatColor.GRAY + " Luck.");
            player.sendMessage(ChatColor.YELLOW + Lore.BULLET + " " + ChatColor.GRAY
                    + "Rolling with " + ChatColor.WHITE + "+" + Math.round(now * 100.0) + "%"
                    + ChatColor.DARK_GRAY + " of your +" + Math.round(full * 100.0) + "%");
            if (cap >= full) {
                player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET
                        + " That is above your Luck, so it changes nothing right now.");
            }
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET + " " + ChatColor.YELLOW + "/limitluck <luck>"
                + ChatColor.DARK_GRAY + " picks it, " + ChatColor.YELLOW + "/limitluck 500"
                + ChatColor.DARK_GRAY + " is +500%.");
        player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET
                + " Lower Luck brings the common end of the table back.");
        player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET + " "
                + ChatColor.YELLOW + "/limitluck off" + ChatColor.DARK_GRAY + " puts it all back.");
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("off", "0", "100", "500", "1000", "2500");
    }
}
