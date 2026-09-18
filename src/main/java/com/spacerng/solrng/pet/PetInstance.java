package com.spacerng.solrng.pet;

/**
 * One pet a player actually owns, as opposed to the {@link PetType} that
 * describes the kind.
 *
 * A type is config and is the same for everybody. An instance is the copy
 * in your collection and carries the three things you spend on it:
 * rarity, tier and shiny. Two players holding the same Starheart can be
 * holding very different pets.
 *
 * Rarity and tier both start at 1, not 0. A freshly made pet is "Rarity
 * 1, Tier 1" rather than "Rarity 0", because a zero on a tooltip reads as
 * broken to the person who just paid ten Cosmic Dust for it.
 *
 * One instance per type per player. Making a pet you already own raises
 * its rarity instead of handing you a second copy, so the collection
 * never fills up with duplicates you cannot tell apart in the menu.
 */
public record PetInstance(String typeId, int rarity, int tier, boolean shiny) {

    /** A brand new pet, straight out of the forge. */
    public static PetInstance fresh(String typeId) {
        return new PetInstance(typeId, 1, 1, false);
    }

    public PetInstance withRarity(int newRarity) {
        return new PetInstance(typeId, Math.max(1, newRarity), tier, shiny);
    }

    public PetInstance withTier(int newTier) {
        return new PetInstance(typeId, rarity, Math.max(1, newTier), shiny);
    }

    public PetInstance asShiny() {
        return new PetInstance(typeId, rarity, tier, true);
    }

    /**
     * How this instance is written to the save file:
     * {@code typeId:rarity:tier:shiny}.
     */
    public String serialise() {
        return typeId + ":" + rarity + ":" + tier + ":" + (shiny ? 1 : 0);
    }

    /**
     * Reads one back. A bare id with no colons is a pet from before
     * rarity and tier existed, and becomes a plain Rarity 1 Tier 1 copy
     * rather than being dropped. Anything unparsable returns null so the
     * loader can skip the line instead of taking the whole file down.
     */
    public static PetInstance parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        String id = parts[0].trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty()) return null;
        if (parts.length < 3) return fresh(id);
        try {
            int rarity = Math.max(1, Integer.parseInt(parts[1].trim()));
            int tier = Math.max(1, Integer.parseInt(parts[2].trim()));
            boolean shiny = parts.length > 3 && "1".equals(parts[3].trim());
            return new PetInstance(id, rarity, tier, shiny);
        } catch (NumberFormatException ex) {
            return fresh(id);
        }
    }
}
