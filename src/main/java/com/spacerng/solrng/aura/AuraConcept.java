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

    /**
     * True for a look that should turn with the wearer's HEAD rather than
     * their body. Both yaws exist and they are not the same: the body
     * lags the head and snaps to it, so wings on the body swing late and
     * then jump. Wings sit where somebody is looking.
     *
     * Only read when {@link #followsBody()} is also true.
     */
    default boolean followsHead() {
        return false;
    }

    /**
     * True for looks that stay out of the wearer's own first person view:
     * down at the feet, up over the head, out behind the back, or far
     * enough out that the wearer looks straight through them. With the
     * middle own-aura setting in /options a wearer sees only these, and
     * everybody else still sees the whole thing.
     *
     * It is about where the look sits, not how big it is. A ring two
     * blocks out at chest height is clear, because there is nothing
     * within arm's reach of the eyes; a column of light standing through
     * the head is not, however thin it is.
     */
    default boolean clearOfView() {
        return false;
    }

    /** A piece everybody sees under the usual rules. */
    int EVERYONE = 0;
    /** A piece everybody sees except its wearer in the middle setting. */
    int SHARED = 1;
    /** A piece only its wearer sees, and only in the middle setting. */
    int OWN = 2;

    /**
     * Who sees piece {@code index}. Almost every look is EVERYONE; a look
     * with a second, low version for its own wearer (the Divine lanterns,
     * V210) splits into SHARED and OWN, because a display has one pose for
     * every viewer and the only way to show two is to spawn both.
     */
    default int audienceAt(int index) {
        return EVERYONE;
    }
}
