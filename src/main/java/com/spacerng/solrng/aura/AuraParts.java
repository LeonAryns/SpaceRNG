package com.spacerng.solrng.aura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
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
        return player.getWorld().spawn(player.getLocation(), TextDisplay.class, display -> {
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

    public static Transformation pose(float y, Quaternionf rotation, float scale) {
        return new Transformation(new Vector3f(0f, y, 0f), rotation,
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** Sends a new pose that the client glides to over {@code ticks}. */
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
