package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Material;

import java.util.Map;

/**
 * One /armor tier — a full set (helmet/chest/legs/boots), a drop cost, and
 * a flat Luck bonus that only applies while all 4 pieces are worn at once.
 */
public class ArmorTier {

    private final String id; // e.g. "LEATHER" — also the Material prefix
    private final String display;
    private final Map<Rarity, Long> costs;
    private final double luckBonus;

    public ArmorTier(String id, String display, Map<Rarity, Long> costs, double luckBonus) {
        this.id = id;
        this.display = display;
        this.costs = costs;
        this.luckBonus = luckBonus;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public Map<Rarity, Long> getCosts() {
        return costs;
    }

    public double getLuckBonus() {
        return luckBonus;
    }

    // Gold armor is "GOLDEN_*" in the Material enum, not "GOLD_*" — every
    // other tier's id matches its Material prefix exactly.
    private String materialPrefix() {
        return id.equals("GOLD") ? "GOLDEN" : id;
    }

    public Material helmet() {
        return Material.valueOf(materialPrefix() + "_HELMET");
    }

    public Material chestplate() {
        return Material.valueOf(materialPrefix() + "_CHESTPLATE");
    }

    public Material leggings() {
        return Material.valueOf(materialPrefix() + "_LEGGINGS");
    }

    public Material boots() {
        return Material.valueOf(materialPrefix() + "_BOOTS");
    }
}
