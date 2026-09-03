package com.spacerng.solrng.placeholder;

import com.spacerng.solrng.SolRNGPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Exposes %solrng_tag% so other plugins — chiefly TAB, which overrides the
 * vanilla tab list and ignores our scoreboard team prefix — can render the
 * equipped tag themselves. TAB needs %solrng_tag% added to its own tablist
 * format config; this plugin can't reach into TAB's config to do that part.
 */
public class SolRNGExpansion extends PlaceholderExpansion {

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

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (!(offlinePlayer instanceof Player player) || !player.isOnline()) return "";

        if (params.equalsIgnoreCase("tag")) {
            return plugin.getTagManager().getPrefix(player);
        }
        return null;
    }
}
