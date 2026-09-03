package com.spacerng.solrng.tag;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Equipping a tag creates/uses a dedicated scoreboard team per player and
 * sets its prefix. This makes the tag show above the player's head in the
 * world AND in the tab list. Chat formatting is handled separately by
 * ChatListener, which reads the same prefix.
 *
 * On top of that, the tag also floats two extra lines above the player's
 * head — item name, then odds — as a chain of invisible text displays
 * riding the player. Mounted passengers are moved by the server
 * automatically, so no per-tick position syncing is needed. Text displays
 * (rather than armor stands) let us set an exact vertical gap via their
 * Transformation instead of guessing at entity bounding-box height.
 */
public class TagManager {

    private static final String TEAM_PREFIX = "solrng_";
    // Vertical gap between stacked lines, in blocks — small and exact,
    // instead of relying on an armor stand's (unpredictable) height.
    private static final float LINE_GAP = 0.27f;

    // index 0 = odds display (rides the player directly), index 1 = item
    // name display (rides the odds display, so it renders highest).
    private final Map<UUID, TextDisplay[]> holograms = new HashMap<>();

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
        hideHologram(player);
    }

    public String getPrefix(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM_PREFIX + shortUuid(player));
        return team == null ? "" : team.getPrefix();
    }

    /**
     * (Re)builds the floating item-name/odds display above the player's
     * head. Safe to call repeatedly (e.g. on join or respawn) — always
     * tears down any previous stands first.
     */
    public void showHologram(Player player, String itemNameColored, String oddsText) {
        hideHologram(player);

        TextDisplay oddsDisplay = spawnLine(player, oddsText);
        TextDisplay nameDisplay = spawnLine(player, itemNameColored);

        player.addPassenger(oddsDisplay);
        oddsDisplay.addPassenger(nameDisplay);

        holograms.put(player.getUniqueId(), new TextDisplay[]{oddsDisplay, nameDisplay});
    }

    public void hideHologram(Player player) {
        removeHologram(player.getUniqueId());
    }

    public void hideHologram(UUID uuid) {
        removeHologram(uuid);
    }

    private void removeHologram(UUID uuid) {
        TextDisplay[] displays = holograms.remove(uuid);
        if (displays == null) return;
        for (TextDisplay display : displays) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
    }

    private TextDisplay spawnLine(Player player, String text) {
        TextDisplay display = (TextDisplay) player.getWorld().spawnEntity(player.getEyeLocation(), EntityType.TEXT_DISPLAY);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setShadowRadius(0f);
        display.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
        display.setTransformation(new Transformation(
                new Vector3f(0f, LINE_GAP, 0f),
                new Quaternionf(),
                new Vector3f(1f, 1f, 1f),
                new Quaternionf()
        ));
        return display;
    }

    private String shortUuid(Player player) {
        // Team names are capped at 16 chars pre-1.18 but Paper 1.21 allows longer;
        // still keep it short and unique.
        return player.getUniqueId().toString().substring(0, 12);
    }
}
