package com.spacerng.solrng.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who owns the line above the hotbar (V314).
 *
 * There is exactly one action bar per player and three things write to
 * it: the dust find, the "level up available" hint on its three second
 * timer, and the crop watch. The hint wrote blind, so a player with a
 * level waiting - which is every auto-roller, since the level is always
 * waiting while they roll - overwrote the dust line within three seconds
 * of it appearing, usually inside the same second. Leon read that as the
 * dust line not showing up under Auto Roll at all, and he was right.
 *
 * So a message can claim the bar for a while. Anything on a timer asks
 * {@link #isHeld} first and skips that player; the claim lapses on its
 * own, so nothing has to be released and a player who logs out mid-claim
 * costs one stale map entry until they come back.
 */
public final class ActionBar {

    private static final Map<UUID, Long> HELD = new HashMap<>();

    private ActionBar() {
    }

    /** Sends a line and keeps the timers off the bar for holdMillis. */
    public static void send(Player player, Component line, long holdMillis) {
        if (player == null || !player.isOnline()) return;
        HELD.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, holdMillis));
        player.sendActionBar(line);
    }

    /** Whether something is still holding this player's action bar. */
    public static boolean isHeld(Player player) {
        if (player == null) return false;
        Long until = HELD.get(player.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            HELD.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** On quit, so the map does not grow with every player who ever joins. */
    public static void forget(UUID id) {
        HELD.remove(id);
    }
}
