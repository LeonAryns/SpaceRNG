package com.spacerng.solrng.crate;

import org.bukkit.Material;

/**
 * One line of a crate's loot table.
 *
 * `target` is whatever the type needs to know beyond an amount: the
 * consumable id for CONSUMABLE, the rarity name for DROP, and nothing for
 * the four currencies. `icon` and `name` are optional overrides; left
 * empty, the crate works both out from the type.
 */
public record CrateReward(Type type, String target, long amount, double weight,
                          Material icon, String name) {

    public enum Type {
        COINS, GEMS, MONEY, CREDITS, CONSUMABLE, DROP
    }
}
