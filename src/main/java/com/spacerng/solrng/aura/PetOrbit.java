package com.spacerng.solrng.aura;

import org.bukkit.Color;
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
import static com.spacerng.solrng.aura.AuraParts.rad;

/**
 * The pet slots: up to three relics standing around the wearer.
 *
 * V333, Leon's call: they stand still. Until now they orbited the hips,
 * each tumbling twice per trip and riding a painted card in its rarity's
 * colour, and he wanted neither the spin nor the coloured square. A pet
 * now holds one spot, faces outward, and wears its rarity as a coloured
 * OUTLINE on the item itself, seen through walls. The outline is the one
 * thing a display can do that reads as "this item, in this colour"
 * rather than "this item, in front of a colour".
 *
 * Where the spots are is the look's business, not this class's. Every
 * aura fills a different band of space, so a fixed radius that is clear
 * of one is inside another; {@link AuraConcept#petStand()} lets a look
 * say where a pet can stand without being drawn through, and the looks
 * that fill the usual band override it.
 *
 * Standing still is also most of a frame budget: three pieces that never
 * move cost three spawns and nothing per tick, where the orbit sent nine
 * transformation updates every four ticks for as long as it was worn.
 */
public final class PetOrbit implements AuraConcept {

    private static final float SCALE = 0.36f;

    /** One worn pet: what it looks like, what colour it is and whether it is shiny. */
    public record Worn(Material icon, Color colour, boolean shiny) {
    }

    private final List<Worn> pets;
    private final PetStand stand;

    public PetOrbit(List<Worn> pets) {
        this(pets, PetStand.DEFAULT);
    }

    private PetOrbit(List<Worn> pets, PetStand stand) {
        this.pets = List.copyOf(pets);
        this.stand = stand;
    }

    /** The same pets, standing where this look says there is room. */
    public PetOrbit placedBy(AuraConcept look) {
        return look == null ? this : new PetOrbit(pets, look.petStand());
    }

    @Override
    public boolean clearOfView() {
        // They stand at hip height an arm's length out and never move, so
        // nothing of them crosses a first person view.
        return true;
    }

    @Override
    public List<Display> spawn(Player player, AuraParts parts) {
        List<Display> displays = new ArrayList<>();
        for (int i = 0; i < pets.size(); i++) {
            Worn pet = pets.get(i);
            Display relic = parts.item(player, pet.icon(), pose(i));
            // The rarity IS the outline now. A shiny one is brighter rather
            // than a different colour, because the colour is the rarity and
            // changing it would say the wrong thing.
            AuraParts.glowing(relic, pet.shiny() ? brighter(pet.colour()) : pet.colour());
            displays.add(relic);
        }
        return displays;
    }

    @Override
    public void tick(List<Display> displays, long frame) {
        // Nothing. They stand.
    }

    @Override
    public void stars(long frame, List<Vector> out) {
        for (int i = 0; i < pets.size(); i++) {
            double a = angle(i);
            out.add(new Vector(Math.cos(a) * stand.radius(), RIDE + height(), Math.sin(a) * stand.radius()));
        }
    }

    /** Where slot i stands, spread evenly whatever is worn. */
    private double angle(int slot) {
        int slots = Math.max(1, pets.size());
        return Math.toRadians(stand.firstAngle() + 360.0 / slots * slot);
    }

    private float height() {
        return FEET + stand.height();
    }

    /**
     * One spot, with the item turned to face away from the wearer, which
     * is where anybody looking at the pet is standing. A small tilt keeps
     * a flat item model from reading as a sticker.
     */
    private Transformation pose(int slot) {
        double a = angle(slot);
        Quaternionf facing = new Quaternionf().rotateY(rad(90) - (float) a).rotateX(rad(8));
        return at((float) (Math.cos(a) * stand.radius()), height(),
                (float) (Math.sin(a) * stand.radius()), facing, SCALE);
    }

    /** A shiny pet's outline, lifted toward white without losing its rarity. */
    private static Color brighter(Color colour) {
        return Color.fromRGB(
                Math.min(255, (int) Math.round(colour.getRed() * 0.45 + 255 * 0.55)),
                Math.min(255, (int) Math.round(colour.getGreen() * 0.45 + 255 * 0.55)),
                Math.min(255, (int) Math.round(colour.getBlue() * 0.45 + 255 * 0.55)));
    }

    /** The pieces, as one aura look combined with whatever else is worn. */
    public static AuraConcept with(AuraConcept look, PetOrbit pets) {
        return look == null ? pets : new AuraConcepts.Combined(look, pets.placedBy(look));
    }
}
