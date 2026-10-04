package com.spacerng.solrng.tab;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
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

    /**
     * Who is afk (V344). Never saved: being away does not survive a
     * reconnect, and a marker left on somebody who logged off and came
     * back would be a lie tab keeps telling.
     */
    private final java.util.Set<java.util.UUID> afk = new java.util.HashSet<>();

    /** Switches the marker and redraws their row. Returns the new state. */
    public boolean toggleAfk(org.bukkit.entity.Player player) {
        boolean now = !afk.contains(player.getUniqueId());
        if (now) {
            afk.add(player.getUniqueId());
        } else {
            afk.remove(player.getUniqueId());
        }
        plugin.getRankManager().refreshName(player);
        return now;
    }

    public boolean isAfk(java.util.UUID uuid) {
        return afk.contains(uuid);
    }

    /** Cheap enough to ask on every move: usually an empty set. */
    public boolean anyAfk() {
        return !afk.isEmpty();
    }

    /** Takes the marker off, and says so once when it was on. */
    public void clearAfk(org.bukkit.entity.Player player, boolean announce) {
        if (!afk.remove(player.getUniqueId())) return;
        plugin.getRankManager().refreshName(player);
        if (announce) {
            plugin.getServer().broadcastMessage(org.bukkit.ChatColor.DARK_GRAY + "* "
                    + org.bukkit.ChatColor.GRAY + player.getName() + " is back.");
        }
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

    /**
     * One block of lines, tokens filled and joined with newlines.
     *
     * A line with {@code ||} in it is two cells, drawn as two columns side
     * by side (V243, the layout Leon pointed at). The client centres every
     * line on its own, so a column only lines up with the one under it if
     * both rows are the same width: each cell is padded with spaces, evenly
     * on both sides, to the widest cell in its column within the block.
     */
    private Component block(Player player, List<String> lines) {
        if (lines.isEmpty()) return Component.empty();
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            // The config writes colours as &7, which the section serializer
            // below does not read, so every code showed as text (V243).
            String filled = fill(player, org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            String[] cells = filled.split(java.util.regex.Pattern.quote("||"), -1);
            for (int c = 0; c < cells.length; c++) cells[c] = cells[c].strip();
            rows.add(cells);
        }
        int columns = 0;
        for (String[] row : rows) columns = Math.max(columns, row.length);
        int[] widest = new int[columns];
        for (String[] row : rows) {
            if (row.length < 2) continue;
            for (int c = 0; c < row.length; c++) widest[c] = Math.max(widest[c], width(row[c]));
        }
        String gap = " ".repeat(Math.max(1, plugin.getConfig().getInt("tab.column-gap", 16)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) out.append('\n');
            String[] row = rows.get(i);
            if (row.length < 2) {
                out.append(row[0]);
                continue;
            }
            for (int c = 0; c < row.length; c++) {
                if (c > 0) out.append(org.bukkit.ChatColor.RESET).append(gap);
                int spaces = Math.max(0, Math.round((widest[c] - width(row[c])) / (float) SPACE));
                out.append(" ".repeat(spaces / 2)).append(row[c]).append(org.bukkit.ChatColor.RESET)
                        .append(" ".repeat(spaces - spaces / 2));
            }
        }
        return LEGACY.deserialize(out.toString());
    }

    /** Pixels a plain space takes in the default font, the padding unit. */
    private static final int SPACE = 4;

    /**
     * How wide a legacy coloured string draws in the default font, in
     * pixels with the gap after each glyph. Good to a pixel or two, which is
     * all the padding can do with four pixel spaces anyway. Small caps and
     * other letters past ASCII take six; symbols from the unicode font nine.
     */
    static int width(String text) {
        int total = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == org.bukkit.ChatColor.COLOR_CHAR && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'l') bold = true;
                else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'x') bold = false;
                continue;
            }
            int w;
            if (ch >= 0x2000) w = 9;
            else if (ch > 0x7E) w = 6;
            else w = switch (ch) {
                case ' ' -> 4;
                case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case 't', 'I', '[', ']' -> 4;
                case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
            total += w + (bold && ch != ' ' ? 1 : 0);
        }
        return total;
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
            int ping = player.getPing();
            // Coloured by how it feels: green is fine, red is lag.
            org.bukkit.ChatColor tone = ping < 80 ? org.bukkit.ChatColor.GREEN
                    : ping < 160 ? org.bukkit.ChatColor.YELLOW : org.bukkit.ChatColor.RED;
            out = out.replace("{ping}", tone.toString() + ping + "ᴍѕ");
        }
        return out;
    }
}
