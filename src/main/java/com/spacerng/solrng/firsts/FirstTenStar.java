package com.spacerng.solrng.firsts;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraParts;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.roll.RollAura;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The star that stands over a Server First 10.
 *
 * Particles read as weather from a distance: lots of small bright things
 * that the eye takes in as a haze. A shape does not. This is a five
 * pointed star up to twenty blocks across hanging high over the spot,
 * turning slowly so everybody gets a face-on look at it, with a column of
 * light running down from it to the ground so the place it happened can be
 * found from anywhere.
 *
 * It was ninety separate glass cubes strung along the edges until V179.
 * Ten stretched beams draw the same star as one clean unbroken outline for
 * a ninth of the pieces, and each beam carries a glow in the rarity's
 * exact colour, so the star is seen through the terrain from any corner of
 * the map. That last part is the whole point of a Server First: it happens
 * ten times per rarity for the life of the server and then never again, so
 * nobody should be able to miss one for standing in the wrong place.
 *
 * Every piece is spawned at one point and pushed out by its own matrix,
 * which is what lets the whole star turn by rewriting transformations
 * rather than teleporting anything. The client glides between the poses,
 * so the server sends one update every four ticks.
 */
final class FirstTenStar {

    /** The waist of a five pointed star, as a fraction of its points. */
    private static final double INNER = 0.382;
    private static final int EDGES = 10;

    private final SolRNGPlugin plugin;
    private final Rarity rarity;
    private final Color colour;
    private final Location centre;
    private final double height;
    private final float thickness;
    private final Vector3f[] vertices = new Vector3f[EDGES];
    private final List<BlockDisplay> beams = new ArrayList<>();
    private final List<Display> everything = new ArrayList<>();
    // The drop itself, held in the middle of the star. The whole server
    // should be able to see WHAT was found, not only that something was.
    private final Material drop;
    private ItemDisplay centrepiece;
    private TextDisplay column;
    private TextDisplay columnCore;

    FirstTenStar(SolRNGPlugin plugin, Rarity rarity, Material drop, Location origin, double height, double radius) {
        this.drop = drop;
        this.plugin = plugin;
        this.rarity = rarity;
        this.colour = RollAura.colorFor(rarity);
        this.height = height;
        this.thickness = (float) (radius * 0.085);
        Location at = origin.clone().add(0, height, 0);
        // A display copies the rotation of wherever it spawns, and a star
        // that hung tilted because the finder happened to be looking down
        // would be the one thing everybody remembered about it.
        at.setYaw(0f);
        at.setPitch(0f);
        this.centre = at;
        for (int i = 0; i < EDGES; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            double r = i % 2 == 0 ? radius : radius * INNER;
            vertices[i] = new Vector3f((float) (Math.cos(angle) * r), (float) (Math.sin(angle) * r), 0f);
        }
    }

    /**
     * Spawns it collapsed at its own centre. The build-up opens it.
     *
     * Hidden from everybody first and shown only to the players who have
     * this rarity's aura switched on, so the one promise /options makes is
     * kept by the biggest thing in the plugin as well.
     */
    void start() {
        if (drop != null) {
            ItemStack held = new ItemStack(drop);
            centrepiece = centre.getWorld().spawn(centre, ItemDisplay.class, d -> {
                common(d);
                d.setItemStack(held);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            });
            everything.add(centrepiece);
        }
        for (int i = 0; i < EDGES; i++) {
            Matrix4f collapsed = edge(i, 0f, 0f);
            BlockDisplay beam = centre.getWorld().spawn(centre, BlockDisplay.class, d -> {
                common(d);
                d.setBlock(glass().createBlockData());
                // Seen through the world, in the rarity's exact colour. The
                // block only decides what it looks like up close; the glow
                // is what carries it across the map.
                d.setGlowColorOverride(colour);
                d.setGlowing(true);
                d.setTransformationMatrix(collapsed);
            });
            beams.add(beam);
            everything.add(beam);
        }
        // A column of light from the ground up to the star, so the spot can
        // be walked to rather than only looked at. Turning with the viewer
        // about the upright axis, so it is never seen edge on.
        column = plate(70, AuraParts.plate(0f, (float) (-height / 2), 0f, new Quaternionf(), 0.01f,
                (float) height));
        columnCore = plate(160, AuraParts.plate(0f, (float) (-height / 2), 0f, new Quaternionf(), 0.01f,
                (float) height));
        refreshAudience();
    }

    private void common(Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setBrightness(new Display.Brightness(15, 15));
        // Six times the usual reach: this is meant to be seen from the far
        // side of the map, which is the difference between a server event
        // and a private one.
        display.setViewRange(6.0f);
        display.setShadowRadius(0f);
    }

