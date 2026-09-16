package com.spacerng.solrng.aura;

import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static com.spacerng.solrng.aura.AuraParts.FEET;
import static com.spacerng.solrng.aura.AuraParts.RIDE;
import static com.spacerng.solrng.aura.AuraParts.at;
import static com.spacerng.solrng.aura.AuraParts.move;
import static com.spacerng.solrng.aura.AuraParts.rad;

/**
 * The pet slots: up to three relics circling the wearer's hips.
 *
 * Pets live in the aura rather than walking behind the player, which is
 * the whole reason the looks stay under control. Three fixed slots at
 * 120 degrees means a player can never be surrounded by a pile of them,
 * they always sit evenly whatever combination is worn, and the pieces
 * cost exactly what one more aura would.
 *
 * Hip height and low to the ground on purpose: a wearer sees their own
 * pets when they look down, and nothing swings through their face in
 * first person.
 */
public final class PetOrbit implements AuraConcept {

    private static final float Y = FEET + 0.85f;
    private static final float RADIUS = 0.8f;
    private static final float SCALE = 0.32f;

    // A step every two frames, which is every four ticks. Eighteen degrees
    // a step is a full turn in four seconds, and the chord between two
    // steps at this radius cannot be told from the arc.
    private static final double STEP_DEGREES = 18.0;

    private final List<Material> pets;

    public PetOrbit(List<Material> pets) {
        this.pets = List.copyOf(pets);
    }

    @Override
    public boolean lowToGround() {
        return true;
    }

    @Override
    public List<Display> spawn(Player player, AuraParts parts) {
        List<Display> displays = new ArrayList<>();
        for (int i = 0; i < pets.size(); i++) {
            displays.add(parts.item(player, pets.get(i), pose(i, 0)));
        }
        return displays;
    }

    @Override
    public void tick(List<Display> displays, long frame) {
        if (frame % 2 != 0) return;
        long step = frame / 2 + 1;
        for (int i = 0; i < displays.size(); i++) {
            move(displays.get(i), pose(i, step), 4);
        }
    }

    @Override
    public void stars(long frame, List<Vector> out) {
        for (int i = 0; i < pets.size(); i++) {
            double a = angle(i, (double) frame / 2.0);
            out.add(new Vector(Math.cos(a) * RADIUS, RIDE + Y, Math.sin(a) * RADIUS));
        }
    }

    /** Where slot i sits after so many steps, spread evenly whatever is worn. */
    private double angle(int slot, double steps) {
        int slots = Math.max(1, pets.size());
        return Math.toRadians(360.0 / slots * slot + STEP_DEGREES * steps);
    }

    private Transformation pose(int slot, long steps) {
        double a = angle(slot, steps);
        // The piece turns twice for every trip round, so it reads as
        // tumbling along its path rather than being dragged sideways.
        Quaternionf spin = new Quaternionf().rotateY((float) (a * 2)).rotateX(rad(12));
        return at((float) (Math.cos(a) * RADIUS), Y, (float) (Math.sin(a) * RADIUS), spin, SCALE);
    }

    /** The pieces, as one aura look combined with whatever else is worn. */
    public static AuraConcept with(AuraConcept look, PetOrbit pets) {
        return look == null ? pets : new AuraConcepts.Combined(look, pets);
    }
}
