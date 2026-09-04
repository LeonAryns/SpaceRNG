package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Material;

import java.util.Map;

/**
 * One /armor tier. The cost is the price of a SINGLE piece — pieces are
 * bought one at a time — and the Luck/Speed bonuses are what each worn
 * piece grants on its own.
 */
public class ArmorTier {

    private final String id; // e.g. "LEATHER" — also the Material prefix
    private final String display;
    private final Map<Rarity, Long> costs;
    private final double luckBonus;
    private final double speedBonus;

    public ArmorTier(String id, String display, Map<Rarity, Long> costs, double luckBonus, double speedBonus) {
        this.id = id;
        this.display = display;
        this.costs = costs;
        this.luckBonus = luckBonus;
        this.speedBonus = speedBonus;
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

    public double getSpeedBonus() {
        return speedBonus;
    }

    // Gold armor is "GOLDEN_*" in the Material enum, not "GOLD_*" — every
    // other tier's id matches its Material prefix exactly.
    private String materialPrefix() {
        return id.equals("GOLD") ? "GOLDEN" : id;
    }

    /** The Material for one piece of this tier, e.g. GOLDEN_BOOTS. */
    public Material materialFor(ArmorPiece piece) {
        return Material.valueOf(materialPrefix() + "_" + piece.name());
    }

    /** e.g. "Leather Boots" — the name the physical item carries. */
    public String pieceDisplay(ArmorPiece piece) {
        return display + " " + piece.displayName();
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
