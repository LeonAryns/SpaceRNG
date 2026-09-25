package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Where the two pieces that hang in front of a roller are put: the item
 * or question mark, and the odds.
 *
 * There are two ways to do this and the plugin has now tried both.
 *
 * <b>Pinned</b> mounts the piece on the player and lets the CLIENT place
 * it: a display with billboard CENTER applies its transformation after
 * turning to face the camera, so a translation in that transformation is
 * measured in the camera's own frame and the piece is rebuilt in front of
 * the eyes every frame with no lag whatsoever. It is the better of the
 * two when it works.
 *
 * <b>World</b> works out the same spot in ordinary world coordinates and
 * teleports the piece there every tick. One tick behind a fast head turn,
 * and impossible to get wrong.
 *
 * <b>Pinned is the default, and V198 was wrong to change that.</b> The
 * reasoning then was that every piece Leon could not see was a pinned one,
 * so the convention behind pinning must be the suspect. He answered it in
 * one line: "die playerhead moet niet zo moeilijk zijn want dat is toch
 * gwn hetzelfde als alle andere items bij de rolling animation". The reel
 * shows its candidates through the very same pinned display on every
 * ordinary roll, so pinning demonstrably works and rebuilding it was
 * solving a problem that was not there. What the cutscene path actually
 * did differently was set the item once instead of every reel step, and
 * gate itself behind a switch the reel does not use.
 *
 * World stays available for the day pinning really is the problem, and
 * because it is the only way to move these two pieces about without a
 * jar: `roll-item.comet.screen.mode: world`.
 */
final class ScreenSpot {

    private ScreenSpot() {
    }

    /** True while the pieces should ride the player instead of being teleported. */
    static boolean pinned(SolRNGPlugin plugin) {
        return "pinned".equalsIgnoreCase(
                plugin.getConfig().getString("roll-item.comet.screen.mode", "pinned"));
    }

    static double ahead(SolRNGPlugin plugin) {
        return plugin.getConfig().getDouble("roll-item.comet.screen.ahead", 1.6);
    }

    static double itemDown(SolRNGPlugin plugin) {
        return plugin.getConfig().getDouble("roll-item.comet.screen.item-down", 0.45);
    }

    static double counterUp(SolRNGPlugin plugin) {
        return plugin.getConfig().getDouble("roll-item.comet.screen.counter-up", 0.0);
    }

    /**
     * The world spot {@code ahead} blocks in front of a player's eyes and
     * {@code up} blocks above that line, with no rotation on it, because a
     * display copies the rotation of wherever it is put and these two turn
     * to face the viewer on their own.
     */
    static Location inFront(Player player, double ahead, double up) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        Location at = eye.clone().add(look.multiply(ahead)).add(0.0, up, 0.0);
        at.setYaw(0f);
        at.setPitch(0f);
        return at;
    }
}
