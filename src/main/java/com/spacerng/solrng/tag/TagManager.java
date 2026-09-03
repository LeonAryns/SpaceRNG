package com.spacerng.solrng.tag;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Equipping a tag creates/uses a dedicated scoreboard team per player and
 * sets its prefix. This makes the tag show above the player's head in the
 * world AND in the tab list. Chat formatting is handled separately by
 * ChatListener, which reads the same prefix.
 */
public class TagManager {

    private static final String TEAM_PREFIX = "solrng_";

    public void applyTag(Player player, String tagText, String colorCode) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = TEAM_PREFIX + shortUuid(player);

        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }

        String color = ChatColor.translateAlternateColorCodes('&', colorCode);
        team.setPrefix(color + "[" + tagText + "] " + ChatColor.RESET);

        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    public void clearTag(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = TEAM_PREFIX + shortUuid(player);
        Team team = board.getTeam(teamName);
        if (team != null) {
            team.setPrefix("");
        }
    }

    public String getPrefix(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM_PREFIX + shortUuid(player));
        return team == null ? "" : team.getPrefix();
    }

    private String shortUuid(Player player) {
        // Team names are capped at 16 chars pre-1.18 but Paper 1.21 allows longer;
        // still keep it short and unique.
        return player.getUniqueId().toString().substring(0, 12);
    }
}
