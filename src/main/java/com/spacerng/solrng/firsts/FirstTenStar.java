package com.spacerng.solrng.firsts;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The star that stands over a Server First 10.
 *
 * Particles read as weather from a distance: lots of small bright things
 * that the eye takes in as a haze. A shape does not. This is a five
 * pointed star twenty blocks across, built out of solid blocks, hanging
 * high enough over the spot to clear the build and be seen from the far
 * side of the map, turning slowly so everybody gets a face-on look at it.
 *
 * It is deliberately expensive. Around ninety block pieces is far more
 * than any aura would ever be allowed, and that is fine: this runs ten
 * times per rarity for the life of the server, and then never again.
 *
 * Every piece is spawned at one point and pushed out by its own
 * translation, which is what lets the whole star turn by rewriting
 * translations instead of teleporting ninety entities. The client glides
 * between the poses, so the server sends one update every four ticks.
 */
final class FirstTenStar {

    private static final int PER_EDGE = 9;
    private static final double INNER = 0.382;
    private static final float PIECE = 0.9f;

    private final SolRNGPlugin plugin;
    private final Rarity rarity;
    private final Location centre;
    private final List<ItemDisplay> pieces = new ArrayList<>();
    private final List<Vector3f> home = new ArrayList<>();
    // The drop itself, held in the middle of the star. The whole server
    // should be able to see WHAT was found, not only that something was.
    private final Material drop;
    private ItemDisplay centrepiece;

    FirstTenStar(SolRNGPlugin plugin, Rarity rarity, Material drop, Location origin, double height, double radius) {
        this.drop = drop;
        this.plugin = plugin;
        this.rarity = rarity;
        Location at = origin.clone().add(0, height, 0);
        // A display copies the rotation of wherever it spawns, and a star
        // that hung tilted because the finder happened to be looking down
        // would be the one thing everybody remembered about it.
        at.setYaw(0f);
        at.setPitch(0f);
        this.centre = at;
        buildPoints(radius);
    }

    /** The ten vertices of a five pointed star, then the blocks along each edge. */
    private void buildPoints(double radius) {
        Vector3f[] vertices = new Vector3f[10];
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            double r = i % 2 == 0 ? radius : radius * INNER;
            vertices[i] = new Vector3f((float) (Math.cos(angle) * r), (float) (Math.sin(angle) * r), 0f);
        }
        for (int i = 0; i < 10; i++) {
            Vector3f from = vertices[i];
            Vector3f to = vertices[(i + 1) % 10];
            for (int step = 0; step < PER_EDGE; step++) {
                float t = (float) step / PER_EDGE;
                home.add(new Vector3f(
                        from.x + (to.x - from.x) * t,
                        from.y + (to.y - from.y) * t,
                        0f));
            }
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
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setItemStack(held);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(6.0f);
                d.setShadowRadius(0f);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            });
        }
        ItemStack block = new ItemStack(glass());
        for (Vector3f ignored : home) {
            ItemDisplay display = centre.getWorld().spawn(centre, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setItemStack(block);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(6.0f);
                d.setShadowRadius(0f);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            });
            pieces.add(display);
        }
        refreshAudience();
    }

    /** Who may see it: anybody who has not muted this rarity's aura. */
    void refreshAudience() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean allowed = plugin.getPlayerDataManager()
                    .get(viewer.getUniqueId()).isAuraEnabled(rarity);
            for (ItemDisplay piece : pieces) {
                if (allowed) viewer.showEntity(plugin, piece);
                else viewer.hideEntity(plugin, piece);
            }
            if (centrepiece != null && centrepiece.isValid()) {
                if (allowed) viewer.showEntity(plugin, centrepiece);
                else viewer.hideEntity(plugin, centrepiece);
            }
        }
    }

    /**
     * Opens and turns it. {@code progress} runs 0 to 1 over the build-up.
     *
     * The pieces fly out of the middle over the first third and the star
     * keeps turning for the whole run, so there is never a moment where
     * nothing is moving.
     */
    void tick(double progress, int ticks) {
        float open = (float) Math.min(1.0, progress / 0.35);
        // Eased, so it leaves the middle fast and settles into the shape
        // rather than arriving at full size at a constant speed.
        float eased = 1f - (1f - open) * (1f - open);
        float scale = 0.01f + PIECE * eased;
        double spin = Math.toRadians(360.0 * progress);
        float cos = (float) Math.cos(spin);
        float sin = (float) Math.sin(spin);

        if (centrepiece != null && centrepiece.isValid()) {
            float held = 0.01f + 2.6f * eased;
            centrepiece.setInterpolationDelay(0);
            centrepiece.setInterpolationDuration(ticks);
            centrepiece.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf().rotateY((float) (spin * 2)),
                    new Vector3f(held, held, held), new Quaternionf()));
        }
        for (int i = 0; i < pieces.size(); i++) {
            ItemDisplay piece = pieces.get(i);
            if (!piece.isValid()) continue;
            Vector3f point = home.get(i);
            Vector3f at = new Vector3f(point.x * cos * eased, point.y * eased, point.x * sin * eased);
            piece.setInterpolationDelay(0);
            piece.setInterpolationDuration(ticks);
            piece.setTransformation(new Transformation(at,
                    new Quaternionf().rotateY((float) spin),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        }
    }

    /**
     * The star blows outward, and the drop is left hanging there.
     *
     * The banner says what was found in words; this is the thing itself,
     * twice the size, turning over the spot for six seconds so anybody who
     * looked up because of the noise still sees it.
     */
    void finish() {
        if (centrepiece != null && centrepiece.isValid()) {
            centrepiece.setInterpolationDelay(0);
            centrepiece.setInterpolationDuration(20);
            centrepiece.setTransformation(new Transformation(new Vector3f(0f, 1.5f, 0f), new Quaternionf(),
                    new Vector3f(5.0f, 5.0f, 5.0f), new Quaternionf()));
            ItemDisplay held = centrepiece;
            centrepiece = null;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (held.isValid()) held.remove();
            }, 120L);
        }
        for (int i = 0; i < pieces.size(); i++) {
            ItemDisplay piece = pieces.get(i);
            if (!piece.isValid()) continue;
            Vector3f point = home.get(i);
            piece.setInterpolationDelay(0);
            piece.setInterpolationDuration(10);
            piece.setTransformation(new Transformation(
                    new Vector3f(point.x * 1.6f, point.y * 1.6f, 0f), new Quaternionf(),
                    new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::remove, 12L);
    }

    void remove() {
        for (ItemDisplay piece : pieces) {
            if (piece.isValid()) piece.remove();
        }
        pieces.clear();
        if (centrepiece != null && centrepiece.isValid()) centrepiece.remove();
        centrepiece = null;
    }

    /**
     * The rarity in a block.
     *
     * Stained glass rather than a light source: fullbright is set on the
     * display itself, so the glass reads as the rarity's colour glowing
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
