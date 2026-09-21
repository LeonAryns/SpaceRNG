package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * The chat line (V173): [prestige] [tag] name → message.
 *
 * The prestige only shows once there is one, the tag is the equipped
 * drop in its own colours, and the name wears the rank's colours (a
 * gradient, or the rainbow for a rank with rgb-name) without the rank
 * symbol. It runs last so EssentialsChat's own format, which used to
 * win and drop the tag, is replaced rather than added to.
 */
public class ChatListener implements Listener {

    private static final String[] ROMAN = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private final SolRNGPlugin plugin;

    public ChatListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null) return;

        StringBuilder line = new StringBuilder();
        if (data.getPrestige() > 0) {
            line.append(ChatColor.DARK_GRAY).append("[").append(ChatColor.GOLD)
                    .append(roman(data.getPrestige())).append(ChatColor.DARK_GRAY).append("] ");
        }
        String tag = tag(data);
        if (!tag.isEmpty()) {
            line.append(ChatColor.DARK_GRAY).append("[").append(tag)
                    .append(ChatColor.RESET).append(ChatColor.DARK_GRAY).append("] ");
        }
        line.append(plugin.getRankManager().coloredName(player));
        line.append(ChatColor.DARK_GRAY).append(" → ").append(ChatColor.WHITE);

        // The format is a printf string: every % in the parts has to be
        // doubled, then the message goes in as %2$s.
        event.setFormat(line.toString().replace("%", "%%") + "%2$s");
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
