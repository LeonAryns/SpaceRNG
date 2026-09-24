package com.spacerng.solrng.aura;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.spacerng.solrng.aura.AuraParts.FEET;
import static com.spacerng.solrng.aura.AuraParts.RIDE;
import static com.spacerng.solrng.aura.AuraParts.at;
import static com.spacerng.solrng.aura.AuraParts.atScaled;
import static com.spacerng.solrng.aura.AuraParts.flat;
import static com.spacerng.solrng.aura.AuraParts.flatRotation;
import static com.spacerng.solrng.aura.AuraParts.move;
import static com.spacerng.solrng.aura.AuraParts.pair;
import static com.spacerng.solrng.aura.AuraParts.pairStars;
import static com.spacerng.solrng.aura.AuraParts.rad;
import static com.spacerng.solrng.aura.AuraParts.softer;

/**
 * The large looks, in the style of atom-grand: glowing blocks, star glyphs
 * on the ground, nether stars and end rods, built wide.
 *
 * Ground rings are text, because a text ring of any size still costs one
 * update per display every second or two. Blocks and nether stars orbit by
 * translation, in steps small enough for their radius that the chord can't
 * be told from the arc. Nether stars are flat sprites, so they are turned
 * to look outward along their own radius and never go thin edge-on.
 */
final class GrandConcepts {

    private GrandConcepts() {
    }

    static void describe(Map<String, String> d) {
        d.put("halo-grand", "a sunburst of sixteen end rods round the head, stars inside it");
        d.put("galaxy-grand", "a galaxy disc nine blocks wide: star bands, nether stars, sea lanterns");
        d.put("nova-grand", "a sea lantern atom, a nether star atom inside, wide star rings below");
        MassiveConcepts.describe(d);
    }

    static AuraConcept create(String key, Rarity rarity, Color color) {
        return switch (key) {
            case "halo-grand" -> new AuraConcepts.Combined(new RodHalo(),
                    new StarRing(color, 0.28f, 7, 1.2f, 3, 10, 1, "✦"));
            case "galaxy-grand" -> galaxy(color);
            case "nova-grand" -> new AuraConcepts.Combined(
                    new AuraConcepts.SolidAtom(Material.SEA_LANTERN, -0.6f, 2.8f, 0.5f, false, 0, 2, 16.0),
                    new AuraConcepts.SolidAtom(Material.NETHER_STAR, -0.8f, 1.5f, 0.7f, false, 90, 2, 24.0, true),
                    new StarRing(color, FEET, 28, 2.6f, 4, 20, -1, "✦"),
                    new StarRing(softer(color), FEET + 0.01f, 16, 2.0f, 3, 15, 1, "✧"));
            default -> MassiveConcepts.create(key, rarity, color);
        };
    }

