package com.spacerng.solrng.firsts;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import com.spacerng.solrng.roll.RollAura;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server First 10. The first players to roll a tracked rarity each take a
 * numbered spot, and the whole server is told: a banner in chat, a toast,
 * the rarity's colour drifting down around every online player and a
 * pillar over spawn.
 *
 * Spots live in firsts.yml and are the only record; a player's status is
 * read from here rather than copied onto their player file. Only the real
 * roll path calls {@link #onRoll}, so an admin roll can never take a spot.
 */
public final class FirstTenManager {

    public record Entry(UUID uuid, String name, String item, long at) {
    }

    /** A spot a player holds: the rarity, and their place in it. */
    public record Spot(Rarity rarity, int place) {
    }

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<Rarity, List<Entry>> entries = new EnumMap<>(Rarity.class);

    public FirstTenManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "firsts.yml");
        load();
    }

    // ------------------------------------------------------------- settings

    public List<Rarity> trackedRarities() {
        List<Rarity> tracked = new ArrayList<>();
        if (!plugin.getConfig().getBoolean("first-ten.enabled", true)) return tracked;
        List<String> names = plugin.getConfig().getStringList("first-ten.rarities");
        if (names.isEmpty()) names = List.of("LEGENDARY", "MYTHICAL", "DIVINE");
        for (String name : names) {
            try {
                Rarity rarity = Rarity.valueOf(name.trim().toUpperCase(Locale.ROOT));
                if (!tracked.contains(rarity)) tracked.add(rarity);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("first-ten.rarities has an unknown rarity: " + name);
            }
        }
        tracked.sort(Comparator.naturalOrder());
        return tracked;
    }

    public int slots() {
        return Math.max(1, plugin.getConfig().getInt("first-ten.slots", 10));
    }

    // ---------------------------------------------------------------- reads

    public List<Entry> entries(Rarity rarity) {
        return List.copyOf(entries.getOrDefault(rarity, List.of()));
    }

    /** Every spot this player holds, rarest first, then best place. */
    public List<Spot> spotsOf(UUID uuid) {
        List<Spot> spots = new ArrayList<>();
        for (Map.Entry<Rarity, List<Entry>> row : entries.entrySet()) {
            List<Entry> list = row.getValue();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).uuid().equals(uuid)) spots.add(new Spot(row.getKey(), i + 1));
            }
        }
        spots.sort(Comparator.comparing(Spot::rarity, Comparator.<Rarity>reverseOrder())
                .thenComparingInt(Spot::place));
        return spots;
    }

    // --------------------------------------------------------------- writes

    /**
     * A real roll just landed. Takes a spot if the rarity is tracked, one is
     * still free and this player doesn't already hold one in it, then
     * schedules the event for after the reveal has played out.
     */
    public void onRoll(Player player, RollableItem item, long delayTicks) {
        Rarity rarity = item.getRarity();
        if (!trackedRarities().contains(rarity)) return;
        List<Entry> list = entries.computeIfAbsent(rarity, key -> new ArrayList<>());
        if (list.size() >= slots()) return;
        // One spot per player per rarity, so one lucky streak can't eat the list.
        for (Entry held : list) {
            if (held.uuid().equals(player.getUniqueId())) return;
        }

        list.add(new Entry(player.getUniqueId(), player.getName(), item.getDisplayName(),
                System.currentTimeMillis()));
        int place = list.size();
        save();

        UUID roller = player.getUniqueId();
        String name = player.getName();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> announce(roller, name, item, place, false), Math.max(1L, delayTicks));
    }

    /** Plays the whole event without recording anything. */
    public void preview(Player viewer, RollableItem item) {
        int place = Math.min(slots(), entries.getOrDefault(item.getRarity(), List.of()).size() + 1);
        announce(viewer == null ? null : viewer.getUniqueId(),
                viewer == null ? "Console" : viewer.getName(), item, place, true);
    }

    public void reset(Rarity rarity) {
        entries.remove(rarity);
        save();
    }

    public void resetAll() {
        entries.clear();
        save();
    }

    // ---------------------------------------------------------------- event

    private void announce(UUID roller, String name, RollableItem item, int place, boolean preview) {
        Rarity rarity = item.getRarity();
        int slots = slots();
        int left = Math.max(0, slots - place);
        String word = plugin.getRarityManager().style(rarity, rarity.displayName());
        String article = "AEIOU".indexOf(rarity.name().charAt(0)) >= 0 ? "an " : "a ";

        String header = (preview ? ChatColor.DARK_GRAY + "(preview) " : "")
                + plugin.getRarityManager().style(rarity, "✦ SERVER FIRST " + slots + " ✦");
        String line = ChatColor.YELLOW + name + ChatColor.GRAY + " is " + ChatColor.WHITE + "#" + place
                + ChatColor.GRAY + " of the first " + slots + " to find " + article + word
                + ChatColor.GRAY + ": " + RollFormat.displayName(plugin, item);
        String footer = left > 0
                ? ChatColor.GRAY + "" + left + (left == 1 ? " spot" : " spots") + " left"
                : ChatColor.RED + "That was the last spot.";
        Component banner = LegacyComponentSerializer.legacySection()
                .deserialize(header + "\n" + line + "\n" + footer);

        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean own = online.getUniqueId().equals(roller);
            // The mute is for other people's news; the roller always hears their own.
            if (!own && !plugin.getPlayerDataManager().get(online.getUniqueId()).isBroadcastEnabled(rarity)) {
                continue;
            }
            online.sendMessage(banner);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
            if (own) {
                online.showTitle(Title.title(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(plugin.getRarityManager().style(rarity, "✦ First " + slots + " ✦")),
                        LegacyComponentSerializer.legacySection()
                                .deserialize(ChatColor.GRAY + "You are " + ChatColor.WHITE + "#" + place
                                        + ChatColor.GRAY + " to find " + article + word),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(400))));
            }
        }
        Bukkit.getConsoleSender().sendMessage(banner);
        new Rain(rarity).start();
    }

    /**
     * The server-wide part. Every online player who hasn't muted the rarity
     * or switched its aura off gets its colour drifting down around them,
     * and anyone within 96 blocks of spawn also sees a pillar there. Sent
     * per viewer, so each client only receives its own particles.
     */
    private final class Rain {
        private final Rarity rarity;
        private final long length;
        private final int density;
        private final Particle.DustOptions dust;
        private final BlockData fall;
        private long frame = 0L;
        private BukkitTask task;

        Rain(Rarity rarity) {
            this.rarity = rarity;
            // Scale follows rarity, as everywhere else: Divine is always the biggest.
            this.length = switch (rarity) {
                case DIVINE -> 140L;
                case MYTHICAL -> 100L;
                default -> 80L;
            };
            this.density = switch (rarity) {
                case DIVINE -> 12;
                case MYTHICAL -> 9;
                default -> 6;
            };
            this.dust = new Particle.DustOptions(RollAura.colorFor(rarity), 1.4f);
            this.fall = switch (rarity) {
                case DIVINE -> Material.QUARTZ_BLOCK.createBlockData();
                case MYTHICAL -> Material.REDSTONE_BLOCK.createBlockData();
                default -> Material.GOLD_BLOCK.createBlockData();
            };
        }

        void start() {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 4L);
        }

        private void tick() {
            frame += 4L;
            if (frame > length) {
                task.cancel();
                return;
            }
            try {
                // Thins out over the last quarter so it settles instead of cutting off.
                int count = (double) frame / length > 0.75 ? Math.max(1, density / 2) : density;
                Location spawn = plugin.getSpawnManager().getSpawn();
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.getPlayerDataManager().get(viewer.getUniqueId());
                    if (!data.isBroadcastEnabled(rarity) || !data.isAuraEnabled(rarity)) continue;

                    Location above = viewer.getLocation().add(0, 7, 0);
                    viewer.spawnParticle(Particle.FALLING_DUST, above, count, 6.0, 1.5, 6.0, 0.0, fall);
                    viewer.spawnParticle(Particle.DUST, above.clone().add(0, -2.5, 0),
                            Math.max(1, count / 2), 5.0, 2.0, 5.0, 0.0, dust);

                    if (spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(viewer.getWorld())
                            && spawn.distanceSquared(viewer.getLocation()) < 96.0 * 96.0) {
                        pillar(viewer, spawn);
                    }
                }
            } catch (RuntimeException ex) {
                task.cancel();
                plugin.getLogger().warning("First 10 event failed: " + ex);
            }
        }

        private void pillar(Player viewer, Location spawn) {
            for (double y = 0.0; y < 24.0; y += 1.5) {
                viewer.spawnParticle(Particle.DUST, spawn.clone().add(0, y, 0), 1, 0.25, 0.1, 0.25, 0.0, dust);
            }
            double spin = frame * 0.15;
            for (int i = 0; i < 12; i++) {
                double angle = spin + (Math.PI * 2 / 12) * i;
                viewer.spawnParticle(Particle.DUST,
                        spawn.clone().add(Math.cos(angle) * 3.0, 0.2, Math.sin(angle) * 3.0),
                        1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
    }

    // ------------------------------------------------------------- storage

    public void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Rarity rarity : Rarity.values()) {
            List<Map<?, ?>> rows = yml.getMapList(rarity.name());
            if (rows.isEmpty()) continue;
            List<Entry> list = new ArrayList<>();
            for (Map<?, ?> row : rows) {
                try {
                    list.add(new Entry(UUID.fromString(String.valueOf(row.get("uuid"))),
                            String.valueOf(row.get("name")),
                            String.valueOf(row.get("item")),
                            row.get("at") instanceof Number number ? number.longValue() : 0L));
                } catch (IllegalArgumentException ignored) {
                }
            }
            entries.put(rarity, list);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<Rarity, List<Entry>> row : entries.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Entry entry : row.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("uuid", entry.uuid().toString());
                map.put("name", entry.name());
                map.put("item", entry.item());
                map.put("at", entry.at());
                list.add(map);
            }
            yml.set(row.getKey().name(), list);
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save firsts.yml: " + ex.getMessage());
        }
    }
}
