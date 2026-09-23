package com.spacerng.solrng.rank;

import java.util.List;

/**
 * One rank, from the ranks section of config. The order in config is the
 * order of the ladder, so a rank further down outranks the ones above it.
 *
 * Linked is the free one a Discord link grants; the rest are bought with
 * Credits in /ranks or handed out from the store with rngadmin rank set.
 */
public record RankTier(String id, String display, List<String> colors, String tag, String letter,
                       String icon, long price,
                       double multiplier, String keyallCrate, int keyallAmount, int vaultPages,
                       boolean fly, boolean nick, boolean size, boolean rgbName) {

    /** Whether this rank can start a key all at all. */
    public boolean hasKeyall() {
        return !keyallCrate.isEmpty() && keyallAmount > 0;
    }
}
