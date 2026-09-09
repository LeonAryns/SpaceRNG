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
 * Common. Turning Luck down is the only way to go back for those, and the
 * alternative players reach for otherwise is stripping their gear off.
 *
 * The limit is stored as a PERCENTAGE of whatever your Luck currently is,
 * never as an absolute number, and that is the whole anti-abuse design.
 * An absolute cap captured at the moment you set it could be carried past
 * a gear change: take the armour off, set the cap, put it back on, and the
 * cap is now above your real Luck and does nothing. A proportion cannot
 * drift that way. It multiplies the finished number, so it is always a
 * reduction, whatever you are wearing and whenever you change it.
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

        String raw = args[0].replace("%", "").trim();
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("reset")
                || raw.equalsIgnoreCase("clear")) {
            raw = "100";
        }

        int percent;
        try {
            percent = (int) Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "That's not a number. Try "
                    + ChatColor.YELLOW + "/limitluck 25" + ChatColor.RED + " or "
                    + ChatColor.YELLOW + "/limitluck off" + ChatColor.RED + ".");
            return true;
        }

        if (percent < 0 || percent > 100) {
            player.sendMessage(ChatColor.RED + "Pick something between 0 and 100. "
                    + ChatColor.GRAY + "The limit can only ever lower your Luck.");
            return true;
        }

        data.setLuckLimitPercent(percent);
        plugin.getScoreboardManager().update(player);
        show(player, data);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
                0.7f, percent >= 100 ? 1.6f : 0.9f);
        return true;
    }

    private void show(Player player, PlayerData data) {
        int percent = data.getLuckLimitPercent();
        double capped = plugin.getPrestigeManager().effectiveLuck(data);
        // The uncapped figure, so the player can see what they gave up.
        double full = percent <= 0 ? 0.0 : capped * 100.0 / percent;

        player.sendMessage("");
        player.sendMessage(Lore.header("Luck Limit"));
        if (percent >= 100) {
            player.sendMessage(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY
                    + "No limit. You roll with all "
                    + ChatColor.WHITE + String.format("%.2f", capped * 100.0) + "%"
                    + ChatColor.GRAY + " Luck.");
        } else {
            player.sendMessage(ChatColor.YELLOW + Lore.BULLET + " " + ChatColor.GRAY
                    + "Limited to " + ChatColor.WHITE + percent + "%"
                    + ChatColor.GRAY + " of your Luck.");
            player.sendMessage(ChatColor.YELLOW + Lore.BULLET + " " + ChatColor.GRAY
                    + "Rolling with " + ChatColor.WHITE
                    + String.format("%.2f", capped * 100.0) + "%"
                    + ChatColor.DARK_GRAY + " instead of "
                    + String.format("%.2f", full * 100.0) + "%");
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET
                + " Lower Luck brings the common end of the table back, which");
        player.sendMessage(ChatColor.DARK_GRAY + "  is the only way to finish an index full of holes.");
        player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET + " "
                + ChatColor.YELLOW + "/limitluck off" + ChatColor.DARK_GRAY + " puts it all back.");
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("off", "0", "10", "25", "50", "75", "100");
    }
}
