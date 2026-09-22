package com.spacerng.solrng.aura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
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
        Transformation scaled = withPlayerSize(player, pose);
        return player.getWorld().spawn(level(player), TextDisplay.class, display -> {
            common(display, scaled);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(false);
            display.setSeeThrough(false);
            // Wide enough that the widest ground rings never wrap onto a second line.
            display.setLineWidth(4000);
            display.text(Component.text(text, TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
        });
    }

    /**
     * The same glyph card, drawn for the other side when {@code back} is
     * set. A text display exists on one face only, so a card standing up or
     * slanted is simply not there from behind. The half turn is about the
     * card's own upright axis, which leaves it in exactly the same plane
     * and swaps which way it looks; the two are left touching, because only
     * one of them is ever drawn.
     *
     * A pair of glyphs is symmetric, so the turn puts them back where they
     * were. A single glyph is not, and wants {@link #singleBack} to move it
     * to the other side of its own text first.
     */
    public TextDisplay text(Player player, String text, Color color, Transformation pose, boolean back) {
        return text(player, text, color, back ? flipped(pose) : pose);
    }

    /** The same pose spun a half turn about the piece's own upright axis. */
    public static Transformation flipped(Transformation pose) {
        return new Transformation(new Vector3f(pose.getTranslation()),
                new Quaternionf(pose.getLeftRotation()).rotateY((float) Math.PI),
                new Vector3f(pose.getScale()), new Quaternionf(pose.getRightRotation()));
    }

    /** A fullbright item or block model, centred on its own origin so it can tumble in place. */
    public ItemDisplay item(Player player, Material material, Transformation pose) {
        Transformation scaled = withPlayerSize(player, pose);
        return player.getWorld().spawn(level(player), ItemDisplay.class, display -> {
            common(display, scaled);
            display.setItemStack(new ItemStack(material));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        });
    }

    /**
     * An item piece that turns about the upright axis to face whoever is
     * looking, for a flat sprite that would otherwise go thin edge on.
     *
     * Only for a piece standing on the wearer's own axis. Under any
     * billboard but FIXED the client reads the transformation's translation
     * in the camera's frame, not the world's, which is the trick
     * {@code RollShowcase} uses to pin itself to the screen. A billboarded
     * piece given an offset therefore does not orbit the wearer at all: it
     * hangs at that offset from the middle of the viewer's screen and
     * follows them about. VERTICAL leaves the upright axis alone, so a
     * piece only offset in height is safe; anything offset sideways has to
     * stay FIXED and be turned to face outward by hand.
     */
    public ItemDisplay item(Player player, Material material, Transformation pose, boolean faceViewer) {
        ItemDisplay display = item(player, material, pose);
        if (faceViewer) display.setBillboard(Display.Billboard.VERTICAL);
        return display;
    }

    /**
     * A flat plate of solid colour: a text display holding one space, with
     * its background painted instead of its text.
     *
     * This is the only piece that can be any shape AND any colour. A glyph
     * takes a colour but is always a star; an item model is whatever Mojang
     * drew. A painted background is a rectangle, and a rectangle with a
     * matrix on it is a ring segment, a blade, a beam or a wing.
     *
     * Alpha under 26 is dropped by the client, so anything meant to be seen
     * stays above it; 255 is solid.
     */
    public TextDisplay plate(Player player, Color color, int alpha, Matrix4f matrix) {
        Matrix4f scaled = withPlayerSize(player, matrix);
        return player.getWorld().spawn(level(player), TextDisplay.class, display -> {
            common(display, EMPTY);
            display.setTransformationMatrix(scaled);
            display.setBillboard(Display.Billboard.FIXED);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(alpha, color.getRed(), color.getGreen(), color.getBlue()));
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setLineWidth(4000);
            display.text(Component.text(" "));
        });
    }

    /** A fullbright block model. Its origin is a corner, so {@link #box} centres it. */
    public BlockDisplay block(Player player, BlockData data, Matrix4f matrix) {
        Matrix4f scaled = withPlayerSize(player, matrix);
        return player.getWorld().spawn(level(player), BlockDisplay.class, display -> {
            common(display, EMPTY);
            display.setTransformationMatrix(scaled);
            display.setBlock(data);
        });
    }

    /**
     * Gives a piece a coloured outline, seen through walls. Strong enough
     * that only one or two pieces of a look should ever carry it.
     */
    public static <T extends Display> T glowing(T display, Color color) {
        display.setGlowColorOverride(color);
        display.setGlowing(true);
        return display;
    }

    /**
     * The player's location with no rotation. A display copies the yaw and
     * pitch of where it spawns, and an aura put on while looking down would
     * otherwise hang tilted for as long as it was worn.
     */
    /**
     * The same pose scaled to the player wearing it, so a /size player wears
     * an aura that grows and shrinks with them. A passenger already rides
     * higher on a bigger player, so only the piece itself needs scaling.
     */
    private static Transformation withPlayerSize(Player player, Transformation pose) {
        float factor = sizeOf(player);
        if (factor == 1f) return pose;
        return new Transformation(
                new Vector3f(pose.getTranslation()).mul(factor),
                pose.getLeftRotation(),
                new Vector3f(pose.getScale()).mul(factor),
                pose.getRightRotation());
    }

    /** The same, for a piece posed by a matrix: one scale about the origin does both. */
    private static Matrix4f withPlayerSize(Player player, Matrix4f matrix) {
        float factor = sizeOf(player);
        return factor == 1f ? matrix : new Matrix4f().scale(factor).mul(matrix);
    }

    private static float sizeOf(Player player) {
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        double size = attribute == null ? 1.0 : attribute.getValue();
        return Math.abs(size - 1.0) < 0.01 ? 1f : (float) size;
    }

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
        // Left at 0 on purpose. Teleport duration smooths a piece's own
        // position and rotation over that many ticks, and a piece riding a
        // player is repositioned by the ride every tick: at 3 the whole aura
        // swam a few ticks behind the wearer while walking. Only looks that
        // turn with the body want it, and AuraManager sets it for those.
        display.setTeleportDuration(0);
        display.setTransformation(pose);
        display.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
    }

    /** One glyph pushed n spaces off the axis. */
    public static String single(String glyph, int spaces) {
        return glyph + " ".repeat(spaces);
    }

    /**
     * The same glyph pushed the same distance the other way. Turning a card
     * a half turn to show its back also swaps left for right, so the back of
     * a {@link #single} card carries this instead and the glyph lands where
     * it started.
     */
    public static String singleBack(String glyph, int spaces) {
        return " ".repeat(spaces) + glyph;
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

    /** The pose a matrix piece is spawned with; the matrix replaces it a line later. */
    private static final Transformation EMPTY =
            new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf());

    /**
     * The matrix that turns the painted background of a one space text
     * display into a one by one square with its bottom left on the origin.
     *
     * That background is 0.125 blocks wide and 0.25 high, sitting from
     * -0.05 to 0.075 across and from 0 to 0.25 up, so eight across and four
     * up lands it on the unit square. Everything {@link #plate} draws is
     * this square with a matrix in front of it.
     */
    public static final Matrix4f UNIT_QUAD = new Matrix4f().translate(0.4f, 0f, 0f).scale(8f, 4f, 1f);

    /** A {@code width} by {@code height} plate centred on (x, y, z) and turned by {@code rotation}. */
    public static Matrix4f plate(float x, float y, float z, Quaternionf rotation, float width, float height) {
        return new Matrix4f().translate(x, y, z).rotate(rotation)
                .scale(width, height, 1f).translate(-0.5f, -0.5f, 0f).mul(UNIT_QUAD);
    }

    /**
     * The same plate again, facing the other way and a hair behind itself.
     *
     * A text display is drawn on one side only: from behind there is
     * nothing there at all, not even a mirrored copy. So anything that can
     * be walked round, a ring standing upright or a wing off the back, needs
     * both faces or it vanishes as the viewer crosses its plane. A half turn
     * about the plate's own upright axis flips which way it looks, and the
     * four millimetres keep the two out of each other's way.
     */
    public static Matrix4f plate(float x, float y, float z, Quaternionf rotation,
                                 float width, float height, boolean back) {
        if (!back) return plate(x, y, z, rotation, width, height);
        Vector3f normal = rotation.transform(new Vector3f(0f, 0f, 1f));
        return new Matrix4f()
                .translate(x - normal.x * BACK_GAP, y - normal.y * BACK_GAP, z - normal.z * BACK_GAP)
                .rotate(rotation).scale(width, height, 1f).rotateY((float) Math.PI)
                .translate(-0.5f, -0.5f, 0f).mul(UNIT_QUAD);
    }

    private static final float BACK_GAP = 0.004f;

    /** A block model centred on (x, y, z), turned by {@code rotation} and stretched on each axis. */
    public static Matrix4f box(float x, float y, float z, Quaternionf rotation, float sx, float sy, float sz) {
        return new Matrix4f().translate(x, y, z).rotate(rotation)
                .scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f);
    }

    /** {@link #move} for a piece posed by a matrix. */
    public static void moveTo(Display display, Matrix4f matrix, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformationMatrix(rider(display) instanceof Player player
                ? withPlayerSize(player, matrix) : matrix);
    }

    /** Who a piece is riding, so a move can be scaled to them the way the spawn was. */
    private static org.bukkit.entity.Entity rider(Display display) {
        return display.getVehicle();
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

    /**
     * Sends a new pose that the client glides to over {@code ticks}; 0 snaps.
     * The wearer's own size is put back on, because a pose comes from the
     * look and knows nothing about who is wearing it; without this a /size
     * player's aura sprang back to normal on its first move.
     */
    public static void move(Display display, Transformation pose, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(rider(display) instanceof Player player
                ? withPlayerSize(player, pose) : pose);
    }

    /** Halfway between a colour and warm near-white, for a second, softer layer. */
    public static Color softer(Color color) {
        return Color.fromRGB((color.getRed() + 255) / 2, (color.getGreen() + 252) / 2, (color.getBlue() + 224) / 2);
    }

    public static float rad(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
