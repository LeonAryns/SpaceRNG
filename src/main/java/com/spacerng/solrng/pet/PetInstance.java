package com.spacerng.solrng.pet;

import com.spacerng.solrng.stats.StatSources;

/**
 * One pet a player actually owns, as opposed to the {@link PetType} that
 * describes the kind.
 *
 * A type is config and is the same for everybody. An instance is the copy
 * in your collection and carries the three things that make it yours:
 * its LEVEL, whether it is shiny, and which STAT its multiplier is
 * pointed at. Two players holding the same Starheart can be holding very
 * different pets.
 *
 * V359, Leon's rework. A pet used to carry a rarity level and a tier,
 * two ladders bought with two currencies, one of which could fail. Both
 * are gone. There is one ladder now, level 1 to 10, each level worth 10%
 * more multiplier, paid in Cosmic Dust, which is what makes the Cosmic
 * Dust skills the thing that grows a pet. Level starts at 1 rather than
 * 0, because a zero on a tooltip reads as broken to the person who just
 * paid for it.
 *
 * {@code stat} is the stat the multiplier is pointed at and it can be
 * changed at any time in /pets, so a pet is a multiplier looking for a
 * job rather than a fixed one. Null means the type's own starting stat,
 * which is what a freshly hatched pet carries until it is pointed
 * somewhere else.
 *
 * Hatching a pet you already own hands you a DUPLICATE (V324). Copies
 * count them; everything a pet is worth still comes off the one
 * instance, and a pet you never want can be marked for autotrash so its
 * copies are thrown away as they land.
 */
public record PetInstance(String typeId, int level, boolean shiny, int copies, StatSources.Id stat) {

    /** A brand new pet, straight out of the egg, on its type's own stat. */
    public static PetInstance fresh(String typeId) {
        return new PetInstance(typeId, 1, false, 1, null);
    }

    public PetInstance withLevel(int newLevel) {
        return new PetInstance(typeId, Math.max(1, newLevel), shiny, copies, stat);
    }

    /** Points the multiplier at another stat. Null puts it back on the type's own. */
    public PetInstance withStat(StatSources.Id newStat) {
        return new PetInstance(typeId, level, shiny, copies, newStat);
    }

    public PetInstance asShiny() {
        return new PetInstance(typeId, level, true, copies, stat);
    }

    /** One more of the same pet. */
    public PetInstance plusCopy() {
        return new PetInstance(typeId, level, shiny, copies + 1, stat);
    }

    /** One fewer, never below the one copy that IS the pet. */
    public PetInstance minusCopy() {
        return new PetInstance(typeId, level, shiny, Math.max(1, copies - 1), stat);
    }

    /** Copies beyond the one you wear. */
    public int spare() {
        return Math.max(0, copies - 1);
    }

    /** Which stat this copy pays, falling back to the type's own. */
    public StatSources.Id statOr(PetType type) {
        if (stat != null) return stat;
        return type == null ? StatSources.Id.LUCK : type.stat();
    }

    /**
     * How this instance is written to the save file:
     * {@code typeId:level:shiny:copies:stat}.
     */
    public String serialise() {
        return typeId + ":" + level + ":" + (shiny ? 1 : 0) + ":" + copies
                + ":" + (stat == null ? "-" : stat.name());
    }

    /**
     * Reads one back.
     *
     * Everything before V359 is a different shape (rarity, tier, shiny,
     * copies) and is not converted, because the rework wipes the pets
     * that were owned under it: anything unreadable comes back as a plain
     * level 1 copy rather than taking the whole save file down.
     */
    public static PetInstance parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        String id = parts[0].trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty()) return null;
        if (parts.length < 4) return fresh(id);
        try {
            int level = Math.max(1, Integer.parseInt(parts[1].trim()));
            boolean shiny = "1".equals(parts[2].trim());
            int copies = Math.max(1, Integer.parseInt(parts[3].trim()));
            StatSources.Id stat = null;
            if (parts.length > 4 && !"-".equals(parts[4].trim())) {
                try {
                    stat = StatSources.Id.valueOf(parts[4].trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    stat = null;
                }
            }
            return new PetInstance(id, level, shiny, copies, stat);
        } catch (NumberFormatException ex) {
            return fresh(id);
        }
    }
}
