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

import static com.spacerng.solrng.aura.AuraParts.RIDE;
import static com.spacerng.solrng.aura.AuraParts.at;
import static com.spacerng.solrng.aura.AuraParts.atScaled;
import static com.spacerng.solrng.aura.AuraParts.move;
import static com.spacerng.solrng.aura.AuraParts.rad;

/**
 * Looks built from item displays that go past orbiting stars: a ring of
 * blades, a crown, pillars of coloured glass and a small planet with moons.
 * Kept apart from AuraConcepts so neither file turns into a wall;
 * AuraConcepts lists these and hands them out.
 *
 * Every piece here is an item model, centred on its own origin, so pieces
 * orbit by moving their translation. Steps stay small enough that the chord
 * between two positions can't be told from the arc at that radius.
 */
final class DisplayConcepts {

    private DisplayConcepts() {
    }

    static void describe(Map<String, String> d) {
        d.put("blades", "six swords circling the waist, blades pointing out");
        d.put("crown", "a crown of eight gems standing on the head, stepping round");
        d.put("pillars", "four beams of the rarity's glass round the body, rising and falling");
        d.put("planet", "a lantern planet above the head with three moons");
        d.put("warlord", "blades and crown");
        d.put("sanctum", "pillars, runes and halo");
        d.put("wings", "stained glass wings fanned from the back, beating gently (experimental)");
        d.put("barrier", "a slow hexagon of the rarity's glass round the hips");
    }

    static AuraConcept create(String key, Rarity rarity, Color color) {
        return switch (key) {
            case "blades" -> new Blades(rarity);
            case "crown" -> new Crown(rarity);
            case "pillars" -> new Pillars(rarity);
            case "planet" -> new Planet(rarity);
            case "warlord" -> new AuraConcepts.Combined(new Blades(rarity), new Crown(rarity));
            case "sanctum" -> new AuraConcepts.Combined(new Pillars(rarity),
                    new AuraConcepts.RuneRing(color), new AuraConcepts.Halo(color));
            case "wings" -> new Wings(rarity);
            case "barrier" -> new Barrier(rarity);
            default -> null;
        };
    }

    /** A point on a level circle, in the same convention as a display's rotateY, so pieces and stars agree. */
    private static Vector3f onCircle(double radius, double angle) {
        return new Vector3f((float) (radius * Math.cos(angle)), 0f, (float) (-radius * Math.sin(angle)));
    }

    // ----------------------------------------------------------------- blades

    /** Six swords circling the waist with their tips pointing outward and a little down. */
    static final class Blades implements AuraConcept {
        private static final float Y = -1.0f;
        private static final float RADIUS = 0.95f;
        private static final float SCALE = 0.85f;
        private static final int COUNT = 6;
        private static final int EVERY = 2;
        private static final double STEP = 15.0;
        private final Material sword;

        Blades(Rarity rarity) {
            this.sword = switch (rarity) {
                case DIVINE -> Material.DIAMOND_SWORD;
                case MYTHICAL -> Material.NETHERITE_SWORD;
                case LEGENDARY -> Material.GOLDEN_SWORD;
                default -> Material.IRON_SWORD;
            };
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < COUNT; i++) {
                displays.add(parts.item(player, sword, pose(i, 0)));
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
            for (int i = 0; i < COUNT; i++) {
                Vector3f p = onCircle(RADIUS + 0.3, angle(i, (double) frame / EVERY));
                out.add(new Vector(p.x, RIDE + Y, p.z));
            }
        }

        private static double angle(int i, double steps) {
            return Math.toRadians(360.0 / COUNT * i + STEP * steps);
        }

        private static Transformation pose(int i, double steps) {
            double a = angle(i, steps);
            Vector3f p = onCircle(RADIUS, a);
            // The sword sprite points up and to the right. Rolling it 60
            // degrees lays the tip outward and slightly down; the turn about
            // the vertical axis then aims it away from the player.
            Quaternionf aim = new Quaternionf().rotateY((float) a).rotateZ(rad(-60));
            return at(p.x, Y, p.z, aim, SCALE);
        }
    }

    // ------------------------------------------------------------------ crown

    /**
     * Eight gems standing upright in a ring on the head, each facing out.
     * Once a second the ring steps round by exactly one gem, which reads as
     * a slow turn and costs one update per gem per second.
     */
    static final class Crown implements AuraConcept {
        private static final float Y = 0.12f;
        private static final float RADIUS = 0.34f;
        private static final float SCALE = 0.32f;
        private static final int COUNT = 8;
        private static final int EVERY = 10;
        private final Material gem;

        Crown(Rarity rarity) {
            this.gem = AuraConcepts.gem(rarity);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < COUNT; i++) {
                displays.add(parts.item(player, gem, pose(i, 0)));
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

        private static Transformation pose(int i, long steps) {
            double a = Math.toRadians(360.0 / COUNT * (i + steps));
            Vector3f p = onCircle(RADIUS, a);
            // A quarter turn past the heading makes the flat sprite face outward.
            return at(p.x, Y, p.z, new Quaternionf().rotateY((float) (a + Math.PI / 2)), SCALE);
        }
    }

