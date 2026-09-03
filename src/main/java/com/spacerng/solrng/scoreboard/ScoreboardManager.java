package com.spacerng.solrng.scoreboard;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * A fully custom sidebar in the "gen server" style — a bold header, grouped
 * stats, and a wallet section. Every line is written to the same fixed slot
 * each refresh (rather than being removed/re-added), so nothing duplicates
 * or lingers on screen when a value changes.
 *
 * Each line's real content lives in that Score's NumberFormat (the part of
 * the row Minecraft normally reserves for a plain number) rather than the
 * entry name — the entry itself is just an invisible unique placeholder.
 * That's what lets a single line combine a label and a colored value
 * together instead of being stuck with vanilla's separate name/number
 * columns.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "solrng_side";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final SolRNGPlugin plugin;
    private Economy economy;

    public ScoreboardManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        var registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            this.economy = registration.getProvider();
            plugin.getLogger().info("[SolRNG] Hooked into Vault economy for the Money scoreboard stat.");
        }
    }

    public void setup(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_ID, "dummy",
                ChatColor.GOLD + "" + ChatColor.BOLD + "⚡ SpaceRNG ⚡");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        update(player);
    }

    public void update(Player player) {
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) return; // player's on a different scoreboard right now

        List<String> lines = buildLines(player);
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            setLine(objective, i, total - i, lines.get(i));
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private List<String> buildLines(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int discovered = data.getDiscoveredItems().size();
        int totalItems = plugin.getRarityManager().getItems().size();
        double luckPercent = data.getBonusLuck() * 100.0;

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName());
        lines.add(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Index: " + ChatColor.AQUA + discovered + ChatColor.GRAY + "/" + ChatColor.AQUA + totalItems);
        lines.add(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Luck: " + ChatColor.GREEN + "+" + String.format("%.2f", luckPercent) + "%");
        lines.add(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Tag: " + tagLine(data));
        lines.add(""); // blank spacer
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "YOUR WALLET");
        lines.add(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Money: " + ChatColor.GREEN + formatMoney(player));
        lines.add(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Tokens: " + ChatColor.AQUA + data.getTokens());
        lines.add(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Credits: " + ChatColor.LIGHT_PURPLE + data.getPoints());
        lines.add(""); // blank spacer
        lines.add(rollStatusLine(player));
        return lines;
    }

    private String tagLine(PlayerData data) {
        if (data.getEquippedTagItemKey() == null) {
            return ChatColor.GRAY + "None";
        }
        String color = plugin.getRarityManager().colorFor(
                com.spacerng.solrng.rarity.Rarity.valueOf(data.getEquippedTagRarity()));
        return color + "[" + data.getEquippedTagItemKey() + "]";
    }

    private String rollStatusLine(Player player) {
        int secondsLeft = plugin.getRollListener().getRemainingSeconds(player.getUniqueId());
        if (secondsLeft > 0) {
            return ChatColor.YELLOW + "Rolling... " + ChatColor.WHITE + secondsLeft + "s";
        }
        return ChatColor.GREEN + "" + ChatColor.BOLD + "Ready to roll!";
    }

    private String formatMoney(Player player) {
        if (economy == null) return "N/A";
        return String.format("%.0f", economy.getBalance(player));
    }

    /**
     * index: which slot this line occupies (0 = top). order: the raw score
     * value controlling vertical position (higher = higher up). content:
     * the fully-colored line text, shown via NumberFormat rather than the
     * entry name so label + value can share one line.
     */
    private void setLine(Objective objective, int index, int order, String content) {
        String entry = ChatColor.RESET.toString().repeat(index + 1); // unique, invisible placeholder
        Score score = objective.getScore(entry);
        score.setScore(order);
        Component component = content.isEmpty() ? Component.empty() : LEGACY.deserialize(content);
        score.numberFormat(NumberFormat.fixed(component));
    }
}
