package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The item a roll is on, floating in front of the roller's eyes. It swaps
 * with every frame of the reel and swells when it lands, so the drop is
 * something you see and not only a name on the screen.
 *
 * It rides the player, and its offset lives in the transformation. A
 * display with billboard CENTER applies its transformation after turning
 * to the camera, so the translation is measured in the camera's own frame:
 * minus Z is straight ahead, minus Y is down the screen. The client
 * rebuilds that every frame from its own camera, and a passenger of the
 * local player moves with it every frame too, so the item is pinned to the
 * screen with no lag at all. Moving it by teleport trailed a tick or two
 * behind every turn of the head.
 */
public final class RollShowcase {

    private static final float AHEAD = 1.5f;
    // The client pins the title and subtitle to the middle of the screen, so
    // the item goes low enough that even landed it clears the subtitle.
    private static final float BELOW = 0.55f;
    // A player's passengers sit at the top of the hitbox, above the eyes.
    private static final float RIDE_ABOVE_EYES = 0.18f;
    private static final float SIZE = 0.34f;
    private static final float LANDED_SIZE = 0.5f;

    private final SolRNGPlugin plugin;
    private final Player player;
    private final ItemDisplay display;
    private final BukkitTask watch;
    private boolean landed = false;

    private RollShowcase(SolRNGPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        Location at = player.getLocation();
        at.setYaw(0f);
        at.setPitch(0f);
        this.display = player.getWorld().spawn(at, ItemDisplay.class, piece -> {
            piece.setPersistent(false);
            piece.setBillboard(Display.Billboard.CENTER);
            piece.setBrightness(new Display.Brightness(15, 15));
            piece.setShadowRadius(0f);
            piece.setViewRange(0.5f);
            piece.setTransformation(pose(SIZE));
            // The aura tag, so the aura sweep on startup clears one a crash left.
            piece.getPersistentDataContainer().set(SolRNGPlugin.key("solrng_aura"), PersistentDataType.BYTE, (byte) 1);
            // Only the roller sees it; to anyone else it was an item hanging in front of someone's face.
            piece.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        player.addPassenger(display);
        this.watch = plugin.getServer().getScheduler().runTaskTimer(plugin, this::watch, 5L, 5L);
    }

    public static RollShowcase start(SolRNGPlugin plugin, Player player) {
        return new RollShowcase(plugin, player);
    }

    /** Shows one frame of the reel; the landing frame also swells the item. */
    public void show(ItemStack item, boolean land) {
        if (!display.isValid()) return;
        display.setItemStack(item);
        if (land && !landed) {
            landed = true;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(6);
            display.setTransformation(pose(LANDED_SIZE));
        }
    }

    /** Holds the landed item for {@code holdTicks}, then clears it away. */
    public void finish(long holdTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cancel, Math.max(1L, holdTicks));
    }

    /** Removes it now. Safe to call more than once. */
    public void cancel() {
        watch.cancel();
        if (display.isValid()) display.remove();
    }

    /** A teleport or a world change drops passengers; put it back, or give up. */
    private void watch() {
        if (!player.isOnline() || !display.isValid()) {
            cancel();
            return;
        }
        if (display.getVehicle() == null || !display.getVehicle().equals(player)) {
            if (!display.getWorld().equals(player.getWorld())) {
                cancel();
                return;
            }
            display.teleport(player.getLocation().setRotation(0f, 0f));
            player.addPassenger(display);
        }
    }

    private static Transformation pose(float scale) {
        return new Transformation(new Vector3f(0f, -BELOW - RIDE_ABOVE_EYES, -AHEAD), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }
}
