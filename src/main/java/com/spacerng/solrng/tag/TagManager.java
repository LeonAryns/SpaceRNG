package com.spacerng.solrng.tag;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
 * Manages the single scoreboard team prefix each player gets — combining
 * their prestige/level badge and their equipped rarity tag into one string,
 * since a player can only be on one team with one prefix at a time. This
 * shows above the player's head in the world AND in the tab list (TAB, if
 * installed, needs the %solrng_tag% placeholder added to its own tablist
 * format to actually render it there — see SolRNGExpansion). Chat
 * formatting is handled separately by ChatListener, which reads the same
 * prefix.
 *
 * The equipped tag also floats two extra lines above the player's head —
 * item name, then odds — as two chained text displays MOUNTED on the
 * player. This went through two other approaches first:
 *  1. Marker armor stands, chained as passengers — zero native mount
 *     height, so both lines rendered on top of each other.
 *  2. Text displays repositioned every tick via teleport() — precise
 *     height control, but any polling approach has at least one tick of
 *     latency, which was visible as the hologram trailing behind the
 *     player while moving.
 * Mounting has zero temporal lag (the client attaches passengers to their
 * vehicle every render frame, not tick-by-tick), so it's the right
 * mechanism for *following* — the actual problem last time was guessing
 * the wrong Transformation offset, not the mounting approach itself.
 * Height is now controlled purely via each display's Transformation
 * (independent of whatever native offset the mount computes).
 */
public class TagManager {

    private static final String TEAM_PREFIX = "solrng_";
    // Local Y offset (in the entity's own render space) for each display,
    // stacked on top of the mount chain. Generous values to confidently
    // clear the vanilla nameplate.
    private static final float ODDS_OFFSET = 0.55f;
    private static final float NAME_OFFSET = 0.35f;

    private final SolRNGPlugin plugin;
    // index 0 = odds display (rides the player), index 1 = item name
    // display (rides the odds display, rendering highest).
    private final Map<UUID, TextDisplay[]> holograms = new HashMap<>();

    public TagManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Rebuilds the player's scoreboard team prefix from scratch, reading
     * prestige/level and the equipped tag straight from PlayerData. Call
     * this any time either piece changes.
     */
    public void refreshPrefix(Player player, PlayerData data) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = TEAM_PREFIX + shortUuid(player);

        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }

        StringBuilder prefix = new StringBuilder();
        if (data.getPrestige() > 0) {
            prefix.append(ChatColor.AQUA).append("[P").append(data.getPrestige()).append("] ");
        }
        prefix.append(ChatColor.GRAY).append("Lv").append(data.getLevel()).append(' ');

        if (data.getEquippedTagItemKey() != null && data.getEquippedTagRarity() != null) {
            String color = plugin.getRarityManager().colorFor(Rarity.valueOf(data.getEquippedTagRarity()));
            prefix.append(color).append('[').append(data.getEquippedTagItemKey()).append("] ");
        }
        prefix.append(ChatColor.RESET);

        team.setPrefix(prefix.toString());
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    public void clearTag(Player player, PlayerData data) {
        data.clearEquippedTag();
        refreshPrefix(player, data);
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
     * tears down any previous displays first.
     */
    public void showHologram(Player player, String itemNameColored, String oddsText) {
        hideHologram(player);

        TextDisplay oddsDisplay = spawnLine(player, oddsText, ODDS_OFFSET);
        TextDisplay nameDisplay = spawnLine(player, itemNameColored, NAME_OFFSET);

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

    private TextDisplay spawnLine(Player player, String text, float yOffset) {
        TextDisplay display = (TextDisplay) player.getWorld().spawnEntity(player.getLocation(), EntityType.TEXT_DISPLAY);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setShadowRadius(0f);
        display.setTransformation(new Transformation(
                new Vector3f(0f, yOffset, 0f),
                new Quaternionf(),
                new Vector3f(1f, 1f, 1f),
                new Quaternionf()
        ));
        display.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
        return display;
    }

    private String shortUuid(Player player) {
        // Team names are capped at 16 chars pre-1.18 but Paper 1.21 allows longer;
        // still keep it short and unique.
        return player.getUniqueId().toString().substring(0, 12);
    }
}
