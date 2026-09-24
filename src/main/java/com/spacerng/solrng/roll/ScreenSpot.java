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
 * World is the default since V198, and the reason is worth writing down.
 * Leon reported three times that he could not see the item, the question
 * mark or the odds, while seeing the comet, the aura and the particles
 * perfectly, and `/rngadmin head` proved the head item itself was fine.
 * Every one of the missing pieces was a pinned one and every piece he
 * could see was not. The camera frame convention behind pinning is the
 * one thing in the reveal that cannot be checked by reading the API, and
 * a sign error in it puts the piece exactly behind the viewer's head,
 * which is indistinguishable from what he described. So the mode that
 * needs no convention is the one that ships, and `pinned` is kept in
 * config for whenever somebody can stand in the game and compare.
 */
final class ScreenSpot {

    private ScreenSpot() {
    }

    /** True while the pieces should ride the player instead of being teleported. */
    static boolean pinned(SolRNGPlugin plugin) {
        return "pinned".equalsIgnoreCase(
                plugin.getConfig().getString("roll-item.comet.screen.mode", "world"));
    }

    static double ahead(SolRNGPlugin plugin) {
        return plugin.getConfig().getDouble("roll-item.comet.screen.ahead", 1.6);
    }

    static double itemDown(SolRNGPlugin plugin) {
        return plugin.getConfig().getDouble("roll-item.comet.screen.item-down", 0.45);
    }

    static double counterUp(SolRNGPlugin plugin) {
        return plugin.getConfig().getDouble("roll-item.comet.screen.counter-up", 0.35);
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
