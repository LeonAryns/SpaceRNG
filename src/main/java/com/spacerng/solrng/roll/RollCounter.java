package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The odds, hanging in front of the roller while a comet falls.
 *
 * It is a display entity rather than a title for one reason: a title has
 * exactly one size and nothing in the API can change it, and this number
 * has to GROW. Leon asked for it to jump out of the screen when it
 * crosses into the next rarity's odds, and a display can be scaled to
 * anything with the client interpolating between the sizes for free.
 *
 * Where it is put is {@link ScreenSpot}'s decision, and it is teleported
 * in front of the eyes every tick by default rather than pinned through
 * the camera's frame. See that class for why.
 *
 * Shown to the roller and to nobody else, like everything else in the
 * cutscene.
 */
final class RollCounter {

    /** How far a promotion throws the number out before it settles back. */
    private static final float POP_FACTOR = 1.5f;
    private static final int POP_TICKS = 3;
    private static final int SETTLE_TICKS = 7;

    // Pinned only: the camera frame offset, minus Y being down the screen.
    private static final float AHEAD = 1.5f;
    private static final float RIDE_ABOVE_EYES = 0.18f;
    // Half the height of one line of text at scale 1, in blocks.
    private static final float HALF_LINE = 0.125f;
    // How far above the crosshair the middle of the number sits, pinned.
    private static final float ABOVE_CENTRE = 0.28f;

    private final SolRNGPlugin plugin;
    private final Player player;
    private final TextDisplay display;
    private final boolean pinned;
    private final BukkitTask watch;
    private float scale;

    // Bedrock (V223): Geyser draws a text display as a name tag with no
    // scale and no place in front of the camera, so the counter goes up
    // as a title instead, which Bedrock shows big in the middle of the
    // screen. It is sent when the number changes rather than every tick.
    private final boolean titles;
    private Component lastTitle;
    private long lastTitleAt;

    RollCounter(SolRNGPlugin plugin, Player player, float scale) {
        this.plugin = plugin;
        this.player = player;
        this.scale = scale;
        this.pinned = ScreenSpot.pinned(plugin);
        this.titles = com.spacerng.solrng.platform.Bedrock.is(player);
        if (titles) {
            this.display = null;
            this.watch = null;
            return;
        }
        this.display = player.getWorld().spawn(spot(), TextDisplay.class, piece -> {
            piece.setPersistent(false);
            piece.setBillboard(Display.Billboard.CENTER);
            piece.setBrightness(new Display.Brightness(15, 15));
            piece.setShadowRadius(0f);
            piece.setViewRange(0.5f);
            piece.setTeleportDuration(pinned ? 0 : 1);
            piece.setAlignment(TextDisplay.TextAlignment.CENTER);
            // No panel behind it. The default background is a dark box, and
            // a number floating in the sky reads as part of the world while
            // a boxed one reads as a plugin.
            piece.setDefaultBackground(false);
            piece.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            // A drop shadow, because this is text meant to be READ against
            // whatever the player happens to be looking at.
            piece.setShadowed(true);
            piece.setSeeThrough(false);
            piece.setLineWidth(4000);
            piece.setTransformation(pose(scale));
            // The aura tag, so the sweep on startup clears one a crash left.
            piece.getPersistentDataContainer()
                    .set(SolRNGPlugin.key("solrng_aura"), PersistentDataType.BYTE, (byte) 1);
            piece.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        if (pinned) player.addPassenger(display);
        this.watch = plugin.getServer().getScheduler().runTaskTimer(plugin, this::watch,
                1L, pinned ? 5L : 1L);
    }

    /** One ordinary frame of the counter: new text, same size. */
    void show(Component text) {
        if (titles) {
            title(text, false);
            return;
        }
        if (!display.isValid()) return;
        display.text(text);
    }

    /** The counter as a title, for a Bedrock roller. A promotion always goes out and plays a hit. */
    private void title(Component text, boolean promotion) {
        if (!player.isOnline()) return;
        long now = System.currentTimeMillis();
        // A title that is not refreshed fades out, so an unchanged number
        // is sent again once a second to keep it up.
        // A climbing number is sent at most ten times a second.
        if (!promotion && now - lastTitleAt < (text.equals(lastTitle) ? 1000L : 100L)) return;
        lastTitle = text;
        lastTitleAt = now;
        player.showTitle(net.kyori.adventure.title.Title.title(text, Component.empty(),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                        java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(250))));
        if (promotion) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        }
    }

    /**
     * A promotion: the number is thrown out past its new size and pulled
     * back into it. The throw is three ticks and the settle seven, which
     * lands the whole move inside half a second, so it reads as a hit
     * rather than as a zoom.
     */
    void pop(Component text, float newScale) {
        if (titles) {
            title(text, true);
            return;
        }
        if (!display.isValid()) return;
        this.scale = newScale;
        display.text(text);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(POP_TICKS);
        display.setTransformation(pose(newScale * POP_FACTOR));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(SETTLE_TICKS);
            display.setTransformation(pose(this.scale));
        }, POP_TICKS);
    }

    /** Removes it now. Safe to call more than once. */
    void stop() {
        if (titles) return;
        watch.cancel();
        if (display.isValid()) display.remove();
    }

    private void watch() {
        if (!player.isOnline() || !display.isValid()) {
            stop();
            return;
        }
        if (!display.getWorld().equals(player.getWorld())) {
            stop();
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

    private Location spot() {
        if (pinned) {
            Location at = player.getLocation();
            at.setYaw(0f);
            at.setPitch(0f);
            return at;
        }
        return ScreenSpot.inFront(player, ScreenSpot.ahead(plugin), ScreenSpot.counterUp(plugin));
    }

    private Transformation pose(float scale) {
        // Centred on the middle of the screen since V210. A text display
        // grows upward from where it stands, so it goes down by half a line
        // at its own size to put the middle of the number on the eye line.
        // The translation is not scaled with it, which is why the half line
        // is worked out per size, and why a pop stays centred too.
        // V212: a little above the middle, because dead centre put the
        // number behind the crosshair.
        float halfLine = HALF_LINE * scale;
        Vector3f offset = pinned
                ? new Vector3f(0f, ABOVE_CENTRE - halfLine - RIDE_ABOVE_EYES, -AHEAD)
                : new Vector3f(0f, -halfLine, 0f);
        return new Transformation(offset, new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }
}
