package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The chat line (V173): [prestige] [tag] name -> message.
 *
 * The prestige only shows once there is one, the tag is the equipped
 * drop in its own colours, and the name wears the rank's colours (a
 * gradient, or the rainbow for a rank with rgb-name) without the rank
 * symbol.
 *
 * V186 moved it off AsyncPlayerChatEvent onto Paper's own AsyncChatEvent
 * with a renderer, because none of the above was reaching the screen.
 * Paper has two chat paths and picks one: the modern event, which is what
 * a 1.21 client's signed chat actually travels on, and a legacy path it
 * only walks when AsyncPlayerChatEvent is the one thing listening. This
 * plugin registered both, ChatTagsListener on the modern event and this
 * one on the legacy event, so the modern path won and setFormat here was
 * never read by anybody.
 *
 * A renderer is also the only thing that composes properly with the tag
 * replacement: it runs after every listener has had the message, so
 * [luck] and friends are already expanded by the time the prefix is put
 * in front of them.
 */
public class ChatListener implements Listener {

    private static final String[] ROMAN = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final SolRNGPlugin plugin;

    public ChatListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Last, so a chat format plugin that set its own renderer earlier is
     * replaced rather than added to. EssentialsChat used to win and drop
     * the tag.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        if (data == null) return;
        // The line reads the same for everybody, so it is built once per
        // message instead of once per person in range. The one exception is
        // a Bedrock reader (V223), who cannot hover, so they get a second
        // copy, also built once, with the hover texts written out.
        // V294: hovering the name shows the sender's stats and a click opens
        // their /stats. Bedrock cannot hover, so its copy goes without, or
        // showHovers would write the whole card into the chat line.
        net.kyori.adventure.text.Component[] built = new net.kyori.adventure.text.Component[2];
        event.renderer((source, displayName, message, viewer) -> {
            if (viewer instanceof Player reader && com.spacerng.solrng.platform.Bedrock.is(reader)) {
                if (built[1] == null) built[1] = com.spacerng.solrng.platform.BedrockText.showHovers(
                        LEGACY.deserialize(prefix(source)).append(message));
                return built[1];
            }
            if (built[0] == null) built[0] = LEGACY.deserialize(prefix(source))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(statsCard(source)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/stats " + source.getName()))
                    .append(message);
            return built[0];
        });
    }

    /** "[IV] [Solar Flare] Leon -> ", in legacy codes. */
    private String prefix(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        StringBuilder line = new StringBuilder();
        line.append(com.spacerng.solrng.rank.RankManager.prestigePrefix(data.getPrestige()));
        String tag = tag(data);
        if (!tag.isEmpty()) {
            line.append(ChatColor.DARK_GRAY).append("[").append(tag)
                    .append(ChatColor.RESET).append(ChatColor.DARK_GRAY).append("] ");
        }
        // The rank badge, [C] for Comet and so on, in front of the name
        // exactly as it reads in tab.
        line.append(plugin.getRankManager().badgeOf(player));
        line.append(plugin.getRankManager().coloredName(player));
        // The cosmetic title goes after the name, not in front of it.
        line.append(plugin.getCosmeticManager().suffixOf(data));
        line.append(ChatColor.DARK_GRAY).append(" \u2192 ").append(ChatColor.WHITE);
        return line.toString();
    }

    /** The hover over a name in chat (V294): who they are and their stats. */
    private net.kyori.adventure.text.Component statsCard(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        StringBuilder card = new StringBuilder();
        card.append(plugin.getRankManager().coloredName(player)).append("\n");
        card.append(ChatColor.DARK_GRAY).append("Level ").append(ChatColor.WHITE).append(data.getLevel());
        if (data.getPrestige() > 0) {
            card.append(ChatColor.DARK_GRAY).append("  Prestige ").append(ChatColor.GOLD).append(roman(data.getPrestige()));
        }
        card.append("\n").append(ChatColor.DARK_GRAY).append("Rolls ").append(ChatColor.WHITE)
                .append(String.format("%,d", data.getTotalRolls())).append("\n");
        try {
            for (com.spacerng.solrng.stats.StatSources.Id id : com.spacerng.solrng.stats.StatSources.Id.values()) {
                var stat = com.spacerng.solrng.stats.StatSources.of(plugin, data, id);
                card.append("\n").append(com.spacerng.solrng.gui.StatsGui.accent(id))
                        .append(com.spacerng.solrng.gui.Lore.BULLET).append(" ").append(ChatColor.GRAY)
                        .append(stat.name()).append(": ").append(ChatColor.WHITE)
                        .append(com.spacerng.solrng.gui.StatsGui.shownValue(stat));
            }
        } catch (RuntimeException ignored) {
            // Read off the main thread; a stat that trips over a change in
            // flight just leaves the card shorter this once.
        }
        card.append("\n\n").append(ChatColor.YELLOW).append("Click for their /stats");
        return LEGACY.deserialize(card.toString());
    }

    /** The equipped tag as the drop's own coloured name, or empty. */
    private String tag(PlayerData data) {
        String key = data.getEquippedTagItemKey();
        if (key == null || data.getEquippedTagRarity() == null) return "";
        RollableItem item = plugin.getRarityManager().findByDisplayName(key);
        return item == null ? ChatColor.WHITE + key : RollFormat.displayName(plugin, item);
    }

    private static String roman(int n) {
        return n <= ROMAN.length ? ROMAN[n - 1] : String.valueOf(n);
    }
}
