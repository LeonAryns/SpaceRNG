package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.leaderboard.LeaderboardManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /top — the leaderboards in chat. The hologram at spawn shows the top
 * three; this is where you go to find yourself.
 */
public class TopCommand implements CommandExecutor, TabCompleter {

    private static final List<String> BOARDS =
            List.of("farming", "farming_total", "rolls", "prestige", "index");

    private final SolRNGPlugin plugin;

    public TopCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String board = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "farming";
        if (!BOARDS.contains(board)) {
            sender.sendMessage(ChatColor.RED + "Boards: " + String.join(", ", BOARDS));
            return true;
        }

        LeaderboardManager boards = plugin.getLeaderboardManager();
        List<LeaderboardManager.Entry> rows = boards.top(board, 10);

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "TOP " + title(board).toUpperCase()
                + ChatColor.DARK_GRAY + "  (" + boards.size() + " tracked)");
        if (board.equals("farming")) {
            sender.sendMessage(ChatColor.GRAY + "Resets in " + ChatColor.WHITE + boards.resetCountdown());
        }
        sender.sendMessage("");

        if (rows.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody's on this board yet.");
            return true;
        }

        for (int i = 0; i < rows.size(); i++) {
            LeaderboardManager.Entry entry = rows.get(i);
            long value = valueOf(board, entry);
            if (value <= 0) continue;

            // Only the paying places get a colour; the rest stay quiet so
            // the podium reads at a glance.
            ChatColor place = i == 0 ? ChatColor.GOLD
                    : i == 1 ? ChatColor.WHITE
                    : i == 2 ? ChatColor.GOLD : ChatColor.DARK_GRAY;

            String reward = board.equals("farming") && boards.payoutFor(i + 1) > 0
                    ? ChatColor.LIGHT_PURPLE + "  +" + boards.payoutFor(i + 1) + " Credits"
                    : "";

            sender.sendMessage(place + "#" + (i + 1) + " " + ChatColor.WHITE + entry.name()
                    + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + Lore.shorten(value)
                    + " " + unit(board) + reward);
        }

        if (sender instanceof Player player) {
            int position = boards.positionOf(board, player.getUniqueId());
            var mine = boards.entryOf(player.getUniqueId());
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "You: " + ChatColor.WHITE
                    + (position > 0 ? "#" + position : "unranked")
                    + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY
                    + (mine == null ? "0" : Lore.shorten(valueOf(board, mine))) + " " + unit(board));
        }
        sender.sendMessage("");
        return true;
    }

    private long valueOf(String board, LeaderboardManager.Entry entry) {
        return switch (board) {
            case "farming" -> entry.farmedPeriod();
            case "farming_total" -> entry.farmedTotal();
            case "rolls" -> entry.rolls();
            case "prestige" -> entry.prestige();
            default -> entry.discoveries();
        };
    }

    private String title(String board) {
        return switch (board) {
            case "farming" -> "Farmers";
            case "farming_total" -> "Farmers (all time)";
            case "rolls" -> "Rollers";
            case "prestige" -> "Prestige";
            default -> "Collectors";
        };
    }

    private String unit(String board) {
        return switch (board) {
            case "farming", "farming_total" -> "farmed";
            case "rolls" -> "rolls";
            case "prestige" -> "prestige";
            default -> "drops";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>();
        for (String board : BOARDS) {
            if (board.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(board);
        }
        return out;
    }
}
