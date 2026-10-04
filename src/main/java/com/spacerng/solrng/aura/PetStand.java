package com.spacerng.solrng.aura;

/**
 * Where worn pets stand while a look is on (V333).
 *
 * Every aura fills a different band of space, so there is no one spot
 * that is clear of all of them: a radius that stands outside a hip ring
 * is inside a barrier wall, and a height that clears a ground look is
 * through the middle of a halo. A look answers for its own shape with
 * {@link AuraConcept#petStand()}, and the pets are placed from that.
 *
 * @param radius     blocks out from the wearer
 * @param height     blocks above the feet
 * @param firstAngle where the first pet stands, in degrees; the rest are
 *                   spread evenly from it. 180 is straight behind the
 *                   wearer's spawn facing, which is where a pet is least
 *                   in the way of their own screen.
 */
public record PetStand(float radius, float height, double firstAngle) {

    /**
     * Hip height, an arm's length out, first pet behind the left
     * shoulder. Outside the usual hip rings, which sit between 0.8 and
     * 1.15, and well under the halos, which sit above the head.
     */
    public static final PetStand DEFAULT = new PetStand(1.35f, 1.0f, 200.0);

    /** For a look that fills the hips: the pets drop to the knees and tuck in. */
    public static final PetStand LOW = new PetStand(1.0f, 0.45f, 200.0);

    /** For a look that lies on the ground: the pets rise to the chest. */
    public static final PetStand HIGH = new PetStand(1.15f, 1.35f, 200.0);

    /** For a wall or a column of a look: the pets stand clear of it. */
    public static final PetStand WIDE = new PetStand(1.85f, 1.0f, 200.0);
}
