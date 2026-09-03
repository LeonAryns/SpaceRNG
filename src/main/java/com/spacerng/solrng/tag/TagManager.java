package com.spacerng.solrng.tag;

import com.spacerng.solrng.SolRNGPlugin;
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
 * Equipping a tag creates/uses a dedicated scoreboard team per player and
 * sets its prefix. This makes the tag show above the player's head in the
 * world AND in the tab list (TAB, if installed, needs the
 * %solrng_tag% placeholder added to its own tablist format to actually
 * render it — see SolRNGExpansion). Chat formatting is handled separately
 * by ChatListener, which reads the same prefix.
 *
 * On top of that, the tag floats two extra lines above the player's head
 * — item name, then odds — as two text displays whose position is
 * explicitly re-synced to the player every couple of ticks. This was
 * previously done by mounting them as passengers and letting Minecraft
 * auto-position them, but the native mount-offset it computes for an
 * arbitrary entity (rather than a real vehicle seat) turned out to be
 * unpredictable — sometimes far too high, sometimes overlapping the
 * player's own nameplate. Direct positioning removes that guesswork.
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
        display.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
        return display;
    }

    private String shortUuid(Player player) {
        // Team names are capped at 16 chars pre-1.18 but Paper 1.21 allows longer;
        // still keep it short and unique.
        return player.getUniqueId().toString().substring(0, 12);
    }
}
