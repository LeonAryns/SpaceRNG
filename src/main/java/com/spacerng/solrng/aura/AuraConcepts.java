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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.spacerng.solrng.aura.AuraParts.FEET;
import static com.spacerng.solrng.aura.AuraParts.RIDE;
import static com.spacerng.solrng.aura.AuraParts.at;
import static com.spacerng.solrng.aura.AuraParts.flat;
import static com.spacerng.solrng.aura.AuraParts.flatRotation;
import static com.spacerng.solrng.aura.AuraParts.move;
import static com.spacerng.solrng.aura.AuraParts.pair;
import static com.spacerng.solrng.aura.AuraParts.pairStars;
import static com.spacerng.solrng.aura.AuraParts.pose;
import static com.spacerng.solrng.aura.AuraParts.rad;
import static com.spacerng.solrng.aura.AuraParts.single;
import static com.spacerng.solrng.aura.AuraParts.singleBack;
import static com.spacerng.solrng.aura.AuraParts.softer;
import static com.spacerng.solrng.aura.AuraParts.tilted;
import static com.spacerng.solrng.aura.AuraParts.upright;

/**
 * The aura looks that can be tried on with /rngadmin auratest.
 *
 * Text looks share one trick: a star glyph sits off-centre inside its own
 * text, pushed out by spaces, so spinning the whole display about its own
 * axis swings the star round the player. Rotation is slerped by the client,
 * so one update every one or two seconds reads as a smooth orbit. Turns are
 * sent in steps of 120 degrees at most, because a slerp always takes the
 * short way round and a bigger step could reverse.
 *
 * Item looks can't do that, an item model is centred on its origin, so they
 * orbit by moving their translation in small steps a few times a second;
 * the chord between two close points on a circle is indistinguishable from
 * the arc.
 *
 * A look that sends a turn of 120 degrees every n frames is, at any frame
 * f, 120 * f / n degrees round, which is how {@code stars} finds its pieces
 * for accents without asking the client.
 */
public final class AuraConcepts {

    /** Every look, with one line saying what it is, in the order /rngadmin auratest list shows them. */
    public static final Map<String, String> DESCRIPTIONS;

    static {
        Map<String, String> d = new LinkedHashMap<>();
        // First in the list because these are the four a tag actually wears.
        SignatureConcepts.describe(d);
        d.put("orbit", "two star rings, waist and shoulders, turning opposite ways");
        d.put("runes", "six runes flat round the feet, four smaller ones inside");
        d.put("halo", "a ring of six stars just above the head");
        d.put("helix", "two stars spiralling up and down the body");
        d.put("galaxy", "three bands round the feet, the inner one fastest");
        d.put("ripple", "rings of stars bursting outward from the feet");
        d.put("atom", "three slanted orbits of crossed stars, solid from any side");
        d.put("atom-cubes", "the atom with small tumbling blocks of the rarity's material");
        d.put("atom-gems", "the atom with the rarity's gems, each facing along its orbit");
        d.put("atom-lanterns", "the atom with glowing froglights and lanterns");
        d.put("atom-hybrid", "the star atom with small lanterns riding between the stars");
        d.put("atom-grand", "a lantern atom round the whole body that grows with rarity, huge on Divine");
        d.put("pulse", "a ring at the feet that swells and settles as it turns");
        d.put("cubes", "three small blocks of the rarity's material orbiting the chest");
        d.put("shards", "four of the rarity's gems circling the waist on a tilted ring");
        d.put("celestial", "orbit and runes");
        d.put("seraph", "halo and orbit");
        d.put("nebula", "galaxy and ripple");
        d.put("cosmos", "atom and runes");
        d.put("stellar", "shards, runes and halo");
        DisplayConcepts.describe(d);
        GrandConcepts.describe(d);
        DESCRIPTIONS = Collections.unmodifiableMap(d);
    }

    public static final List<String> KEYS = List.copyOf(DESCRIPTIONS.keySet());

    private AuraConcepts() {
    }

