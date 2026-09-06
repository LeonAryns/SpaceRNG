package com.spacerng.solrng.welcome;

import com.spacerng.solrng.SolRNGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The join banner: the player's own face drawn in chat, with the server's
 * lines beside it.
 *
 * The face is real pixels. Paper hands us the skin's texture URL straight
 * off the player's profile, so there's no Mojang API call to rate-limit
 * and no session-server lookup — we download the PNG once, read the 8x8
 * face out of it, and print each pixel as a coloured block character.
 *
 * The download happens off the main thread and the result is cached for
 * the session. A join message is never worth a server hitch.
 */
public class WelcomeManager {

    // The face is at 8,8 in every skin, 8x8, with the hat layer at 40,8.
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int HAT_X = 40;
    private static final int FACE_SIZE = 8;
    private static final String PIXEL = "██";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final SolRNGPlugin plugin;
    private final Map<UUID, List<String>> faces = new HashMap<>();

    private boolean enabled = true;
    private long delayTicks = 20L;
    private List<String> lines = List.of();

    public WelcomeManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        enabled = config.getBoolean("welcome.enabled", true);
        delayTicks = Math.max(0L, config.getLong("welcome.delay-ticks", 20L));
        lines = config.getStringList("welcome.lines");
    }

    public void forget(UUID uuid) {
        faces.remove(uuid);
    }

    /**
     * Sends the banner, fetching the skin first if this is the first time
     * we've seen this player.
     *
     * Delayed a moment on purpose: a message printed on the same tick as
     * the join lands above the vanilla join spam and scrolls away before
     * anyone reads it.
     */
    public void send(Player player) {
        if (!enabled || lines.isEmpty()) return;

        List<String> cached = faces.get(player.getUniqueId());
        if (cached != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> print(player, cached), delayTicks);
            return;
        }

        String url = skinUrl(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> face = url == null ? blankFace() : renderFace(url);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                faces.put(player.getUniqueId(), face);
                if (player.isOnline()) print(player, face);
            }, delayTicks);
        });
    }

    /** Paper exposes the skin on the profile, so no web lookup is needed. */
    private String skinUrl(Player player) {
        try {
            var textures = player.getPlayerProfile().getTextures();
            URL skin = textures.getSkin();
            return skin == null ? null : skin.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The 8x8 face, hat layer composited over it, as eight strings of
     * coloured blocks.
     */
    private List<String> renderFace(String url) {
        try {
            BufferedImage skin = ImageIO.read(new java.net.URI(url).toURL());
            if (skin == null) return blankFace();

            List<String> rows = new ArrayList<>(FACE_SIZE);
            for (int y = 0; y < FACE_SIZE; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < FACE_SIZE; x++) {
                    int rgb = skin.getRGB(FACE_X + x, FACE_Y + y);
                    // The hat layer is transparent where there's no hat, so
                    // it only overwrites the face where it actually exists.
                    if (skin.getWidth() >= HAT_X + FACE_SIZE) {
                        int hat = skin.getRGB(HAT_X + x, FACE_Y + y);
                        if (((hat >> 24) & 0xFF) > 16) rgb = hat;
                    }
                    row.append(hex(rgb)).append(PIXEL);
                }
                rows.add(row.toString());
            }
            return rows;
        } catch (Exception ex) {
            plugin.getLogger().warning("[SolRNG] Couldn't render a join face: " + ex.getMessage());
            return blankFace();
        }
    }

    private List<String> blankFace() {
        List<String> rows = new ArrayList<>(FACE_SIZE);
        for (int y = 0; y < FACE_SIZE; y++) {
            rows.add(ChatColor.DARK_GRAY + PIXEL.repeat(FACE_SIZE));
        }
        return rows;
    }

    private String hex(int rgb) {
        return net.md_5.bungee.api.ChatColor
                .of(String.format("#%06X", rgb & 0xFFFFFF)).toString();
    }

    /**
     * Prints the face and the text side by side. Config lines are padded
     * out to the height of the face so the block never comes apart, and
     * anything past eight lines simply continues underneath it.
     */
    private void print(Player player, List<String> face) {
        List<String> text = new ArrayList<>();
        for (String line : lines) {
            text.add(ChatColor.translateAlternateColorCodes('&',
                    line.replace("{player}", player.getName())
                        .replace("{online}", String.valueOf(
                                plugin.getServer().getOnlinePlayers().size()))));
        }

        player.sendMessage("");
        int rows = Math.max(face.size(), text.size());
        for (int i = 0; i < rows; i++) {
            String left = i < face.size() ? face.get(i) : ChatColor.RESET + "                ";
            String right = i < text.size() ? text.get(i) : "";
            player.sendMessage(LEGACY.deserialize(left + ChatColor.RESET + "  " + right));
        }
        player.sendMessage("");
    }

    /** So a config reload can be seen without rejoining. */
    public Component preview(Player player) {
        return Component.text("Rejoin, or /rngadmin welcome, to see it.");
    }
}
