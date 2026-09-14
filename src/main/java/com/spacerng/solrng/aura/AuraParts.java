package com.spacerng.solrng.aura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Builds the pieces auras are made of. Every piece is non-persistent, so it
 * is never written to a chunk, and carries the aura tag, so a startup sweep
 * can find anything a crash left behind.
 *
 * Offsets are relative to where a passenger rides a player, which is about
 * the top of the head: the feet are roughly 1.75 below it.
 *
 * Radius on a text piece comes from spaces inside the text. A centred line
 * of one glyph followed by n spaces puts the glyph's centre 2n font pixels
 * off the axis; a glyph, n spaces and a glyph puts both 4 + 2n off it. One
 * font pixel is 0.025 blocks at scale 1. Item pieces are centred on their
 * own origin, so they orbit by moving their translation instead.
 */
public final class AuraParts {

    public static final float FEET = -1.74f;

    /** How high a passenger rides above the feet of a standing player. */
    public static final float RIDE = 1.8f;

    private final NamespacedKey tag;

    AuraParts(NamespacedKey tag) {
        this.tag = tag;
    }

    /**
     * A fullbright text display. Glyphs are the only piece that takes any
     * RGB colour, which is what gives every rarity its own aura colour.
     */
    public TextDisplay text(Player player, String text, Color color, Transformation pose) {
        return player.getWorld().spawn(level(player), TextDisplay.class, display -> {
            common(display, pose);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.text(Component.text(text, TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
        });
    }

    /** A fullbright item or block model, centred on its own origin so it can tumble in place. */
    public ItemDisplay item(Player player, Material material, Transformation pose) {
        return player.getWorld().spawn(level(player), ItemDisplay.class, display -> {
            common(display, pose);
            display.setItemStack(new ItemStack(material));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        });
    }

    /**
     * The player's location with no rotation. A display copies the yaw and
     * pitch of where it spawns, and an aura put on while looking down would
     * otherwise hang tilted for as long as it was worn.
     */
    private static Location level(Player player) {
        Location at = player.getLocation();
        at.setYaw(0f);
        at.setPitch(0f);
        return at;
    }

    private void common(Display display, Transformation pose) {
        display.setPersistent(false);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(0.6f);
        display.setShadowRadius(0f);
        display.setTransformation(pose);
        display.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
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
        return at(0f, y, 0f, rotation, scale);
    }

    public static Transformation at(float x, float y, float z, Quaternionf rotation, float scale) {
        return new Transformation(new Vector3f(x, y, z), rotation,
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** Like {@link #at}, stretched differently on each axis, for beams and plates. */
    public static Transformation atScaled(float x, float y, float z, Quaternionf rotation,
                                          float sx, float sy, float sz) {
        return new Transformation(new Vector3f(x, y, z), rotation, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /** Standing up, turned about the vertical axis. */
    public static Transformation upright(float y, float angle, float scale) {
        return pose(y, new Quaternionf().rotateY(angle), scale);
    }

    /** Laid flat first, then turned about the vertical axis, so the spin stays level. */
    public static Transformation flat(float y, float angle, float scale) {
        return pose(y, flatRotation(angle), scale);
    }

    /** The rotation {@link #flat} uses, for working out where its glyphs are. */
    public static Quaternionf flatRotation(float angle) {
        return new Quaternionf().rotateY(angle).rotateX(rad(-90));
    }

    /** Spun about its own axis, then tipped by {@code tilt}, so the orbit runs on a slanted circle. */
    public static Transformation tilted(float y, Quaternionf tilt, float angle, float scale) {
        return pose(y, new Quaternionf(tilt).rotateY(angle), scale);
    }

    /**
     * Where a pair display's two glyphs sit, relative to the feet, run
     * through the same rotation the display is drawn with. The glyph's
     * centre is about four and a half font pixels above the text baseline.
     */
    public static void pairStars(List<Vector> out, float y, Quaternionf rotation, int spaces, float scale) {
        float radius = (4 + 2 * spaces) * 0.025f * scale;
        float lift = 4.5f * 0.025f * scale;
        for (int side = -1; side <= 1; side += 2) {
            Vector3f point = rotation.transform(new Vector3f(side * radius, lift, 0f));
            out.add(new Vector(point.x, RIDE + y + point.y, point.z));
        }
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