    public static AuraConcept create(String key, Rarity rarity, Color color) {
        return switch (key) {
            case "orbit" -> new StarOrbit(color);
            case "runes" -> new RuneRing(color);
            case "halo" -> new Halo(color);
            case "helix" -> new Helix(color);
            case "galaxy" -> new Galaxy(color);
            case "ripple" -> new Ripple(color);
            case "atom" -> new Atom(color);
            case "atom-cubes" -> new SolidAtom(block(rarity), Atom.CHEST, 0.85f, 0.22f, false, 0);
            case "atom-gems" -> new SolidAtom(gem(rarity), Atom.CHEST, 0.85f, 0.38f, true, 0);
            case "atom-lanterns" -> new SolidAtom(lantern(rarity), Atom.CHEST, 0.85f, 0.22f, false, 0);
            // Same ring and speed as the star atom, a quarter turn behind, so
            // each lantern rides exactly between two stars.
            case "atom-hybrid" -> new Combined(new Atom(color),
                    new SolidAtom(lantern(rarity), Atom.CHEST, Atom.RADIUS, 0.16f, false, 90));
            case "atom-grand" -> grand(rarity, color);
            case "pulse" -> new Pulse(color);
            case "cubes" -> new Cubes(block(rarity));
            case "shards" -> new Shards(gem(rarity));
            case "celestial" -> new Combined(new StarOrbit(color), new RuneRing(color));
            case "seraph" -> new Combined(new Halo(color), new StarOrbit(color));
            case "nebula" -> new Combined(new Galaxy(color), new Ripple(color));
            case "cosmos" -> new Combined(new Atom(color), new RuneRing(color));
            case "stellar" -> new Combined(new Shards(gem(rarity)), new RuneRing(color), new Halo(color));
            default -> {
                AuraConcept signature = SignatureConcepts.create(key, rarity, color);
                if (signature != null) yield signature;
                AuraConcept display = DisplayConcepts.create(key, rarity, color);
                yield display != null ? display : GrandConcepts.create(key, rarity, color);
            }
        };
    }

    /**
     * atom-grand grows with the rarity, and Divine is on another scale: a
     * lantern atom nearly six blocks across with pieces half a block wide,
     * a second atom of end rods turning inside it a quarter turn behind, and
     * the runes at the feet. Big rings move in small steps, so the orbit
     * stays round instead of showing its corners.
     */
    private static AuraConcept grand(Rarity rarity, Color color) {
        Material light = lantern(rarity);
        return switch (rarity) {
            // Divine also gets supernova's stars bursting out along the
            // ground, kept a little shorter than supernova's own.
            case DIVINE -> new Combined(
                    new SolidAtom(light, -0.6f, 2.8f, 0.5f, false, 0, 2, 16.0),
                    new SolidAtom(Material.END_ROD, -0.8f, 1.45f, 0.35f, false, 90, 2, 24.0),
                    new MassiveConcepts.WideRipple(color, 28, 3.2f, 20, 3),
                    new RuneRing(color));
            case MYTHICAL -> new Combined(
                    new SolidAtom(light, -0.8f, 1.85f, 0.36f, false, 0, 2, 20.0), new RuneRing(color));
            case LEGENDARY -> new Combined(
                    new SolidAtom(light, -0.85f, 1.5f, 0.3f, false, 0, 3, 30.0), new RuneRing(color));
            default -> new Combined(
                    new SolidAtom(light, -0.9f, 1.25f, 0.26f, false, 0), new RuneRing(color));
        };
    }

    // ---------------------------------------------------- materials by rarity

