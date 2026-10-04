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
 * @param firstAngle the middle of the fan the pets stand in, in degrees
 *                   around the wearer. 270 is straight behind them: the
 *                   pieces turn with the body since V336, so behind
 *                   stays behind however the wearer turns, which is what
 *                   Leon asked for ("i want the pets to be behind them
 *                   not in front of them").
 */
public record PetStand(float radius, float height, double firstAngle) {

    /** Straight behind the wearer, at hip height, an arm's length out. */
    public static final float BEHIND = 270.0f;

    /**
     * Hip height, an arm's length out, fanned out behind the wearer.
     * Outside the usual hip rings, which sit between 0.8 and 1.15, and
     * well under the halos, which sit above the head.
     */
    public static final PetStand DEFAULT = new PetStand(1.35f, 1.0f, BEHIND);

    /** For a look that fills the hips: the pets drop to the knees and tuck in. */
    public static final PetStand LOW = new PetStand(1.0f, 0.45f, BEHIND);

    /** For a look that lies on the ground: the pets rise to the chest. */
    public static final PetStand HIGH = new PetStand(1.15f, 1.35f, BEHIND);

    /** For a wall or a column of a look: the pets stand clear of it. */
    public static final PetStand WIDE = new PetStand(1.85f, 1.0f, BEHIND);
}
