package com.spacerng.solrng.announce;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotating server announcements.
 *
 * Kept in the plugin rather than handed to a generic announcer because
 * these lines are the one place a new player is told that /novacore,
 * /farmtree and /convert exist — they need to stay in step with the
 * features as they change, and they can use the plugin's own colours and
 * placeholders without a bridge.
 *
 * It rotates in order rather than picking at random: random repeats
 * itself and leaves other tips unseen for ages, which is exactly wrong
 * for something meant to teach.
 */
public class AnnouncerManager {

    private final SolRNGPlugin plugin;
    private final List<List<String>> messages = new ArrayList<>();

    private boolean enabled = true;
    private int intervalTicks = 20 * 300;
    private String header = "";
    private int next = 0;

    public AnnouncerManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        messages.clear();
        next = 0;

        enabled = config.getBoolean("announcements.enabled", true);
        intervalTicks = Math.max(20, config.getInt("announcements.interval-seconds", 300) * 20);
        header = colour(config.getString("announcements.header", ""));

        List<?> raw = config.getList("announcements.messages");
        if (raw != null) {
            for (Object entry : raw) {
                List<String> block = new ArrayList<>();
                if (entry instanceof List<?> lines) {
                    for (Object line : lines) block.add(colour(String.valueOf(line)));
                } else {
                    block.add(colour(String.valueOf(entry)));
                }
                if (!block.isEmpty()) messages.add(block);
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded " + messages.size() + " announcement blocks.");
    }

    private String colour(String raw) {
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    public boolean isEnabled() {
        return enabled && !messages.isEmpty();
    }

    /** Sends the next block, then moves the pointer on. */
    public void broadcastNext() {
        if (!isEnabled()) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return; // nothing to say it to

        List<String> block = messages.get(next % messages.size());
        next = (next + 1) % messages.size();
        send(block);
    }

    /** Sends a specific block by index — used by /rngadmin announce. */
    public boolean broadcast(int index) {
        if (index < 0 || index >= messages.size()) return false;
        send(messages.get(index));
        return true;
    }

    public int size() {
        return messages.size();
    }

    private void send(List<String> block) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            if (!header.isEmpty()) {
                player.sendMessage(header);
            }
            for (String line : block) {
                player.sendMessage(line);
            }
            player.sendMessage("");
        }
    }
}
