package com.spacerng.solrng.tab;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * The header and footer of the tab list (V186).
 *
 * The names in tab already wear their rank's colours, which RankManager
 * does when a rank changes. Everything above and below them was empty,
 * which is most of why Leon kept saying the tab list looked unfinished.
 *
 * Both blocks are config, {@code tab.header} and {@code tab.footer}, so
 * the wording is his. Tokens are filled per player, because a footer that
 * says nothing about the person reading it is a waste of the only screen
 * every player opens by reflex.
 *
 * Switched off with {@code tab.enabled} for a server running the TAB
 * plugin, which owns the same two blocks and would simply overwrite these
 * every second.
 */
public final class TabListManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final SolRNGPlugin plugin;
    private BukkitTask task;

    public TabListManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) return;
        long period = Math.max(20L, plugin.getConfig().getLong("tab.refresh-ticks", 40L));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        List<String> header = plugin.getConfig().getStringList("tab.header");
        List<String> footer = plugin.getConfig().getStringList("tab.footer");
        if (header.isEmpty() && footer.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.sendPlayerListHeaderAndFooter(block(player, header), block(player, footer));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Tab list failed for " + player.getName() + ": " + ex);
            }
        }
    }

    /** One block of lines, tokens filled and joined with newlines. */
    private Component block(Player player, List<String> lines) {
        if (lines.isEmpty()) return Component.empty();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(fill(player, lines.get(i)));
        }
        return LEGACY.deserialize(out.toString());
    }

    /**
     * The tokens. Kept to the handful worth a glance: who you are, what
     * you are worth, and how busy the server is. Anything more and the
     * block stops being readable at a glance, which is the only thing a
     * tab list is for.
     */
    private String fill(Player player, String line) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        String out = line;
        if (out.contains("{title}")) {
            out = out.replace("{title}", Lore.banner(plugin.getConfig()
                    .getString("tab.title", "SPACERNG")));
        }
        if (out.contains("{name}")) {
            out = out.replace("{name}", plugin.getRankManager().coloredName(player));
        }
        if (out.contains("{rank}")) {
            out = out.replace("{rank}", plugin.getRankManager()
                    .styled(plugin.getRankManager().rankOf(data)));
        }
        if (out.contains("{luck}")) {
            out = out.replace("{luck}", Lore.shorten(Math.round(
                    plugin.getPrestigeManager().effectiveLuck(data) * 100.0)) + "%");
        }
        if (out.contains("{rolls}")) {
            out = out.replace("{rolls}", Lore.shorten(data.getTotalRolls()));
        }
        if (out.contains("{prestige}")) {
            out = out.replace("{prestige}", String.valueOf(data.getPrestige()));
        }
        if (out.contains("{online}")) {
            out = out.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        }
        if (out.contains("{max}")) {
            out = out.replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
        }
        if (out.contains("{ping}")) {
            out = out.replace("{ping}", player.getPing() + "ms");
        }
        return out;
    }
}
