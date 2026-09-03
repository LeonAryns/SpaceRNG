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
 * Manages the equipped-tag scoreboard team prefix (item tag only — no
 * level/prestige, see {@link #levelBadge}) and the floating item-name/odds
 * hologram above a player's head.
 *
 * Each player gets their own personal {@link Scoreboard} (see
 * ScoreboardManager, for the per-player sidebar) — a player can only be
 * subscribed to ONE Scoreboard at a time, so a team registered only on
 * Bukkit's shared "main" scoreboard is invisible to every player once
 * they're switched onto their own board. That was the root cause of tags
 * not showing in the nametag/tab list/chat for anyone: teams were being
 * written to the main scoreboard while everyone was actually looking at
 * their own personal one. Fix: every team is mirrored onto every online
 * player's own Scoreboard object directly (see {@link #pushTeamToAllViewers}
 * and {@link #syncAllTeamsTo}, the latter called right after a player is
 * handed a fresh personal board on join).
 *
 * Level/Prestige is intentionally NOT part of the team prefix (so it never
 * shows above a player's head, in chat, or in the vanilla tab list) — it's
 * exposed only via the %solrng_level% PlaceholderAPI placeholder, for TAB
 * (or similar) to place in its own tab-list-only format.
 *
 * The equipped tag also floats two extra lines above the player's head —
 * mounted as chained TextDisplay passengers (zero temporal lag — the
 * client attaches passengers to their vehicle every render frame, not
 * tick-by-tick — unlike any teleport-polling approach, which always trails
 * by at least one tick). Height is controlled purely via each display's
 * own Transformation, independent of whatever native offset the mount
 * chain computes.
 */
public class TagManager {

    private static final String TEAM_PREFIX = "solrng_";
    // Local Y offset (in the entity's own render space) for each display,
    // stacked on top of the mount chain. Generous values to confidently
    // clear the vanilla nameplate and the item name text itself.
    private static final float TOP_OFFSET = 0.70f;
    private static final float BOTTOM_OFFSET = 0.50f;

    private final SolRNGPlugin plugin;
    // index 0 = bottom display (rides the player), index 1 = top display
    // (rides the bottom display, rendering highest).
    private final Map<UUID, TextDisplay[]> holograms = new HashMap<>();
    // Cached equipped-tag prefix text per player, so a newly-joined
    // player's fresh Scoreboard can be backfilled with everyone else's
    // team without needing to ask each of them to refresh.
    private final Map<UUID, String> prefixCache = new HashMap<>();

    public TagManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Rebuilds the player's scoreboard team prefix (equipped tag only)
     * and pushes it onto every online player's own Scoreboard. Call this
     * any time the equipped tag changes.
     */
    public void refreshPrefix(Player player, PlayerData data) {
        String prefix = buildTagPrefix(data);
        prefixCache.put(player.getUniqueId(), prefix);
        pushTeamToAllViewers(player, prefix);
    }

    private String buildTagPrefix(PlayerData data) {
        if (data.getEquippedTagItemKey() == null || data.getEquippedTagRarity() == null) return "";
        String color = plugin.getRarityManager().colorFor(Rarity.valueOf(data.getEquippedTagRarity()));
        return color + "[" + data.getEquippedTagItemKey() + "] " + ChatColor.RESET;
    }

    private void pushTeamToAllViewers(Player subject, String prefix) {
        String teamName = TEAM_PREFIX + shortUuid(subject);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            applyTeam(viewer.getScoreboard(), teamName, prefix, subject.getName());
        }
    }

    private void applyTeam(Scoreboard board, String teamName, String prefix, String entryName) {
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.setPrefix(prefix);
        if (!team.hasEntry(entryName)) {
            team.addEntry(entryName);
        }
    }

    /**
     * Backfills a player's brand-new personal Scoreboard (just handed to
     * them via player.setScoreboard()) with every other online player's
     * cached tag team — otherwise they'd see nobody's tag, including
     * their own, until someone re-equips.
     */
    public void syncAllTeamsTo(Player viewer) {
        Scoreboard board = viewer.getScoreboard();
        for (Player subject : Bukkit.getOnlinePlayers()) {
            String prefix = prefixCache.getOrDefault(subject.getUniqueId(), "");
            applyTeam(board, TEAM_PREFIX + shortUuid(subject), prefix, subject.getName());
        }
    }

    /** Drops the cached prefix so it isn't replayed onto future joiners. */
    public void forgetPrefix(UUID uuid) {
        prefixCache.remove(uuid);
    }

    public void clearTag(Player player, PlayerData data) {
        data.clearEquippedTag();
        refreshPrefix(player, data);
        hideHologram(player);
    }

    /** The equipped-tag prefix only — no level/prestige. Used in chat. */
    public String getPrefix(Player player) {
        return prefixCache.getOrDefault(player.getUniqueId(), "");
    }

    /**
     * Compact Level/Prestige badge, e.g. "[P2] Lv23" or just "Lv5". Not
     * part of the team prefix — exposed only via %solrng_level% for TAB's
     * own tab-list-only format, so it never shows in chat, nametags, or
     * join/quit messages.
     */
    public String levelBadge(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        if (data.getPrestige() > 0) {
            sb.append(ChatColor.AQUA).append("[P").append(data.getPrestige()).append("] ");
        }
        sb.append(ChatColor.GRAY).append("Lv").append(data.getLevel());
        return sb.toString();
    }

    /**
     * (Re)builds the floating item-name/odds display above the player's
     * head. Safe to call repeatedly (e.g. on join or respawn) — always
     * tears down any previous displays first. itemNameColored renders on
     * the BOTTOM (closer to the player), oddsText renders on TOP.
     */
    public void showHologram(Player player, String itemNameColored, String oddsText) {
        hideHologram(player);

        TextDisplay bottomDisplay = spawnLine(player, itemNameColored, BOTTOM_OFFSET);
        TextDisplay topDisplay = spawnLine(player, oddsText, TOP_OFFSET);

        player.addPassenger(bottomDisplay);
        bottomDisplay.addPassenger(topDisplay);

        holograms.put(player.getUniqueId(), new TextDisplay[]{bottomDisplay, topDisplay});
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
