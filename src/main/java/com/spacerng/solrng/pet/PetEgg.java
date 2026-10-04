package com.spacerng.solrng.pet;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One tier of egg on the pets screen.
 *
 * Every egg draws from the same forty-two pets. What a dearer egg buys
 * is the chance of each RARITY, written down one line each in config
 * (V324). Whatever is left over after those chances is the egg's floor
 * rarity, so the numbers always add up to one egg.
 *
 * Until V324 an egg carried a single {@code boost} that multiplied the
 * weight of every pet from Epic up. That could not move Epic, Legendary
 * and Mythical against each other, only all three against Common and
 * Uncommon together, so the Nebula and the Supernova both landed about a
 * quarter of their hatches on a Mythical however the boosts were tuned,
 * and the two eggs felt the same. Leon reported exactly that. A chance
 * per rarity cannot come apart that way, and the tooltip prints the
 * config numbers rather than a calculation of them.
 */
public record PetEgg(String id, String display, List<String> colors, Material icon,
                     long cost, int minPrestige, Rarity floor, Map<Rarity, Double> chances) {

    /** The chance this egg hatches a given rarity, floor included. */
    public double chanceOf(Rarity rarity) {
        if (rarity == floor) return leftover();
        Double value = chances.get(rarity);
        return value == null ? 0.0 : Math.max(0.0, value);
    }

    /** What is left for the floor rarity once every written chance is taken. */
    public double leftover() {
        double used = 0.0;
        for (Map.Entry<Rarity, Double> entry : chances.entrySet()) {
            if (entry.getKey() == floor) continue;
            used += Math.max(0.0, entry.getValue());
        }
        return Math.max(0.0, 1.0 - used);
    }

    /** Every rarity this egg can hatch, rarest first, with its chance. */
    public Map<Rarity, Double> ladder() {
        Map<Rarity, Double> out = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            double chance = chanceOf(rarity);
            if (chance > 0.0) out.put(rarity, chance);
        }
        return out;
    }

    public String[] stops() {
        return colors.toArray(new String[0]);
    }
}