    // ---------------------------------------------------------------- pillars

    /**
     * Four beams of the rarity's stained glass standing round the body, a
     * glowing end rod inside each, circling slowly and bobbing in turn so
     * two are always rising while two fall.
     */
    static final class Pillars implements AuraConcept {
        private static final float RADIUS = 1.1f;
        private static final float CENTRE = -0.9f;
        private static final int COUNT = 4;
        private static final int EVERY = 4;
        private static final double STEP = 20.0;
        private final Material glass;

        Pillars(Rarity rarity) {
            this.glass = switch (rarity) {
                case DIVINE -> Material.WHITE_STAINED_GLASS;
                case MYTHICAL -> Material.RED_STAINED_GLASS;
                case LEGENDARY -> Material.ORANGE_STAINED_GLASS;
                default -> Material.PURPLE_STAINED_GLASS;
            };
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < COUNT; i++) {
                displays.add(parts.item(player, glass, beam(i, 0)));
                displays.add(parts.item(player, Material.END_ROD, core(i, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long n = frame / EVERY + 1;
            for (int i = 0; i < COUNT; i++) {
                move(displays.get(i * 2), beam(i, n), EVERY * 2);
                move(displays.get(i * 2 + 1), core(i, n), EVERY * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            long n = frame / EVERY;
            for (int i = 0; i < COUNT; i++) {
                Vector3f p = onCircle(RADIUS, angle(i, n));
                out.add(new Vector(p.x, RIDE + CENTRE + lift(i, n) + 1.0, p.z));
            }
        }

        private static double angle(int i, long steps) {
            return Math.toRadians(90.0 * i + STEP * steps);
        }

        /** Alternate pillars sit high on alternate steps, so the pairs pass each other. */
        private static float lift(int i, long steps) {
            return (i + steps) % 2 == 0 ? 0f : 0.25f;
        }

        private static Transformation beam(int i, long steps) {
            double a = angle(i, steps);
            Vector3f p = onCircle(RADIUS, a);
            return atScaled(p.x, CENTRE + lift(i, steps), p.z, new Quaternionf().rotateY((float) a), 0.14f, 2.0f, 0.14f);
        }

        private static Transformation core(int i, long steps) {
            double a = angle(i, steps);
            Vector3f p = onCircle(RADIUS, a);
            return atScaled(p.x, CENTRE + lift(i, steps), p.z, new Quaternionf().rotateY((float) a), 1.0f, 1.8f, 1.0f);
        }
    }

    // ----------------------------------------------------------------- planet

    /**
     * A lantern planet floating above the floating tag, turning on a tipped
     * axis, with three of the rarity's gems as moons on a slanted orbit.
     */
    static final class Planet implements AuraConcept {
        private static final float Y = 1.45f;
        private static final float MOON_RADIUS = 0.5f;
        private static final Quaternionf ORBIT = new Quaternionf().rotateX(rad(25));
        private final Material core;
        private final Material moon;

        Planet(Rarity rarity) {
            this.core = AuraConcepts.lantern(rarity);
            this.moon = AuraConcepts.gem(rarity);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            displays.add(parts.item(player, core, planet(0)));
            for (int m = 0; m < 3; m++) {
                displays.add(parts.item(player, moon, moonPose(m, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 == 0) {
                move(displays.get(0), planet(frame / 10 + 1), 20);
            }
            if (frame % 2 == 0) {
                long n = frame / 2 + 1;
                for (int m = 0; m < 3; m++) {
                    move(displays.get(1 + m), moonPose(m, n), 4);
                }
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            for (int m = 0; m < 3; m++) {
                Vector3f p = ORBIT.transform(onCircle(MOON_RADIUS, moonAngle(m, (double) frame / 2)));
                out.add(new Vector(p.x, RIDE + Y + p.y, p.z));
            }
        }

        private static Transformation planet(long steps) {
            // A quarter turn a second on an axis tipped 20 degrees.
            Quaternionf spin = new Quaternionf().rotateY(rad(90.0 * steps)).rotateX(rad(20));
            return at(0f, Y, 0f, spin, 0.34f);
        }

        private static double moonAngle(int m, double steps) {
            return Math.toRadians(120.0 * m + 30.0 * steps);
        }

        private static Transformation moonPose(int m, double steps) {
            double a = moonAngle(m, steps);
            Vector3f p = ORBIT.transform(onCircle(MOON_RADIUS, a));
            return at(p.x, Y + p.y, p.z, new Quaternionf().rotateY((float) (a * 2)), 0.2f);
        }
    }

    /** The rarity's stained glass. */
    private static Material glass(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> Material.WHITE_STAINED_GLASS;
            case MYTHICAL -> Material.RED_STAINED_GLASS;
            case LEGENDARY -> Material.ORANGE_STAINED_GLASS;
            default -> Material.PURPLE_STAINED_GLASS;
        };
    }

    // ------------------------------------------------------------------ wings

    /**
     * Wings of the rarity's stained glass: five feathers a side fanned out
     * from the shoulder blades, a glowing end rod on each leading edge, a
     * gentle beat once a second. They hang off the back, so this look turns
     * with the body.
     *
     * A feather is an item model centred on its origin, so it is placed at
     * the middle of its reach, the pivot plus half its length along its
     * direction, and rotated so its long axis (model X) points that way:
     * raised by alpha about Z, then swung back by beta about Y. The left
     * wing mirrors that by swinging to 180 - beta.
     */
    static final class Wings implements AuraConcept {
        private static final float PIVOT_X = 0.1f;
        private static final float PIVOT_Y = -0.38f;
        private static final float PIVOT_Z = -0.22f;
        private static final double[] RAISE = {40, 22, 5, -15, -35};
        private static final float[] LENGTH = {1.2f, 1.35f, 1.25f, 1.0f, 0.75f};
        private static final int EVERY = 10;
        private final Material glass;

        Wings(Rarity rarity) {
            this.glass = glass(rarity);
        }

        @Override
        public boolean followsBody() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int side = -1; side <= 1; side += 2) {
                for (int f = 0; f < RAISE.length; f++) {
                    displays.add(parts.item(player, glass, feather(side, f, 0)));
                }
                displays.add(parts.item(player, Material.END_ROD, edge(side, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long beat = frame / EVERY + 1;
            int index = 0;
            for (int side = -1; side <= 1; side += 2) {
                for (int f = 0; f < RAISE.length; f++) {
                    move(displays.get(index++), feather(side, f, beat), EVERY * 2);
                }
                move(displays.get(index++), edge(side, beat), EVERY * 2);
            }
        }

        /** Degrees swept back: open and folded on alternate beats. */
        private static double sweep(long beat) {
            return beat % 2 == 0 ? 25 : 42;
        }

        /** Degrees added to every feather's raise: up on the open beat, down on the fold. */
        private static double lift(long beat) {
            return beat % 2 == 0 ? 6 : -4;
        }

        private static Quaternionf rotation(int side, double alpha, double beta) {
            return new Quaternionf().rotateY((float) (side > 0 ? beta : Math.PI - beta)).rotateZ((float) alpha);
        }

        private static Vector3f direction(int side, double alpha, double beta) {
            return new Vector3f((float) (side * Math.cos(alpha) * Math.cos(beta)), (float) Math.sin(alpha),
                    (float) (-Math.cos(alpha) * Math.sin(beta)));
        }

        private static Transformation feather(int side, int f, long beat) {
            double alpha = Math.toRadians(RAISE[f] + lift(beat));
            // Lower feathers sweep a touch further back, like a real wing.
            double beta = Math.toRadians(sweep(beat) + f * 3);
            Vector3f dir = direction(side, alpha, beta);
            float half = LENGTH[f] / 2;
            return atScaled(side * PIVOT_X + dir.x * half, PIVOT_Y + dir.y * half - f * 0.04f,
                    PIVOT_Z + dir.z * half, rotation(side, alpha, beta), LENGTH[f], 0.11f, 0.05f);
        }

        /** The end rod along the top feather; the rod model is long on Y, so it is turned onto X first. */
        private static Transformation edge(int side, long beat) {
            double alpha = Math.toRadians(RAISE[0] + lift(beat));
            double beta = Math.toRadians(sweep(beat));
            Vector3f dir = direction(side, alpha, beta);
            float half = LENGTH[0] / 2;
            Quaternionf rod = rotation(side, alpha, beta).rotateZ(rad(-90));
            return atScaled(side * PIVOT_X + dir.x * half, PIVOT_Y + dir.y * half + 0.06f,
                    PIVOT_Z + dir.z * half, rod, 0.8f, LENGTH[0], 0.8f);
        }
    }

    // ---------------------------------------------------------------- barrier

    /**
     * Six panes of the rarity's glass closing into a hexagon round the hips,
     * turning slowly. Kept below the eyes on purpose: a shell the wearer's
     * camera sat inside would tint their whole screen.
     */
    static final class Barrier implements AuraConcept {
        private static final float RADIUS = 1.15f;
        private static final float CENTRE = -1.35f;
        private static final float HEIGHT = 0.8f;
        private static final int COUNT = 6;
        private static final int EVERY = 4;
        private static final double STEP = 15.0;
        // Wide enough that neighbouring panes meet at the hexagon's corners.
        private static final float WIDTH = (float) (2 * RADIUS * Math.tan(Math.toRadians(30)));
        private final Material glass;

        Barrier(Rarity rarity) {
            this.glass = glass(rarity);
        }

        @Override
        public boolean clearOfView() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < COUNT; i++) {
                displays.add(parts.item(player, glass, pane(i, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long n = frame / EVERY + 1;
            for (int i = 0; i < COUNT; i++) {
                move(displays.get(i), pane(i, n), EVERY * 2);
            }
        }

        private static Transformation pane(int i, long steps) {
            double a = Math.toRadians(60.0 * i + STEP * steps);
            Vector3f p = onCircle(RADIUS, a);
            // A quarter turn past the heading lays the pane along the ring.
            return atScaled(p.x, CENTRE, p.z, new Quaternionf().rotateY((float) (a + Math.PI / 2)),
                    WIDTH, HEIGHT, 0.04f);
        }
    }
}
