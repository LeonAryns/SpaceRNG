package com.spacerng.solrng.aura;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static com.spacerng.solrng.aura.AuraParts.FEET;
import static com.spacerng.solrng.aura.AuraParts.flat;
import static com.spacerng.solrng.aura.AuraParts.move;
import static com.spacerng.solrng.aura.AuraParts.pair;
import static com.spacerng.solrng.aura.AuraParts.rad;
import static com.spacerng.solrng.aura.AuraParts.single;
import static com.spacerng.solrng.aura.AuraParts.softer;
import static com.spacerng.solrng.aura.AuraParts.tilted;
import static com.spacerng.solrng.aura.AuraParts.upright;

/**
 * The aura looks that can be tried on with /rngadmin auratest.
 *
 * The trick they share: a star glyph sits off-centre inside its own text,
 * pushed out by spaces, so spinning the whole display about its own axis
 * swings the star round the player. Rotation is slerped by the client, so
 * one update every one or two seconds reads as a smooth orbit. Turns are
 * sent in steps of 120 degrees at most, because a slerp always takes the
 * short way round and a bigger step could reverse.
 */
public final class AuraConcepts {

    public static final List<String> KEYS = List.of(
            "orbit", "runes", "halo", "helix", "galaxy", "ripple", "atom",
            "celestial", "seraph", "nebula", "cosmos");

    private AuraConcepts() {
    }

    public static AuraConcept create(String key, Color color) {
        return switch (key) {
            case "orbit" -> new StarOrbit(color);
            case "runes" -> new RuneRing(color);
            case "halo" -> new Halo(color);
            case "helix" -> new Helix(color);
            case "galaxy" -> new Galaxy(color);
            case "ripple" -> new Ripple(color);
            case "atom" -> new Atom(color);
            case "celestial" -> new Combined(new StarOrbit(color), new RuneRing(color));
            case "seraph" -> new Combined(new Halo(color), new StarOrbit(color));
            case "nebula" -> new Combined(new Galaxy(color), new Ripple(color));
            case "cosmos" -> new Combined(new Atom(color), new RuneRing(color));
            default -> null;
        };
    }

    /** Every {@code every} frames, the step count since spawn, wrapped to a full turn of 120 degree steps. */
    private static int step(long frame, int every) {
        return (int) ((frame / every + 1) % 3);
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
            return List.of(
                    parts.text(player, pair("✦", 8), color, upright(WAIST, 0f, 2.0f)),
                    parts.text(player, pair("✦", 8), soft, upright(SHOULDER, rad(90), 1.6f)));
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            int step = step(frame, 10);
            move(displays.get(0), upright(WAIST, rad(120 * step), 2.0f), 20);
            move(displays.get(1), upright(SHOULDER, rad(90 - 120 * step), 1.6f), 20);
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
            return List.of(
                    parts.text(player, single("✦", 9), color, upright(heightAt(0), 0f, 1.8f)),
                    parts.text(player, single("✦", 9), soft, upright(heightAt(LEGS), rad(180), 1.8f)));
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            long n = frame / 10 + 1;
            int turn = (int) (n % 3);
            move(displays.get(0), upright(heightAt(n), rad(120 * turn), 1.8f), 20);
            move(displays.get(1), upright(heightAt(n + LEGS), rad(180 + 120 * turn), 1.8f), 20);
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

    /** Three slanted orbits around the chest, 60 degrees apart, like an atom. */
    static final class Atom implements AuraConcept {
        private static final float CHEST = -0.8f;
        private static final Quaternionf[] TILTS = {
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
                displays.add(parts.text(player, pair("✦", 7), k == 1 ? soft : color,
                        tilted(CHEST, TILTS[k], 0f, 1.7f)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return;
            int step = step(frame, 10);
            for (int k = 0; k < 3; k++) {
                // The middle orbit runs the other way, so the three never line up.
                int direction = k == 1 ? -1 : 1;
                move(displays.get(k), tilted(CHEST, TILTS[k], rad(direction * 120 * step), 1.7f), 20);
            }
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
    }
}