    /** A painted plate: a text display holding one space with its background filled in. */
    private TextDisplay plate(int alpha, Matrix4f matrix) {
        TextDisplay display = centre.getWorld().spawn(centre, TextDisplay.class, d -> {
            common(d);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(alpha, colour.getRed(), colour.getGreen(), colour.getBlue()));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setLineWidth(4000);
            d.text(Component.text(" "));
            d.setTransformationMatrix(matrix);
        });
        everything.add(display);
        return display;
    }

    /** Who may see it: anybody who has not muted this rarity's aura. */
    void refreshAudience() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean allowed = plugin.getPlayerDataManager()
                    .get(viewer.getUniqueId()).isAuraEnabled(rarity);
            for (Display piece : everything) {
                if (!piece.isValid()) continue;
                if (allowed) viewer.showEntity(plugin, piece);
                else viewer.hideEntity(plugin, piece);
            }
        }
    }

    /**
     * Opens and turns it. {@code progress} runs 0 to 1 over the build-up.
     *
     * The star grows out of the middle over the first third and keeps
     * turning for the whole run, so there is never a moment where nothing
     * is moving. The column widens with it.
     */
    void tick(double progress, int ticks) {
        float open = (float) Math.min(1.0, progress / 0.35);
        // Eased, so it leaves the middle fast and settles into the shape
        // rather than arriving at full size at a constant speed.
        float eased = 1f - (1f - open) * (1f - open);
        float spin = (float) Math.toRadians(360.0 * progress);

        if (centrepiece != null && centrepiece.isValid()) {
            float held = 0.01f + 2.6f * eased;
            centrepiece.setInterpolationDelay(0);
            centrepiece.setInterpolationDuration(ticks);
            centrepiece.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf().rotateY(spin * 2f),
                    new Vector3f(held, held, held), new Quaternionf()));
        }
        for (int i = 0; i < beams.size(); i++) {
            BlockDisplay beam = beams.get(i);
            if (!beam.isValid()) continue;
            beam.setInterpolationDelay(0);
            beam.setInterpolationDuration(ticks);
            beam.setTransformationMatrix(edge(i, spin, eased));
        }
        // A slow breath on the column, so it reads as something running
        // rather than as a wall somebody built.
        float breath = (float) (0.9 + 0.1 * Math.sin(progress * Math.PI * 6));
        widen(column, 2.4f * eased * breath, ticks);
        widen(columnCore, 0.7f * eased * breath, ticks);
    }

    private void widen(TextDisplay plate, float width, int ticks) {
        if (plate == null || !plate.isValid()) return;
        plate.setInterpolationDelay(0);
        plate.setInterpolationDuration(ticks);
        plate.setTransformationMatrix(AuraParts.plate(0f, (float) (-height / 2), 0f, new Quaternionf(),
                Math.max(0.01f, width), (float) height));
    }

    /**
     * One edge of the star as a beam: a block stretched along the line
     * between two of the ten vertices, turned to lie on it. The star's own
     * plane turns about the upright axis through its middle, so both ends
     * are swung before the beam between them is worked out.
     */
    private Matrix4f edge(int i, float spin, float open) {
        Vector3f a = swing(vertices[i], spin, open);
        Vector3f b = swing(vertices[(i + 1) % EDGES], spin, open);
        Vector3f along = new Vector3f(b).sub(a);
        float length = along.length();
        // Collapsed at the centre, every vertex is the same point, and
        // normalising a zero vector would hand the client a NaN matrix.
        if (length < 0.001f) {
            along.set(0f, 1f, 0f);
            length = 0.001f;
        } else {
            along.div(length);
        }
        Quaternionf aim = new Quaternionf().rotateTo(new Vector3f(0f, 0f, 1f), along);
        float thick = Math.max(0.01f, thickness * open);
        return AuraParts.box((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f, aim, thick, thick, length);
    }

    /** A point of the star, opened out from the middle and turned about the upright axis. */
    private Vector3f swing(Vector3f point, float spin, float open) {
        float x = point.x * open;
        float y = point.y * open;
        return new Vector3f(x * (float) Math.cos(spin), y, x * (float) Math.sin(spin));
    }

    /**
     * The star blows outward, and the drop is left hanging there.
     *
     * The banner says what was found in words; this is the thing itself,
     * five times the size, turning over the spot for six seconds so anybody
     * who looked up because of the noise still sees it.
     */
    void finish() {
        if (centrepiece != null && centrepiece.isValid()) {
            centrepiece.setInterpolationDelay(0);
            centrepiece.setInterpolationDuration(20);
            centrepiece.setTransformation(new Transformation(new Vector3f(0f, 1.5f, 0f), new Quaternionf(),
                    new Vector3f(5.0f, 5.0f, 5.0f), new Quaternionf()));
            ItemDisplay held = centrepiece;
            centrepiece = null;
            everything.remove(held);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (held.isValid()) held.remove();
            }, 120L);
        }
        // Thrown outward and thinned to nothing, rather than simply deleted.
        for (int i = 0; i < beams.size(); i++) {
            BlockDisplay beam = beams.get(i);
            if (!beam.isValid()) continue;
            beam.setInterpolationDelay(0);
            beam.setInterpolationDuration(10);
            beam.setTransformationMatrix(edge(i, 0f, 1.7f));
            beam.setGlowing(false);
        }
        widen(column, 0.01f, 10);
        widen(columnCore, 0.01f, 10);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::remove, 12L);
    }

    void remove() {
        for (Display piece : everything) {
            if (piece.isValid()) piece.remove();
        }
        everything.clear();
        beams.clear();
        centrepiece = null;
        column = null;
        columnCore = null;
    }

    /**
     * The rarity in a block, for anybody close enough to see the beam
     * itself rather than only its glow.
     *
     * Stained glass rather than a light source: fullbright is set on the
     * display, so the glass reads as the rarity's colour lit from inside
     * rather than as whatever colour Mojang gave a lamp.
     */
    private Material glass() {
        return switch (rarity) {
            case DIVINE -> Material.WHITE_STAINED_GLASS;
            case MYTHICAL -> Material.RED_STAINED_GLASS;
            case LEGENDARY -> Material.ORANGE_STAINED_GLASS;
            default -> Material.PURPLE_STAINED_GLASS;
        };
    }
}
