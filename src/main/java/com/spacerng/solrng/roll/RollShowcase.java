package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Where it is put is {@link ScreenSpot}'s decision. Pinned, which is the
 * default and what the reel has always used, it rides the player and the
 * client rebuilds the offset from its own camera every frame. In world
 * mode it is teleported to the spot in front of their eyes every tick,
 * which is a tick behind a fast head turn and cannot be got wrong.
 *
 * Only the roller ever sees it; to anybody else it would be an item
 * hanging in front of someone's face.
 */
public final class RollShowcase {

    private static final float SIZE = 0.34f;
    /*
     * A note on the question mark head, measured off Leon's screenshot of
     * V199 rather than reasoned about, because two rounds of reasoning
     * about it were wrong.
     *
     * In that shot the head is plainly there and two things are off. It is
     * about two and a half percent of the screen's height, where a thing
     * the whole roll is about wants ten or more: hence
     * roll-item.comet.head-scale, which is a straight multiplier on its
     * size. And it sits left of and below centre by very close to the
     * offset V197 added to "centre a corner origin model", which is the
     * measurement that says the model was already centred and that offset
     * was putting it off centre. It is gone.
     *
     * What is left is a size and a half turn: a skull's face is on the
     * north side of its own model and a billboarded display turns its
     * local +Z at the camera, so without the turn all anybody sees is the
     * back of a black head, which is exactly what the screenshot shows.
     */
    private static final float LANDED_SIZE = 0.5f;
    // The landing pops past its size, then settles while it starts to turn.
    private static final float PULSE_SIZE = 0.64f;
    private static final long PULSE_TICKS = 3L;
    // A quarter turn per update, so the client's shortest-path slerp can
    // never pick the wrong way round; ten ticks each is a turn every two seconds.
    private static final long SPIN_EVERY = 10L;

    // Pinned only: a display with billboard CENTER reads its translation in
    // the camera's frame, minus Z ahead and minus Y down the screen.
    private static final float AHEAD = 1.5f;
    // How far under the middle of the screen the drop hangs. 0.55 sat
    // low enough to read as the bottom of the screen rather than as the
    // thing you are looking at.
    private static final float BELOW = 0.38f;
    /** A player's passengers sit at the top of the hitbox, above the eyes. */
    private static final float RIDE_ABOVE_EYES = 0.18f;

    private final SolRNGPlugin plugin;
    private final Player player;
    private final ItemDisplay display;
    private final boolean pinned;
    private final BukkitTask watch;
    private BukkitTask spin;
    private int quarterTurns = 0;
    private boolean landed = false;
    /**
     * Whether what is being held is a block shaped model rather than a
     * flat sprite. A player head is the only one the reel ever shows, and
     * it is the only one that needs a size of its own and a half turn to
     * put its face towards the camera.
     */
    private boolean blockShaped = false;
    /**
     * How much bigger than its base the piece is drawn right now.
     *
     * One per act of a reveal, so the question mark starts small and
     * swells with every band it survives: "laat de playerhead een beetje
     * bewegen dus van klein naar groot bij elke stage". The client
     * interpolates between the two sizes, so a step is a swell rather
     * than a jump.
     */
    private float growth = 1f;

