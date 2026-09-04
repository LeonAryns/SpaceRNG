package com.spacerng.solrng.player;

/**
 * One armor slot. Pieces are bought individually, so a tier is no longer
 * a single purchase — ownership is tracked per (tier, piece) pair.
 */
public enum ArmorPiece {
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS;

    /** "Chestplate" rather than "CHESTPLATE". */
    public String displayName() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    /** The key ownership is stored under, e.g. "LEATHER:BOOTS". */
    public static String key(String tierId, ArmorPiece piece) {
        return tierId + ":" + piece.name();
    }
}
