package com.spacerng.solrng.tag;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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
 * head — item name, then odds — as a chain of invisible marker armor
 * stands riding the player. Mounted passengers are moved by the server
 * automatically, so no per-tick position syncing is needed.
 */
public class TagManager {

    private static final String TEAM_PREFIX = "solrng_";

    // index 0 = odds stand (rides the player directly), index 1 = item
    // name stand (rides the odds stand, so it renders highest).
    private final Map<UUID, ArmorStand[]> holograms = new HashMap<>();

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

        ArmorStand oddsStand = spawnLine(player, oddsText);
        ArmorStand nameStand = spawnLine(player, itemNameColored);

        player.addPassenger(oddsStand);
        oddsStand.addPassenger(nameStand);

        holograms.put(player.getUniqueId(), new ArmorStand[]{oddsStand, nameStand});
    }

    public void hideHologram(Player player) {
        removeHologram(player.getUniqueId());
    }

    public void hideHologram(UUID uuid) {
        removeHologram(uuid);
    }

    private void removeHologram(UUID uuid) {
        ArmorStand[] stands = holograms.remove(uuid);
        if (stands == null) return;
        for (ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
    }

    private ArmorStand spawnLine(Player player, String text) {
        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getEyeLocation(), EntityType.ARMOR_STAND);
        // NOT marker — marker armor stands report zero bounding-box height,
        // which is what the client uses to space out stacked passengers.
        // With marker=true both lines rendered on top of each other.
        stand.setSmall(true);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setPersistent(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomNameVisible(true);
        stand.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
        return stand;
    }

    private String shortUuid(Player player) {
        // Team names are capped at 16 chars pre-1.18 but Paper 1.21 allows longer;
        // still keep it short and unique.
        return player.getUniqueId().toString().substring(0, 12);
    }
}
