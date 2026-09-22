package com.spacerng.solrng.aura;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static com.spacerng.solrng.aura.AuraParts.FEET;
import static com.spacerng.solrng.aura.AuraParts.RIDE;
import static com.spacerng.solrng.aura.AuraParts.at;
import static com.spacerng.solrng.aura.AuraParts.move;
import static com.spacerng.solrng.aura.AuraParts.moveTo;
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
 *
 * Each relic rides on a painted card in its own rarity's colour (V180).
 * Before that they were three item models and nothing else, so a Common
 * pet and a Divine one were the same sight unless you already knew the
 * item, and the thing a player spent a hundred Cosmic Dust on looked like
 * the thing they were given. The card is drawn on both faces, because a
 * plate has no back, and a shiny pet carries an outline in the same
 * colour, seen through walls, since a shiny is rare enough to be worth
 * pointing at.
 */
public final class PetOrbit implements AuraConcept {

    private static final float Y = FEET + 0.85f;
    private static final float RADIUS = 0.8f;
    private static final float SCALE = 0.32f;
    private static final float CARD = 0.46f;

    // A step every two frames, which is every four ticks. Eighteen degrees
    // a step is a full turn in four seconds, and the chord between two
    // steps at this radius cannot be told from the arc.
    private static final double STEP_DEGREES = 18.0;

    /** One worn pet: what it looks like, what colour it is and whether it is shiny. */
    public record Worn(Material icon, Color colour, boolean shiny) {
    }

    private final List<Worn> pets;

    public PetOrbit(List<Worn> pets) {
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
            Worn pet = pets.get(i);
            Display relic = parts.item(player, pet.icon(), pose(i, 0));
            if (pet.shiny()) AuraParts.glowing(relic, pet.colour());
            displays.add(relic);
            displays.add(parts.plate(player, pet.colour(), pet.shiny() ? 200 : 150, card(i, 0, false)));
            displays.add(parts.plate(player, pet.colour(), pet.shiny() ? 200 : 150, card(i, 0, true)));
        }
        return displays;
    }

    @Override
    public void tick(List<Display> displays, long frame) {
        if (frame % 2 != 0) return;
        long step = frame / 2 + 1;
        for (int i = 0; i < pets.size(); i++) {
            move(displays.get(i * 3), pose(i, step), 4);
            moveTo(displays.get(i * 3 + 1), card(i, step, false), 4);
            moveTo(displays.get(i * 3 + 2), card(i, step, true), 4);
        }
    }

    @Override
    public void stars(long frame, List<Vector> out) {
        for (int i = 0; i < pets.size(); i++) {
            double a = angle(i, (double) frame / 2.0);
            out.add(new Vector(Math.cos(a) * RADIUS, RIDE + height(i, (double) frame / 2.0), Math.sin(a) * RADIUS));
        }
    }

    /** Where slot i sits after so many steps, spread evenly whatever is worn. */
    private double angle(int slot, double steps) {
        int slots = Math.max(1, pets.size());
        return Math.toRadians(360.0 / slots * slot + STEP_DEGREES * steps);
    }

    /**
     * How high slot i rides now. Each one bobs a tenth of a block, a third
     * of a cycle behind the one before it, so three pets never rise
     * together and the orbit reads as alive rather than as a turntable.
     */
    private float height(int slot, double steps) {
        int slots = Math.max(1, pets.size());
        return Y + (float) (0.10 * Math.sin(steps * 0.30 + Math.PI * 2 * slot / slots));
    }

    private Transformation pose(int slot, long steps) {
        double a = angle(slot, steps);
        // The piece turns twice for every trip round, so it reads as
        // tumbling along its path rather than being dragged sideways.
        Quaternionf spin = new Quaternionf().rotateY((float) (a * 2)).rotateX(rad(12));
        return at((float) (Math.cos(a) * RADIUS), height(slot, steps),
                (float) (Math.sin(a) * RADIUS), spin, SCALE);
    }

    /**
     * The card behind slot i, standing upright with its face pointing away
     * from the wearer, which is where anybody looking at the pet stands.
     * This orbit measures its angle the other way round from the rest of
     * the looks, so the turn that aims a face outward is a quarter turn
     * minus the angle rather than plus it.
     */
    private Matrix4f card(int slot, long steps, boolean back) {
        double a = angle(slot, steps);
        float radius = RADIUS + 0.07f;
        Quaternionf face = new Quaternionf().rotateY(rad(90) - (float) a);
        return AuraParts.plate((float) (Math.cos(a) * radius), height(slot, steps),
                (float) (Math.sin(a) * radius), face, CARD, CARD, back);
    }

    /** The pieces, as one aura look combined with whatever else is worn. */
    public static AuraConcept with(AuraConcept look, PetOrbit pets) {
        return look == null ? pets : new AuraConcepts.Combined(look, pets);
    }
}
