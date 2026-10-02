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
 * /farmtree and /convert exist - they need to stay in step with the
 * features as they change, and they can use the plugin's own colours and
 * placeholders without a bridge.
 *
 * It rotates in order rather than picking at random: random repeats
 * itself and leaves other tips unseen for ages, which is exactly wrong
 * for something meant to teach.
 */
public class AnnouncerManager {

    private static final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer LEGACY =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
    // A web address in a tip (discord.gg/spacerng, https://...) opens on click.
    private static final java.util.regex.Pattern URL =
            java.util.regex.Pattern.compile("(https?://)?[\\w-]+(\\.[\\w-]+)*\\.[a-z]{2,}(/[^\\s]*)?");
    private static final net.kyori.adventure.text.TextReplacementConfig LINKS =
            net.kyori.adventure.text.TextReplacementConfig.builder()
                    .match(URL)
                    .replacement((match, text) -> {
                        String url = match.group().startsWith("http") ? match.group() : "https://" + match.group();
                        return text.clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                        net.kyori.adventure.text.Component.text("Open " + url,
                                                net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                    })
                    .build();

    private final SolRNGPlugin plugin;
    private final List<List<String>> messages = new ArrayList<>();

    private boolean enabled = true;
    private int intervalTicks = 20 * 300;
    private String header = "";
    private String footer = "";
    private int next = 0;

    public AnnouncerManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        messages.clear();
        next = 0;

        enabled = config.getBoolean("announcements.enabled", true);
        intervalTicks = Math.max(20, config.getInt("announcements.interval-seconds", 300) * 20);
        discordEvery = Math.max(0, config.getInt("announcements.discord-every", 2));
        feedback.clear();
        List<String> feedbackLines = config.getStringList("announcements.feedback");
        if (feedbackLines.isEmpty()) feedbackLines = DEFAULT_FEEDBACK;
        for (String line : feedbackLines) feedback.add(colour(line));
        header = colour(config.getString("announcements.header", ""));
        footer = colour(config.getString("announcements.footer", ""));

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
        plugin.getLogger().info("Loaded " + messages.size() + " announcement blocks.");
    }

    private String colour(String raw) {
        if (raw == null) return "";
        // The tips were written with discord.gg/spacerng, which is not the
        // real invite; they follow discord.invite now (V248).
        String invite = plugin.getConfig().getString("discord.invite", "https://discord.gg/E8V67kjAj")
                .replaceFirst("^https?://", "");
        return ChatColor.translateAlternateColorCodes('&', raw.replace("discord.gg/spacerng", invite));
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    public boolean isEnabled() {
        return enabled && !messages.isEmpty();
    }

    /**
     * The Discord tip comes round more often than the rest (V260): every
     * discord-every-th announcement is that one, and the normal rotation
     * skips it. 0 puts it back in line with the others.
     */
    private int discordEvery = 2;
    private int sent;

    /**
     * Credits for feedback (V261). Takes turns with the Discord tip in its
     * slot, since both send people to the Discord. From
     * announcements.feedback, or these lines when the live config has none.
     */
    private static final List<String> DEFAULT_FEEDBACK = List.of(
            "&3▎ &e▸ &7Found a bug or have an idea? You earn &dCredits&7 for it.",
            "&3▎ &e▸ &7Post it in the &9&lDiscord&7 feedback channel. Type &e/discord&7 to join.");
    private final List<String> feedback = new ArrayList<>();
    private boolean feedbackTurn;

    private int discordBlock() {
        for (int i = 0; i < messages.size(); i++) {
            for (String line : messages.get(i)) {
                if (line.toLowerCase(java.util.Locale.ROOT).contains("discord.gg")) return i;
            }
        }
        return -1;
    }

    /** Sends the next block, then moves the pointer on. */
    public void broadcastNext() {
        if (!isEnabled()) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return; // nothing to say it to

        int discord = discordEvery > 0 ? discordBlock() : -1;
        sent++;
        if (discord >= 0 && sent % discordEvery == 0) {
            feedbackTurn = !feedbackTurn;
            send(feedbackTurn && !feedback.isEmpty() ? feedback : messages.get(discord));
            return;
        }
        if (discord >= 0 && next % messages.size() == discord && messages.size() > 1) {
            next = (next + 1) % messages.size();
        }
        List<String> block = messages.get(next % messages.size());
        next = (next + 1) % messages.size();
        send(block);
    }

    /** Sends a specific block by index - used by /rngadmin announce. */
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
                player.sendMessage(LEGACY.deserialize(line).replaceText(LINKS));
            }
            if (!footer.isEmpty()) {
                player.sendMessage(footer);
            }
            player.sendMessage("");
        }
    }
}
