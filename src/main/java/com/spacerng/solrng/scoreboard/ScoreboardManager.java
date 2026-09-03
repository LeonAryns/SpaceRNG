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
 * Most lines' content is set via customName (the left-aligned "name" part
 * of a scoreboard row), with the number hidden via NumberFormat.blank() —
 * the entry itself is just an invisible unique placeholder used only to key
 * which row is being written to. The three currency lines instead use the
 * genuinely right-aligned number slot (NumberFormat.fixed()) for their
 * symbol+balance, see {@link Line}.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "solrng_side";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    // Generous upper bound on possible line count (currently maxes out
    // around 13) so leftover entries from a longer previous frame — e.g.
    // the "Rolling... Ns" lines once a roll finishes — always get cleared.
    private static final int MAX_LINES = 20;

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
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "⚡ SpaceRNG ⚡");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        // A fresh personal Scoreboard has none of the tag teams other
        // players' boards already have — backfill them all now.
        plugin.getTagManager().syncAllTeamsTo(player);
        update(player);
    }

    public void update(Player player) {
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) return; // player's on a different scoreboard right now

        List<Line> lines = buildLines(player);
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            setLine(objective, i, total - i, lines.get(i));
        }
        // Line count varies (rolling adds 2 lines) — clear anything left
        // over from a longer previous frame so old lines don't linger.
        for (int i = total; i < MAX_LINES; i++) {
            board.resetScores(ChatColor.RESET.toString().repeat(i + 1));
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private List<Line> buildLines(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int discovered = data.getDiscoveredItems().size();
        int totalItems = plugin.getRarityManager().getItems().size();
        double luckPercent = plugin.getPrestigeManager().effectiveLuck(data) * 100.0;

        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("")); // breathing room under the header
        lines.add(Line.of(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName()));
        lines.add(Line.of("")); // breathing room under the name
        lines.add(Line.of(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Index: " + ChatColor.AQUA + discovered + ChatColor.GRAY + "/" + ChatColor.AQUA + totalItems));
        lines.add(Line.of(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Luck: " + ChatColor.GREEN + "+" + String.format("%.2f", luckPercent) + "%"));
        lines.add(Line.of(ChatColor.YELLOW + "| " + prestigeLine(data)));
        lines.add(Line.of("")); // blank spacer
        lines.add(Line.of(ChatColor.GOLD + "" + ChatColor.BOLD + "CURRENCY"));
        lines.add(Line.of("")); // breathing room under the title
        // Symbol + balance goes in the genuinely right-aligned score-number
        // slot instead of the left-aligned name text — that's the only way
        // to get pixel-perfect alignment across "Money"/"Tokens"/"Credits",
        // since Minecraft's default font isn't monospace and those labels
        // are all different widths.
        lines.add(new Line(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Money:", ChatColor.GOLD + "$ " + ChatColor.GREEN + formatMoney(player)));
        lines.add(new Line(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Tokens:", ChatColor.AQUA + "♦ " + ChatColor.AQUA + data.getTokens()));
        lines.add(new Line(ChatColor.YELLOW + "| " + ChatColor.WHITE + "Credits:", ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + data.getPoints()));

        String rollStatus = rollStatusLine(player);
        if (rollStatus != null) {
            lines.add(Line.of("")); // blank spacer
            lines.add(Line.of(rollStatus));
        }
        lines.add(Line.of("")); // blank spacer
        lines.add(Line.of(ChatColor.GRAY + "SpaceRNG.Minehut.gg"));
        return lines;
    }

    private static final String[] ROMAN_NUMERALS = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private String prestigeLine(PlayerData data) {
        if (data.getPrestige() <= 0) {
            return ChatColor.WHITE + "Level: " + ChatColor.GRAY + data.getLevel();
        }
        String numeral = data.getPrestige() <= ROMAN_NUMERALS.length
                ? ROMAN_NUMERALS[data.getPrestige() - 1]
                : String.valueOf(data.getPrestige());
        return ChatColor.WHITE + "Prestige: " + ChatColor.AQUA + numeral
                + ChatColor.GRAY + " - " + ChatColor.WHITE + data.getLevel();
    }

    /**
     * Null when the player isn't mid-roll — the idle "Ready to roll!"
     * line was removed, so this line is skipped entirely while idle.
     */
    private String rollStatusLine(Player player) {
        int secondsLeft = plugin.getRollListener().getRemainingSeconds(player.getUniqueId());
        if (secondsLeft > 0) {
            return ChatColor.YELLOW + "Rolling... " + ChatColor.WHITE + secondsLeft + "s";
        }
        return null;
    }

    private String formatMoney(Player player) {
        if (economy == null) return "N/A";
        return String.format("%.0f", economy.getBalance(player));
    }

    /**
     * A scoreboard row has two independently-positioned parts: the name
     * (left-aligned, like a normal player name) and the score number
     * (always right-aligned, flush to the sidebar's right edge regardless
     * of how long the left text is). Most lines just use the left side,
     * with the right side hidden via NumberFormat.blank(). The three
     * currency lines put their symbol+balance in the right slot instead,
     * so they line up in a real column no matter how long "Money"/
     * "Tokens"/"Credits" is.
     */
    private record Line(String left, String right) {
        static Line of(String left) {
            return new Line(left, null);
        }
    }

    /**
     * index: which slot this line occupies (0 = top). order: the raw score
     * value controlling vertical position (higher = higher up).
     */
    private void setLine(Objective objective, int index, int order, Line line) {
        String entry = ChatColor.RESET.toString().repeat(index + 1); // unique, invisible placeholder
        Score score = objective.getScore(entry);
        score.setScore(order);
        Component left = line.left().isEmpty() ? Component.empty() : LEGACY.deserialize(line.left());
        score.customName(left);
        score.numberFormat(line.right() == null ? NumberFormat.blank() : NumberFormat.fixed(LEGACY.deserialize(line.right())));
    }
}
