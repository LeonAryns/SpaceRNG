package com.spacerng.solrng.perk;

import com.spacerng.solrng.rarity.Rarity;

import java.util.UUID;

/**
 * One perk in a player's collection.
 *
 * A stable UUID rather than a {@code List} index so equipping and moving
 * survive vault reordering, and so a save file can point at "this perk"
 * unambiguously even when the vault is rewritten.
 */
public record PerkInstance(UUID id, PerkType type, Rarity tier, int level) {

    public PerkInstance {
        if (level < 1) level = 1;
        if (level > 5) level = 5;
    }

    /** Fresh id every time - used when a roll produces a new perk. */
    public static PerkInstance freshly(PerkType type, Rarity tier, int level) {
        return new PerkInstance(UUID.randomUUID(), type, tier, level);
    }

    /** "Legendary Money Perk" - the display name of the item. */
    public String display() {
        return tier.displayName() + " " + type.label();
    }

    /** Roman I..V for the level. */
    public String roman() {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    /** Compact key for save files. */
    public String encode() {
        return id + "|" + type.name() + "|" + tier.name() + "|" + level;
    }

    /** Reads a perk out of a save file. Returns null on bad input. */
    public static PerkInstance decode(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split("\\|");
        if (parts.length != 4) return null;
        try {
            return new PerkInstance(UUID.fromString(parts[0]),
                    PerkType.valueOf(parts[1]),
                    Rarity.valueOf(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
