package com.spacerng.solrng.pet;

/**
 * One pet a player actually owns, as opposed to the {@link PetType} that
 * describes the kind.
 *
 * A type is config and is the same for everybody. An instance is the
 * copy in your collection and carries the three things you spend on it:
 * rarity, tier and shiny. Two players holding the same Starheart can be
 * holding very different pets.
 *
 * Rarity and tier both start at 1, not 0. A freshly made pet is "Rarity
 * 1, Tier 1" rather than "Rarity 0", because a zero on a tooltip reads
 * as broken to the person who just paid for it.
 *
 * V324: hatching a pet you already own hands you a DUPLICATE, Leon's
 * call. It used to silently become a free rarity level, which meant a
 * hatch you already had never felt like a result. {@code copies} counts
 * them; everything a pet is worth still comes off the one instance, and
 * a pet you never want can be marked for autotrash in the pet index so
 * its copies are thrown away as they land.
 */
public record PetInstance(String typeId, int rarity, int tier, boolean shiny, int copies) {

    /** A brand new pet, straight out of the egg. */
    public static PetInstance fresh(String typeId) {
        return new PetInstance(typeId, 1, 1, false, 1);
    }

    public PetInstance withRarity(int newRarity) {
        return new PetInstance(typeId, Math.max(1, newRarity), tier, shiny, copies);
    }

    public PetInstance withTier(int newTier) {
        return new PetInstance(typeId, rarity, Math.max(1, newTier), shiny, copies);
    }

    public PetInstance asShiny() {
        return new PetInstance(typeId, rarity, tier, true, copies);
    }

    /** One more of the same pet. */
    public PetInstance plusCopy() {
        return new PetInstance(typeId, rarity, tier, shiny, copies + 1);
    }

    /** One fewer, never below the one copy that IS the pet. */
    public PetInstance minusCopy() {
        return new PetInstance(typeId, rarity, tier, shiny, Math.max(1, copies - 1));
    }

    /** Copies beyond the one you wear. */
    public int spare() {
        return Math.max(0, copies - 1);
    }

    /**
     * How this instance is written to the save file:
     * {@code typeId:rarity:tier:shiny:copies}.
     */
    public String serialise() {
        return typeId + ":" + rarity + ":" + tier + ":" + (shiny ? 1 : 0) + ":" + copies;
    }

    /**
     * Reads one back. A bare id with no colons is a pet from before
     * rarity and tier existed, and becomes a plain Rarity 1 Tier 1 copy
     * rather than being dropped; a four part line is one from before
     * duplicates and counts as a single copy. Anything unparsable
     * returns null so the loader can skip the line instead of taking the
     * whole file down.
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
            int copies = parts.length > 4 ? Math.max(1, Integer.parseInt(parts[4].trim())) : 1;
            return new PetInstance(id, rarity, tier, shiny, copies);
        } catch (NumberFormatException ex) {
            return fresh(id);
        }
    }
}
