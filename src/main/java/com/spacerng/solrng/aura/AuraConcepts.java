package com.spacerng.solrng.aura;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * The aura looks that can be tried on with /rngadmin auratest.
 *
 * The trick they share: a star glyph sits off-centre inside its own text
 * (a few spaces push it out), so spinning the whole display around its own
 * vertical axis swings the star round the player. Rotation is slerped by
 * the client, so one update every one or two seconds reads as a perfectly
 * smooth orbit. Two stars on opposite ends of one text make a pair, and a
 * few displays set at an angle to each other make a full ring.
 */
public final class AuraConcepts {

    public static final List<String> KEYS = List.of("orbit", "runes", "celestial");

    private AuraConcepts() {
    }

    public static AuraConcept create(String key, Color color) {
        return switch (key) {
            case "orbit" -> new StarOrbit(color);
            case "runes" -> new RuneRing(color);
            case "celestial" -> new Combined(new StarOrbit(color), new RuneRing(color));
            default -> null;
        };
    }

    /** Two rings of stars, waist and shoulders, turning in opposite directions. */
    static final class StarOrbit implements AuraConcept {
        private static final float WAIST = -1.05f;
        private static final float SHOULDER = -0.45f;
        private final Color color;
        private final Color soft;

        StarOrbit(Color color) {
            this.color = color;
            this.soft = AuraParts.softer(color);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            String pair = "✦" + " ".repeat(8) + "✦";
            return List.of(
                    parts.text(player, pair, color, upright(WAIST, 0f, 2.0f)),
                    parts.text(player, pair, soft, upright(SHOULDER, AuraParts.rad(90), 1.6f)));
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 10 != 0) return; // every 20 ticks, a third of a turn
            int step = (int) ((frame / 10 + 1) % 3);
            AuraParts.move(displays.get(0), upright(WAIST, AuraParts.rad(120 * step), 2.0f), 20);
            AuraParts.move(displays.get(1), upright(SHOULDER, AuraParts.rad(90 - 120 * step), 1.6f), 20);
        }

        private static Transformation upright(float y, float angle, float scale) {
            return AuraParts.pose(y, new Quaternionf().rotateY(angle), scale);
        }
    }

    /**
     * Runes lying flat around the feet: six in an outer ring from three
     * displays set 60 degrees apart, and four smaller ones inside turning
     * the other way.
     */
    static final class RuneRing implements AuraConcept {
        private final Color color;
        private final Color soft;

        RuneRing(Color color) {
            this.color = color;
            this.soft = AuraParts.softer(color);
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            String outer = "✦" + " ".repeat(12) + "✦";
            String inner = "✧" + " ".repeat(6) + "✧";
            List<Display> displays = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                displays.add(parts.text(player, outer, color, flat(AuraParts.FEET, AuraParts.rad(60 * i), 1.8f)));
            }
            for (int i = 0; i < 2; i++) {
                displays.add(parts.text(player, inner, soft, flat(AuraParts.FEET + 0.01f, AuraParts.rad(90 * i), 1.4f)));
            }
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            if (frame % 20 != 0) return; // every 40 ticks, a slow third of a turn
            int step = (int) ((frame / 20 + 1) % 3);
            for (int i = 0; i < 3; i++) {
                AuraParts.move(displays.get(i),
                        flat(AuraParts.FEET, AuraParts.rad(60 * i + 120 * step), 1.8f), 40);
            }
            for (int i = 0; i < 2; i++) {
                AuraParts.move(displays.get(3 + i),
                        flat(AuraParts.FEET + 0.01f, AuraParts.rad(90 * i - 120 * step), 1.4f), 40);
            }
        }

        /** Laid flat first, then turned about the vertical axis, so the spin stays level. */
        private static Transformation flat(float y, float angle, float scale) {
            return AuraParts.pose(y, new Quaternionf().rotateY(angle).rotateX(AuraParts.rad(-90)), scale);
        }
    }

    /** Two looks worn at once, each driving its own share of the displays. */
    static final class Combined implements AuraConcept {
        private final AuraConcept first;
        private final AuraConcept second;
        private int split;

        Combined(AuraConcept first, AuraConcept second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public List<Display> spawn(Player player, AuraParts parts) {
            List<Display> displays = new ArrayList<>(first.spawn(player, parts));
            split = displays.size();
            displays.addAll(second.spawn(player, parts));
            return displays;
        }

        @Override
        public void tick(List<Display> displays, long frame) {
            first.tick(displays.subList(0, split), frame);
            second.tick(displays.subList(split, displays.size()), frame);
        }
    }
}