    private RollShowcase(SolRNGPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.pinned = ScreenSpot.pinned(plugin);
        // Bedrock has no item displays (V223), so it gets no showcase at
        // all. The reel still reaches it: every frame is also a title.
        if (com.spacerng.solrng.platform.Bedrock.is(player)) {
            this.display = null;
            this.watch = null;
            return;
        }
        Location at = spot();
        this.display = player.getWorld().spawn(at, ItemDisplay.class, piece -> {
            piece.setPersistent(false);
            piece.setBillboard(Display.Billboard.CENTER);
            piece.setBrightness(new Display.Brightness(15, 15));
            piece.setShadowRadius(0f);
            piece.setViewRange(0.5f);
            // A teleported piece is smoothed over the tick between updates;
            // a pinned one is carried by its ride and must not be.
            piece.setTeleportDuration(pinned ? 0 : 1);
            piece.setTransformation(pose(SIZE, 0));
            // The aura tag, so the aura sweep on startup clears one a crash left.
            piece.getPersistentDataContainer().set(SolRNGPlugin.key("solrng_aura"), PersistentDataType.BYTE, (byte) 1);
            // Only the roller sees it.
            piece.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        if (pinned) player.addPassenger(display);
        // Pinned needs only the occasional check that it is still riding;
        // teleported has to be put back in front of the eyes every tick.
        this.watch = plugin.getServer().getScheduler().runTaskTimer(plugin, this::watch,
                1L, pinned ? 5L : 1L);
    }

    public static RollShowcase start(SolRNGPlugin plugin, Player player) {
        return new RollShowcase(plugin, player);
    }

    /** Shows one frame of the reel; the landing frame also swells the item. */
    public void show(ItemStack item, boolean land) {
        if (display == null || !display.isValid()) return;
        boolean wasBlockShaped = blockShaped;
        blockShaped = item != null && item.getType() == Material.PLAYER_HEAD;
        display.setItemStack(item);
        // A head has to be re-posed the moment it arrives, or it hangs in
        // the wrong place until something else moves it.
        if (blockShaped != wasBlockShaped && !land) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setTransformation(pose(SIZE, quarterTurns));
        }
        if (land && !landed) {
            landed = true;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration((int) PULSE_TICKS);
            display.setTransformation(pose(PULSE_SIZE, 0));
            spin = plugin.getServer().getScheduler().runTaskTimer(plugin, this::spinStep, PULSE_TICKS, SPIN_EVERY);
        }
    }

    /**
     * Grows or shrinks the piece, over four ticks so it reads as a swell.
     * Ignored once the drop has landed, since the landing has a size of
     * its own and a spin to go with it.
     */
    public void grow(float factor) {
        if (display == null || !display.isValid() || landed || Math.abs(factor - growth) < 0.01f) return;
        growth = factor;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(4);
        display.setTransformation(pose(SIZE, quarterTurns));
    }

    /** One quarter turn at the landed size; the first one also settles the pulse. */
    private void spinStep() {
        if (display == null || !display.isValid()) return;
        quarterTurns = (quarterTurns + 1) % 4;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration((int) SPIN_EVERY);
        display.setTransformation(pose(LANDED_SIZE, quarterTurns));
    }

    /** Holds the landed item for {@code holdTicks}, then clears it away. */
    public void finish(long holdTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cancel, Math.max(1L, holdTicks));
    }

    /** Removes it now. Safe to call more than once. */
    public void cancel() {
        if (watch != null) watch.cancel();
        if (spin != null) spin.cancel();
        if (display != null && display.isValid()) display.remove();
    }

    /**
     * Keeps it where it belongs. Teleported, that is every tick in front
     * of the eyes; pinned, it is only a check that a teleport or a death
     * has not dropped the passenger.
     */
    private void watch() {
        if (!player.isOnline() || !display.isValid()) {
            cancel();
            return;
        }
        if (!display.getWorld().equals(player.getWorld())) {
            cancel();
            return;
        }
        if (!pinned) {
            display.teleport(spot());
            return;
        }
        if (display.getVehicle() == null || !display.getVehicle().equals(player)) {
            display.teleport(player.getLocation().setRotation(0f, 0f));
            player.addPassenger(display);
        }
    }

    /** Where the piece goes: on the player when pinned, in front of them otherwise. */
    /** How much bigger a head is drawn than a sprite, from config. */
    private float headScale() {
        return (float) Math.max(0.5, plugin.getConfig().getDouble("roll-item.comet.head-scale", 2.0));
    }

    private Location spot() {
        if (pinned) {
            Location at = player.getLocation();
            at.setYaw(0f);
            at.setPitch(0f);
            return at;
        }
        return ScreenSpot.inFront(player, ScreenSpot.ahead(plugin), -ScreenSpot.itemDown(plugin));
    }

    /**
     * The pose. Pinned, it carries the offset in the camera's frame; in
     * world mode the teleport has already done that and only the size is
     * left. Either way a corner origin model is pulled back by half its
     * own scaled size, because the scale is applied before the translation.
     */
    private Transformation pose(float scale, int quarterTurns) {
        float size = (blockShaped ? scale * headScale() : scale) * growth;
        Vector3f offset = pinned
                ? new Vector3f(0f, -BELOW - RIDE_ABOVE_EYES, -AHEAD)
                : new Vector3f(0f, 0f, 0f);
        double turn = Math.PI / 2 * quarterTurns + (blockShaped ? Math.PI : 0.0);
        return new Transformation(offset,
                new Quaternionf().rotateY((float) turn),
                new Vector3f(size, size, size), new Quaternionf());
    }
}
