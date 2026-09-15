package com.spacerng.solrng.aura;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.spacerng.solrng.aura.AuraParts.FEET;
import static com.spacerng.solrng.aura.AuraParts.atScaled;
import static com.spacerng.solrng.aura.AuraParts.flat;
import static com.spacerng.solrng.aura.AuraParts.move;
import static com.spacerng.solrng.aura.AuraParts.pair;
import static com.spacerng.solrng.aura.AuraParts.rad;
import static com.spacerng.solrng.aura.AuraParts.softer;

/**
 * How far a worn aura can go: looks for tiers above Divine, built from the
 * pieces that read best (ground stars, sea lanterns, nether stars, end
 * rods) at a scale meant to fill the space around a player.
 *
 * They are deliberately heavy. Blocks and nether stars orbiting on rings
 * several blocks wide need frequent small steps to stay round, so one of
 * these costs roughly 90 to 130 transformation updates a second for each
 * player watching. Still pieces, like the beacon column, cost nothing
 * after they are built, and ground rings stay cheap at any width.
 */
final class MassiveConcepts {

    private MassiveConcepts() {
    }

    private static final Set<String> HEAVY = Set.of("singularity", "titan", "supernova");

    /** The looks AuraManager limits per area, since each costs about a hundred updates a second per viewer. */
    static boolean isHeavy(String key) {
        return HEAVY.contains(key);
    }

    static void describe(Map<String, String> d) {
        d.put("singularity", "a twelve block galaxy: star rings, nether stars, lanterns, a beacon (heavy)");
        d.put("singularity-lite", "singularity at seventy percent, the Mythical tag's look");
        d.put("titan", "three nested atoms, lanterns a block wide, star rings below (heavy)");
        d.put("supernova", "star rings bursting eight blocks out, a beacon and a lantern crown (heavy)");
    }

    static AuraConcept create(String key, Rarity rarity, Color color) {
        Color soft = softer(color);
        return switch (key) {
            case "singularity" -> new AuraConcepts.Combined(
                    new GrandConcepts.StarRing(color, FEET + 0.02f, 10, 2.2f, 3, 15, 1, "✦"),
                    new GrandConcepts.StarRing(color, FEET + 0.01f, 20, 3.0f, 4, 30, -1, "✦"),
                    new GrandConcepts.StarRing(soft, FEET, 34, 3.4f, 5, 45, 1, "✧"),
                    new GrandConcepts.FlatOrbit(Material.NETHER_STAR, true, FEET + 0.6f,
                            new float[]{2.4f, 4.6f}, 4, 1.0f, 2, 6.0),
                    new GrandConcepts.FlatOrbit(Material.SEA_LANTERN, false, FEET + 0.9f,
                            new float[]{3.5f}, 6, 0.6f, 2, 5.0),
                    new Column(Material.END_ROD, 0.6f, 3, 1.2f),
                    new GrandConcepts.RodHalo());
            // The Mythical tag's look: singularity at about seventy percent, so
            // it reads as the same galaxy without filling a whole room.
            case "singularity-lite" -> new AuraConcepts.Combined(
                    new GrandConcepts.StarRing(color, FEET + 0.02f, 7, 1.9f, 3, 15, 1, "✦"),
                    new GrandConcepts.StarRing(color, FEET + 0.01f, 14, 2.4f, 4, 30, -1, "✦"),
                    new GrandConcepts.StarRing(soft, FEET, 24, 2.8f, 5, 45, 1, "✧"),
                    new GrandConcepts.FlatOrbit(Material.NETHER_STAR, true, FEET + 0.5f,
                            new float[]{1.8f, 3.3f}, 4, 0.8f, 2, 8.0),
                    new GrandConcepts.FlatOrbit(Material.SEA_LANTERN, false, FEET + 0.75f,
                            new float[]{2.5f}, 6, 0.45f, 2, 7.0),
                    new Column(Material.END_ROD, 0.6f, 2, 1.0f),
                    new GrandConcepts.RodHalo());
            case "titan" -> new AuraConcepts.Combined(
                    new AuraConcepts.SolidAtom(Material.SEA_LANTERN, -0.4f, 4.8f, 1.0f, false, 0, 2, 8.0),
                    new AuraConcepts.SolidAtom(Material.NETHER_STAR, -0.6f, 3.0f, 1.1f, false, 60, 2, 12.0, true),
                    new AuraConcepts.SolidAtom(Material.END_ROD, -0.8f, 1.6f, 0.6f, false, 120, 2, 20.0),
                    new GrandConcepts.StarRing(color, FEET, 24, 3.6f, 5, 40, 1, "✦"),
                    new GrandConcepts.StarRing(soft, FEET + 0.01f, 12, 2.6f, 4, 25, -1, "✧"));
            case "supernova" -> new AuraConcepts.Combined(
                    new WideRipple(color, 40, 4.0f, 20, 3),
                    new GrandConcepts.StarRing(color, FEET + 0.05f, 8, 2.0f, 3, 10, 1, "✦"),
                    new Column(Material.END_ROD, 0.6f, 3, 1.2f),
                    new GrandConcepts.FlatOrbit(Material.SEA_LANTERN, false, 1.55f,
                            new float[]{1.3f}, 4, 0.4f, 2, 10.0));
            default -> null;
        };
    }

    // ----------------------------------------------------------------- column

    /**
     * A still column of glowing pieces rising from above the head, like a
     * beacon. Nothing about it moves, so after it is built it costs nothing.
     */
    static final class Column implements AuraConcept {
        private final Material material;
        private final float from;
        private final int count;
        private final float length;

        Column(Material material, float from, int count, float length) {
            this.material = material;
            this.from = from;
            this.count = count;
            this.length = length;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                displays.add(parts.item(player, material,
                        atScaled(0f, from + length * (i + 0.5f), 0f, new Quaternionf(), 1.4f, length, 1.4f)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
        }
    }

    // ------------------------------------------------------------ wide ripple

    /**
     * Rings of stars bursting outward from the feet to the width the spaces
     * allow, over and over, several at a time and staggered so one is always
     * travelling. Each snaps to nothing while invisible, then grows out; the
     * stars grow with the ring, so the far edge is made of the biggest ones.
     */
    static final class WideRipple implements AuraConcept {
        private final Color color;
        private final Color soft;
        private final int spaces;
        private final float maxScale;
        private final int cycle;
        private final int count;

        /**
         * @param cycle frames from one burst of a ring to its next
         * @param count rings running at once, staggered across the cycle
         */
        WideRipple(Color color, int spaces, float maxScale, int cycle, int count) {
            this.color = color;
            this.soft = softer(color);
            this.spaces = spaces;
            this.maxScale = maxScale;
            this.cycle = cycle;
            this.count = count;
        }

        @Override
        public boolean lowToGround() {
            return true;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                boolean even = i % 2 == 0;
                displays.add(parts.text(player, pair(even ? "✦" : "✧", spaces), even ? color : soft,
                        flat(FEET + 0.01f * i, rad(180.0 / count * i), 0.01f)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            for (int i = 0; i < count; i++) {
                long local = frame + (long) i * cycle / count;
                long phase = local % cycle;
                // A fresh heading every burst, set while the ring is too small to see.
                float angle = rad(180.0 / count * i + 30.0 * (local / cycle));
                float y = FEET + 0.01f * i;
                if (phase == 0) {
                    move(displays.get(i), flat(y, angle, 0.01f), 0);
                } else if (phase == 1) {
                    move(displays.get(i), flat(y, angle, maxScale), (cycle - 2) * 2);
                }
            }
        }
    }
}
