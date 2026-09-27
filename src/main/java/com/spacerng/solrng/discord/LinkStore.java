package com.spacerng.solrng.discord;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which Discord account belongs to which player, since V230 kept by the
 * plugin itself in links.yml rather than by DiscordSRV.
 *
 * A link is made the other way round from DiscordSRV: /link in game hands
 * out a six digit code, and the player types it into the bot in Discord
 * (the button on the link card, the /link slash command, or the code on
 * its own in the link channel). Codes live five minutes and only in
 * memory.
 *
 * Thread safe on purpose: the Discord side answers on JDA's threads, and a
 * lookup from there must never race a save on the main thread.
 */
public class LinkStore {

    private static final long CODE_MILLIS = 5L * 60_000L;

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<UUID, String> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, UUID> byDiscord = new ConcurrentHashMap<>();
    private final Map<String, Pending> codes = new ConcurrentHashMap<>();

    private record Pending(UUID uuid, long expires) {
    }

    public LinkStore(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "links.yml");
        load();
    }

    private void load() {
        byPlayer.clear();
        byDiscord.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var section = yml.getConfigurationSection("links");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String discordId = section.getString(key, "");
                if (discordId.isEmpty()) continue;
                byPlayer.put(uuid, discordId);
                byDiscord.put(discordId, uuid);
            } catch (IllegalArgumentException ignored) {
                // A hand edited line that is not a UUID.
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : byPlayer.entrySet()) {
            yml.set("links." + entry.getKey(), entry.getValue());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save links.yml (" + ex.getMessage() + ").");
        }
    }

    public String discordOf(UUID uuid) {
        return uuid == null ? null : byPlayer.get(uuid);
    }

    public UUID playerOf(String discordId) {
        return discordId == null ? null : byDiscord.get(discordId);
    }

    public int count() {
        return byPlayer.size();
    }

    /** A fresh code for this player; any older code of theirs stops working. */
    public String newCode(UUID uuid) {
        codes.values().removeIf(p -> p.uuid().equals(uuid) || p.expires() < System.currentTimeMillis());
        String code;
        do {
            code = String.valueOf(100_000 + ThreadLocalRandom.current().nextInt(900_000));
        } while (codes.containsKey(code));
        codes.put(code, new Pending(uuid, System.currentTimeMillis() + CODE_MILLIS));
        return code;
    }

    /**
     * Uses a code. Returns the player it belonged to, or null when it is
     * unknown or ran out. A used code is gone.
     */
    public UUID redeem(String code) {
        if (code == null) return null;
        Pending pending = codes.remove(code.trim());
        if (pending == null || pending.expires() < System.currentTimeMillis()) return null;
        return pending.uuid();
    }

    public void link(UUID uuid, String discordId) {
        String oldDiscord = byPlayer.remove(uuid);
        if (oldDiscord != null) byDiscord.remove(oldDiscord);
        UUID oldPlayer = byDiscord.remove(discordId);
        if (oldPlayer != null) byPlayer.remove(oldPlayer);
        byPlayer.put(uuid, discordId);
        byDiscord.put(discordId, uuid);
        save();
    }

    public boolean unlink(UUID uuid) {
        String discordId = byPlayer.remove(uuid);
        if (discordId == null) return false;
        byDiscord.remove(discordId);
        save();
        return true;
    }
}
