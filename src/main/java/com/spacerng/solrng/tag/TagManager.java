package com.spacerng.solrng.tag;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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
 * item name, then odds — as two text displays whose position is explicitly
 * re-synced to the player every couple of ticks. This was previously done
 * by mounting them as passengers and letting Minecraft auto-position them,
 * but the native mount-offset it computes for an arbitrary entity (rather
 * than a real vehicle seat) turned out to be unpredictable. Direct
 * positioning removes that guesswork.
 */
public class TagManager {

    private static final String TEAM_PREFIX = "solrng_";
    // Height above the player's feet, in blocks. The vanilla nameplate
    // sits at roughly 2.3-2.4, so these clear it with a small margin.
    private static final double ODDS_LINE_HEIGHT = 2.65;
    private static final double NAME_LINE_HEIGHT = 2.95;

    private final SolRNGPlugin plugin;
    // index 0 = odds display, index 1 = item name display (rendered above it).
    private final Map<UUID, TextDisplay[]> holograms = new HashMap<>();

    public TagManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /** Keeps every active hologram glued to its player's current position. */
    public void startSyncTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, TextDisplay[]> entry : holograms.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) continue;

                TextDisplay[] displays = entry.getValue();
                Location base = player.getLocation();
                displays[0].teleport(withHeight(base, ODDS_LINE_HEIGHT));
                displays[1].teleport(withHeight(base, NAME_LINE_HEIGHT));
            }
        }, 0L, 2L);
    }

    private Location withHeight(Location playerFeet, double height) {
        Location loc = playerFeet.clone();
        loc.setY(loc.getY() + height);
        return loc;
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

        TextDisplay oddsDisplay = spawnLine(player, oddsText, ODDS_LINE_HEIGHT);
        TextDisplay nameDisplay = spawnLine(player, itemNameColored, NAME_LINE_HEIGHT);

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

    private TextDisplay spawnLine(Player player, String text, double height) {
        TextDisplay display = (TextDisplay) player.getWorld().spawnEntity(
                withHeight(player.getLocation(), height), EntityType.TEXT_DISPLAY);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setShadowRadius(0f);
        // Makes the client smoothly interpolate between teleports instead
        // of snapping — without this the sync task's every-2-tick
        // repositioning looked jerky/laggy rather than glued to the player.
        display.setTeleportDuration(3);
        display.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
        return display;
    }

    private String shortUuid(Player player) {
        // Team names are capped at 16 chars pre-1.18 but Paper 1.21 allows longer;
        // still keep it short and unique.
        return player.getUniqueId().toString().substring(0, 12);
    }
}
