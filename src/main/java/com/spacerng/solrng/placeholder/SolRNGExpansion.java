package com.spacerng.solrng.placeholder;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Exposes %solrng_tag% (equipped item tag) and %solrng_level% (Level /
 * Prestige badge) for other plugins — chiefly TAB, which overrides the
 * vanilla tab list — to render in their own tablist format. Neither is
 * automatically shown in chat/nametags by this plugin's own tag team
 * prefix (that's %solrng_tag% only); %solrng_level% is tab-list-only by
 * design, so TAB should only ever be pointed at it from tabprefix/suffix,
 * never from a chat or join-message format.
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
        if (params.equalsIgnoreCase("level")) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            return plugin.getTagManager().levelBadge(data);
        }
        return null;
    }
}