    /**
     * Four bands of ground stars from under a block out to four and a half,
     * the inner ones turning fastest so the bands read as arms winding in,
     * nether stars sailing over the outer bands and sea lanterns close in.
     */
    private static AuraConcept galaxy(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new StarRing(soft, FEET + 0.03f, 6, 1.6f, 2, 5, 1, "✦"),
                new StarRing(color, FEET + 0.02f, 14, 2.0f, 2, 10, 1, "✦"),
                new StarRing(soft, FEET + 0.01f, 22, 2.4f, 3, 15, 1, "✧"),
                new StarRing(color, FEET, 30, 2.8f, 3, 25, 1, "✦"),
                new FlatOrbit(Material.NETHER_STAR, true, FEET + 0.45f, new float[]{2.2f, 3.7f}, 2, 0.75f, 2, 5.0),
                new FlatOrbit(Material.SEA_LANTERN, false, FEET + 0.45f, new float[]{1.1f}, 4, 0.35f, 2, 9.0));
    }

    /** A point on a level circle, in the same convention as a display's rotateY. */
    private static Vector3f onCircle(double radius, double angle) {
        return new Vector3f((float) (radius * Math.cos(angle)), 0f, (float) (-radius * Math.sin(angle)));
    }

    // -------------------------------------------------------------- star ring

    /**
     * A flat ring of star glyphs, as wide as the spaces make it. Displays are
     * spread over half a turn, each carrying two stars on opposite sides, so
     * n displays make 2n stars evenly round the circle.
     */
    static final class StarRing implements AuraConcept {
        private final Color color;
        private final float y;
        private final int spaces;
        private final float scale;
        private final int count;
        private final int every;
        private final int direction;
        private final String glyph;

        /**
         * @param every     frames per third of a turn; bigger is slower
         * @param direction 1 or -1
         */
        StarRing(Color color, float y, int spaces, float scale, int count, int every, int direction, String glyph) {
            this.color = color;
            this.y = y;
            this.spaces = spaces;
            this.scale = scale;
            this.count = count;
            this.every = every;
            this.direction = direction;
            this.glyph = glyph;
        }

        @Override
        public boolean clearOfView() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                displays.add(parts.text(player, pair(glyph, spaces), color, flat(y, rad(offset(i)), scale)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % every != 0) return;
            int step = (int) ((frame / every + 1) % 3);
            for (int i = 0; i < count; i++) {
                move(displays.get(i), flat(y, rad(offset(i) + direction * 120.0 * step), scale), every * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            for (int i = 0; i < count; i++) {
                pairStars(out, y, flatRotation(rad(offset(i) + direction * 120.0 * frame / every)), spaces, scale);
            }
        }

        private double offset(int i) {
            return 180.0 * i / count;
        }
    }

    // --------------------------------------------------------------- rod halo

    /**
     * Sixteen end rods pointing straight out from just above the head, a
     * sunburst halo. Once a second the ring steps round by exactly one rod,
     * which reads as a slow turn.
     */
    static final class RodHalo implements AuraConcept {
        private static final float Y = 0.3f;
        private static final float RADIUS = 1.1f;
        private static final int COUNT = 16;
        private static final int EVERY = 10;

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < COUNT; i++) {
                displays.add(parts.item(player, Material.END_ROD, pose(i, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long n = frame / EVERY + 1;
            for (int i = 0; i < COUNT; i++) {
                move(displays.get(i), pose(i, n), EVERY * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            double steps = (double) frame / EVERY;
            for (int i = 0; i < COUNT; i++) {
                Vector3f tip = onCircle(RADIUS + 0.4, Math.toRadians(360.0 / COUNT * (i + steps)));
                out.add(new Vector(tip.x, RIDE + Y, tip.z));
            }
        }

        private static Transformation pose(int i, long steps) {
            double a = Math.toRadians(360.0 / COUNT * (i + steps));
            Vector3f p = onCircle(RADIUS, a);
            // The rod model stands on Y; a quarter roll lays it along X, the turn aims it outward.
            Quaternionf aim = new Quaternionf().rotateY((float) a).rotateZ(rad(-90));
            return atScaled(p.x, Y, p.z, aim, 1.3f, 0.8f, 1.3f);
        }
    }

    // ------------------------------------------------------------- flat orbit

    /**
     * Items circling level at one or more radii. Each ring runs the other way
     * from the one inside it and slower the further out it is. Blocks tumble
     * as they go; a flat sprite is turned to look outward instead.
     */
    static final class FlatOrbit implements AuraConcept {
        private final Material material;
        private final boolean faceViewer;
        private final float y;
        private final float[] radii;
        private final int perRing;
        private final float scale;
        private final int every;
        private final double step;

        FlatOrbit(Material material, boolean faceViewer, float y, float[] radii, int perRing, float scale,
                  int every, double step) {
            this.material = material;
            this.faceViewer = faceViewer;
            this.y = y;
            this.radii = radii;
            this.perRing = perRing;
            this.scale = scale;
            this.every = every;
            this.step = step;
        }

        @Override
        public boolean clearOfView() {
            // Only when it is not swinging through the wearer's own view.
            // Divine's lanterns ride at chest height barely a block out,
            // which is exactly where a first person camera is pointing:
            // "je hebt de sea lanterns die nogsteeds voor de view gaan".
            float nearest = radii.length == 0 ? 0f : radii[0];
            for (float r : radii) nearest = Math.min(nearest, r);
            return AuraParts.outOfView(y, nearest);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int r = 0; r < radii.length; r++) {
                for (int j = 0; j < perRing; j++) {
                    // Never billboarded: these ride out at a radius, and a
                    // billboarded piece reads its offset in the camera's
                    // frame, so it would hang off the viewer's screen
                    // instead of orbiting the wearer. They are turned to
                    // face outward instead.
                    displays.add(parts.item(player, material, pose(r, j, 0)));
                }
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % every != 0) return;
            long n = frame / every + 1;
            int index = 0;
            for (int r = 0; r < radii.length; r++) {
                for (int j = 0; j < perRing; j++) {
                    move(displays.get(index++), pose(r, j, n), every * 2);
                }
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            double steps = (double) frame / every;
            for (int r = 0; r < radii.length; r++) {
                for (int j = 0; j < perRing; j++) {
                    Vector3f p = onCircle(radii[r], angle(r, j, steps));
                    out.add(new Vector(p.x, RIDE + y, p.z));
                }
            }
        }

        private double angle(int r, int j, double steps) {
            int direction = r % 2 == 0 ? 1 : -1;
            return Math.toRadians(360.0 / perRing * j + 37.0 * r + direction * step * steps / (r + 1));
        }

        private Transformation pose(int r, int j, double steps) {
            double a = angle(r, j, steps);
            Vector3f p = onCircle(radii[r], a);
            // A flat sprite is turned to look outward along its own radius,
            // which is where anyone watching stands; a block tumbles.
            Quaternionf rotation = faceViewer
                    ? new Quaternionf().rotateY((float) a + rad(90))
                    : new Quaternionf().rotateY((float) (a * 2)).rotateX(rad(35)).rotateZ(rad(45));
            return at(p.x, y, p.z, rotation, scale);
        }
    }
}
