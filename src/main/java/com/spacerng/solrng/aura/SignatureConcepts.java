package com.spacerng.solrng.aura;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.spacerng.solrng.aura.AuraParts.FEET;
import static com.spacerng.solrng.aura.AuraParts.RIDE;
import static com.spacerng.solrng.aura.AuraParts.moveTo;
import static com.spacerng.solrng.aura.AuraParts.rad;
import static com.spacerng.solrng.aura.AuraParts.softer;

/**
 * The signature look of each rarity, and the pieces they are built from.
 *
 * Every other look in this plugin is drawn with star glyphs and item
 * models. Both have a hard limit: a glyph takes any colour but is always
 * the shape of a star, and an item model is any shape but only ever the
 * colours Mojang painted on it. So a Legendary aura ended up as orange
 * stars around a sea lantern that is not orange at all.
 *
 * These looks are drawn with plates instead: the painted background of a
 * text display holding a single space, which is a rectangle in any RGB at
 * any transparency, with a matrix deciding where that rectangle goes. A
 * ring is a polygon of them, a flame is one standing on its end, a column
 * of light is one that turns to face whoever is looking. Colour and shape
 * at the same time, which is what the other looks never had.
 *
 * Cost sits where the grand looks put it: a ring only sends updates while
 * it turns, and a ring drawn full and still sends none at all after it is
 * built.
 */
final class SignatureConcepts {

    private SignatureConcepts() {
    }

    static void describe(Map<String, String> d) {
        d.put("sigil", "a broken circle turning over a still one, with four spokes");
        d.put("ember", "two circles on the floor and six flames breathing at the waist");
        d.put("eclipse", "a ring standing round the body, sweeping over a wide floor");
        d.put("ascend", "a slanted orbit, a column of light and a lantern atom");
        d.put("signature", "whichever of the four painted looks the rarity wears");
        d.put("prism", "the sigil with a ring standing through it and a short column");
        d.put("pyre", "the flames with wings of fire off the back and a tall column");
        d.put("rift", "two standing rings crossed and swinging opposite ways (heavy)");
        d.put("empyrean", "the slanted orbit with wings and a crown of light (heavy)");
        d.put("shiny", "whichever of the four shiny looks the rarity wears");
        d.put("armillary", "three rings round the body at different angles, all turning");
        d.put("vortex", "five circles narrowing up the body, the top one fastest");
        d.put("cage", "eight bars standing round you between two solid circles");
        d.put("shield", "six wide panels standing round the hips, slowly turning");
        d.put("beacon", "a column of light nine blocks up, a floor circle and a halo");
        d.put("portal", "a ring standing round you with a second inside and an eye");
    }

    static AuraConcept create(String key, Rarity rarity, Color color) {
        return switch (key) {
            case "sigil" -> sigil(color);
            case "ember" -> ember(color);
            case "eclipse" -> eclipse(color);
            case "ascend" -> ascend(color);
            case "signature" -> forRarity(rarity, color);
            case "prism" -> prism(color);
            case "pyre" -> pyre(color);
            case "rift" -> rift(color);
            case "empyrean" -> empyrean(color);
            case "shiny" -> shinyFor(rarity, color);
            case "armillary" -> armillary(color);
            case "vortex" -> vortex(color);
            case "cage" -> cage(color);
            case "shield" -> shield(color);
            case "beacon" -> beacon(color);
            case "portal" -> portal(color);
            default -> null;
        };
    }

    /** The look a rarity wears when nothing else is chosen. */
    static AuraConcept forRarity(Rarity rarity, Color color) {
        return switch (rarity) {
            case DIVINE -> ascend(color);
            case MYTHICAL -> eclipse(color);
            case LEGENDARY -> ember(color);
            default -> sigil(color);
        };
    }

    /** The look unlocked by finding a shiny of that rarity. */
    static AuraConcept shinyFor(Rarity rarity, Color color) {
        return switch (rarity) {
            case DIVINE -> empyrean(color);
            case MYTHICAL -> rift(color);
            case LEGENDARY -> pyre(color);
            default -> prism(color);
        };
    }

