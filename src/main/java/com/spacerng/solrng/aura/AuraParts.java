package com.spacerng.solrng.aura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Builds the pieces auras are made of. Every piece is non-persistent, so it
 * is never written to a chunk, and carries the aura tag, so a startup sweep
 * can find anything a crash left behind.
 *
 * Offsets are relative to where a passenger rides a player, which is about
 * the top of the head: the feet are roughly 1.75 below it.
 *
 * Radius comes from spaces inside the text. A centred line of one glyph
 * followed by n spaces puts the glyph's centre 2n font pixels off the
 * axis; a glyph, n spaces and a glyph puts both 4 + 2n off it. One font
 * pixel is 0.025 blocks at scale 1.
 */
public final class AuraParts {

    public static final float FEET = -1.74f;

    private final NamespacedKey tag;

    AuraParts(NamespacedKey tag) {
        this.tag = tag;
    }

    /**
     * A fullbright text display. Glyphs are the only piece that takes any
     * RGB colour, which is what gives every rarity its own aura colour.
     */
    public TextDisplay text(Player player, String text, Color color, Transformation pose) {
        // Spawned level. At the player's own location the display would copy
        // their yaw and pitch, and an aura put on while looking down would
        // hang tilted for as long as it was worn.
        Location at = player.getLocation();
        at.setYaw(0f);
        at.setPitch(0f);
        return player.getWorld().spawn(at, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(0.6f);
            display.setShadowRadius(0f);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.text(Component.text(text, TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
            display.setTransformation(pose);
            display.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** One glyph pushed n spaces off the axis. */
    public static String single(String glyph, int spaces) {
        return glyph + " ".repeat(spaces);
    }

    /** Two glyphs on opposite sides of the axis. */
    public static String pair(String glyph, int spaces) {
        return glyph + " ".repeat(spaces) + glyph;
    }

    public static Transformation pose(float y, Quaternionf rotation, float scale) {
        return new Transformation(new Vector3f(0f, y, 0f), rotation,
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** Standing up, turned about the vertical axis. */
    public static Transformation upright(float y, float angle, float scale) {
        return pose(y, new Quaternionf().rotateY(angle), scale);
    }

    /** Laid flat first, then turned about the vertical axis, so the spin stays level. */
    public static Transformation flat(float y, float angle, float scale) {
        return pose(y, new Quaternionf().rotateY(angle).rotateX(rad(-90)), scale);
    }

    /** Spun about its own axis, then tipped by {@code tilt}, so the orbit runs on a slanted circle. */
    public static Transformation tilted(float y, Quaternionf tilt, float angle, float scale) {
        return pose(y, new Quaternionf(tilt).rotateY(angle), scale);
    }

    /** Sends a new pose that the client glides to over {@code ticks}; 0 snaps. */
    public static void move(Display display, Transformation pose, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(pose);
    }

    /** Halfway between a colour and warm near-white, for a second, softer layer. */
    public static Color softer(Color color) {
        return Color.fromRGB((color.getRed() + 255) / 2, (color.getGreen() + 252) / 2, (color.getBlue() + 224) / 2);
    }

    public static float rad(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
