package com.spacerng.solrng.placeholder;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.RollableItem;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Everything this plugin knows about a player, exposed to any plugin that
 * speaks PlaceholderAPI — in practice TAB, which takes over the tab list
 * and nametags entirely and can only see other plugins' data through
 * placeholders.
 *
 * Two flavours of the tag are offered on purpose:
 *   %solrng_tag%       - exactly what the nametag shows: the item's own
 *                        gradient, the obfuscated flair on Epic+, and a
 *                        trailing space + reset so a name can follow it.
 *   %solrng_tag_plain% - the same gradient with NO flair and no trailing
 *                        padding. The flair is an obfuscated (&k)
 *                        character, which in a tab list re-scrambles every
 *                        client tick and reads as noise, so this is the
 *                        one to use there.
 *
 * Placeholders never return null for a known key — an unset value comes
 * back as an empty string, so a TAB format never renders the raw
 * "%solrng_x%" text at a player who hasn't got one yet.
 */
public class SolRNGExpansion extends PlaceholderExpansion {

    private static final String[] ROMAN_NUMERALS = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private final SolRNGPlugin plugin;

    public SolRNGExpansion(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "solrng";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LaLaLeon";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Keeps the expansion registered across a PlaceholderAPI reload —
     * without this, /papi reload silently unregisters it and every
     * %solrng_% placeholder in TAB starts rendering as literal text.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (!(offlinePlayer instanceof Player player) || !player.isOnline()) return "";

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        switch (params.toLowerCase()) {
            // --- tag ---
            case "tag":
                return plugin.getTagManager().getPrefix(player);
            case "tag_plain":
                return styledTag(data, false);
            case "tag_name":
                return data.getEquippedTagItemKey() == null ? "" : data.getEquippedTagItemKey();
            case "tag_odds": {
                RollableItem item = equippedItem(data);
                return item == null ? "" : String.format("%,d", item.getOdds());
            }
            case "tag_multiplier":
                return String.format("%.2f", plugin.getRarityManager().tagMultiplierFor(data));

            // --- prestige ---
            case "prestige":
                return String.valueOf(data.getPrestige());
            case "prestige_roman":
                return roman(data.getPrestige());
            case "prestige_badge":
                // Blank at prestige 0 so a TAB format doesn't show an
                // empty bracket for every new player.
                return data.getPrestige() <= 0 ? ""
                        : ChatColor.GOLD + "\u2605 " + ChatColor.AQUA + roman(data.getPrestige());

            // --- level ---
            case "level":
                return plugin.getTagManager().levelBadge(data);
            case "level_number":
                return String.valueOf(data.getLevel());

            default:
                return null; // unknown key — let PAPI report it as unrecognised
        }
    }

    private RollableItem equippedItem(PlayerData data) {
        String equipped = data.getEquippedTagItemKey();
        return equipped == null ? null : plugin.getRarityManager().byName(equipped);
    }

    /** The equipped item's name in its own colors, flair optional. */
    private String styledTag(PlayerData data, boolean withFlair) {
        RollableItem item = equippedItem(data);
        if (item == null) {
            return data.getEquippedTagItemKey() == null ? "" : data.getEquippedTagItemKey();
        }
        return plugin.getRarityManager().styleItemName(item, withFlair);
    }

    private static String roman(int value) {
        if (value <= 0) return "0";
        return value <= ROMAN_NUMERALS.length ? ROMAN_NUMERALS[value - 1] : String.valueOf(value);
    }
}