    // ----------------------------------------------------------- the four looks

    /**
     * Epic, the smallest of the four and the only one that stays inside two
     * blocks. A broken circle turning one way over a still, fainter circle,
     * with four spokes crossing between them the other way. Nothing above
     * the waist, so an Epic tag reads as a mark on the floor rather than as
     * something worn.
     */
    private static AuraConcept sigil(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 235, FEET + 0.02f, 1.35f, 8, 0.09f, 0.55f, 0f, 3, 7.5, 0.0),
                new PlateRing(soft, 150, FEET + 0.01f, 0.90f, 14, 0.05f, 1.0f, 0f, 0, 0.0, 0.0),
                new Spokes(color, 200, FEET + 0.03f, 0.95f, 1.30f, 4, 0.06f, 4, -6.0));
    }

    /**
     * Legendary. Two circles on the floor, the outer one broken and turning,
     * and six flames standing round the waist that rise and fall in turn, so
     * the wave runs round the body once every six seconds.
     */
    private static AuraConcept ember(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 240, FEET + 0.01f, 1.95f, 16, 0.11f, 1.0f, 0f, 0, 0.0, 0.0),
                new PlateRing(soft, 170, FEET + 0.02f, 2.55f, 10, 0.07f, 0.5f, 0f, 3, 5.0, 0.0),
                new Petals(color, 200, -1.15f, 1.05f, 6, 0.34f, 1.15f, 18f, 5, 12));
    }

    /**
     * Mythical. A ring nearly four blocks across standing upright around the
     * wearer, swinging round them like a gyroscope while its own gaps travel
     * the other way, over a wide floor circle. One nether star hangs at the
     * chest with a red outline, so a Mythical tag is recognisable through a
     * wall, which is most of the reason to wear one.
     */
    private static AuraConcept eclipse(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 225, -0.75f, 1.90f, 12, 0.11f, 0.72f, 90f, 4, 8.0, 4.0),
                new PlateRing(color, 240, FEET + 0.01f, 2.40f, 16, 0.12f, 1.0f, 0f, 0, 0.0, 0.0),
                new PlateRing(soft, 160, FEET + 0.02f, 3.00f, 12, 0.07f, 0.45f, 0f, 4, -6.0, 0.0),
                new Core(Material.NETHER_STAR, color, -0.55f, 0.55f));
    }

    /**
     * Divine. Three circles on the floor, a slanted orbit two and a half
     * blocks out that never lies in the same plane twice, a column of light
     * standing seven blocks up through the wearer, and the sea lantern atom
     * the old Divine look was built round, kept because it is the one piece
     * with real depth to it.
     */
    private static AuraConcept ascend(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 240, FEET + 0.01f, 2.30f, 16, 0.13f, 1.0f, 0f, 0, 0.0, 0.0),
                new PlateRing(soft, 165, FEET + 0.02f, 3.30f, 10, 0.08f, 0.45f, 0f, 4, 5.0, 0.0),
                new PlateRing(soft, 120, FEET + 0.03f, 4.10f, 8, 0.06f, 0.35f, 0f, 8, -5.5, 0.0),
                new PlateRing(color, 215, -0.85f, 2.55f, 10, 0.10f, 0.8f, 25f, 5, 9.0, 5.0),
                new Column(color, -1.70f, 7.0f, 1.15f, 0.30f, 55, 150),
                new AuraConcepts.SolidAtom(Material.SEA_LANTERN, -0.70f, 1.45f, 0.42f, false, 0, 2, 20.0));
    }

    // ------------------------------------------------------- the four shinies

    /**
     * Epic shiny. The sigil with a ring standing through it, tipped a little
     * off upright so it never hides behind the wearer, and a short column.
     */
    private static AuraConcept prism(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                sigil(color),
                new PlateRing(soft, 205, -0.80f, 1.25f, 10, 0.08f, 0.65f, 78f, 4, 9.0, 5.0),
                new Column(color, -1.70f, 3.2f, 0.60f, 0.16f, 45, 120));
    }

    /**
     * Legendary shiny. The flames, with a pair of wings off the back drawn in
     * the same fire and a column standing through the middle. The wings are
     * the only pieces that turn with the body, so the circles under them stay
     * exactly where the wearer is.
     */
    private static AuraConcept pyre(Color color) {
        return new AuraConcepts.Combined(
                ember(color),
                new PlateWings(color, 215),
                new Column(color, -1.70f, 4.5f, 0.75f, 0.20f, 45, 130));
    }

    /**
     * Mythical shiny. Two rings standing upright through the wearer, crossed
     * a quarter turn apart and swinging opposite ways, so there is always one
     * of them broadside on however you stand. Over the floor circles and the
     * outlined star of the plain Mythical look.
     */
    private static AuraConcept rift(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 225, -0.75f, 2.00f, 10, 0.12f, 0.7f, 90f, 4, 8.0, 4.0),
                new PlateRing(soft, 190, -0.75f, 1.70f, 10, 0.10f, 0.7f, 90f, 4, -8.0, -4.0),
                new PlateRing(color, 240, FEET + 0.01f, 2.60f, 14, 0.12f, 1.0f, 0f, 0, 0.0, 0.0),
                new PlateRing(soft, 160, FEET + 0.02f, 3.30f, 8, 0.07f, 0.45f, 0f, 5, -6.0, 0.0),
                new Core(Material.NETHER_STAR, color, -0.55f, 0.60f));
    }

    /**
     * Divine shiny, the rarest thing anyone can wear. Two floor circles, the
     * slanted orbit, the column, the lantern atom, wings off the back and a
     * crown of light standing over the head.
     */
    private static AuraConcept empyrean(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 240, FEET + 0.01f, 2.30f, 16, 0.13f, 1.0f, 0f, 0, 0.0, 0.0),
                new PlateRing(soft, 165, FEET + 0.02f, 3.40f, 10, 0.08f, 0.45f, 0f, 5, 6.0, 0.0),
                new PlateRing(color, 215, -0.85f, 2.55f, 10, 0.10f, 0.8f, 25f, 5, 9.0, 5.0),
                new Column(color, -1.70f, 8.0f, 1.20f, 0.32f, 55, 155),
                new AuraConcepts.SolidAtom(Material.SEA_LANTERN, -0.70f, 1.45f, 0.42f, false, 0, 2, 20.0),
                new PlateWings(soft, 210),
                new Petals(soft, 215, 0.16f, 0.42f, 6, 0.12f, 0.34f, 12f, 5, 16));
    }

    // -------------------------------------------------------- six more to pick

    /**
     * Three rings round the whole body at three different angles, each
     * swinging its own plane at its own rate, so they are never twice in
     * the same arrangement. An armillary sphere, near enough.
     */
    private static AuraConcept armillary(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 230, -0.85f, 2.10f, 8, 0.11f, 0.75f, 0f, 5, 6.0, 0.0),
                new PlateRing(soft, 205, -0.85f, 1.85f, 8, 0.10f, 0.75f, 62f, 4, -7.0, 5.0),
                new PlateRing(color, 205, -0.85f, 2.35f, 8, 0.10f, 0.75f, 118f, 5, 7.0, -4.0));
    }

    /**
     * Five circles stacked from the floor to over the head, each narrower
     * and faster than the one under it, so the eye reads them as one funnel
     * being drawn upward rather than as five separate rings.
     */
    private static AuraConcept vortex(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 225, FEET + 0.02f, 1.90f, 8, 0.10f, 0.5f, 0f, 6, 4.0, 0.0),
                new PlateRing(soft, 205, -1.25f, 1.55f, 7, 0.09f, 0.5f, 0f, 5, 5.0, 0.0),
                new PlateRing(color, 195, -0.85f, 1.20f, 6, 0.08f, 0.5f, 0f, 4, 6.5, 0.0),
                new PlateRing(soft, 185, -0.45f, 0.85f, 5, 0.07f, 0.5f, 0f, 3, 8.0, 0.0),
                new PlateRing(color, 180, -0.05f, 0.50f, 4, 0.06f, 0.5f, 0f, 2, 10.0, 0.0));
    }

    /** Eight bars standing between a solid circle at the feet and another over the head. */
    private static AuraConcept cage(Color color) {
        return new AuraConcepts.Combined(
                new Bars(color, 190, FEET + 0.05f, 0.95f, 8, 0.10f, 2.10f, 5, 4.0),
                new PlateRing(color, 235, FEET + 0.01f, 1.02f, 10, 0.10f, 1.0f, 0f, 0, 0.0, 0.0),
                new PlateRing(color, 235, 0.33f, 1.02f, 10, 0.10f, 1.0f, 0f, 0, 0.0, 0.0));
    }

    /**
     * Six wide panels standing round the hips, faint enough to see the
     * wearer through and slow enough to read as held there rather than
     * spun. The cheapest of the lot and the only one that looks like armour.
     */
    private static AuraConcept shield(Color color) {
        return new AuraConcepts.Combined(
                new Bars(color, 95, -1.30f, 1.15f, 6, 0.95f, 1.50f, 6, 3.0),
                new PlateRing(softer(color), 210, FEET + 0.01f, 1.25f, 12, 0.08f, 1.0f, 0f, 0, 0.0, 0.0));
    }

    /** A column of light nine blocks up, a solid circle at the feet and a broken halo. */
    private static AuraConcept beacon(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new Column(color, FEET, 9.0f, 0.95f, 0.26f, 50, 145),
                new PlateRing(color, 235, FEET + 0.01f, 1.60f, 14, 0.10f, 1.0f, 0f, 0, 0.0, 0.0),
                new PlateRing(soft, 180, FEET + 0.02f, 2.20f, 10, 0.07f, 0.45f, 0f, 4, -5.0, 0.0),
                new PlateRing(soft, 210, 0.30f, 0.70f, 8, 0.06f, 0.5f, 0f, 3, 12.0, 0.0));
    }

    /**
     * A ring standing round the wearer with a smaller one inside it running
     * the other way, both on the same swinging plane, and an eye held in the
     * middle of them. No outline on this one: a look anybody can pick should
     * not be seen through walls.
     */
    private static AuraConcept portal(Color color) {
        Color soft = softer(color);
        return new AuraConcepts.Combined(
                new PlateRing(color, 230, -0.70f, 1.75f, 12, 0.12f, 0.8f, 90f, 4, 7.0, 3.0),
                new PlateRing(soft, 195, -0.70f, 1.30f, 8, 0.08f, 0.6f, 90f, 4, -9.0, 3.0),
                new Core(Material.ENDER_EYE, color, -0.70f, 0.50f, false),
                new PlateRing(color, 235, FEET + 0.01f, 1.90f, 14, 0.09f, 1.0f, 0f, 0, 0.0, 0.0));
    }

    // -------------------------------------------------------------- plate ring

    /**
     * A ring drawn as a polygon of painted plates. Each plate lies in the
     * ring's plane with its long side along the circle and its short side
     * across it, so the ring is a band of solid colour.
     *
     * A coverage under 1 leaves gaps between the plates, and that is what
     * makes a turn visible at all: a full ring of sixteen plates looks
     * exactly the same at every angle, so turning it reads as nothing. A
     * full ring is therefore left still, which also costs nothing to run.
     *
     * Tilt stands the plane up, 0 flat and 90 upright, and precess swings
     * that standing plane round the wearer, which is the one motion in the
     * plugin that cannot be mistaken for any other look.
     */
    static final class PlateRing implements AuraConcept {
        private final Color color;
        private final int alpha;
        private final float y;
        private final float radius;
        private final int segments;
        private final float thickness;
        private final float tilt;
        private final int every;
        private final double spin;
        private final double precess;
        private final float chord;
        private final boolean twoSided;

        /**
         * @param every   frames between moves, 0 for a ring that never moves
         * @param spin    degrees per move round the ring's own axis
         * @param precess degrees per move that the plane itself swings round
         */
        PlateRing(Color color, int alpha, float y, float radius, int segments, float thickness,
                  float coverage, float tilt, int every, double spin, double precess) {
            this.color = color;
            this.alpha = alpha;
            this.y = y;
            this.radius = radius;
            this.segments = segments;
            this.thickness = thickness;
            this.tilt = tilt;
            this.every = every;
            this.spin = spin;
            this.precess = precess;
            this.chord = (float) (2.0 * radius * Math.sin(Math.PI / segments)) * coverage;
            // A ring lying at the feet is only ever looked down on, so one
            // face is enough. Anything else, a ring standing up or a halo
            // over the head, is seen from the side a plate does not exist on.
            this.twoSided = !(tilt == 0f && y < FEET + 0.5f);
        }

        @Override
        public boolean lowToGround() {
            // A flat ring at the feet stays out of the wearer's own view; a
            // standing or slanted one crosses it and counts as worn.
            return !twoSided;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < segments; i++) {
                displays.add(parts.plate(player, color, alpha, pose(i, 0, false)));
                if (twoSided) displays.add(parts.plate(player, color, alpha, pose(i, 0, true)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (every == 0 || frame % every != 0) return;
            long n = frame / every + 1;
            int stride = twoSided ? 2 : 1;
            for (int i = 0; i < segments; i++) {
                moveTo(displays.get(i * stride), pose(i, n, false), every * 2);
                if (twoSided) moveTo(displays.get(i * stride + 1), pose(i, n, true), every * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            double steps = every == 0 ? 0 : (double) frame / every;
            Quaternionf plane = plane(steps);
            for (int i = 0; i < segments; i++) {
                Vector3f p = plane.transform(onCircle(radius, angle(i, steps)));
                out.add(new Vector(p.x, RIDE + y + p.y, p.z));
            }
        }

        private double angle(int i, double steps) {
            return Math.toRadians(360.0 / segments * i + spin * steps);
        }

        /** The ring's plane: tipped up by tilt, then swung by however far it has precessed. */
        private Quaternionf plane(double steps) {
            return new Quaternionf().rotateY(rad(precess * steps)).rotateX(rad(tilt));
        }

        private Matrix4f pose(int i, double steps, boolean back) {
            double a = angle(i, steps);
            Quaternionf plane = plane(steps);
            Vector3f p = plane.transform(onCircle(radius, a));
            // Turned along the circle, then laid flat so the plate's face
            // points out of the ring's plane, with the plane's own turn on top.
            Quaternionf turn = new Quaternionf(plane).rotateY((float) a + rad(90)).rotateX(rad(-90));
            return AuraParts.plate(p.x, y + p.y, p.z, turn, chord, thickness, back);
        }
    }

    // ------------------------------------------------------------------ spokes

    /** Short plates lying flat and pointing outward, from one radius to another. */
    static final class Spokes implements AuraConcept {
        private final Color color;
        private final int alpha;
        private final float y;
        private final float from;
        private final float to;
        private final int count;
        private final float width;
        private final int every;
        private final double spin;

        Spokes(Color color, int alpha, float y, float from, float to, int count, float width,
               int every, double spin) {
            this.color = color;
            this.alpha = alpha;
            this.y = y;
            this.from = from;
            this.to = to;
            this.count = count;
            this.width = width;
            this.every = every;
            this.spin = spin;
        }

        @Override
        public boolean lowToGround() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                displays.add(parts.plate(player, color, alpha, pose(i, 0)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (every == 0 || frame % every != 0) return;
            long n = frame / every + 1;
            for (int i = 0; i < count; i++) {
                moveTo(displays.get(i), pose(i, n), every * 2);
            }
        }

        private Matrix4f pose(int i, double steps) {
            double a = Math.toRadians(360.0 / count * i + spin * steps);
            Vector3f p = onCircle((from + to) / 2.0, a);
            // The long side runs outward, so this is a quarter turn short of
            // where a ring segment at the same angle would sit.
            Quaternionf turn = new Quaternionf().rotateY((float) a).rotateX(rad(-90));
            return AuraParts.plate(p.x, y, p.z, turn, to - from, width);
        }
    }

    // ------------------------------------------------------------------ petals

    /**
     * Plates standing on their ends round a circle, faces pointing outward
     * and tipped a little back, rising and falling one after another so the
     * wave runs round the body. Flames in the aura's own colour, which no
     * fire particle can be.
     */
    static final class Petals implements AuraConcept {
        private final Color color;
        private final int alpha;
        private final float y;
        private final float radius;
        private final int count;
        private final float width;
        private final float height;
        private final float lean;
        private final int every;
        private final int period;

        /** @param period steps in one full wave round the ring */
        Petals(Color color, int alpha, float y, float radius, int count, float width, float height,
               float lean, int every, int period) {
            this.color = color;
            this.alpha = alpha;
            this.y = y;
            this.radius = radius;
            this.count = count;
            this.width = width;
            this.height = height;
            this.lean = lean;
            this.every = every;
            this.period = period;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                displays.add(parts.plate(player, color, alpha, pose(i, 0, false)));
                // Both faces: a flame on the far side of the body points away
                // from anyone looking, and a plate has nothing on its back.
                displays.add(parts.plate(player, color, alpha, pose(i, 0, true)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % every != 0) return;
            long n = frame / every + 1;
            for (int i = 0; i < count; i++) {
                moveTo(displays.get(i * 2), pose(i, n, false), every * 2);
                moveTo(displays.get(i * 2 + 1), pose(i, n, true), every * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            double steps = (double) frame / every;
            for (int i = 0; i < count; i++) {
                Vector3f p = onCircle(radius, Math.toRadians(360.0 / count * i));
                out.add(new Vector(p.x, RIDE + y + height * tall(i, steps) * 0.5f, p.z));
            }
        }

        /** How tall this petal stands now: a sine a fraction of a turn behind the one before it. */
        private float tall(int i, double steps) {
            double phase = steps / period + (double) i / count;
            return (float) (0.62 + 0.38 * Math.sin(phase * Math.PI * 2));
        }

        private Matrix4f pose(int i, double steps, boolean back) {
            double a = Math.toRadians(360.0 / count * i);
            Vector3f p = onCircle(radius, a);
            float h = height * tall(i, steps);
            // Facing outward, then tipped so the top leans away from the body.
            Quaternionf turn = new Quaternionf().rotateY((float) a + rad(90)).rotateX(rad(lean));
            return AuraParts.plate(p.x, y + h / 2f, p.z, turn, width, h, back);
        }
    }

    // -------------------------------------------------------------------- bars

    /**
     * Plates standing on their ends round a circle, faces pointing outward,
     * all the same height and turning together. Narrow and tall they are the
     * bars of a cage; wide and faint they are the panels of a shield. The
     * only difference between the two is the numbers.
     */
    static final class Bars implements AuraConcept {
        private final Color color;
        private final int alpha;
        private final float y;
        private final float radius;
        private final int count;
        private final float width;
        private final float height;
        private final int every;
        private final double spin;

        Bars(Color color, int alpha, float y, float radius, int count, float width, float height,
             int every, double spin) {
            this.color = color;
            this.alpha = alpha;
            this.y = y;
            this.radius = radius;
            this.count = count;
            this.width = width;
            this.height = height;
            this.every = every;
            this.spin = spin;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                displays.add(parts.plate(player, color, alpha, pose(i, 0, false)));
                displays.add(parts.plate(player, color, alpha, pose(i, 0, true)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (every == 0 || frame % every != 0) return;
            long n = frame / every + 1;
            for (int i = 0; i < count; i++) {
                moveTo(displays.get(i * 2), pose(i, n, false), every * 2);
                moveTo(displays.get(i * 2 + 1), pose(i, n, true), every * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            double steps = every == 0 ? 0 : (double) frame / every;
            for (int i = 0; i < count; i++) {
                Vector3f p = onCircle(radius, angle(i, steps));
                out.add(new Vector(p.x, RIDE + y + height / 2f, p.z));
            }
        }

        private double angle(int i, double steps) {
            return Math.toRadians(360.0 / count * i + spin * steps);
        }

        private Matrix4f pose(int i, double steps, boolean back) {
            double a = angle(i, steps);
            Vector3f p = onCircle(radius, a);
            Quaternionf turn = new Quaternionf().rotateY((float) a + rad(90));
            return AuraParts.plate(p.x, y + height / 2f, p.z, turn, width, height, back);
        }
    }

    // ------------------------------------------------------------------ column

    /**
     * A column of light standing through the wearer: a wide faint plate with
     * a bright narrow one inside it, both set to turn with whoever is
     * looking, so the column is never seen edge on and never has to be moved
     * to keep facing anybody. It breathes, and that is all it does.
     */
    static final class Column implements AuraConcept {
        private static final int EVERY = 8;
        private final Color color;
        private final float from;
        private final float height;
        private final float wide;
        private final float core;
        private final int outerAlpha;
        private final int coreAlpha;

        Column(Color color, float from, float height, float wide, float core,
               int outerAlpha, int coreAlpha) {
            this.color = color;
            this.from = from;
            this.height = height;
            this.wide = wide;
            this.core = core;
            this.outerAlpha = outerAlpha;
            this.coreAlpha = coreAlpha;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            displays.add(facing(parts.plate(player, color, outerAlpha, pose(wide, 0))));
            displays.add(facing(parts.plate(player, softer(color), coreAlpha, pose(core, 0))));
            return displays;
        }

        /** Turning about the upright axis only, so the column stays upright at any angle. */
        private static Display facing(Display display) {
            display.setBillboard(Display.Billboard.VERTICAL);
            return display;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long n = frame / EVERY + 1;
            moveTo(displays.get(0), pose(wide, n), EVERY * 2);
            moveTo(displays.get(1), pose(core, n), EVERY * 2);
        }

        private Matrix4f pose(float width, double steps) {
            float breath = (float) (0.88 + 0.12 * Math.sin(steps / 9.0 * Math.PI * 2));
            return AuraParts.plate(0f, from + height / 2f, 0f, new Quaternionf(), width * breath, height);
        }
    }

    // -------------------------------------------------------------------- core

    /**
     * One item held at the chest, turned to whoever is looking and outlined
     * in the aura's colour, so the wearer is picked out through a wall. Kept
     * to a single piece on purpose: an outline is the loudest thing a
     * display can do, and a room full of them would be unreadable.
     */
    static final class Core implements AuraConcept {
        private static final int EVERY = 5;
        private final Material material;
        private final Color color;
        private final float y;
        private final float scale;
        private final boolean glow;

        Core(Material material, Color color, float y, float scale) {
            this(material, color, y, scale, true);
        }

        /** @param glow an outline through walls, which only the rarest looks should carry */
        Core(Material material, Color color, float y, float scale, boolean glow) {
            this.material = material;
            this.color = color;
            this.y = y;
            this.scale = scale;
            this.glow = glow;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            Display piece = parts.item(player, material, AuraParts.pose(y, new Quaternionf(), scale), true);
            return List.of(glow ? AuraParts.glowing(piece, color) : piece);
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % EVERY != 0) return;
            long n = frame / EVERY + 1;
            float breath = (float) (1.0 + 0.12 * Math.sin(n / 8.0 * Math.PI * 2));
            AuraParts.move(displays.get(0),
                    AuraParts.pose(y, new Quaternionf(), scale * breath), EVERY * 2);
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            out.add(new Vector(0.0, RIDE + y, 0.0));
        }
    }

    // ------------------------------------------------------------------- wings

    /**
     * A pair of wings fanned off the back, drawn as painted feathers with a
     * brighter plate along the leading edge. The geometry is the one the old
     * stained glass wings used, because that part was right: feathers spread
     * from one pivot at the shoulder along direction vectors, the lower ones
     * swept a little further back, the whole wing opening and folding on
     * alternate beats.
     *
     * What was wrong was the material. A wing of lime stained glass is a
     * green wing on every rarity; a wing of plates is whatever colour the
     * rarity is.
     */
    static final class PlateWings implements AuraConcept {
        private static final float PIVOT_X = 0.1f;
        private static final float PIVOT_Y = -0.38f;
        private static final float PIVOT_Z = -0.22f;
        // Four feathers rather than the old five, because each is drawn twice
        // now, once per face.
        private static final double[] RAISE = {38, 18, -4, -26};
        private static final float[] LENGTH = {1.25f, 1.4f, 1.2f, 0.9f};
        private static final float[] WIDTH = {0.22f, 0.26f, 0.23f, 0.17f};
        private static final int EVERY = 10;
        private final Color color;
        private final Color edge;
        private final int alpha;

        PlateWings(Color color, int alpha) {
            this.color = color;
            this.edge = softer(color);
            this.alpha = alpha;
        }

        @Override
        public boolean followsBody() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int side = -1; side <= 1; side += 2) {
                // Each feather twice, once for each face. A wing has a left
                // and a right side to be looked at, and a plate only exists
                // on one of them.
                for (int f = 0; f < RAISE.length; f++) {
                    displays.add(parts.plate(player, color, alpha, feather(side, f, 0, false)));
                    displays.add(parts.plate(player, color, alpha, feather(side, f, 0, true)));
                }
                displays.add(parts.plate(player, edge, 245, leading(side, 0, false)));
                displays.add(parts.plate(player, edge, 245, leading(side, 0, true)));
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
                    moveTo(displays.get(index++), feather(side, f, beat, false), EVERY * 2);
                    moveTo(displays.get(index++), feather(side, f, beat, true), EVERY * 2);
                }
                moveTo(displays.get(index++), leading(side, beat, false), EVERY * 2);
                moveTo(displays.get(index++), leading(side, beat, true), EVERY * 2);
            }
        }

        @Override
        public void stars(long frame, List<Vector> out) {
            long beat = frame / EVERY;
            for (int side = -1; side <= 1; side += 2) {
                for (int f = 0; f < RAISE.length; f++) {
                    double raise = Math.toRadians(RAISE[f] + lift(beat));
                    double swept = Math.toRadians(sweep(beat) + f * 3);
                    Vector3f dir = direction(side, raise, swept);
                    out.add(new Vector(side * PIVOT_X + dir.x * LENGTH[f],
                            RIDE + PIVOT_Y + dir.y * LENGTH[f], PIVOT_Z + dir.z * LENGTH[f]));
                }
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

        private static Quaternionf rotation(int side, double a, double b) {
            return new Quaternionf().rotateY((float) (side > 0 ? b : Math.PI - b)).rotateZ((float) a);
        }

        private static Vector3f direction(int side, double a, double b) {
            return new Vector3f((float) (side * Math.cos(a) * Math.cos(b)), (float) Math.sin(a),
                    (float) (-Math.cos(a) * Math.sin(b)));
        }

        private static Matrix4f feather(int side, int f, long beat, boolean back) {
            return blade(side, Math.toRadians(RAISE[f] + lift(beat)),
                    Math.toRadians(sweep(beat) + f * 3), LENGTH[f], WIDTH[f], -f * 0.04f, back);
        }

        /** A thinner, brighter plate along the top feather, so the wing has an edge. */
        private static Matrix4f leading(int side, long beat, boolean back) {
            return blade(side, Math.toRadians(RAISE[0] + lift(beat) + 3),
                    Math.toRadians(sweep(beat)), LENGTH[0] * 1.1f, 0.05f, 0f, back);
        }

        private static Matrix4f blade(int side, double a, double b, float length, float width,
                                      float drop, boolean back) {
            Vector3f dir = direction(side, a, b);
            float half = length / 2f;
            return AuraParts.plate(side * PIVOT_X + dir.x * half, PIVOT_Y + dir.y * half + drop,
                    PIVOT_Z + dir.z * half, rotation(side, a, b), length, width, back);
        }
    }

    /** A point on a level circle, in the same convention as a display's rotateY. */
    private static Vector3f onCircle(double radius, double angle) {
        return new Vector3f((float) (radius * Math.cos(angle)), 0f, (float) (-radius * Math.sin(angle)));
    }
}
