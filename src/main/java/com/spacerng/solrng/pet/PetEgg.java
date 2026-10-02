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
                     com.spacerng.solrng.rarity.Rarity minRarity, com.spacerng.solrng.rarity.Rarity maxRarity) {

    /**
     * V284: each egg hatches its own band of pets, Leon's call: the first
     * egg nothing above Rare, the dearer ones nothing below their floor.
     * The default band by egg id, for a live config without the keys.
     */
    static com.spacerng.solrng.rarity.Rarity[] defaultBand(String id) {
        return switch (id) {
            case "stardust" -> new com.spacerng.solrng.rarity.Rarity[]{
                    com.spacerng.solrng.rarity.Rarity.COMMON, com.spacerng.solrng.rarity.Rarity.RARE};
            case "nebula" -> new com.spacerng.solrng.rarity.Rarity[]{
                    com.spacerng.solrng.rarity.Rarity.UNCOMMON, com.spacerng.solrng.rarity.Rarity.LEGENDARY};
            case "supernova" -> new com.spacerng.solrng.rarity.Rarity[]{
                    com.spacerng.solrng.rarity.Rarity.RARE, com.spacerng.solrng.rarity.Rarity.DIVINE};
            default -> new com.spacerng.solrng.rarity.Rarity[]{
                    com.spacerng.solrng.rarity.Rarity.COMMON, com.spacerng.solrng.rarity.Rarity.DIVINE};
        };
    }

    public boolean hatches(com.spacerng.solrng.rarity.Rarity rarity) {
        return rarity.ordinal() >= minRarity.ordinal() && rarity.ordinal() <= maxRarity.ordinal();
    }


    public String[] stops() {
        return colors.toArray(new String[0]);
    }
}