    /** A solid block in the rarity's colour. */
    private static Material block(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> Material.QUARTZ_BLOCK;
            case MYTHICAL -> Material.REDSTONE_BLOCK;
            case LEGENDARY -> Material.GOLD_BLOCK;
            default -> Material.AMETHYST_BLOCK;
        };
    }

    /** A flat gem or charm in the rarity's colour. */
    static Material gem(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> Material.NETHER_STAR;
            case MYTHICAL -> Material.FIRE_CHARGE;
            case LEGENDARY -> Material.GOLD_INGOT;
            default -> Material.AMETHYST_SHARD;
        };
    }

    /** A block that looks lit from inside, which reads as light even at full brightness. */
    static Material lantern(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> Material.SEA_LANTERN;
            case MYTHICAL -> Material.SHROOMLIGHT;
            case LEGENDARY -> Material.OCHRE_FROGLIGHT;
            default -> Material.PEARLESCENT_FROGLIGHT;
        };
    }

    /** Front then back, for a piece that has to be drawn for both of its faces. */
    private static final boolean[] BOTH = {false, true};

    /** Every {@code every} frames, the step count since spawn, wrapped to a full turn of 120 degree steps. */
    private static int step(long frame, int every) {
        return (int) ((frame / every + 1) % 3);
    }

    /** Degrees turned by frame f for a look that turns 120 degrees every {@code every} frames. */
    private static double turned(long frame, int every) {
        return 120.0 * frame / every;
    }

    // ------------------------------------------------------------------ orbit

    /** Two rings of stars, waist and shoulders, turning in opposite directions. */
    static final class StarOrbit implements AuraConcept {
        private static final float WAIST = -1.05f;
        private static final float SHOULDER = -0.45f;
        private final Color color;
        private final Color soft;

        StarOrbit(Color color) {
            this.color = color;
            this.soft = softer(color);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            // Two cards each, one per face: a card standing up is not drawn
            // at all from behind, so half the ring would go missing.
            return List.of(
                    parts.text(player, pair("✦", 8), color, upright(WAIST, 0f, 2.0f), false),
                    parts.text(player, pair("✦", 8), color, upright(WAIST, 0f, 2.0f), true),
                    parts.text(player, pair("✦", 8), soft, upright(SHOULDER, rad(90), 1.6f), false),
                    parts.text(player, pair("✦", 8), soft, upright(SHOULDER, rad(90), 1.6f), true));
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            int step = step(frame, 10);
            Transformation waist = upright(WAIST, rad(120 * step), 2.0f);
            Transformation shoulder = upright(SHOULDER, rad(90 - 120 * step), 1.6f);
            move(displays.get(0), waist, 20);
            move(displays.get(1), AuraParts.flipped(waist), 20);
            move(displays.get(2), shoulder, 20);
            move(displays.get(3), AuraParts.flipped(shoulder), 20);
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            pairStars(out, WAIST, new Quaternionf().rotateY(rad(turned(frame, 10))), 8, 2.0f);
            pairStars(out, SHOULDER, new Quaternionf().rotateY(rad(90 - turned(frame, 10))), 8, 1.6f);
        }
    }

    // ------------------------------------------------------------------ runes

    /** Six runes flat around the feet, four smaller ones inside turning the other way. */
    static final class RuneRing implements AuraConcept {
        private final Color color;
        private final Color soft;

        RuneRing(Color color) {
            this.color = color;
            this.soft = softer(color);
        }

        @Override
        public boolean clearOfView() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                displays.add(parts.text(player, pair("✦", 12), color, flat(FEET, rad(60 * i), 1.8f)));
            }
            for (int i = 0; i < 2; i++) {
                displays.add(parts.text(player, pair("✧", 6), soft, flat(FEET + 0.01f, rad(90 * i), 1.4f)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 20 != 0) return;
            int step = step(frame, 20);
            for (int i = 0; i < 3; i++) {
                move(displays.get(i), flat(FEET, rad(60 * i + 120 * step), 1.8f), 40);
            }
            for (int i = 0; i < 2; i++) {
                move(displays.get(3 + i), flat(FEET + 0.01f, rad(90 * i - 120 * step), 1.4f), 40);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            for (int i = 0; i < 3; i++) {
                pairStars(out, FEET, flatRotation(rad(60 * i + turned(frame, 20))), 12, 1.8f);
            }
            for (int i = 0; i < 2; i++) {
                pairStars(out, FEET + 0.01f, flatRotation(rad(90 * i - turned(frame, 20))), 6, 1.4f);
            }
        }
    }

    // ------------------------------------------------------------------- halo

    /** A level ring of six small stars just above the head, under the floating tag. */
    static final class Halo implements AuraConcept {
        private static final float Y = 0.22f;
        private final Color color;
        private final Color soft;

        Halo(Color color) {
            this.color = color;
            this.soft = softer(color);
        }

        @Override
        public boolean clearOfView() {
            // A ring above the head, which the wearer is under.
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                displays.add(parts.text(player, pair("✦", 6), i == 1 ? soft : color, flat(Y, rad(60 * i), 1.1f)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            int step = step(frame, 10);
            for (int i = 0; i < 3; i++) {
                move(displays.get(i), flat(Y, rad(60 * i + 120 * step), 1.1f), 20);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            for (int i = 0; i < 3; i++) {
                pairStars(out, Y, flatRotation(rad(60 * i + turned(frame, 10))), 6, 1.1f);
            }
        }
    }

    // ------------------------------------------------------------------ helix

    /**
     * Two stars on opposite sides, circling while they climb and fall in
     * turn, so they trace a double helix up and down the body. Height is a
     * straight glide between waypoints, turn is a slerp; sent together, the
     * two blend into one spiral path.
     */
    static final class Helix implements AuraConcept {
        private static final float LOW = -1.45f;
        private static final float HIGH = -0.15f;
        private static final int LEGS = 4;
        private final Color color;
        private final Color soft;

        Helix(Color color) {
            this.color = color;
            this.soft = softer(color);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            // Two cards per star, one per face. The back card carries its
            // glyph on the other side of its own text, because the half turn
            // that shows the back also swaps left for right.
            return List.of(
                    parts.text(player, single("✦", 9), color, upright(heightAt(0), 0f, 1.8f), false),
                    parts.text(player, singleBack("✦", 9), color, upright(heightAt(0), 0f, 1.8f), true),
                    parts.text(player, single("✦", 9), soft, upright(heightAt(LEGS), rad(180), 1.8f), false),
                    parts.text(player, singleBack("✦", 9), soft, upright(heightAt(LEGS), rad(180), 1.8f), true));
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            long n = frame / 10 + 1;
            int turn = (int) (n % 3);
            Transformation lead = upright(heightAt(n), rad(120 * turn), 1.8f);
            Transformation trail = upright(heightAt(n + LEGS), rad(180 + 120 * turn), 1.8f);
            move(displays.get(0), lead, 20);
            move(displays.get(1), AuraParts.flipped(lead), 20);
            move(displays.get(2), trail, 20);
            move(displays.get(3), AuraParts.flipped(trail), 20);
        }

        /** A triangle wave: LEGS steps up, LEGS steps down. */
        private static float heightAt(long n) {
            long phase = n % (2L * LEGS);
            long up = phase <= LEGS ? phase : 2L * LEGS - phase;
            return LOW + (HIGH - LOW) * up / LEGS;
        }
    }

    // ----------------------------------------------------------------- galaxy

    /**
     * Three flat bands of stars around the feet, two arms each. The inner
     * band turns fastest and the outer slowest, so the arms read as a
     * spiral winding in rather than a wheel.
     */
    static final class Galaxy implements AuraConcept {
        private static final String[] GLYPHS = {"◆", "✦", "✧"};
        private static final int[] SPACES = {6, 12, 20};
        private static final float[] SCALES = {1.2f, 1.4f, 1.5f};
        private static final int[] EVERY = {5, 10, 20};
        private final Color[] colors;

        Galaxy(Color color) {
            this.colors = new Color[]{softer(color), color, softer(softer(color))};
        }

        @Override
        public boolean clearOfView() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int band = 0; band < 3; band++) {
                for (int arm = 0; arm < 2; arm++) {
                    displays.add(parts.text(player, single(GLYPHS[band], SPACES[band]), colors[band],
                            flat(FEET + 0.01f * band, rad(180 * arm + 40 * band), SCALES[band])));
                }
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            for (int band = 0; band < 3; band++) {
                if (frame % EVERY[band] != 0) continue;
                int step = step(frame, EVERY[band]);
                for (int arm = 0; arm < 2; arm++) {
                    move(displays.get(band * 2 + arm),
                            flat(FEET + 0.01f * band, rad(180 * arm + 40 * band + 120 * step), SCALES[band]),
                            EVERY[band] * 2);
                }
            }
        }
    }

    // ----------------------------------------------------------------- ripple

    /**
     * Rings of stars bursting outward from the feet over and over. Each ring
     * snaps to nothing, then grows out over a second and a half; two rings
     * run half a cycle apart so there is always one travelling.
     */
    static final class Ripple implements AuraConcept {
        private static final int CYCLE = 15;
        private final Color color;
        private final Color soft;

        Ripple(Color color) {
            this.color = color;
            this.soft = softer(color);
        }

        @Override
        public boolean clearOfView() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            return List.of(
                    parts.text(player, pair("✦", 10), color, flat(FEET, 0f, 0.01f)),
                    parts.text(player, pair("✧", 10), soft, flat(FEET + 0.01f, rad(90), 0.01f)));
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            for (int i = 0; i < 2; i++) {
                long local = frame + (long) i * CYCLE / 2;
                long phase = local % CYCLE;
                // A new heading each cycle, set while the ring is invisible.
                float angle = rad(90 * i + 40 * (local / CYCLE));
                if (phase == 0) {
                    move(displays.get(i), flat(FEET + 0.01f * i, angle, 0.01f), 0);
                } else if (phase == 1) {
                    move(displays.get(i), flat(FEET + 0.01f * i, angle, 2.4f), (CYCLE - 2) * 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------- atom

    /**
     * Three slanted orbits around the chest, 60 degrees apart, like an atom.
     *
     * Each orbit is two text cards crossed at right angles along the line
     * through their stars. A single card went thin whenever it turned edge-on
     * to the viewer, which read as the whole aura going flat from one side;
     * with a second card at 90 degrees, one of the two always faces you.
     */
    static final class Atom implements AuraConcept {
        static final float CHEST = -0.8f;
        static final float RADIUS = (4 + 2 * 7) * 0.025f * 1.7f;
        static final Quaternionf[] TILTS = {
                new Quaternionf().rotateX(rad(60)),
                new Quaternionf().rotateY(rad(60)).rotateX(rad(60)),
                new Quaternionf().rotateY(rad(120)).rotateX(rad(60))
        };
        private final Color color;
        private final Color soft;

        Atom(Color color) {
            this.color = color;
            this.soft = softer(color);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                Color tint = k == 1 ? soft : color;
                // Four cards per orbit: two crossed planes, each drawn for
                // both of its faces. Two planes alone leave a quarter of the
                // directions you can stand in with nothing facing you, which
                // is what made the atom fade in and out as you walked round.
                for (boolean back : BOTH) {
                    displays.add(parts.text(player, pair("✦", 7), tint, tilted(CHEST, TILTS[k], 0f, 1.7f), back));
                }
                for (boolean back : BOTH) {
                    displays.add(parts.text(player, pair("✦", 7), tint, crossed(k, 0f), back));
                }
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            int step = step(frame, 10);
            for (int k = 0; k < 3; k++) {
                // The middle orbit runs the other way, so the three never line up.
                float angle = rad(direction(k) * 120 * step);
                Transformation plane = tilted(CHEST, TILTS[k], angle, 1.7f);
                Transformation cross = crossed(k, angle);
                move(displays.get(k * 4), plane, 20);
                move(displays.get(k * 4 + 1), AuraParts.flipped(plane), 20);
                move(displays.get(k * 4 + 2), cross, 20);
                move(displays.get(k * 4 + 3), AuraParts.flipped(cross), 20);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            for (int k = 0; k < 3; k++) {
                pairStars(out, CHEST, new Quaternionf(TILTS[k]).rotateY(rad(direction(k) * turned(frame, 10))), 7, 1.7f);
            }
        }

        /** The second card: the same orbit, rolled a quarter turn about the line through its stars. */
        private static Transformation crossed(int k, float angle) {
            return pose(CHEST, new Quaternionf(TILTS[k]).rotateY(angle).rotateX(rad(90)), 1.7f);
        }

        static int direction(int k) {
            return k == 1 ? -1 : 1;
        }
    }

    // -------------------------------------------------------------- solid atom

    /**
     * The atom built from solid pieces, two items on each of its three
     * slanted orbits, so it has volume from every side. Items can't use the
     * glyph trick, so every 6 ticks each one moves 36 degrees along its
     * slanted circle: the same 12 degrees a frame as the star atom, which is
     * what lets a hybrid ride its lanterns exactly between the stars.
     */
    static final class SolidAtom implements AuraConcept {
        private final int every;
        private final double step;
        private final boolean faceViewer;
        private final Material material;
        private final float y;
        private final float radius;
        private final float scale;
        private final boolean facing;
        private final double phase;

        /**
         * @param facing gems turn to face along their path; blocks tumble instead
         * @param phase  degrees ahead of the star atom's own stars
         */
        SolidAtom(Material material, float y, float radius, float scale, boolean facing, double phase) {
            this(material, y, radius, scale, facing, phase, 3, 36.0);
        }

        /**
         * @param every frames between moves
         * @param step  degrees per move; a big ring wants a small step to stay round
         */
        SolidAtom(Material material, float y, float radius, float scale, boolean facing, double phase,
                  int every, double step) {
            this(material, y, radius, scale, facing, phase, every, step, false);
        }

        /** @param faceViewer a flat sprite such as a nether star, turned along its path so it never goes thin */
        SolidAtom(Material material, float y, float radius, float scale, boolean facing, double phase,
                  int every, double step, boolean faceViewer) {
            this.faceViewer = faceViewer;
            this.every = every;
            this.step = step;
            this.material = material;
            this.y = y;
            this.radius = radius;
            this.scale = scale;
            this.facing = facing;
            this.phase = phase;
        }

        @Override
        public boolean clearOfView() {
            // A wide atom is a cage the wearer stands in the middle of and
            // looks out through; a tight one tumbles across their face.
            return radius >= SignatureConcepts.CLEAR_RADIUS;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                for (int side = 0; side < 2; side++) {
                    // Never billboarded: an atom piece rides out at a radius,
                    // and a billboarded piece reads its offset in the
                    // camera's frame, so it would hang off the viewer's
                    // screen rather than orbit. It is turned to face along
                    // its own path instead.
                    displays.add(parts.item(player, material, pose(k, side, 0)));
                }
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % every != 0) return;
            long n = frame / every + 1;
            for (int k = 0; k < 3; k++) {
                for (int side = 0; side < 2; side++) {
                    move(displays.get(k * 2 + side), pose(k, side, n), every * 2);
                }
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            double steps = (double) frame / every;
            for (int k = 0; k < 3; k++) {
                for (int side = 0; side < 2; side++) {
                    Vector3f p = offset(k, angle(k, side, steps));
                    out.add(new Vector(p.x, RIDE + y + p.y, p.z));
                }
            }
        }

        private double angle(int k, int side, double steps) {
            return Math.toRadians(phase + 180 * side + Atom.direction(k) * step * steps);
        }

        private Vector3f offset(int k, double a) {
            return Atom.TILTS[k].transform(new Vector3f((float) (radius * Math.cos(a)), 0f, (float) (-radius * Math.sin(a))));
        }

        private Transformation pose(int k, int side, double steps) {
            double a = angle(k, side, steps);
            Vector3f p = offset(k, a);
            // A flat sprite gets the same turn as a gem that follows its
            // path, which keeps its face along the orbit instead of edge on.
            Quaternionf rotation = facing || faceViewer
                    ? new Quaternionf(Atom.TILTS[k]).rotateY((float) a + rad(90))
                    : new Quaternionf().rotateY((float) (a * 2)).rotateX(rad(35)).rotateZ(rad(45));
            return at(p.x, y + p.y, p.z, rotation, scale);
        }
    }

    // ------------------------------------------------------------------ pulse

    /** A ring of stars at the feet that swells and settles while it turns, like breathing. */
    static final class Pulse implements AuraConcept {
        private final Color color;
        private final Color soft;

        Pulse(Color color) {
            this.color = color;
            this.soft = softer(color);
        }

        @Override
        public boolean clearOfView() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                displays.add(parts.text(player, pair("✦", 10), i == 1 ? soft : color, flat(FEET, rad(60 * i), 1.3f)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            long n = frame / 10 + 1;
            float scale = n % 2 == 0 ? 1.3f : 2.0f;
            // A sixth of a turn per breath, well inside what a slerp keeps straight.
            int turn = (int) (n % 6);
            for (int i = 0; i < 3; i++) {
                move(displays.get(i), flat(FEET, rad(60 * i + 60 * turn), scale), 20);
            }
        }
    }

    // ------------------------------------------------------------------ cubes

    /** Three small blocks of the rarity's own material orbiting the chest, tumbling as they go. */
    static final class Cubes implements AuraConcept {
        private static final float Y = -0.75f;
        private static final float RADIUS = 0.95f;
        private static final float SCALE = 0.28f;
        private static final int EVERY = 2;
        private static final double STEP = 30.0;
        private final Material material;

        Cubes(Material material) {
            this.material = material;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                displays.add(parts.item(player, material, pose(i, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long n = frame / EVERY + 1;
            for (int i = 0; i < 3; i++) {
                move(displays.get(i), pose(i, n), EVERY * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            double steps = (double) frame / EVERY;
            for (int i = 0; i < 3; i++) {
                double a = Math.toRadians(120 * i + STEP * steps);
                out.add(new Vector(RADIUS * Math.cos(a), RIDE + Y, -RADIUS * Math.sin(a)));
            }
        }

        private static Transformation pose(int i, double steps) {
            double a = Math.toRadians(120 * i + STEP * steps);
            // Turning twice as fast as it orbits, on a tipped axis, so every face shows.
            Quaternionf tumble = new Quaternionf().rotateY((float) (a * 2)).rotateX(rad(35)).rotateZ(rad(45));
            return at((float) (RADIUS * Math.cos(a)), Y, (float) (-RADIUS * Math.sin(a)), tumble, SCALE);
        }
    }

    // ----------------------------------------------------------------- shards

    /** Four of the rarity's gems circling the waist on a gently tilted ring, each facing along its path. */
    static final class Shards implements AuraConcept {
        private static final float Y = -1.0f;
        private static final float RADIUS = 0.85f;
        private static final float SCALE = 0.45f;
        private static final int EVERY = 2;
        private static final double STEP = 24.0;
        private static final Quaternionf TILT = new Quaternionf().rotateX(rad(18));
        private final Material material;

        Shards(Material material) {
            this.material = material;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                displays.add(parts.item(player, material, pose(i, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long n = frame / EVERY + 1;
            for (int i = 0; i < 4; i++) {
                move(displays.get(i), pose(i, n), EVERY * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            for (int i = 0; i < 4; i++) {
                Vector3f p = offset(angle(i, (double) frame / EVERY));
                out.add(new Vector(p.x, RIDE + Y + p.y, p.z));
            }
        }

        /** Clockwise, so it crosses any counter-clockwise look worn with it. */
        private static double angle(int i, double steps) {
            return Math.toRadians(90 * i - STEP * steps);
        }

        private static Vector3f offset(double a) {
            return TILT.transform(new Vector3f((float) (RADIUS * Math.cos(a)), 0f, (float) (-RADIUS * Math.sin(a))));
        }

        private static Transformation pose(int i, double steps) {
            double a = angle(i, steps);
            Vector3f p = offset(a);
            Quaternionf facing = new Quaternionf(TILT).rotateY((float) a + rad(90));
            return at(p.x, Y + p.y, p.z, facing, SCALE);
        }
    }

    // --------------------------------------------------------------- combined

    /** Several looks worn at once, each driving its own share of the displays. */
    static final class Combined implements AuraConcept {
        private final AuraConcept[] looks;
        private final int[] starts;

        Combined(AuraConcept... looks) {
            this.looks = looks;
            this.starts = new int[looks.length + 1];
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < looks.length; i++) {
                starts[i] = displays.size();
                displays.addAll(looks[i].spawn(player, parts));
            }
            starts[looks.length] = displays.size();
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            for (int i = 0; i < looks.length; i++) {
                looks[i].tick(displays.subList(starts[i], starts[i + 1]), frame);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            for (AuraConcept look : looks) {
                look.stars(frame, out);
            }
        }

        @Override
        public boolean followsBody() {
            for (AuraConcept look : looks) {
                if (look.followsBody()) return true;
            }
            return false;
        }

        @Override
        public boolean clearOfView() {
            for (AuraConcept look : looks) {
                if (!look.clearOfView()) return false;
            }
            return true;
        }

        /** Whether piece {@code index} of this combination belongs to a look that stays out of the wearer's view. */
        boolean clearAt(int index) {
            for (int i = 0; i < looks.length; i++) {
                if (index >= starts[i] && index < starts[i + 1]) {
                    return looks[i] instanceof Combined inner
                            ? inner.clearAt(index - starts[i]) : looks[i].clearOfView();
                }
            }
            return false;
        }

        /**
         * Whether piece {@code index} belongs to a look that turns with the
         * body. Asked per piece rather than per look, because a pair of wings
         * put on top of ground rings would otherwise hand every ring the
         * smoothing that only the wings need, and the rings would swim behind
         * the wearer again.
         */
        boolean followsAt(int index) {
            for (int i = 0; i < looks.length; i++) {
                if (index >= starts[i] && index < starts[i + 1]) {
                    return looks[i] instanceof Combined inner
                            ? inner.followsAt(index - starts[i]) : looks[i].followsBody();
                }
            }
            return false;
        }
    }
}
