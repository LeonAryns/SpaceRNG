package com.spacerng.solrng.boss;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraParts;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * The arena a boss stands in.
 *
 * A boss was one item model turning over a spot with a ring of dust
 * running outward from it. That reads as a floating icon rather than as
 * an event, and from any distance the dust is gone. This paints the
 * ground instead: a solid circle five blocks out marking where it is, a
 * second circle inside it that empties as the clock runs down, and a
 * slanted ring turning round the body itself.
 *
 * The clock is the point of it. Every fighter has their own health, so
 * the panel can only ever say what the event is, not how anybody is
 * doing. The one number the whole server shares is the time left, and a
 * circle that visibly closes says it from across the map without anybody
 * reading a word.
 *
 * These stand in the world rather than on a player, so they are placed
 * once and only the clock ring is ever rewritten.
 */
final class BossCircle {

    private static final int ARENA = 20;
    private static final int CLOCK = 24;
    private static final int ORBIT = 12;
    private static final double VIEW = 96.0;

    private final SolRNGPlugin plugin;
    private final Location centre;
    private final Color colour;
    private final Color soft;
    private final List<Display> arena = new ArrayList<>();
    private final List<Display> clock = new ArrayList<>();
    private final List<Display> orbit = new ArrayList<>();
    private int litSegments = -1;

    BossCircle(SolRNGPlugin plugin, Location spot, Color colour) {
        this.plugin = plugin;
        this.centre = spot.clone();
        this.colour = colour;
        this.soft = AuraParts.softer(colour);
    }

    void start() {
        AuraParts parts = plugin.getAuraManager().parts();
        for (int i = 0; i < ARENA; i++) {
            arena.add(parts.plate(centre, colour, 235, VIEW, false,
                    flat(ARENA, i, 5.0f, 0.0, 0.22f, 1.0f)));
        }
        for (int i = 0; i < CLOCK; i++) {
            clock.add(parts.plate(centre, soft, 210, VIEW, false,
                    flat(CLOCK, i, 4.2f, 0.0, 0.16f, 1.0f)));
        }
        for (int i = 0; i < ORBIT; i++) {
            orbit.add(parts.plate(centre, colour, 215, VIEW, false, slanted(i, 0.0, false)));
            orbit.add(parts.plate(centre, colour, 215, VIEW, false, slanted(i, 0.0, true)));
        }
    }

    /**
     * {@code left} is the fraction of the window still to run, 1 at the
     * start and 0 at the end. The clock ring keeps that many of its
     * segments and thins the rest away to nothing, so the circle visibly
     * opens up as the time goes and is a bare gap by the end. Only the
     * segments that changed are rewritten, so a ring that is not losing a
     * segment this frame costs nothing at all.
     */
    void tick(long frame, double left) {
        double spin = frame * 1.1;
        for (int i = 0; i < ORBIT; i++) {
            Display front = orbit.get(i * 2);
            Display back = orbit.get(i * 2 + 1);
            if (front.isValid()) AuraParts.moveTo(front, slanted(i, spin, false), 8);
            if (back.isValid()) AuraParts.moveTo(back, slanted(i, spin, true), 8);
        }
        int lit = (int) Math.round(Math.max(0.0, Math.min(1.0, left)) * CLOCK);
        if (lit == litSegments) return;
        litSegments = lit;
        for (int i = 0; i < CLOCK; i++) {
            Display piece = clock.get(i);
            if (!piece.isValid()) continue;
            AuraParts.moveTo(piece, flat(CLOCK, i, 4.2f, 0.0, 0.16f, i < lit ? 1.0f : 0.001f), 10);
        }
    }

    void remove() {
        for (List<Display> group : List.of(arena, clock, orbit)) {
            for (Display piece : group) {
                if (piece.isValid()) piece.remove();
            }
            group.clear();
        }
    }

    /** One segment of a ring lying flat just off the ground. */
    private Matrix4f flat(int segments, int i, float radius, double spin, float thickness, float length) {
        double a = Math.toRadians(360.0 / segments * i + spin);
        float chord = (float) (2.0 * radius * Math.sin(Math.PI / segments)) * 0.9f * length;
        Quaternionf turn = new Quaternionf().rotateY((float) a + AuraParts.rad(90)).rotateX(AuraParts.rad(-90));
        return AuraParts.plate((float) (radius * Math.cos(a)), 0.08f, (float) (-radius * Math.sin(a)),
                turn, Math.max(0.001f, chord), thickness);
    }

    /** One segment of the ring slanted round the body, drawn for both of its faces. */
    private Matrix4f slanted(int i, double spin, boolean back) {
        float radius = 2.3f;
        Quaternionf plane = new Quaternionf().rotateY(AuraParts.rad(spin * 0.35)).rotateX(AuraParts.rad(28));
        double a = Math.toRadians(360.0 / ORBIT * i + spin);
        org.joml.Vector3f p = plane.transform(new org.joml.Vector3f(
                (float) (radius * Math.cos(a)), 0f, (float) (-radius * Math.sin(a))));
        float chord = (float) (2.0 * radius * Math.sin(Math.PI / ORBIT)) * 0.7f;
        Quaternionf turn = new Quaternionf(plane).rotateY((float) a + AuraParts.rad(90)).rotateX(AuraParts.rad(-90));
        return AuraParts.plate(p.x, 2.2f + p.y, p.z, turn, chord, 0.14f, back);
    }
}
