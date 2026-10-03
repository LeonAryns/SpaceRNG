package com.spacerng.solrng.pet;

import org.bukkit.Material;

import java.util.List;

/**
 * One tier of egg on the pets screen.
 *
 * Every egg draws from the same pets. What a dearer egg buys is odds: the
 * weight of every pet at or above {@code pets.eggs.boosted-from} is
 * multiplied by the egg's boost, so a boost of 20 makes those pets twenty
 * times as heavy against the rest. The chances printed on the egg are
 * worked out from the same weights, never written down twice.
 */
public record PetEgg(String id, String display, List<String> colors, Material icon,
                     long cost, double boost, int minPrestige,
                     com.spacerng.solrng.rarity.Rarity minRarity, com.spacerng.solrng.rarity.Rarity maxRarity,
                     double divineChance) {

    /**
     * V313: Divine is a flat chance per egg, not a weight in the pool.
     *
     * Leon asked for the first egg to hatch anything but a Divine, the
     * second to carry a very small Divine chance and the third ten times
     * that. Expressed as weights that is not reachable: the egg's boost
     * multiplies every tier from boosted-from up, Divine included, so
     * raising the boost to make Epics likelier drags the Divine chance
     * along and "ten times" stops being ten times. A chance of its own
     * is read before the pool, so the number in config IS the number,
     * and the third egg is exactly ten times the second whatever else
     * is tuned.
     *
     * The ordinary band stops at Mythical on every egg for the same
     * reason: one route to a Divine, not two that have to agree.
     */
    public boolean hatchesDivine() {
        return divineChance > 0.0;
    }

    /**
     * V284: each egg hatches its own band of pets, Leon's call: the first
     * egg nothing above Rare, the dearer ones nothing below their floor.
     * The default band by egg id, for a live config without the keys.
     */
    static com.spacerng.solrng.rarity.Rarity[] defaultBand(String id) {
        // V313: the ceilings are Mythical on every egg, because Divine is
        // its own chance now (see divineChance above) and two routes to
        // the same pet would have to agree about the odds. The floors are
        // V284's and stay his: a dearer egg does not hand out Commons.
        // Egg one opened up to the whole band below Divine, Leon's call:
        // "egg 1 alles maar geen divine".
        return switch (id) {
            case "nebula" -> new com.spacerng.solrng.rarity.Rarity[]{
                    com.spacerng.solrng.rarity.Rarity.UNCOMMON, com.spacerng.solrng.rarity.Rarity.MYTHICAL};
            case "supernova" -> new com.spacerng.solrng.rarity.Rarity[]{
                    com.spacerng.solrng.rarity.Rarity.RARE, com.spacerng.solrng.rarity.Rarity.MYTHICAL};
            default -> new com.spacerng.solrng.rarity.Rarity[]{
                    com.spacerng.solrng.rarity.Rarity.COMMON, com.spacerng.solrng.rarity.Rarity.MYTHICAL};
        };
    }

    public boolean hatches(com.spacerng.solrng.rarity.Rarity rarity) {
        return rarity.ordinal() >= minRarity.ordinal() && rarity.ordinal() <= maxRarity.ordinal();
    }


    public String[] stops() {
        return colors.toArray(new String[0]);
    }
}
