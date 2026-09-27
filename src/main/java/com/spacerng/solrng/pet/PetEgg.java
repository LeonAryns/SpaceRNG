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
                     long cost, double boost, int minPrestige) {

    public String[] stops() {
        return colors.toArray(new String[0]);
    }
}
