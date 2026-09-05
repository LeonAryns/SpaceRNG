package com.spacerng.solrng.scoreboard;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.rarity.RollFormat;
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
 * Each line's real content is set via customName (the left-aligned "name"
 * part of a scoreboard row), with the number hidden via NumberFormat.blank()
 * — the entry itself is just an invisible unique placeholder used only to
 * key which row is being written to.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "solrng_side";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    // Generous upper bound on possible line count (currently maxes out
    // around 13) so leftover entries from a longer previous frame — e.g.
    // the "Rolling... Ns" lines once a roll finishes — always get cleared.
    private static final int MAX_LINES = 20;
    private static final String[] ROMAN_NUMERALS = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

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
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("scoreboard.title", "&5&l✦ SPACERNG ✦")));
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

        List<String> lines = buildLines(player);
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

    private List<String> buildLines(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int discovered = data.getDiscoveredItems().size();
        int totalItems = plugin.getRarityManager().getItems().size();
        double luckPercent = plugin.getPrestigeManager().effectiveLuck(data) * 100.0;

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_GRAY + plugin.getConfig().getString("scoreboard.season", "SEASON I"));
        lines.add("");
        lines.add(ChatColor.GOLD + Lore.BULLET + " " + ChatColor.GOLD + ChatColor.BOLD
                + player.getName().toUpperCase());
        lines.add(entry(ChatColor.GOLD, "PRESTIGE", ChatColor.AQUA + prestigeText(data)));
        lines.add(entry(ChatColor.GREEN, "LUCK", ChatColor.GREEN + "+" + String.format("%.2f", luckPercent) + "%"));
        lines.add(entry(ChatColor.YELLOW, "SPEED",
                ChatColor.YELLOW + String.valueOf(Math.round(data.getEffectiveRollSpeedMultiplier() * 100))));
        lines.add(entry(ChatColor.LIGHT_PURPLE, "MULTI", ChatColor.LIGHT_PURPLE
                + String.format("%.2f", plugin.getNovaCoreManager().multiplierAt(data.getNovaTier())) + "x"));
        lines.add(entry(ChatColor.AQUA, "INDEX", ChatColor.AQUA + String.valueOf(discovered)
                + ChatColor.DARK_GRAY + "/" + ChatColor.AQUA + totalItems));

        lines.add("");
        lines.add(ChatColor.GOLD + Lore.BULLET + " " + ChatColor.GOLD + ChatColor.BOLD + "WALLET");
        lines.add(wallet(ChatColor.DARK_GREEN, balanceText(player), "MONEY"));
        lines.add(wallet(ChatColor.YELLOW, RollFormat.abbreviate(data.getTokens()), "TOKENS"));
        lines.add(wallet(ChatColor.AQUA, RollFormat.abbreviate(data.getShards()), "SHARDS"));
        lines.add(wallet(ChatColor.LIGHT_PURPLE, RollFormat.abbreviate(data.getPoints()), "CREDITS"));

        String rollStatus = rollStatusLine(player);
        if (rollStatus != null) {
            lines.add("");
            lines.add(rollStatus);
        }
        lines.add("");
        lines.add(ChatColor.DARK_GRAY + plugin.getConfig().getString("scoreboard.footer", "SPACERNG.MINEHUT.GG"));
        return lines;
    }

    /**
     * "▎ LUCK: +34.00%" — the bullet carries the stat's own colour, so the
     * left edge of the board reads as a colour key before any of the text
     * is parsed.
     */
    private String entry(ChatColor accent, String label, String value) {
        return accent + Lore.BULLET + " " + ChatColor.WHITE + label + ChatColor.DARK_GRAY + ": " + value;
    }

    /** "▎ $11.17K MONEY" — amount first, then what it is. */
    private String wallet(ChatColor accent, String amount, String label) {
        return accent + Lore.BULLET + " " + accent + ChatColor.BOLD + amount + ChatColor.RESET
                + " " + ChatColor.GRAY + label;
    }

    /** "★ III", or "★ 0" before the first prestige. */
    private String prestigeText(PlayerData data) {
        String numeral = data.getPrestige() <= 0 ? "0"
                : data.getPrestige() <= ROMAN_NUMERALS.length
                        ? ROMAN_NUMERALS[data.getPrestige() - 1]
                        : String.valueOf(data.getPrestige());
        return ChatColor.GOLD + Lore.STAR + " " + ChatColor.AQUA + numeral;
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

    private String balanceText(Player player) {
        if (economy == null) return "N/A";
        return "$" + RollFormat.abbreviate(Math.round(economy.getBalance(player)));
    }

    /**
     * index: which slot this line occupies (0 = top). order: the raw score
     * value controlling vertical position (higher = higher up). content:
     * the fully-colored line text.
     */
    private void setLine(Objective objective, int index, int order, String content) {
        String entry = ChatColor.RESET.toString().repeat(index + 1); // unique, invisible placeholder
        Score score = objective.getScore(entry);
        score.setScore(order);
        Component component = content.isEmpty() ? Component.empty() : LEGACY.deserialize(content);
        score.customName(component);
        score.numberFormat(NumberFormat.blank());
    }
}
