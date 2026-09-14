package com.spacerng.solrng.aura;

import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * One look an aura can take. It builds its display entities once and then
 * only nudges their transformations now and then; the client interpolates
 * between nudges, so the server never moves anything tick by tick.
 */
public interface AuraConcept {

    /** Spawns this look's displays at the player. The manager mounts and tracks them. */
    List<Display> spawn(Player player, AuraParts parts);

    /** Called every 2 ticks while worn; {@code frame} counts those calls from 0. */
    void tick(List<Display> displays, long frame);

    /**
     * Where this look's stars are at {@code frame}, relative to the feet,
     * for accents that follow them. A look that can't say leaves it empty.
     */
    default void stars(long frame, List<org.bukkit.util.Vector> out) {
    }

    /**
     * True for looks that hang off one side of the body, like wings, and so
     * must turn with it. The manager then sets every piece's yaw to the
     * body's whenever it turns.
     */
    default boolean followsBody() {
        return false;
    }
}
