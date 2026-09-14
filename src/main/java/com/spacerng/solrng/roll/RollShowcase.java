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
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The item a roll is on, floating in front of the roller's eyes. It swaps
 * with every frame of the reel and swells when it lands, so the drop is
 * something you see and not only a name on the screen.
 *
 * One item display, not mounted: a passenger can't sit in front of the
 * face, because it doesn't turn with the head. It is moved to a point
 * ahead of the eyes every two ticks and the client glides it there over
 * the same two ticks. It sits below the crosshair so it doesn't cover the
 * title, and faces the camera from any angle.
 */
public final class RollShowcase {

    private static final double AHEAD = 1.5;
    private static final double BELOW = 0.45;
    private static final float SIZE = 0.38f;
    private static final float LANDED_SIZE = 0.62f;

    private final SolRNGPlugin plugin;
    private final Player player;
    private final ItemDisplay display;
    private final BukkitTask follow;
    private boolean landed = false;

    private RollShowcase(SolRNGPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.display = player.getWorld().spawn(target(), ItemDisplay.class, piece -> {
            piece.setPersistent(false);
            piece.setBillboard(Display.Billboard.CENTER);
            piece.setBrightness(new Display.Brightness(15, 15));
            piece.setShadowRadius(0f);
            piece.setViewRange(0.5f);
            piece.setTeleportDuration(2);
            piece.setTransformation(size(SIZE));
            // The aura tag, so the aura sweep on startup clears one a crash left.
            piece.getPersistentDataContainer().set(SolRNGPlugin.key("solrng_aura"), PersistentDataType.BYTE, (byte) 1);
        });
        this.follow = plugin.getServer().getScheduler().runTaskTimer(plugin, this::follow, 2L, 2L);
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
            display.setTransformation(size(LANDED_SIZE));
        }
    }

    /** Holds the landed item for {@code holdTicks}, then clears it away. */
    public void finish(long holdTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cancel, Math.max(1L, holdTicks));
    }

    /** Removes it now. Safe to call more than once. */
    public void cancel() {
        follow.cancel();
        if (display.isValid()) display.remove();
    }

    private void follow() {
        if (!player.isOnline() || !display.isValid() || !display.getWorld().equals(player.getWorld())) {
            cancel();
            return;
        }
        display.teleport(target());
    }

    private Location target() {
        Location eye = player.getEyeLocation();
        Vector ahead = eye.getDirection().multiply(AHEAD);
        Location at = eye.add(ahead).add(0.0, -BELOW, 0.0);
        at.setYaw(0f);
        at.setPitch(0f);
        return at;
    }

    private static Transformation size(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
    }
}
